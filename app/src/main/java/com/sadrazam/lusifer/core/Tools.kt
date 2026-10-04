package com.sadrazam.lusifer.core

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.sadrazam.lusifer.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Hazırlanmış iş: önce hedef çözülür (uygulama/kişi/dosya), sonra doğrulama, sonra [run]. */
data class Prepared(
    val error: String? = null,
    val danger: String? = null,          // file_delete / app_delete / sms / call / install
    val q1: String = "",
    val q2: String = "",
    val needsUnlock: Boolean = false,
    val run: suspend () -> String = { "" }
)

/**
 * Araç katmanı. run() sonucu: "\n---\n" öncesi SESLİ okunur, tamamı ekranda/geçmişte görünür.
 */
object Tools {
    private fun ok(needsUnlock: Boolean = false, run: suspend () -> String) = Prepared(needsUnlock = needsUnlock, run = run)
    private fun err(m: String) = Prepared(error = m)
    private fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private data class AppInfo(val label: String, val pkg: String)
    private data class Contact(val name: String, val number: String)

    suspend fun prepare(ctx: Context, p: Prefs, a: Action): Prepared {
        val n = p.userName
        return when (a.tool) {
            "time" -> ok { "Saat " + SimpleDateFormat("HH:mm", Tx.tr).format(Date()) }
            "date" -> ok { "Bugün " + SimpleDateFormat("d MMMM yyyy EEEE", Tx.tr).format(Date()) }
            "battery" -> ok {
                val bm = ctx.getSystemService(BatteryManager::class.java)
                "Pil yüzde " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }
            "torch" -> ok { torch(ctx, a.args["on"] == "1") }
            "volume" -> ok { volume(ctx, a.args["dir"] ?: "up") }
            "brightness" -> ok { brightness(ctx, p, a) }
            "wifi" -> toggle(ctx, p, a, "Wi-Fi", "svc wifi", android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "bluetooth" -> toggle(ctx, p, a, "Bluetooth", "svc bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane" -> airplane(ctx, p, a)
            "global" -> ok { globalAct(a.args["a"] ?: "") }
            "screen_read" -> ok { AccService.readScreen() }
            "ui_click" -> ok(true) {
                val t = a.args["text"] ?: ""
                if (AccService.clickText(t)) "Tıkladım" else "Ekranda \"$t\" bulamadım veya erişilebilirlik kapalı."
            }
            "ui_type" -> ok(true) {
                if (AccService.typeText(a.args["text"] ?: "")) "Yazdım" else "Yazı alanı bulunamadı veya erişilebilirlik kapalı."
            }
            "open_app" -> openApp(ctx, p, a)
            "uninstall_app" -> uninstall(ctx, p, a, n)
            "install_app" -> install(ctx, p, a, n)
            "open_url" -> {
                val u = a.args["url"]?.let { if (it.startsWith("http")) it else Tx.toUrl(it) }
                if (u == null) err("Bu adresi anlayamadım.") else ok(true) { openUrl(ctx, u); "Açılıyor" }
            }
            "download_file" -> {
                val u = a.args["url"]?.let { if (it.startsWith("http")) it else Tx.toUrl(it) }
                if (u == null) err("İndirilecek adresi anlayamadım.") else ok { download(ctx, u) }
            }
            "find_file" -> findFile(ctx, p, a)
            "list_dir" -> listDir(ctx, a)
            "read_file" -> readFile(ctx, p, a)
            "delete_file" -> deleteFile(ctx, p, a, n)
            "scan_errors" -> scanErrors(ctx, p, a)
            "call" -> call(ctx, p, a, n)
            "sms" -> sms(ctx, a, n)
            "online_ask" -> ok {
                val q = a.args["q"] ?: ""
                val r = Online.chat(ctx, "Kısa, net ve Türkçe cevap ver. En fazla 3 cümle.", q)
                r.getOrElse { "Online hata: ${it.message}" }
            }
            "say" -> ok { a.args["text"] ?: "" }
            else -> err("Bu aracı tanımıyorum: ${a.tool}")
        }
    }

    // ---------------- Hızlı ayarlar ----------------
    private fun torch(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Fener bulunamadı."
        cm.setTorchMode(id, on)
        return if (on) "Fener açıldı" else "Fener kapatıldı"
    }

    private fun volume(ctx: Context, dir: String): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val d = when (dir) {
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            else -> AudioManager.ADJUST_RAISE
        }
        repeat(if (dir == "mute") 1 else 2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI) }
        return when (dir) { "down" -> "Ses kısıldı"; "mute" -> "Ses kapatıldı"; else -> "Ses yükseltildi" }
    }

    private suspend fun brightness(ctx: Context, p: Prefs, a: Action): String {
        val set = a.args["set"]?.toIntOrNull()
        val delta = a.args["delta"]?.toIntOrNull() ?: 0
        return try {
            if (Settings.System.canWrite(ctx)) {
                val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val v = (if (set != null) set * 255 / 100 else cur + delta).coerceIn(5, 255)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
                "Parlaklık ayarlandı"
            } else if (Shell.available(p)) {
                val cur = (Shell.run(p, "settings get system screen_brightness").out.trim().toIntOrNull()) ?: 128
                val v = (if (set != null) set * 255 / 100 else cur + delta).coerceIn(5, 255)
                Shell.run(p, "settings put system screen_brightness $v")
                "Parlaklık ayarlandı"
            } else "Parlaklık için \"Sistem ayarlarını değiştir\" iznini verin (Yetki ekranı)."
        } catch (e: Exception) { "Parlaklık ayarlanamadı: ${e.message}" }
    }

    private suspend fun toggle(ctx: Context, p: Prefs, a: Action, label: String, cmd: String, panel: String): Prepared {
        val on = a.args["on"] == "1"
        return ok {
            if (Shell.available(p)) {
                val r = Shell.run(p, cmd + if (on) " enable" else " disable")
                if (r.ok) "$label ${if (on) "açıldı" else "kapatıldı"}" else "$label değiştirilemedi: ${r.out.take(80)}"
            } else {
                ctx.startActivity(Intent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "$label ayar paneli açıldı. Sessiz değiştirmek için Shizuku veya Root gerekir."
            }
        }
    }

    private suspend fun airplane(ctx: Context, p: Prefs, a: Action): Prepared {
        val on = a.args["on"] == "1"
        return ok {
            if (Shell.available(p)) {
                val r = Shell.run(p, "cmd connectivity airplane-mode " + if (on) "enable" else "disable")
                if (r.ok) "Uçak modu ${if (on) "açıldı" else "kapatıldı"}" else "Uçak modu değiştirilemedi."
            } else {
                ctx.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Uçak modu ayarı açıldı. Sessiz değiştirmek için Shizuku veya Root gerekir."
            }
        }
    }

    private fun globalAct(a: String): String {
        val svc = AccService.inst ?: return "Erişilebilirlik servisi kapalı. Yetki ekranından açın."
        val act = when (a) {
            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "lock" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            else -> return "Bilinmeyen hareket"
        }
        svc.performGlobalAction(act)
        return "Tamam"
    }

    // ---------------- Uygulamalar ----------------
    private fun apps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private suspend fun openApp(ctx: Context, p: Prefs, a: Action): Prepared {
        val q = a.args["app"] ?: return err("Hangi uygulamayı açayım?")
        val app = Tx.best(q, apps(ctx), { it.label })
        if (app == null) {
            val u = Tx.siteFor(q)
            return if (u != null) ok(true) { openUrl(ctx, "https://$u"); "$q sitesi açılıyor" }
            else err("$q uygulamasını bulamadım.")
        }
        return ok(true) {
            if (launchApp(ctx, p, app.pkg)) "${app.label} açılıyor" else "${app.label} açılamadı. Diğer uygulamaların üzerinde gösterme iznini verin."
        }
    }

    private suspend fun launchApp(ctx: Context, p: Prefs, pkg: String): Boolean {
        if (Shell.available(p)) {
            val r = Shell.run(p, "monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            if (r.ok) return true
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }

    private suspend fun uninstall(ctx: Context, p: Prefs, a: Action, n: String): Prepared {
        val q = a.args["app"] ?: return err("Hangi uygulamayı sileyim?")
        val app = Tx.best(q, apps(ctx), { it.label }) ?: return err("$q uygulamasını bulamadım.")
        return Prepared(
            danger = "app_delete",
            q1 = "${app.label} uygulamasını silmek üzereyim $n, emin misiniz?",
            q2 = "Son kez soruyorum, silinsin mi?",
            needsUnlock = true,
            run = {
                if (Shell.available(p)) {
                    val r = Shell.run(p, "pm uninstall ${app.pkg}")
                    if (r.ok) "${app.label} silindi" else "Silinemedi: ${r.out.take(100)}"
                } else {
                    val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    "Sistem onay penceresi açıldı. Sessiz silme için Shizuku veya Root gerekir."
                }
            }
        )
    }

    private suspend fun install(ctx: Context, p: Prefs, a: Action, n: String): Prepared {
        val name = a.args["name"] ?: ""
        val files = Files.search(ctx, p, name, 5).filter { it.name.endsWith(".apk", true) }
        val f = files.firstOrNull() ?: return err("APK dosyasını bulamadım.")
        return Prepared(
            danger = "install",
            q1 = "${f.name} dosyasını yüklemek üzereyim $n, emin misiniz?",
            q2 = "Son kez soruyorum, yüklensin mi?",
            needsUnlock = true,
            run = {
                if (Shell.available(p)) {
                    val r = Shell.run(p, "pm install -r \"${f.absolutePath}\"")
                    if (r.ok) "${f.name} yüklendi" else "Yüklenemedi: ${r.out.take(100)}"
                } else {
                    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "Önce bilinmeyen kaynaklardan yükleme iznini verin."
                    } else {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(i)
                        "Yükleme penceresi açıldı."
                    }
                }
            }
        )
    }

    // ---------------- Web ----------------
    private fun openUrl(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun download(ctx: Context, url: String): String {
        val name = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "indirilen_${System.currentTimeMillis()}"
        val req = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        ctx.getSystemService(DownloadManager::class.java).enqueue(req)
        return "İndirme başladı: $name"
    }

    // ---------------- Dosyalar ----------------
    private fun storageErr(): String =
        "Dosyalara erişim izni yok. Yetki ekranından \"Tüm dosyalara erişim\" iznini verin."

    private suspend fun findFile(ctx: Context, p: Prefs, a: Action): Prepared {
        val q = a.args["name"] ?: return err("Hangi dosyayı bulayım?")
        if (q.contains("/data/data")) return err(Files.dataDataNote(p))
        return ok {
            if (!Files.canRead()) storageErr()
            else {
                val r = Files.search(ctx, p, q, 8)
                if (r.isEmpty()) "Dosya bulunamadı."
                else {
                    val f = r[0]
                    "${r.size} sonuç buldum. İlki ${f.parentFile?.name} klasöründe ${f.name}.\n---\n" +
                        r.joinToString("\n") { it.absolutePath }
                }
            }
        }
    }

    private suspend fun listDir(ctx: Context, a: Action): Prepared {
        val raw = a.args["path"] ?: ""
        val dir = Files.resolveDir(raw) ?: return err("Klasörü bulamadım.")
        return ok {
            if (!Files.canRead()) storageErr()
            else {
                val items = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
                if (items.isEmpty()) "${dir.name} klasörü boş veya okunamıyor."
                else "${dir.name} klasöründe ${items.size} öğe var. " +
                    items.take(5).joinToString(", ") { it.name } + "\n---\n" +
                    items.take(60).joinToString("\n") { (if (it.isDirectory) "[K] " else "    ") + it.name }
            }
        }
    }

    private suspend fun readFile(ctx: Context, p: Prefs, a: Action): Prepared {
        val q = a.args["name"] ?: return err("Hangi dosyayı okuyayım?")
        return ok {
            if (!Files.canRead()) storageErr()
            else {
                val f = Files.search(ctx, p, q, 1).firstOrNull { it.isFile }
                if (f == null) "Dosyayı bulamadım."
                else {
                    val txt = withContext(Dispatchers.IO) { f.bufferedReader().use { it.readText().take(4000) } }
                    "${f.name} içeriği: ${txt.take(300)}\n---\n$txt"
                }
            }
        }
    }

    private suspend fun deleteFile(ctx: Context, p: Prefs, a: Action, n: String): Prepared {
        val q = a.args["name"] ?: return err("Hangi dosyayı sileyim?")
        if (!Files.canRead()) return err(storageErr())
        val f = Files.search(ctx, p, q, 1).firstOrNull() ?: return err("Dosyayı bulamadım.")
        return Prepared(
            danger = "file_delete",
            q1 = "${f.name} ${if (f.isDirectory) "klasörünü" else "dosyasını"} silmek üzereyim $n, emin misiniz?",
            q2 = "Son kez soruyorum, silinsin mi?",
            run = {
                val okDel = withContext(Dispatchers.IO) { if (f.isDirectory) f.deleteRecursively() else f.delete() }
                if (okDel) "${f.name} silindi" else {
                    if (Shell.available(p)) {
                        val r = Shell.run(p, "rm -rf \"${f.absolutePath}\"")
                        if (r.ok) "${f.name} silindi" else "Silinemedi."
                    } else "Silinemedi."
                }
            }
        )
    }

    private suspend fun scanErrors(ctx: Context, p: Prefs, a: Action): Prepared {
        val q = a.args["name"] ?: ""
        if (q.isBlank()) return err("Hangi dosyadaki hataları bulayım?")
        if (!Files.canRead()) return err(storageErr())
        val f = Files.search(ctx, p, q, 1).firstOrNull { it.isFile } ?: return err("Dosyayı bulamadım.")
        return ok {
            val txt = withContext(Dispatchers.IO) { try { f.readText().take(24000) } catch (e: Exception) { "" } }
            if (txt.isBlank()) "Dosya okunamadı veya boş."
            else {
                val r = Online.chat(
                    ctx,
                    "Sen kıdemli bir hata ayıklama uzmanısın. Verilen dosyadaki hataları, mantık sorunlarını ve eksikleri bul. " +
                        "Türkçe, madde madde, satır numarası/yer belirterek ve düzeltme önererek yaz. Önce tek cümlelik özet ver.",
                    "Dosya adı: ${f.name}\n\n$txt"
                )
                r.fold(
                    onSuccess = { out ->
                        val first = out.lineSequence().firstOrNull { it.isNotBlank() }?.take(260) ?: out.take(260)
                        "${f.name} analiz edildi. $first\n---\n$out"
                    },
                    onFailure = { "Online analiz yapılamadı: ${it.message}" }
                )
            }
        }
    }

    // ---------------- Kişi / arama / mesaj ----------------
    private fun contacts(ctx: Context): List<Contact> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Contact>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) out.add(Contact(c.getString(0) ?: "", c.getString(1) ?: ""))
        }
        return out
    }

    private fun resolveContact(ctx: Context, q: String): Contact? {
        Tx.spokenDigits(Tx.norm(q))?.let { return Contact(it, it) }
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return null
        return Tx.best(q, contacts(ctx), { it.name }, 0.68)
    }

    private suspend fun call(ctx: Context, p: Prefs, a: Action, n: String): Prepared {
        val q = a.args["name"] ?: return err("Kimi arayayım?")
        val digits = Tx.spokenDigits(Tx.norm(q))
        if (digits == null && !has(ctx, Manifest.permission.READ_CONTACTS))
            return err("Kişilere erişim izni yok. Yetki ekranından verin.")
        val c = resolveContact(ctx, q) ?: return err("$q adında bir kişi bulamadım.")
        return Prepared(
            danger = "call",
            q1 = "${c.name} kişisini aramak üzereyim $n, emin misiniz?",
            q2 = "Son kez soruyorum, aransın mı?",
            needsUnlock = true,
            run = {
                val num = c.number
                if (Shell.available(p)) {
                    val r = Shell.run(p, "am start -a android.intent.action.CALL -d tel:${Uri.encode(num)}")
                    if (r.ok) "${c.name} aranıyor" else dial(ctx, num, c.name)
                } else dial(ctx, num, c.name)
            }
        )
    }

    private fun dial(ctx: Context, num: String, name: String): String {
        val action = if (has(ctx, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(num))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "$name aranıyor"
    }

    private suspend fun sms(ctx: Context, a: Action, n: String): Prepared {
        val q = a.args["name"] ?: return err("Kime mesaj atayım?")
        val text = a.args["text"] ?: ""
        if (text.isBlank()) return err("Mesaj metnini anlayamadım.")
        if (!has(ctx, Manifest.permission.SEND_SMS)) return err("SMS izni yok. Yetki ekranından verin.")
        val c = resolveContact(ctx, q) ?: return err("$q adında bir kişi bulamadım.")
        return Prepared(
            danger = "sms",
            q1 = "${c.name} kişisine şu mesajı göndermek üzereyim: $text. Emin misiniz $n?",
            q2 = "Son kez soruyorum, gönderilsin mi?",
            run = {
                val sm = smsManager(ctx)
                sm.sendMultipartTextMessage(c.number, null, sm.divideMessage(text), null, null)
                "Mesaj gönderildi"
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
}

/** Dosya arama / yol çözme. */
object Files {
    fun canRead(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun dataDataNote(p: Prefs) =
        if (p.privMode == "ROOT") "Root ile /data/data okunabilir ama bu sürümde arama yalnızca ortak depolamada yapılır."
        else "Başka uygulamaların /data/data klasörü yalnızca root ile okunur. Shizuku ile sadece /sdcard/Android/data görülebilir."

    fun resolveDir(spoken: String): File? {
        val s = Tx.norm(spoken)
        val root = Environment.getExternalStorageDirectory()
        return when {
            s.isBlank() || s.contains("sdcard") || s.contains("depolama") || s.contains("dahili") -> root
            s.contains("indirilen") || s.contains("download") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            s.contains("dcim") || s.contains("kamera") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            s.contains("belge") || s.contains("document") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            s.contains("resim") || s.contains("foto") || s.contains("pictures") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            s.contains("muzik") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            s.contains("video") || s.contains("movies") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            s.startsWith("/") -> File(spoken.trim())
            else -> {
                val f = search0(root, spoken, 1) { it.isDirectory }
                f.firstOrNull()
            }
        }
    }

    private fun search0(root: File, q: String, limit: Int, accept: (File) -> Boolean): List<File> {
        val qs = Tx.variants(q).filter { it.isNotEmpty() }
        if (qs.isEmpty()) return emptyList()
        val res = ArrayList<File>()
        val start = System.currentTimeMillis()
        val skip = File(root, "Android").absolutePath
        val seq = root.walkTopDown().maxDepth(9).onEnter { d -> !d.name.startsWith(".") && d.absolutePath != skip }
        for (f in seq) {
            if (System.currentTimeMillis() - start > 9000) break
            if (!accept(f)) continue
            val nn = Tx.norm(f.name)
            if (qs.any { nn.contains(it) }) {
                res.add(f)
                if (res.size >= limit * 6) break
            }
        }
        return res.sortedBy { Tx.lev(Tx.norm(it.nameWithoutExtension), qs[0]) }.take(limit)
    }

    suspend fun search(ctx: Context, p: Prefs, q: String, limit: Int): List<File> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        val direct = File(q.trim())
        if (q.trim().startsWith("/") && direct.exists()) return@withContext listOf(direct)
        val root = Environment.getExternalStorageDirectory()
        var r = search0(root, q, limit) { true }
        if (r.isEmpty() && Shell.available(p)) {
            val key = Tx.norm(q).replace(" ", "*")
            val out = Shell.run(p, "find /sdcard/Android/data -maxdepth 5 -iname \"*$key*\" 2>/dev/null | head -n $limit")
            r = out.out.lines().filter { it.startsWith("/") }.map { File(it) }
        }
        r
    }
}
