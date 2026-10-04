package com.sadrazam.lusifer.core

import android.content.Context
import com.sadrazam.lusifer.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/** Uyandırma kelimesi eşleştirme: "LUSİFER" / "Hey LUSİFER" (bulanık, hassasiyet ayarlı). */
object Wake {
    private const val T = "lusifer"

    fun matches(textRaw: String, sens: Int): Boolean {
        val t = Tx.norm(textRaw).trim()
        if (t.isEmpty()) return false
        val th = 0.90 - sens.coerceIn(0, 100) * 0.0030
        val toks = t.split(" ").filter { it.isNotEmpty() }
        val cands = ArrayList<String>(toks)
        for (i in 0 until toks.size - 1) cands.add(toks[i] + toks[i + 1])
        for (c in cands) {
            if (c.length < 4) continue
            if (c.contains(T)) return true
            if (Tx.sim(c, T) >= th) return true
            if (Tx.sim(Tx.phon(c), Tx.phon(T)) >= th) return true
        }
        return false
    }
}

/** Vosk Türkçe (offline): uyandırma dinleme + komut dinleme. Mikrofon aynı anda tek işe verilir. */
class VoskEngine(private val ctx: Context) {
    private var model: Model? = null
    private data class Msg(val text: String?, val wake: Boolean)
    @Volatile private var cur: Channel<Msg>? = null

    val isReady: Boolean get() = model != null

    suspend fun init(onStatus: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        try {
            val dir = File(ctx.filesDir, "vosk-tr")
            if (!File(dir, "ready").exists()) {
                val list = ctx.assets.list("vosk-tr")
                if (list == null || list.isEmpty()) return@withContext false
                onStatus("Ses modeli ilk kez hazırlanıyor…")
                dir.deleteRecursively()
                copyAssets("vosk-tr", dir)
                File(dir, "ready").writeText("1")
            }
            model = Model(dir.absolutePath)
            true
        } catch (e: Throwable) {
            onStatus("Vosk hatası: ${e.message}")
            false
        }
    }

    private fun copyAssets(path: String, dst: File) {
        val names = ctx.assets.list(path) ?: emptyArray()
        if (names.isEmpty()) {
            dst.parentFile?.mkdirs()
            ctx.assets.open(path).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dst.mkdirs()
            for (n in names) copyAssets("$path/$n", File(dst, n))
        }
    }

    /** Dinlemeyi (uyandırma veya komut) anında bırakır. */
    fun cancel() { cur?.trySend(Msg(null, false)) }

    /** Uyandırma kelimesi beklemeden doğrudan komut moduna geç. */
    fun forceWake() { cur?.trySend(Msg(null, true)) }

    private fun jsonText(h: String?): String {
        if (h == null) return ""
        return try {
            val o = JSONObject(h)
            o.optString("text").ifEmpty { o.optString("partial") }
        } catch (e: Exception) { "" }
    }

    /** true: uyandı, false: iptal/hata */
    suspend fun waitWake(p: Prefs): Boolean {
        val m = model ?: return false
        val ch = Channel<Msg>(16)
        val rec = if (p.wakeMode == "GRAMMAR")
            Recognizer(m, 16000f, "[\"lusifer\", \"lüsifer\", \"lucifer\", \"hey lusifer\", \"[unk]\"]")
        else Recognizer(m, 16000f)
        val sens = p.wakeSens
        val grammar = p.wakeMode == "GRAMMAR"
        var svc: SpeechService? = null
        return try {
            svc = SpeechService(rec, 16000f)
            val chk = { h: String? ->
                val t = jsonText(h)
                if (t.isNotBlank()) {
                    val hit = if (grammar) Tx.norm(t).contains("lusifer") || Tx.norm(t).contains("lucifer") || Wake.matches(t, sens)
                    else Wake.matches(t, sens)
                    if (hit) ch.trySend(Msg(null, true))
                }
            }
            val ok = svc.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) { chk(hypothesis) }
                override fun onResult(hypothesis: String?) { chk(hypothesis) }
                override fun onFinalResult(hypothesis: String?) { chk(hypothesis) }
                override fun onError(exception: Exception?) { ch.trySend(Msg(null, false)) }
                override fun onTimeout() {}
            })
            if (!ok) return false
            cur = ch
            ch.receive().wake
        } catch (e: Throwable) {
            false
        } finally {
            cur = null
            withContext(NonCancellable + Dispatchers.IO) {
                try { svc?.stop() } catch (_: Throwable) {}
                try { svc?.shutdown() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Tek komut dinler. Konuşma gelene kadar [noSpeechMs], toplam en fazla [totalMs] bekler.
     * [grammar] verilirse yalnızca bu kelimeler tanınır (evet/hayır doğrulaması için).
     */
    suspend fun listenOnce(totalMs: Int, noSpeechMs: Int, grammar: List<String>? = null): String? {
        val m = model ?: return null
        val ch = Channel<Msg>(16)
        val rec = if (grammar != null)
            Recognizer(m, 16000f, grammar.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" })
        else Recognizer(m, 16000f)
        var svc: SpeechService? = null
        val spoke = java.util.concurrent.atomic.AtomicBoolean(false)
        val lastPartial = java.util.concurrent.atomic.AtomicReference("")
        return try {
            svc = SpeechService(rec, 16000f)
            val ok = svc.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val t = jsonText(hypothesis)
                    if (t.isNotBlank()) { spoke.set(true); lastPartial.set(t) }
                }
                override fun onResult(hypothesis: String?) {
                    val t = jsonText(hypothesis)
                    if (t.isNotBlank()) ch.trySend(Msg(t, false))
                }
                override fun onFinalResult(hypothesis: String?) {
                    val t = jsonText(hypothesis)
                    if (t.isNotBlank()) ch.trySend(Msg(t, false))
                }
                override fun onError(exception: Exception?) { ch.trySend(Msg(null, false)) }
                override fun onTimeout() {}
            })
            if (!ok) return null
            cur = ch
            val t0 = System.currentTimeMillis()
            var result: String? = null
            while (true) {
                val r = withTimeoutOrNull(250) { ch.receive() }
                if (r != null) { result = r.text; break }
                val el = System.currentTimeMillis() - t0
                if (!spoke.get() && el > noSpeechMs) break
                if (el > totalMs) { result = lastPartial.get().ifBlank { null }; break }
            }
            result
        } catch (e: Throwable) {
            null
        } finally {
            cur = null
            withContext(NonCancellable + Dispatchers.IO) {
                try { svc?.stop() } catch (_: Throwable) {}
                try { svc?.shutdown() } catch (_: Throwable) {}
            }
        }
    }
}
