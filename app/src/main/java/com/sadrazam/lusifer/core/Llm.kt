package com.sadrazam.lusifer.core

import android.content.Context
import com.sadrazam.lusifer.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object LlmNative {
    @Volatile var loadedOk = false
    init {
        try { System.loadLibrary("lusifer_llm"); loadedOk = true } catch (e: Throwable) { loadedOk = false }
    }
    external fun load(path: String, nCtx: Int, threads: Int): Long
    external fun generate(h: Long, prompt: String, maxTokens: Int, temp: Float): ByteArray
    external fun free(h: Long)
}

/** Offline LLM (Qwen2.5 GGUF, llama.cpp). Seçili model Ayarlar > Modeller'den belirlenir. */
object Llm {
    private var handle = 0L
    private var loaded = ""
    private val lock = Mutex()
    @Volatile var lastUsed = 0L

    fun fileName(choice: String) = if (choice == "1.5B") "qwen-1.5b.gguf" else "qwen-0.5b.gguf"

    fun modelFile(ctx: Context, choice: String): File {
        val d = File(ctx.filesDir, "models")
        d.mkdirs()
        return File(d, fileName(choice))
    }

    fun inApk(ctx: Context, choice: String): Boolean = try {
        ctx.assets.list("llm")?.contains(fileName(choice)) == true
    } catch (e: Exception) { false }

    fun isReady(ctx: Context, choice: String): Boolean {
        val f = modelFile(ctx, choice)
        return f.exists() && f.length() > 50_000_000L
    }

    fun nativeOk(): Boolean = LlmNative.loadedOk

    /** APK içindeki modeli uygulama dizinine (bir kez) kopyalar. */
    suspend fun prepare(ctx: Context, choice: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (isReady(ctx, choice)) return@withContext true
        if (!inApk(ctx, choice)) return@withContext false
        val out = modelFile(ctx, choice)
        val tmp = File(out.path + ".tmp")
        try {
            val path = "llm/" + fileName(choice)
            var total = 0L
            val ins = try {
                val fd = ctx.assets.openFd(path)
                total = fd.length
                fd.createInputStream()
            } catch (e: Exception) {
                ctx.assets.open(path)
            }
            ins.use { input ->
                tmp.outputStream().use { os ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pc = (done * 100 / total).toInt()
                            if (pc != last) { last = pc; onProgress(pc) }
                        }
                    }
                }
            }
            tmp.renameTo(out)
        } catch (e: Throwable) {
            tmp.delete()
            false
        }
    }

    suspend fun ensureLoaded(ctx: Context, p: Prefs): Boolean = lock.withLock {
        val choice = p.llmChoice
        if (choice == "OFF" || !LlmNative.loadedOk) return@withLock false
        if (handle != 0L && loaded == choice) {
            lastUsed = System.currentTimeMillis()
            return@withLock true
        }
        withContext(Dispatchers.IO) {
            try {
                if (handle != 0L) { LlmNative.free(handle); handle = 0L; loaded = "" }
                if (!prepare(ctx, choice) { }) return@withContext false
                val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
                handle = LlmNative.load(modelFile(ctx, choice).absolutePath, 2048, threads)
                loaded = if (handle != 0L) choice else ""
                lastUsed = System.currentTimeMillis()
                handle != 0L
            } catch (t: Throwable) {
                handle = 0L
                false
            }
        }
    }

    suspend fun complete(prompt: String, maxTokens: Int = 160, temp: Float = 0.3f): String = lock.withLock {
        withContext(Dispatchers.Default) {
            try {
                if (handle == 0L) "" else {
                    lastUsed = System.currentTimeMillis()
                    String(LlmNative.generate(handle, prompt, maxTokens, temp), Charsets.UTF_8)
                }
            } catch (t: Throwable) { "" }
        }
    }

    suspend fun unload() = lock.withLock {
        withContext(Dispatchers.IO) {
            try { if (handle != 0L) LlmNative.free(handle) } catch (_: Throwable) {}
            handle = 0L
            loaded = ""
        }
    }

    fun idleMs(): Long = System.currentTimeMillis() - lastUsed
    fun isLoaded(): Boolean = handle != 0L
}

/** Kural motoru çözemediğinde: offline LLM hem sohbet eder hem JSON araç komutu üretir. */
object Brain {
    data class R(val action: Action?, val say: String?)

    private fun system(name: String) = """Sen LUSİFER'sin; kullanıcının telefonunu yöneten sesli asistansın. Kullanıcıya "$name" diye hitap et. Daima Türkçe, kısa ve net konuş (en fazla 2 cümle).
Kullanıcı telefonda bir iş istiyorsa SADECE şu JSON'u yaz, başka hiçbir şey yazma:
{"tool":"<arac>","args":{...},"say":"<kısa onay>"}
Araçlar: open_app{app}, open_url{url}, call{name}, sms{name,text}, uninstall_app{app}, find_file{name}, read_file{name}, list_dir{path}, torch{on:1 veya 0}, volume{dir:up/down/mute}, wifi{on:1 veya 0}, bluetooth{on:1 veya 0}.
Sadece sohbet ediyorsa JSON yazma, düz Türkçe cevap ver."""

    fun buildPrompt(name: String, input: String, hist: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(system(name)).append("<|im_end|>\n")
        for ((u, a) in hist.takeLast(3)) {
            sb.append("<|im_start|>user\n").append(u.take(200)).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n").append(a.take(300)).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(input.take(400)).append("<|im_end|>\n<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun extractJson(s: String): String? {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        return if (a >= 0 && b > a) s.substring(a, b + 1) else null
    }

    suspend fun ask(ctx: Context, p: Prefs, input: String, hist: List<Pair<String, String>>): R {
        if (p.llmChoice == "OFF") return R(null, null)
        if (!Llm.ensureLoaded(ctx, p)) {
            return R(null, "Çevrimdışı model hazır değil. Modeller bölümünden hazırlayın.")
        }
        val out = Llm.complete(buildPrompt(p.userName, input, hist), 160, 0.3f).trim()
        if (out.isBlank()) return R(null, null)
        val js = extractJson(out)
        if (js != null) {
            try {
                val o = JSONObject(js)
                val tool = o.optString("tool")
                if (tool.isNotBlank() && tool != "null") {
                    val args = HashMap<String, String>()
                    val ao = o.optJSONObject("args")
                    if (ao != null) for (k in ao.keys()) args[k] = ao.optString(k)
                    return R(Action(tool, args), o.optString("say"))
                }
                val s = o.optString("say")
                if (s.isNotBlank()) return R(null, s)
            } catch (_: Exception) { }
        }
        return R(null, out)
    }

    suspend fun debug(ctx: Context, p: Prefs, input: String): String {
        if (p.llmChoice == "OFF") return "Offline model kapalı."
        if (!LlmNative.loadedOk) return "Yerel kütüphane (llama.cpp) yüklenemedi."
        if (!Llm.ensureLoaded(ctx, p)) return "Model yüklenemedi (APK'da var mı? Hazırla'ya bastınız mı?)."
        val out = Llm.complete(buildPrompt(p.userName, input, emptyList()), 120, 0.3f)
        return if (out.isBlank()) "(boş cevap — llama.cpp APK'ya dahil edilmemiş olabilir)" else out
    }
}
