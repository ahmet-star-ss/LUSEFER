package com.sadrazam.lusifer.core

import com.sadrazam.lusifer.Prefs

/**
 * Kimlik / sohbet soruları için ANINDA cevap (LLM'e gitmeden).
 * "Seni kim yaptı", "adın ne", "merhaba" gibi sorular küçük modelde genel kalıp cevaba düşüyordu.
 */
object Smalltalk {
    private fun clean(s: String): String =
        Tx.norm(s).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val greetings = setOf(
        "merhaba", "merhabalar", "selam", "selamlar", "selam lusifer", "merhaba lusifer",
        "gunaydin", "iyi gunler", "iyi aksamlar", "iyi geceler", "hey", "alo", "orada misin", "duyuyor musun"
    )

    fun reply(input: String, p: Prefs): String? {
        val t = clean(input)
        if (t.isBlank()) return null
        val n = p.userName
        val cold = p.persona == "SOGUK"
        fun has(vararg k: String) = k.any { t.contains(it) }

        if (has("kim yapti", "kim yapmis", "kim gelistirdi", "kim tasarladi", "kim olusturdu", "kim kodladi",
                "kim hazirladi", "yapimcin", "yapimcin kim", "yaraticin", "kimin eseri", "seni kim", "kim yazdi"))
            return "Beni BY_SADRAZAM geliştirdi."

        if (has("adin ne", "ismin ne", "adin nedir", "ismin nedir", "senin adin", "senin ismin", "sana ne denir", "sana ne diyeyim"))
            return "Adım LUSİFER."

        if (has("sen kimsin", "kimsin", "sen nesin", "nesin sen", "kendini tanit", "kendini anlat"))
            return "Ben LUSİFER, telefonunuzu sesle yöneten Türkçe asistanım. Beni BY_SADRAZAM geliştirdi."

        if (has("ne yapabilirsin", "neler yapabilirsin", "yeteneklerin", "ne ise yararsin", "neye yararsin", "ozelliklerin"))
            return "Uygulama açar, arama ve mesaj yapar, fener, ses, Wi-Fi ve Bluetooth gibi ayarları yönetir, dosya bulur ve internetten soru sorabilirim."

        if (has("beni taniyor musun", "benim adim ne", "ben kimim"))
            return "Sizi $n olarak tanıyorum."

        if (has("nasilsin", "naber", "ne haber", "iyi misin"))
            return if (cold) "İyiyim." else "İyiyim $n, emrinizdeyim. Siz nasılsınız?"

        if (has("calisiyor musun", "beni duyuyor musun", "orada misin"))
            return if (cold) "Buradayım." else "Evet $n, buradayım ve dinliyorum."

        if (t in greetings || (t.split(" ").size <= 3 && (t.startsWith("merhaba") || t.startsWith("selam"))))
            return if (cold) "Dinliyorum." else "Merhaba $n, dinliyorum."

        return null
    }
}
