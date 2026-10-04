package com.sadrazam.lusifer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadrazam.lusifer.Prefs
import com.sadrazam.lusifer.R
import com.sadrazam.lusifer.Secure
import com.sadrazam.lusifer.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun startSettings(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

// ======================= KOMUT GEÇMİŞİ =======================
@Composable
fun HistoryPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(History.all(ctx).reversed()) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    PageFrame("Komut Geçmişi", onBack) {
        NeonButton("GEÇMİŞİ SİL") { History.clear(ctx); items = emptyList() }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) N("Henüz komut yok.", 13.sp, 0.7f)
        items.forEach { e ->
            Panel {
                N("${fmt.format(Date(e.time))}  ·  ${e.tool}", 10.sp, 0.5f)
                N("› ${e.heard}", 13.sp, bold = true)
                N(e.reply.take(500), 12.sp, 0.85f)
            }
        }
    }
}

// ======================= MODELLER =======================
@Composable
fun ModelsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(p.llmChoice) }
    var progress by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var test by remember { mutableStateOf("Merhaba, kendini tanıt") }
    var testOut by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val sttOk by remember { mutableStateOf(runCatching { ctx.assets.list("vosk-tr")?.isNotEmpty() == true }.getOrDefault(false)) }
    val provider = Online.current(p)
    @Suppress("UNUSED_VARIABLE") val t = tick

    PageFrame("Modeller", onBack) {
        SectionTitle("OFFLINE LLM (hangisi etkin olacak?)")
        listOf(
            Triple("OFF", "Kapalı", null as String?),
            Triple("0.5B", "Qwen2.5-0.5B  (hızlı, ~400 MB)", "0.5B"),
            Triple("1.5B", "Qwen2.5-1.5B  (daha akıllı, ~1 GB)", "1.5B")
        ).forEach { (k, label, mk) ->
            val desc = if (mk == null) null else
                "APK'da: ${if (Llm.inApk(ctx, mk)) "var" else "yok"} · Hazır: ${if (Llm.isReady(ctx, mk)) "evet" else "hayır"}"
            RadioRow(label, desc, choice == k) {
                choice = k
                p.llmChoice = k
                scope.launch { Llm.unload() }
            }
        }
        N(
            if (Llm.nativeOk()) "Yerel kütüphane (llama.cpp): yüklü" else "Yerel kütüphane (llama.cpp): YÜKLENEMEDİ",
            11.sp, 0.7f
        )
        Spacer(Modifier.height(8.dp))
        if (choice != "OFF") {
            NeonButton(if (busy) "HAZIRLANIYOR…" else "SEÇİLİ MODELİ HAZIRLA / YÜKLE") {
                if (!busy) {
                    busy = true
                    msg = "Model APK'dan kopyalanıyor (ilk seferde birkaç dakika sürebilir)…"
                    scope.launch {
                        val ok = Llm.prepare(ctx, choice) { progress = it }
                        progress = -1
                        msg = if (ok) "Model hazır." else "Model APK içinde bulunamadı veya kopyalanamadı."
                        tick++
                        busy = false
                    }
                }
            }
            if (progress >= 0) N("%$progress", 13.sp, bold = true)
        }
        if (msg.isNotBlank()) N(msg, 12.sp, 0.8f)

        SectionTitle("MODELİ DENE")
        NeonField(test, { test = it }, "Deneme cümlesi")
        Spacer(Modifier.height(6.dp))
        NeonButton("GÖNDER") {
            testOut = "Düşünüyor…"
            scope.launch { testOut = Brain.debug(ctx, p, test); tick++ }
        }
        if (testOut.isNotBlank()) { Spacer(Modifier.height(6.dp)); N(testOut, 12.sp, 0.9f) }

        SectionTitle("OFFLINE SES TANIMA")
        N("Vosk Türkçe modeli: ${if (sttOk) "APK'da var" else "APK'da YOK"}", 12.sp, 0.8f)

        SectionTitle("ONLINE (API)")
        N("Sağlayıcı: ${provider.name}", 12.sp, 0.8f)
        N("Model: ${p.model.ifBlank { provider.model.ifBlank { "seçilmedi" } }}", 12.sp, 0.8f)
        N("Ayar: API Key ekranı", 11.sp, 0.5f)
    }
}

// ======================= SES =======================
@Composable
fun VoicePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()
    var fx by remember { mutableStateOf(p.voiceFx) }
    var pitch by remember { mutableStateOf(p.fxPitch) }
    var ring by remember { mutableStateOf(p.fxRing) }
    var rev by remember { mutableStateOf(p.fxReverb) }
    var bits by remember { mutableStateOf(p.fxBits) }
    var rate by remember { mutableStateOf(p.fxRate) }
    PageFrame("Ses", onBack) {
        N("Anonymous / Deep Hacker Voice efekt zinciri", 12.sp, 0.7f)
        SwitchRow("Efektleri uygula", "Kapalıysa düz Türkçe TTS sesi", fx) { fx = it; p.voiceFx = it }
        SliderRow("Ses derinliği (pitch)", pitch, 0.55f..1.0f, { "%.2f".format(it) }) { pitch = it; p.fxPitch = it }
        SliderRow("Ring modülasyon (robotik)", ring, 0f..0.8f, { "%.2f".format(it) }) { ring = it; p.fxRing = it }
        SliderRow("Yankı / reverb", rev, 0f..0.7f, { "%.2f".format(it) }) { rev = it; p.fxReverb = it }
        SliderRow("Bit-crush (düşük = daha kirli)", bits, 5f..16f, { "${it.toInt()} bit" }, steps = 10) { bits = it; p.fxBits = it }
        SliderRow("Konuşma hızı", rate, 0.7f..1.4f, { "%.2f".format(it) }) { rate = it; p.fxRate = it }
        Spacer(Modifier.height(8.dp))
        NeonButton("SESİ DENE") {
            scope.launch {
                Assistant.ensureInit()
                Assistant.say("Merhaba ${p.userName}. Ben Lusifer. Emrinizdeyim.")
            }
        }
        Spacer(Modifier.height(8.dp))
        NeonButton("TELEFON TTS AYARLARI") {
            startSettings(ctx, Intent("com.android.settings.TTS_SETTINGS"))
        }
        Spacer(Modifier.height(6.dp))
        N("Türkçe konuşmuyorsa: TTS ayarlarında Google (veya yüklü) motor için Türkçe ses verisini indirin.", 11.sp, 0.55f)
    }
}

// ======================= KİŞİLİK =======================
@Composable
fun PersonaPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    var mode by remember { mutableStateOf(p.persona) }
    var warn by remember { mutableStateOf(p.warnLevel.toFloat()) }
    var name by remember { mutableStateOf(p.userName) }
    PageFrame("Kişilik", onBack) {
        N("Asistan size bağlıdır: sizi reddedemez, kilitleyemez, engelleyemez.", 12.sp, 0.7f)
        SectionTitle("KİŞİLİK MODU")
        RadioRow("SADIK", "\"Emredin ${name}.\" Her komutu yapar.", mode == "SADIK") { mode = "SADIK"; p.persona = "SADIK" }
        RadioRow("SOĞUKKANLI", "Kısa, resmi, hacker tarzı cevaplar.", mode == "SOGUK") { mode = "SOGUK"; p.persona = "SOGUK" }
        RadioRow("UYARICI", "Riskli komutta uyarır, ama onaylarsanız yine de yapar.", mode == "UYARICI") { mode = "UYARICI"; p.persona = "UYARICI" }
        SectionTitle("UYARI SEVİYESİ (UYARICI modunda)")
        SliderRow("Seviye", warn, 0f..2f, { listOf("Kapalı", "Normal", "Sert")[it.toInt().coerceIn(0, 2)] }, steps = 1) {
            warn = it; p.warnLevel = it.toInt()
        }
        SectionTitle("HİTAP")
        NeonField(name, { name = it }, "Kullanıcı adı")
        Spacer(Modifier.height(6.dp))
        NeonButton("KAYDET") { p.userName = name }
    }
}

// ======================= GÜVENLİK / ONAYLAR =======================
@Composable
fun SecurityPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    var on by remember { mutableStateOf(p.confirmEnabled) }
    var cats by remember { mutableStateOf(p.confirmCats) }
    val labels = listOf(
        "file_delete" to "Dosya / klasör silme",
        "app_delete" to "Uygulama silme",
        "sms" to "Mesaj gönderme",
        "call" to "Arama yapma",
        "install" to "Uygulama yükleme"
    )
    PageFrame("Güvenlik / Onaylar", onBack) {
        SwitchRow("Tehlikeli işlerde çift doğrulama", "Önce işi ve hedefi söyler, \"evet\" deyince ikinci kez sorar.", on) {
            on = it; p.confirmEnabled = it
        }
        N("Onay kelimeleri: evet / onaylıyorum / sil / hayır / vazgeç. Ekran butonu yoktur, yalnızca sesli.", 11.sp, 0.6f)
        SectionTitle("DOĞRULAMA İSTENEN İŞLER")
        labels.forEach { (k, l) ->
            SwitchRow(l, null, k in cats) { v ->
                cats = if (v) cats + k else cats - k
                p.confirmCats = cats
            }
        }
    }
}

// ======================= İZİNLER / YETKİ =======================
@Composable
private fun PermRow(label: String, ok: Boolean, btn: String, onClick: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                N(label, 13.sp, bold = true)
                N(if (ok) "● Açık" else "○ Kapalı", 11.sp, if (ok) 1f else 0.5f)
            }
            if (!ok) NeonButton(btn, onClick = onClick)
        }
    }
}

@Composable
fun PermsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(p.privMode) }
    var tick by remember { mutableStateOf(0) }
    var rootMsg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    @Suppress("UNUSED_VARIABLE") val t = tick

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val adbCmds = remember {
        val pk = ctx.packageName
        listOf(
            "adb shell pm grant $pk android.permission.WRITE_SECURE_SETTINGS",
            "adb shell pm grant $pk android.permission.RECORD_AUDIO",
            "adb shell pm grant $pk android.permission.READ_CONTACTS",
            "adb shell pm grant $pk android.permission.CALL_PHONE",
            "adb shell pm grant $pk android.permission.SEND_SMS",
            "adb shell pm grant $pk android.permission.POST_NOTIFICATIONS",
            "adb shell appops set $pk MANAGE_EXTERNAL_STORAGE allow",
            "adb shell appops set $pk SYSTEM_ALERT_WINDOW allow",
            "adb shell appops set $pk WRITE_SETTINGS allow",
            "adb shell appops set $pk REQUEST_INSTALL_PACKAGES allow",
            "adb shell dumpsys deviceidle whitelist +$pk"
        ).joinToString("\n")
    }

    PageFrame("İzinler / Yetki", onBack) {
        SectionTitle("YETKİ YÖNTEMİ")
        val shz = if (Shell.shizukuReady()) "Bağlı ve izinli" else if (Shell.shizukuAlive()) "Çalışıyor, izin verilmedi" else "Çalışmıyor (Shizuku'yu başlatın)"
        RadioRow("Shizuku (varsayılan)", "Durum: $shz", mode == "SHIZUKU") { mode = "SHIZUKU"; p.privMode = "SHIZUKU" }
        RadioRow(
            "ADB (kablosuz hata ayıklama)",
            "WRITE_SECURE_SETTINGS: ${if (Shell.canWriteSecure(ctx)) "verildi" else "verilmedi"}",
            mode == "ADB"
        ) { mode = "ADB"; p.privMode = "ADB" }
        RadioRow("Root (Magisk)", if (rootMsg.isBlank()) "Test edilmedi" else rootMsg, mode == "ROOT") { mode = "ROOT"; p.privMode = "ROOT" }
        RadioRow("Yok", "Yalnızca normal izinler ve sistem onay pencereleri", mode == "NONE") { mode = "NONE"; p.privMode = "NONE" }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("SHIZUKU İZNİ İSTE") {
                try { if (Shell.shizukuAlive()) Shizuku.requestPermission(1001) } catch (_: Throwable) {}
            }
            NeonButton("ROOT'U TEST ET") {
                rootMsg = "Test ediliyor…"
                scope.launch { rootMsg = if (Shell.rootAvailable(true)) "Root: var" else "Root: yok / izin verilmedi" }
            }
        }
        Spacer(Modifier.height(6.dp))
        N("Sınır: Başka uygulamaların /data/data klasörü SADECE root ile okunur. Shizuku ile yalnızca /sdcard/Android/data görülebilir.", 11.sp, 0.55f)

        SectionTitle("ADB KOMUTLARI (bir kez çalıştır)")
        Panel {
            N(adbCmds, 10.sp, 0.9f)
        }
        NeonButton("KOMUTLARI KOPYALA") {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("adb", adbCmds))
        }

        SectionTitle("İZİNLER")
        PermRow("Mikrofon, bildirim, kişiler, telefon, SMS", Perms.mic(ctx) && Perms.has(ctx, android.Manifest.permission.READ_CONTACTS) &&
            Perms.has(ctx, android.Manifest.permission.CALL_PHONE) && Perms.has(ctx, android.Manifest.permission.SEND_SMS), "VER") {
            permLauncher.launch(Perms.runtime)
        }
        PermRow("Tüm dosyalara erişim", Perms.allFiles(), "AÇ") {
            startSettings(ctx, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri(ctx)))
        }
        PermRow("Erişilebilirlik servisi", Perms.accessibility(), "AÇ") {
            if (Shell.canWriteSecure(ctx)) {
                try {
                    val cn = "${ctx.packageName}/com.sadrazam.lusifer.core.AccService"
                    val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    if (!cur.contains(cn)) {
                        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (cur.isEmpty()) cn else "$cur:$cn")
                    }
                    Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                } catch (e: Exception) {
                    startSettings(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            } else startSettings(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PermRow("Diğer uygulamaların üzerinde göster (uygulama açmak için)", Perms.overlay(ctx), "AÇ") {
            startSettings(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(ctx)))
        }
        PermRow("Sistem ayarlarını değiştir (parlaklık)", Perms.writeSettings(ctx), "AÇ") {
            startSettings(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(ctx)))
        }
        PermRow("Bilinmeyen kaynaklardan yükleme", Perms.installUnknown(ctx), "AÇ") {
            startSettings(ctx, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri(ctx)))
        }
        PermRow("Pil kısıtlaması yok", Perms.battery(ctx), "AÇ") {
            startSettings(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx)))
        }
    }
}

// ======================= API KEY =======================
@Composable
fun ApiPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(p.provider) }
    var key by remember { mutableStateOf(Secure.getKey(ctx, p.provider)) }
    var model by remember { mutableStateOf(p.model) }
    var base by remember { mutableStateOf(p.customBase) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var msg by remember { mutableStateOf("") }
    PageFrame("API Key", onBack) {
        N("Online mod: site/link açma, indirme ve \"dosyadaki hataları bul\" gibi ağır işler.", 11.sp, 0.6f)
        SectionTitle("SAĞLAYICI")
        Online.providers.forEach { pr ->
            RadioRow(pr.name, pr.base.ifBlank { "kendi adresini gir" }, provider == pr.name) {
                provider = pr.name
                p.provider = pr.name
                key = Secure.getKey(ctx, pr.name)
                model = pr.model
                p.model = pr.model
                models = emptyList()
                msg = ""
            }
        }
        if (provider == Online.CUSTOM) {
            NeonField(base, { base = it; p.customBase = it }, "Taban adres (örn. https://api.x.com/v1)")
            Spacer(Modifier.height(6.dp))
        }
        SectionTitle("API ANAHTARI")
        NeonField(key, { key = it }, "API anahtarı", password = true)
        Spacer(Modifier.height(6.dp))
        NeonField(model, { model = it; p.model = it }, "Model adı (elle yaz veya aşağıdan seç)")
        Spacer(Modifier.height(8.dp))
        NeonButton("KAYDET + TEST ET + MODELLERİ BUL") {
            Secure.setKey(ctx, provider, key)
            p.customBase = base
            msg = "Denetleniyor…"
            scope.launch {
                val r = Online.listModels(Online.current(p).base, key)
                r.fold(
                    onSuccess = {
                        models = it
                        msg = "Anahtar geçerli. ${it.size} model bulundu. Birini seçin."
                    },
                    onFailure = { msg = "Hata: ${it.message}" }
                )
            }
        }
        if (msg.isNotBlank()) { Spacer(Modifier.height(6.dp)); N(msg, 12.sp, 0.9f) }
        if (models.isNotEmpty()) {
            SectionTitle("BULUNAN MODELLER")
            models.take(60).forEach { m ->
                RadioRow(m, null, model == m) { model = m; p.model = m }
            }
        }
    }
}

// ======================= AYARLAR =======================
@Composable
fun SettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    var wakeMode by remember { mutableStateOf(p.wakeMode) }
    var sens by remember { mutableStateOf(p.wakeSens.toFloat()) }
    var auto by remember { mutableStateOf(p.autoStart) }
    var name by remember { mutableStateOf(p.userName) }
    PageFrame("Ayarlar", onBack) {
        SectionTitle("UYANDIRMA")
        RadioRow("Esnek (önerilen)", "Serbest tanıma + bulanık eşleşme: \"lusifer\", \"lüsifer\", \"lucifer\"…", wakeMode == "FLEX") { wakeMode = "FLEX"; p.wakeMode = "FLEX" }
        RadioRow("Gramer (hafif)", "Yalnızca birkaç kelimeye kısıtlı, daha az pil. Model kelimeyi bilmiyorsa duyamaz.", wakeMode == "GRAMMAR") { wakeMode = "GRAMMAR"; p.wakeMode = "GRAMMAR" }
        SliderRow("Uyandırma hassasiyeti", sens, 0f..100f, { "${it.toInt()}" }) { sens = it; p.wakeSens = it.toInt() }
        N("Yüksek = daha kolay uyanır (yanlış tetikleme artabilir).", 11.sp, 0.55f)
        SwitchRow("Uygulama açılınca dinlemeyi başlat", null, auto) { auto = it; p.autoStart = it }

        SectionTitle("HİTAP")
        NeonField(name, { name = it }, "Kullanıcı adı")
        Spacer(Modifier.height(6.dp))
        NeonButton("KAYDET") { p.userName = name }

        SectionTitle("PİL / ARKA PLAN")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("UYGULAMA BİLGİSİ") {
                startSettings(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))
            }
            NeonButton("OTOMATİK BAŞLATMA") {
                try {
                    ctx.startActivity(
                        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    startSettings(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))
                }
            }
        }

        SectionTitle("KURULUM REHBERİ")
        SetupGuide.forEach { (title, body) ->
            var open by remember { mutableStateOf(false) }
            Panel(onClick = { open = !open }) {
                N(title, 14.sp, bold = true)
                if (open) {
                    Spacer(Modifier.height(8.dp))
                    N(body, 12.sp, 0.85f)
                }
            }
        }

        SectionTitle("VERİ")
        NeonButton("KOMUT GEÇMİŞİNİ SİL") { History.clear(ctx) }
        Spacer(Modifier.height(10.dp))
        N("API anahtarları cihazda şifreli saklanır, yalnızca seçilen sağlayıcıya istek atılırken kullanılır.", 11.sp, 0.55f)
    }
}

// ======================= HAKKINDA =======================
@Composable
fun AboutPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    PageFrame("Hakkında", onBack) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_sigil), null, Modifier.size(120.dp))
        }
        Spacer(Modifier.height(16.dp))
        N("LUSİFER", 22.sp, bold = true)
        N("Sesli Android yapay zeka asistanı", 12.sp, 0.7f)
        Spacer(Modifier.height(20.dp))
        N("Yapımcı", 12.sp, 0.6f)
        N("@BY_SADRAZAM", 16.sp, bold = true)
        Spacer(Modifier.height(14.dp))
        N("Geliştirici kanalı (güncellemeler)", 12.sp, 0.6f)
        N(
            "https://t.me/LulzSecARSIV", 14.sp, bold = true,
            modifier = Modifier.clickable { uri.openUri("https://t.me/LulzSecARSIV") }.padding(vertical = 4.dp)
        )
        Spacer(Modifier.height(20.dp))
        N("Sürüm 1.0", 11.sp, 0.5f)
    }
}
