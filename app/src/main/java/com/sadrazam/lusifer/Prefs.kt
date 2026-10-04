package com.sadrazam.lusifer

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Tüm ayarlar. Her okuma doğrudan depodan yapılır (servis ve arayüz aynı değeri görür). */
class Prefs private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("lusifer", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx.applicationContext).also { inst = it } }

        val ALL_CATS: Set<String> = setOf("file_delete", "app_delete", "sms", "call", "install")
    }

    private fun str(k: String, d: String): String = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) { sp.edit().putString(k, v).apply() }
    private fun putB(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    private fun putF(k: String, v: Float) { sp.edit().putFloat(k, v).apply() }
    private fun putI(k: String, v: Int) { sp.edit().putInt(k, v).apply() }

    val nameSet: Boolean get() = sp.getBoolean("nameSet", false)

    var userName: String
        get() = str("user", "BY_SADRAZAM")
        set(v) { sp.edit().putString("user", v.trim().ifEmpty { "BY_SADRAZAM" }).putBoolean("nameSet", true).apply() }

    var eyeMode: Boolean
        get() = sp.getBoolean("eyeMode", false)
        set(v) = putB("eyeMode", v)

    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = putB("autoStart", v)

    // Kişilik: SADIK / SOGUK / UYARICI
    var persona: String
        get() = str("persona", "SADIK")
        set(v) = putS("persona", v)

    var warnLevel: Int
        get() = sp.getInt("warnLevel", 1)
        set(v) = putI("warnLevel", v)

    // Doğrulama
    var confirmEnabled: Boolean
        get() = sp.getBoolean("confirmEnabled", true)
        set(v) = putB("confirmEnabled", v)

    var confirmCats: Set<String>
        get() = HashSet(sp.getStringSet("confirmCats", ALL_CATS) ?: ALL_CATS)
        set(v) { sp.edit().putStringSet("confirmCats", HashSet(v)).apply() }

    // Yetki: NONE / SHIZUKU / ADB / ROOT
    var privMode: String
        get() = str("privMode", "SHIZUKU")
        set(v) = putS("privMode", v)

    // Offline LLM: OFF / 0.5B / 1.5B
    var llmChoice: String
        get() = str("llmChoice", "0.5B")
        set(v) = putS("llmChoice", v)

    // Uyandırma: FLEX (serbest + bulanık eşleşme) / GRAMMAR (kısıtlı sözlük)
    var wakeMode: String
        get() = str("wakeMode", "FLEX")
        set(v) = putS("wakeMode", v)

    var wakeSens: Int
        get() = sp.getInt("wakeSens", 50)
        set(v) = putI("wakeSens", v)

    // Ses efektleri
    var voiceFx: Boolean
        get() = sp.getBoolean("voiceFx", true)
        set(v) = putB("voiceFx", v)
    var fxPitch: Float
        get() = sp.getFloat("fxPitch", 0.86f)
        set(v) = putF("fxPitch", v)
    var fxRing: Float
        get() = sp.getFloat("fxRing", 0.35f)
        set(v) = putF("fxRing", v)
    var fxReverb: Float
        get() = sp.getFloat("fxReverb", 0.30f)
        set(v) = putF("fxReverb", v)
    var fxBits: Float
        get() = sp.getFloat("fxBits", 10f)
        set(v) = putF("fxBits", v)
    var fxRate: Float
        get() = sp.getFloat("fxRate", 1.0f)
        set(v) = putF("fxRate", v)

    // Online API
    var provider: String
        get() = str("provider", "Groq")
        set(v) = putS("provider", v)
    var model: String
        get() = str("model", "")
        set(v) = putS("model", v)
    var customBase: String
        get() = str("customBase", "")
        set(v) = putS("customBase", v)
}

/** API anahtarları: EncryptedSharedPreferences (cihazda şifreli, hiçbir yere gönderilmez). */
object Secure {
    private fun sp(ctx: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "lusifer_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        ctx.getSharedPreferences("lusifer_secure_fallback", Context.MODE_PRIVATE)
    }

    fun getKey(ctx: Context, provider: String): String = sp(ctx).getString("key_$provider", "") ?: ""
    fun setKey(ctx: Context, provider: String, key: String) {
        sp(ctx).edit().putString("key_$provider", key.trim()).apply()
    }
}
