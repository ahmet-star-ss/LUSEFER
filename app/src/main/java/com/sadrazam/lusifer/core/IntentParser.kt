package com.sadrazam.lusifer.core

data class Action(val tool: String, val args: Map<String, String> = emptyMap())

/**
 * KURAL TABANLI niyet çözümü (önce bu çalışır, çözemezse offline LLM devreye girer).
 * Vosk çıktısı: küçük harf, noktalama yok, sayılar kelime olarak gelir.
 */
object IntentParser {

    private fun rest(t: String, drop: Set<String>): String =
        t.split(" ").filter { it.isNotEmpty() && it !in drop }.joinToString(" ")

    private fun dotify(s: String) = s.replace(" nokta ", ".").replace(" nokta", ".").replace("nokta ", ".").trim()

    private val fileWords = setOf(
        "dosyadaki", "dosyasindaki", "kodundaki", "icindeki", "hatalari", "hatalarini", "hatalar", "hata",
        "bul", "bulur", "musun", "misin", "bana", "lutfen", "su", "bu", "dosyasini", "dosyayi", "dosyasi",
        "dosya", "nerede", "benim", "klasorunu", "klasoru", "klasor", "sil", "oku", "kaldir", "apk",
        "yukle", "kur", "apksini", "listele", "goster", "icinde", "ne", "var", "bakar"
    )

    fun parse(input: String): Action? {
        val raw = Tx.lower(input)
        val t = Tx.norm(raw)
        if (t.isBlank()) return null

        fun has(vararg k: String) = k.any { t.contains(it) }
        fun word(vararg k: String) = k.any { Regex("\\b$it\\b").containsMatchIn(t) }
        fun on(): String = if (has("kapat", "sondur", "kapa ", "devre disi")) "0" else "1"

        // ---- Bilgi ----
        if (has("saat kac")) return Action("time")
        if (has("hangi gun", "bugun gunlerden", "tarih ne", "bugunun tarihi")) return Action("date")
        if (word("pil") && has("yuzde", "kac", "seviye", "ne kadar", "durum")) return Action("battery")

        // ---- Hızlı ayarlar ----
        if (has("fener")) return Action("torch", mapOf("on" to on()))
        if (word("ses", "sesi")) {
            if (has("sessize", "sessiz")) return Action("volume", mapOf("dir" to "mute"))
            if (has("arttir", "artir", "yukselt", "yukari", "ac ", "yuksel")) return Action("volume", mapOf("dir" to "up"))
            if (has("kis", "azalt", "dusur", "asagi")) return Action("volume", mapOf("dir" to "down"))
        }
        if (has("parlak")) {
            val n = Tx.number(t.replace("yuzde", "").trim())
            return when {
                has("yuzde") && n != null -> Action("brightness", mapOf("set" to n.toString()))
                has("azalt", "kis", "dusur") -> Action("brightness", mapOf("delta" to "-40"))
                else -> Action("brightness", mapOf("delta" to "40"))
            }
        }
        if (has("wifi", "wi fi", "vayfay", "kablosuz ag", "kablosuz internet")) return Action("wifi", mapOf("on" to on()))
        if (has("bluetooth", "blutuz", "blutut")) return Action("bluetooth", mapOf("on" to on()))
        if (has("ucak modu")) return Action("airplane", mapOf("on" to on()))

        // ---- Sistem hareketleri (erişilebilirlik) ----
        if (has("geri git", "geri don", "geri tusu")) return Action("global", mapOf("a" to "back"))
        if (has("ana ekrana", "ana ekran")) return Action("global", mapOf("a" to "home"))
        if (has("son uygulamalar", "acik uygulamalar")) return Action("global", mapOf("a" to "recents"))
        if (has("bildirim") && has("ac", "goster", "indir")) return Action("global", mapOf("a" to "notifications"))
        if (has("ekrani kilitle", "telefonu kilitle")) return Action("global", mapOf("a" to "lock"))
        if (has("ekranda ne yaziyor", "ekrani oku", "ekrandakini oku")) return Action("screen_read")
        Regex("^(?:ekranda )?(.+?) (?:butonuna |dugmesine |yazisina |)(?:tikla|dokun)$").find(t)?.let {
            return Action("ui_click", mapOf("text" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)))
        }
        Regex("^yaz (.+)$").find(t)?.let {
            return Action("ui_type", mapOf("text" to raw.substring(it.groups[1]!!.range.first)))
        }

        // ---- İnternet / yapay zeka (ONLINE) ----
        Regex("^(?:yapay zekaya sor|internetten sor|yapay zekaya|ai ye sor) (.+)$").find(t)?.let {
            return Action("online_ask", mapOf("q" to raw.substring(it.groups[1]!!.range.first)))
        }
        Regex("^(.+?) (?:diye )?(?:internette|google da|googleda|google de) ara$").find(t)?.let {
            val q = raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)
            return Action("open_url", mapOf("url" to "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")))
        }

        // ---- Dosya hataları (ONLINE) ----
        if (has("hata") && has("bul")) {
            val name = dotify(rest(t, fileWords))
            return Action("scan_errors", mapOf("name" to name))
        }

        // ---- Dosya işleri ----
        if ((has("dosya", "klasor") ) && has(" sil", "kaldir") && !has("uygulama")) {
            return Action("delete_file", mapOf("name" to dotify(rest(t, fileWords))))
        }
        if (has("apk") && has("yukle", "kur")) {
            return Action("install_app", mapOf("name" to dotify(rest(t, fileWords))))
        }
        if (has("klasor") && has("ne var", "listele", "goster", "icinde")) {
            return Action("list_dir", mapOf("path" to dotify(rest(t, fileWords))))
        }
        if (has("dosya") && has(" oku", "okur musun")) {
            return Action("read_file", mapOf("name" to dotify(rest(t, fileWords))))
        }
        if (has("dosya bul", "dosyasini bul", "dosyayi bul", "nerede") && !has("hata")) {
            val n = dotify(rest(t, fileWords))
            if (n.isNotBlank()) return Action("find_file", mapOf("name" to n))
        }

        // ---- İndirme ----
        Regex("^(?:su |bu )?(.+?) (?:dosyasini |linkini |)indir$").find(t)?.let {
            return Action("download_file", mapOf("url" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)))
        }
        Regex("^indir (.+)$").find(t)?.let {
            return Action("download_file", mapOf("url" to raw.substring(it.groups[1]!!.range.first)))
        }

        // ---- Mesaj ----
        Regex("^(.+?) (?:e |a |ye |ya |ne |na )?(?:bir )?mesaj (?:at|gonder|yaz|yolla)(?: (.+))?$").find(t)?.let {
            val name = raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)
            val g2 = it.groups[2]
            val text = if (g2 != null) raw.substring(g2.range.first) else ""
            return Action("sms", mapOf("name" to name, "text" to text))
        }
        Regex("^mesaj (?:at|gonder|yaz|yolla) (\\S+) (.+)$").find(t)?.let {
            return Action("sms", mapOf(
                "name" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1),
                "text" to raw.substring(it.groups[2]!!.range.first)
            ))
        }

        // ---- Arama ----
        Regex("^(?:lutfen )?ara (.+)$").find(t)?.let {
            return Action("call", mapOf("name" to raw.substring(it.groups[1]!!.range.first)))
        }
        Regex("^(.+?) (?:i |yi |u |yu |)(?:ara|arar misin|arasana|arayin|aramak istiyorum)$").find(t)?.let {
            if (!it.groupValues[1].contains("uygulama")) {
                return Action("call", mapOf("name" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)))
            }
        }

        // ---- Uygulama sil / site aç / uygulama aç ----
        Regex("^(.+?) (?:uygulamasini |programini |)(?:sil|kaldir|yok et)$").find(t)?.let {
            return Action("uninstall_app", mapOf("app" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)))
        }
        Regex("^(.+?) (?:sitesini |sitesine |adresini |linkini |linke |sayfasini |)(?:ac|git|gir)$").find(t)?.let {
            val a = raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)
            if (Tx.looksLikeUrl(a)) return Action("open_url", mapOf("url" to a))
        }
        Regex("^(?:lutfen )?(.+?) (?:uygulamasini |programini |oyununu |)(?:ac|baslat|calistir)(?: lutfen)?$").find(t)?.let {
            return Action("open_app", mapOf("app" to raw.substring(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)))
        }
        Regex("^(?:ac|baslat|calistir) (.+)$").find(t)?.let {
            return Action("open_app", mapOf("app" to raw.substring(it.groups[1]!!.range.first)))
        }

        return null
    }
}
