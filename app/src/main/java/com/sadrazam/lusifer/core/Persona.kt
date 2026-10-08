package com.sadrazam.lusifer.core

import com.sadrazam.lusifer.Prefs

/** Kişilik modları: SADIK / SOGUK (soğukkanlı) / UYARICI. Asistan kullanıcıyı asla reddetmez. */
object Persona {
    fun wakePhrases(p: Prefs): List<String> {
        val n = p.userName
        return when (p.persona) {
            "SOGUK" -> listOf("Evet.", "Dinliyorum.")
            "UYARICI" -> listOf("Evet efendim, dinliyorum.", "Buradayım $n.")
            else -> listOf("Evet efendim.", "Emredin $n.", "Dinliyorum efendim.")
        }
    }

    fun wake(p: Prefs): String = wakePhrases(p).random()

    fun wait(p: Prefs) = if (p.persona == "SOGUK") "Bekleyin." else "Bir saniye efendim."

    fun misunderstood(p: Prefs) = "${p.userName}, sizi anlayamadım, tekrar edebilir misiniz?"

    fun cancelled(p: Prefs) = when (p.persona) {
        "SOGUK" -> "İptal edildi."
        else -> "Vazgeçildi ${p.userName}, işlem yapılmadı."
    }

    fun bye(p: Prefs) = when (p.persona) {
        "SOGUK" -> "Tamam."
        else -> "Emredersiniz, çağırdığınızda buradayım."
    }

    fun unlock(p: Prefs) = if (p.persona == "SOGUK") "Kilidi açın." else "Kilidi açın efendim."

    /** UYARICI kipinde riskli işte itiraz/uyarı (yine de karar kullanıcıda). */
    fun warn(p: Prefs, cat: String): String {
        if (p.persona != "UYARICI" || p.warnLevel <= 0) return ""
        val n = p.userName
        val what = when (cat) {
            "file_delete" -> "Silinen dosya geri gelmeyebilir."
            "app_delete" -> "Uygulamanın verileri de gider."
            "sms" -> "Gönderilen mesaj geri alınamaz."
            "call" -> "Arama ücretli olabilir."
            else -> "Bu işlem riskli olabilir."
        }
        return if (p.warnLevel >= 2) "Uyarıyorum $n, bu işlem tehlikeli. $what Yine de karar sizin. "
        else "Dikkat. $what "
    }

    private val stopWords = setOf(
        "tamam", "kapat", "sus", "gorusuruz", "iptal", "bu kadar", "hepsi bu kadar",
        "tesekkurler", "tesekkur ederim", "gorusmek uzere", "kapat artik"
    )

    fun isStop(input: String): Boolean = Tx.norm(input).trim() in stopWords
}
