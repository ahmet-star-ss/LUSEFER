package com.sadrazam.lusifer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sadrazam.lusifer.Prefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class Pcm(val samples: FloatArray, val sr: Int)

/** "Anonymous / Deep Hacker Voice": pitch düşürme + ring modülasyon + bit-crush + yankı. */
object Fx {
    fun apply(src: Pcm, p: Prefs): Pair<ShortArray, Int> {
        var x = src.samples
        val sr = src.sr
        if (p.voiceFx) {
            // 1) Pitch düşürme (yeniden örnekleme: ses kalınlaşır)
            val r = p.fxPitch.coerceIn(0.5f, 1.0f)
            if (r < 0.995f) {
                val n = (x.size / r).toInt()
                val y = FloatArray(n)
                for (i in 0 until n) {
                    val pos = i * r
                    val i0 = pos.toInt()
                    val fr = pos - i0
                    val a = x[minOf(i0, x.size - 1)]
                    val b = x[minOf(i0 + 1, x.size - 1)]
                    y[i] = a + (b - a) * fr
                }
                x = y
            }
            // 2) Ring modülasyon (robotik / metalik tını)
            val mix = p.fxRing.coerceIn(0f, 1f)
            if (mix > 0.01f) {
                val w = 2.0 * PI * 55.0 / sr
                val y = FloatArray(x.size)
                for (i in x.indices) {
                    val c = sin(w * i).toFloat()
                    y[i] = x[i] * (1f - mix) + x[i] * c * mix * 1.6f
                }
                x = y
            }
            // 3) Bit-crush + örnek tutma
            val bits = p.fxBits.coerceIn(4f, 16f)
            if (bits < 15.5f) {
                val levels = 2f.pow(bits - 1f)
                val hold = if (bits <= 8f) 3 else if (bits <= 11f) 2 else 1
                val y = FloatArray(x.size)
                var held = 0f
                for (i in x.indices) {
                    if (i % hold == 0) held = (x[i] * levels).roundToInt() / levels
                    y[i] = held
                }
                x = y
            }
            // 4) Yankı / hafif reverb (3 comb filtresi)
            val rv = p.fxReverb.coerceIn(0f, 1f)
            if (rv > 0.01f) {
                val pad = (sr * 0.55).toInt()
                val len = x.size + pad
                val wet = FloatArray(len)
                val delaysMs = intArrayOf(37, 61, 89)
                val gains = floatArrayOf(0.42f, 0.32f, 0.24f)
                for (k in delaysMs.indices) {
                    val d = delaysMs[k] * sr / 1000
                    val buf = FloatArray(len)
                    for (i in 0 until len) {
                        val inp = if (i < x.size) x[i] else 0f
                        buf[i] = inp + (if (i >= d) gains[k] * buf[i - d] else 0f)
                        wet[i] += buf[i] / 3f
                    }
                }
                val y = FloatArray(len)
                for (i in 0 until len) {
                    val dry = if (i < x.size) x[i] else 0f
                    y[i] = dry * (1f - rv * 0.5f) + wet[i] * rv * 1.2f
                }
                x = y
            }
        }
        // Normalize + yumuşak sınırlama
        var peak = 0f
        for (v in x) if (abs(v) > peak) peak = abs(v)
        val g = if (peak > 0.0001f) 0.9f / peak else 1f
        val out = ShortArray(x.size)
        for (i in x.indices) {
            val v = (x[i] * g).coerceIn(-1f, 1f)
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out to sr
    }
}

/** Offline TTS: Android TTS (Türkçe) sesi dosyaya üretilir, efekt zincirinden geçirilip çalınır. */
class TtsEngine(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile var ready = false
    @Volatile var turkish = false
    private val waits = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var stopReq = false

    suspend fun init(): Boolean {
        if (ready) return true
        val d = CompletableDeferred<Boolean>()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { st ->
            val t = engine
            if (st == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Tx.tr)
                turkish = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { waits.remove(utteranceId)?.complete(true) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { waits.remove(utteranceId)?.complete(false) }
                    override fun onError(utteranceId: String?, errorCode: Int) { waits.remove(utteranceId)?.complete(false) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { waits.remove(utteranceId)?.complete(false) }
                })
                ready = true
            }
            d.complete(ready)
        }
        tts = engine
        return withTimeoutOrNull(10000) { d.await() } ?: false
    }

    fun stop() {
        stopReq = true
        try { tts?.stop() } catch (_: Throwable) {}
        try { track?.stop() } catch (_: Throwable) {}
    }

    private fun clean(s: String): String =
        s.replace(Regex("[*_#`>~|]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun chunks(text: String): List<String> {
        val out = ArrayList<String>()
        val parts = text.split(Regex("(?<=[.!?:;])\\s+"))
        var cur = StringBuilder()
        for (p in parts) {
            if (cur.length + p.length > 260 && cur.isNotEmpty()) { out.add(cur.toString().trim()); cur = StringBuilder() }
            cur.append(p).append(' ')
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out.flatMap { if (it.length > 300) it.chunked(280) else listOf(it) }
    }

    // Kısa cümleler (uyandırma/onay) bir kez üretilip saklanır: tekrar söylenirken gecikme sıfır.
    private val cache = object : LinkedHashMap<String, Pair<ShortArray, Int>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<ShortArray, Int>>?): Boolean = size > 24
    }

    private fun fxKey(p: Prefs) = "${p.voiceFx}|${p.fxPitch}|${p.fxRing}|${p.fxReverb}|${p.fxBits}|${p.fxRate}"

    private fun configure(t: TextToSpeech, p: Prefs) {
        t.setPitch(if (p.voiceFx) 0.8f else 1.0f)
        val comp = if (p.voiceFx) (1f / p.fxPitch.coerceIn(0.5f, 1f)).coerceIn(1f, 1.6f) else 1f
        t.setSpeechRate((p.fxRate * comp).coerceIn(0.5f, 2.2f))
    }

    private suspend fun prepare(t: TextToSpeech, chunk: String, p: Prefs): Pair<ShortArray, Int>? {
        val key = fxKey(p) + "#" + chunk
        synchronized(cache) { cache[key] }?.let { return it }
        val f = File(ctx.cacheDir, "tts_${System.nanoTime()}.wav")
        val ok = synth(t, chunk, f)
        val pcm = if (ok) readWav(f) else null
        f.delete()
        if (pcm == null) return null
        val r = withContext(Dispatchers.Default) { Fx.apply(pcm, p) }
        if (chunk.length <= 90) synchronized(cache) { cache[key] = r }
        return r
    }

    /** Sık söylenen kısa cümleleri önceden üretir (arka planda çağrılır). */
    suspend fun prewarm(phrases: List<String>) {
        val t = tts ?: return
        if (!ready) return
        val p = Prefs.get(ctx)
        configure(t, p)
        for (ph in phrases) {
            val c = clean(ph)
            if (c.isBlank()) continue
            try { prepare(t, c, p) } catch (_: Throwable) {}
        }
    }

    suspend fun speak(text: String) {
        val t = tts ?: return
        if (!ready) return
        val p = Prefs.get(ctx)
        stopReq = false
        configure(t, p)
        val list = chunks(clean(text))
        if (list.isEmpty()) return
        coroutineScope {
            // Bir parça çalarken sıradaki parça arka planda hazırlanır (parçalar arası boşluk kalmaz).
            var next: Deferred<Pair<ShortArray, Int>?>? = async { prepare(t, list[0], p) }
            for (i in list.indices) {
                if (stopReq) { next?.cancel(); break }
                val cur = next?.await()
                next = if (i + 1 < list.size) async { prepare(t, list[i + 1], p) } else null
                if (stopReq) { next?.cancel(); break }
                if (cur != null) play(cur.first, cur.second) else fallbackSpeak(t, list[i])
            }
            next?.cancel()
        }
    }

    private suspend fun synth(t: TextToSpeech, text: String, f: File): Boolean {
        val id = "s${System.nanoTime()}"
        val d = CompletableDeferred<Boolean>()
        waits[id] = d
        val r = t.synthesizeToFile(text, null, f, id)
        if (r != TextToSpeech.SUCCESS) { waits.remove(id); return false }
        return withTimeoutOrNull(25000) { d.await() } ?: false
    }

    private suspend fun fallbackSpeak(t: TextToSpeech, text: String) {
        val id = "f${System.nanoTime()}"
        val d = CompletableDeferred<Boolean>()
        waits[id] = d
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(30000) { d.await() }
    }

    private fun readWav(f: File): Pcm? {
        if (!f.exists() || f.length() < 48) return null
        val b = f.readBytes()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var sr = 22050
        var ch = 1
        var bits = 16
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val sz = bb.getInt(pos + 4)
            if (id == "fmt ") {
                ch = bb.getShort(pos + 10).toInt()
                sr = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataOff = pos + 8
                dataLen = if (sz <= 0 || dataOff + sz > b.size) b.size - dataOff else sz
                break
            }
            if (sz < 0) break
            pos += 8 + sz + (sz and 1)
        }
        if (dataOff < 0 || bits != 16 || ch < 1) return null
        val frames = dataLen / (2 * ch)
        if (frames <= 0) return null
        val x = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until ch) sum += bb.getShort(dataOff + (i * ch + c) * 2).toFloat() / 32768f
            x[i] = sum / ch
        }
        return Pcm(x, sr)
    }

    private suspend fun play(s: ShortArray, sr: Int) {
        if (s.isEmpty()) return
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(s.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = at
        try {
            at.write(s, 0, s.size)
            at.play()
            val maxMs = s.size * 1000L / sr + 1500
            val t0 = System.currentTimeMillis()
            while (!stopReq && at.playbackHeadPosition < s.size && System.currentTimeMillis() - t0 < maxMs) delay(30)
        } finally {
            try { at.stop() } catch (_: Throwable) {}
            at.release()
            track = null
        }
    }
}
