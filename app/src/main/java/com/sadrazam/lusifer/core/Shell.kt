package com.sadrazam.lusifer.core

import android.content.Context
import android.content.pm.PackageManager
import com.sadrazam.lusifer.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/** Yetki katmanı: Shizuku / Root kabuk komutları. ADB modu, izinleri bir kez adb ile verir (kabuk açmaz). */
object Shell {
    data class Result(val ok: Boolean, val out: String)

    @Volatile private var rootCache: Boolean? = null

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun shizukuAlive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    suspend fun rootAvailable(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force) rootCache?.let { return@withContext it }
        val r = try { execProc(arrayOf("su", "-c", "id")) } catch (e: Throwable) { Result(false, "") }
        val ok = r.ok && r.out.contains("uid=0")
        rootCache = ok
        ok
    }

    /** Seçili moda göre kabuk kullanılabilir mi? */
    suspend fun available(p: Prefs): Boolean = when (p.privMode) {
        "SHIZUKU" -> shizukuReady()
        "ROOT" -> rootAvailable()
        else -> false
    }

    suspend fun run(p: Prefs, cmd: String): Result = withContext(Dispatchers.IO) {
        try {
            when (p.privMode) {
                "ROOT" -> execProc(arrayOf("su", "-c", cmd))
                "SHIZUKU" -> shizukuExec(cmd)
                else -> Result(false, "Kabuk yetkisi yok (Yetki ekranından Shizuku veya Root seçin).")
            }
        } catch (e: Throwable) {
            Result(false, e.message ?: "hata")
        }
    }

    private fun execProc(cmd: Array<String>): Result {
        val pr = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = pr.inputStream.bufferedReader().readText()
        val done = pr.waitFor(25, TimeUnit.SECONDS)
        if (!done) { pr.destroy(); return Result(false, "zaman aşımı") }
        return Result(pr.exitValue() == 0, out.trim())
    }

    private fun shizukuExec(cmd: String): Result {
        if (!shizukuReady()) return Result(false, "Shizuku hazır değil")
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val pr = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = pr.inputStream.bufferedReader().readText()
            val err = pr.errorStream.bufferedReader().readText()
            val code = pr.waitFor()
            Result(code == 0, (out + err).trim())
        } catch (e: Throwable) {
            Result(false, "Shizuku hatası: ${e.message}")
        }
    }

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
}
