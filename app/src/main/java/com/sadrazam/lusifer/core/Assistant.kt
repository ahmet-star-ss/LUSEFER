package com.sadrazam.lusifer.core

import android.app.KeyguardManager
import android.content.Context
import com.sadrazam.lusifer.Prefs
import com.sadrazam.lusifer.ui.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Asistanın beyni: uyandırma -> dinleme -> niyet (kural / LLM) -> doğrulama -> araç -> sesli cevap.
 */
object Assistant {
    lateinit var app: Context
    lateinit var vosk: VoskEngine
    lateinit var tts: TtsEngine

    val state = MutableStateFlow(AssistantState.IDLE)
    val status = MutableStateFlow("")
    val heard = MutableStateFlow("")
    val said = MutableStateFlow("")
    val serviceOn = MutableStateFlow(false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = Mutex()
    private val initMutex = Mutex()
    private val typed = Channel<String>(Channel.UNLIMITED)
    private val chatHistory = mutableListOf<Pair<String, String>>()
    @Volatile private var inited = false

    fun attach(ctx: Context) {
        app = ctx.applicationContext
        vosk = VoskEngine(app)
        tts = TtsEngine(app)
    }

    suspend fun ensureInit(): Boolean = initMutex.withLock {
        if (inited) return@withLock true
        status.value = "Ses modelleri hazırlanıyor…"
        val okV = vosk.init { status.value = it }
        val okT = tts.init()
        inited = okV
        status.value = when {
            !okV -> "Vosk Türkçe modeli bulunamadı (APK'ya gömülmemiş)."
            !okT -> "Metin okuma motoru başlatılamadı (Ayarlar > Ses)."
            !tts.turkish -> "Uyarı: Türkçe ses verisi yok. Telefon TTS ayarlarından Türkçe sesi indirin."
            else -> ""
        }
        okV
    }

    // ---------------- Dışarıdan komut (yazılı) ----------------
    fun submit(text: String) {
        if (text.isBlank()) return
        if (serviceOn.value) {
            typed.trySend(text)
            vosk.cancel()
        } else {
            scope.launch {
                ensureInit()
                process(text)
                state.value = AssistantState.IDLE
            }
        }
    }

    /** Uyandırma kelimesini beklemeden dinlemeye geç (halkaya dokunma). */
    fun manualWake() { if (serviceOn.value) vosk.forceWake() }

    // ---------------- Ana döngü (servis içinde) ----------------
    suspend fun runLoop() {
        if (!ensureInit()) { delay(1500); return }
        val p = Prefs.get(app)
        while (currentCoroutineContext().isActive) {
            state.value = AssistantState.IDLE
            val woke = vosk.waitWake(p)
            val t = typed.tryReceive().getOrNull()
            if (t != null) { process(t); continue }
            if (!woke) { delay(500); continue }
            session()
        }
    }

    private suspend fun session() {
        val p = Prefs.get(app)
        state.value = AssistantState.WAKE
        say(Persona.wake(p))
        delay(200)
        var silent = 0
        while (currentCoroutineContext().isActive) {
            state.value = AssistantState.LISTENING
            val text = vosk.listenOnce(14000, 6500)
            val t = typed.tryReceive().getOrNull()
            val input = t ?: text
            if (input.isNullOrBlank()) {
                silent++
                if (silent >= 2) break else continue
            }
            silent = 0
            if (Persona.isStop(input)) { say(Persona.bye(p)); break }
            process(input)
        }
        state.value = AssistantState.IDLE
        // RAM (S9): bir süre kullanılmazsa LLM boşaltılır
        scope.launch {
            delay(120_000)
            if (Llm.isLoaded() && Llm.idleMs() > 110_000) Llm.unload()
        }
    }

    // ---------------- Konuşma ----------------
    suspend fun say(text: String, display: String? = null) {
        if (text.isBlank()) return
        said.value = display ?: text
        val prev = state.value
        state.value = AssistantState.SPEAKING
        try { tts.speak(text) } finally { state.value = prev }
        delay(150)
    }

    // ---------------- Komut işleme ----------------
    suspend fun process(input: String) {
        busy.withLock {
            val p = Prefs.get(app)
            heard.value = input
            state.value = AssistantState.THINKING

            var action: Action? = IntentParser.parse(input)
            var chatSay: String? = null
            if (action == null) {
                val r = Brain.ask(app, p, input, chatHistory)
                action = r.action
                chatSay = r.say
            }
            if (action == null) {
                val msg = chatSay ?: Persona.misunderstood(p)
                say(msg)
                if (chatSay != null) {
                    chatHistory.add(input to chatSay)
                    if (chatHistory.size > 6) chatHistory.removeAt(0)
                }
                History.add(app, input, msg, if (chatSay != null) "chat" else "anlasilmadi")
                return@withLock
            }

            var a: Action = action
            // Eksik argüman: mesaj metni
            if (a.tool == "sms" && a.args["text"].isNullOrBlank()) {
                say("Ne yazmamı istersiniz?")
                state.value = AssistantState.LISTENING
                val tx = vosk.listenOnce(14000, 7000)
                if (tx.isNullOrBlank()) {
                    say(Persona.misunderstood(p))
                    History.add(app, input, "mesaj metni alınamadı", "sms")
                    return@withLock
                }
                a = a.copy(args = a.args + ("text" to tx))
            }

            state.value = AssistantState.THINKING
            val prep = try {
                Tools.prepare(app, p, a)
            } catch (e: Throwable) {
                Prepared(error = "Hata: ${e.message}")
            }
            if (prep.error != null) {
                say(prep.error)
                History.add(app, input, prep.error, a.tool)
                return@withLock
            }

            val cat = prep.danger
            if (cat != null) {
                val warn = Persona.warn(p, cat)
                if (p.confirmEnabled && cat in p.confirmCats) {
                    if (!confirm(warn + prep.q1)) {
                        val m = Persona.cancelled(p)
                        say(m)
                        History.add(app, input, m, a.tool)
                        return@withLock
                    }
                    if (!confirm(prep.q2)) {
                        val m = Persona.cancelled(p)
                        say(m)
                        History.add(app, input, m, a.tool)
                        return@withLock
                    }
                } else if (warn.isNotBlank()) {
                    say(warn)
                }
            }

            if (prep.needsUnlock) ensureUnlocked(p)
            state.value = AssistantState.THINKING
            val out = try {
                prep.run()
            } catch (e: Throwable) {
                "Hata: ${e.message}"
            }
            val spoken = out.substringBefore("\n---\n")
            say(spoken, out.replace("\n---\n", "\n"))
            History.add(app, input, out.replace("\n---\n", "\n"), a.tool)
        }
    }

    // ---------------- Çift sesli doğrulama (K9) ----------------
    private val yesWords = listOf("evet", "onayliyorum", "sil")
    private val noWords = listOf("hayir", "vazgec")
    private val confirmGrammar = listOf("evet", "onaylıyorum", "sil", "hayır", "vazgeç", "[unk]")

    private suspend fun confirm(question: String): Boolean {
        for (attempt in 0..1) {
            say(if (attempt == 0) question else "Evet ya da hayır diyebilir misiniz?")
            state.value = AssistantState.LISTENING
            val ans = vosk.listenOnce(9000, 6000, confirmGrammar)
            state.value = AssistantState.THINKING
            if (ans.isNullOrBlank()) continue
            val words = Tx.norm(ans).split(" ")
            if (words.any { it in noWords }) return false
            if (words.any { it in yesWords }) return true
        }
        return false
    }

    // ---------------- Kilit (K4 / S2) ----------------
    private suspend fun ensureUnlocked(p: Prefs) {
        val km = app.getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        say(Persona.unlock(p))
        var waited = 0
        while (km.isKeyguardLocked && waited < 45_000) { delay(500); waited += 500 }
        delay(700)
    }
}
