package com.sadrazam.lusifer.core

import java.util.Locale

/** Metin yardımcıları: Türkçe normalize, bulanık eşleşme, söylenen URL / sayı çevirme. */
object Tx {
    val tr: Locale = Locale("tr", "TR")

    fun lower(s: String): String = s.lowercase(tr).trim().replace(Regex("\\s+"), " ")

    /** Karakter karakter 1:1 eşleme (uzunluk değişmez). */
    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase(tr)) {
            sb.append(
                when (c) {
                    'ı' -> 'i'
                    'ş' -> 's'
                    'ğ' -> 'g'
                    'ü' -> 'u'
                    'ö' -> 'o'
                    'ç' -> 'c'
                    'â' -> 'a'
                    'î' -> 'i'
                    'û' -> 'u'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    fun sim(a: String, b: String): Double {
        val m = maxOf(a.length, b.length)
        return if (m == 0) 1.0 else 1.0 - lev(a, b).toDouble() / m
    }

    /** Kaba sesbilgisi anahtarı: "whatsapp" ~ "vatsap", "youtube" ~ "yutup". */
    fun phon(s: String): String {
        var t = norm(s).replace(" ", "")
        t = t.replace("ph", "f").replace("ck", "k").replace("ou", "u").replace("oo", "u").replace("ee", "i")
        t = t.replace('w', 'v').replace('q', 'k').replace("x", "ks").replace('y', 'i')
        t = t.replace('b', 'p').replace('d', 't').replace('g', 'k').replace('c', 'k').replace("h", "")
        val sb = StringBuilder()
        for (c in t) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    private val fillers = listOf(
        "uygulamasini", "uygulamasi", "uygulama", "programini", "programi", "oyununu",
        "dosyasini", "dosyayi", "dosyasi", "dosya", "klasorunu", "klasoru", "lutfen"
    )

    /** Ek (-i, -yi, -e...) olasılıklarına karşı aday yazımlar üretir. */
    fun variants(q: String): List<String> {
        var s = norm(q).trim()
        for (f in fillers) s = s.replace(Regex("\\b$f\\b"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        val out = ArrayList<String>()
        out.add(s)
        for (cut in 1..3) if (s.length - cut >= 3) out.add(s.dropLast(cut))
        val parts = s.split(" ")
        if (parts.size > 1 && parts.last().length <= 2) out.add(parts.dropLast(1).joinToString(" "))
        return out.distinct()
    }

    fun score(q: String, cand: String): Double {
        val c = norm(cand).trim()
        if (c.isEmpty()) return 0.0
        var best = 0.0
        for (v in variants(q)) {
            if (v.isEmpty()) continue
            val s = when {
                v == c -> 1.0
                v.length >= 3 && c.length >= 3 && (c.startsWith(v) || v.startsWith(c)) -> 0.92
                else -> maxOf(
                    sim(phon(v), phon(c)),
                    sim(phon(v), phon(c.substringBefore(' '))) * 0.95
                )
            }
            if (s > best) best = s
        }
        return best
    }

    fun <T> best(q: String, items: List<T>, label: (T) -> String, min: Double = 0.72): T? {
        var bi: T? = null
        var bs = min
        for (item in items) {
            val s = score(q, label(item))
            if (s > bs) {
                bs = s
                bi = item
            }
        }
        return bi
    }

    // ---- Söylenen sayılar ----
    private val units = mapOf(
        "sifir" to 0, "bir" to 1, "iki" to 2, "uc" to 3, "dort" to 4, "bes" to 5,
        "alti" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9
    )
    private val tens = mapOf(
        "on" to 10, "yirmi" to 20, "otuz" to 30, "kirk" to 40, "elli" to 50,
        "altmis" to 60, "yetmis" to 70, "seksen" to 80, "doksan" to 90
    )

    /** "elli beş" -> 55, "yüz" -> 100, "75" -> 75 */
    fun number(normText: String): Int? {
        var total = 0
        var cur = 0
        var any = false
        for (w in normText.split(" ")) {
            val d = w.toIntOrNull()
            when {
                d != null -> { cur += d; any = true }
                units.containsKey(w) -> { cur += units[w]!!; any = true }
                tens.containsKey(w) -> { cur += tens[w]!!; any = true }
                w == "yuz" -> { cur = (if (cur == 0) 1 else cur) * 100; any = true }
                w == "bin" -> { total += (if (cur == 0) 1 else cur) * 1000; cur = 0; any = true }
            }
        }
        return if (any) total + cur else null
    }

    /** "sıfır beş üç iki ..." -> "0532..." (hepsi rakam kelimesiyse) */
    fun spokenDigits(normText: String): String? {
        val toks = normText.split(" ").filter { it.isNotEmpty() }
        if (toks.isEmpty()) return null
        val sb = StringBuilder()
        for (w in toks) {
            val u = units[w]
            if (u != null) sb.append(u)
            else if (w.all { it.isDigit() }) sb.append(w)
            else return null
        }
        return if (sb.length >= 3) sb.toString() else null
    }

    // ---- Söylenen URL ----
    val sites: Map<String, String> = mapOf(
        "google" to "google.com", "gugil" to "google.com", "youtube" to "youtube.com",
        "yutup" to "youtube.com", "instagram" to "instagram.com", "twitter" to "x.com",
        "github" to "github.com", "wikipedia" to "tr.wikipedia.org", "vikipedi" to "tr.wikipedia.org",
        "facebook" to "facebook.com", "netflix" to "netflix.com", "trendyol" to "trendyol.com",
        "hepsiburada" to "hepsiburada.com", "sahibinden" to "sahibinden.com", "gmail" to "mail.google.com",
        "telegram" to "web.telegram.org", "whatsapp" to "web.whatsapp.com", "reddit" to "reddit.com"
    )

    fun siteFor(name: String): String? {
        val k = Tx.best(name, sites.keys.toList(), { it }, 0.8) ?: return null
        return sites[k]
    }

    fun looksLikeUrl(spoken: String): Boolean {
        val t = lower(spoken)
        return t.contains("nokta") || t.contains(".") || Regex("(com|net|org|io|app|dev|tr)$").containsMatchIn(t.replace(" ", ""))
    }

    fun toUrl(spoken: String): String? {
        var t = lower(spoken)
        t = t.replace(" nokta ", ".").replace("nokta", ".")
            .replace(" bolu ", "/").replace(" slash ", "/").replace(" tire ", "-")
            .replace(" alt çizgi ", "_").replace(" ", "")
        if (t.isEmpty()) return null
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        if (t.contains(".")) return "https://$t"
        val s = siteFor(spoken) ?: return null
        return "https://$s"
    }
}
