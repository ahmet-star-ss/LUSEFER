package com.sadrazam.lusifer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sadrazam.lusifer.Prefs
import com.sadrazam.lusifer.R
import com.sadrazam.lusifer.core.AccService
import com.sadrazam.lusifer.core.Assistant
import com.sadrazam.lusifer.core.LusiferService

enum class Screen(val title: String, val desc: String) {
    HOME("Konuş", "Mikrofon, sohbet ekranı"),
    GECMIS("Komut Geçmişi", "Yapılan işler, geri bakış"),
    MODELLER("Modeller", "Offline model seçimi ve durumu"),
    SES("Ses", "Efekt ayarları: pitch, ring mod, reverb"),
    KISILIK("Kişilik", "SADIK / SOĞUKKANLI / UYARICI, hitap"),
    GUVENLIK("Güvenlik / Onaylar", "Çift sesli doğrulama, tehlikeli işler"),
    YETKI("İzinler / Yetki", "Shizuku / ADB / Root, izinler"),
    API("API Key", "Sağlayıcı, anahtar, otomatik model tespiti"),
    AYARLAR("Ayarlar", "Uyandırma, pil, kurulum rehberi"),
    HAKKINDA("Hakkında", "Yapımcı ve geliştirici kanalı"),
    MENU("Menü", "")
}

object Perms {
    val runtime: Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS
    )

    fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun mic(ctx: Context) = has(ctx, Manifest.permission.RECORD_AUDIO)
    fun allFiles(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun writeSettings(ctx: Context) = Settings.System.canWrite(ctx)
    fun battery(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    fun installUnknown(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
    fun accessibility(): Boolean = AccService.inst != null
}

fun startListening(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(ctx, LusiferService::class.java))
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var askName by remember { mutableStateOf(!prefs.nameSet) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (Perms.mic(ctx) && prefs.autoStart) startListening(ctx)
    }

    // Açılışta (izin varsa) dinlemeyi kendiliğinden başlat (K5)
    LaunchedEffect(Unit) {
        if (prefs.nameSet && prefs.autoStart && Perms.mic(ctx) && !Assistant.serviceOn.value) startListening(ctx)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.MENU) Screen.HOME else Screen.MENU
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Uygulama arka planı: LUSİFER görseli
        Image(
            painterResource(R.drawable.bg_lusifer), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.TopCenter
        )
        if (screen != Screen.HOME) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)))

        val back = { screen = Screen.MENU }
        when (screen) {
            Screen.HOME -> HomeScreen { screen = Screen.MENU }
            Screen.MENU -> MenuScreen(onPick = { screen = it }, onBack = { screen = Screen.HOME })
            Screen.GECMIS -> HistoryPage(back)
            Screen.MODELLER -> ModelsPage(back)
            Screen.SES -> VoicePage(back)
            Screen.KISILIK -> PersonaPage(back)
            Screen.GUVENLIK -> SecurityPage(back)
            Screen.YETKI -> PermsPage(back)
            Screen.API -> ApiPage(back)
            Screen.AYARLAR -> SettingsPage(back)
            Screen.HAKKINDA -> AboutPage(back)
        }

        if (askName) NameDialog(prefs.userName) {
            prefs.userName = it
            askName = false
            permLauncher.launch(Perms.runtime)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Neon.copy(alpha = if (selected) 1f else 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { N(label, 12.sp, if (selected) 1f else 0.5f, bold = selected) }
}

@Composable
fun HomeScreen(onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    val st by Assistant.state.collectAsState()
    val on by Assistant.serviceOn.collectAsState()
    val heard by Assistant.heard.collectAsState()
    val said by Assistant.said.collectAsState()
    val status by Assistant.status.collectAsState()
    var eye by remember { mutableStateOf(p.eyeMode) }
    var text by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (Perms.mic(ctx)) startListening(ctx)
    }
    val toggle: () -> Unit = {
        if (on) {
            ctx.stopService(Intent(ctx, LusiferService::class.java))
        } else if (Perms.mic(ctx)) {
            startListening(ctx)
        } else {
            permLauncher.launch(Perms.runtime)
        }
    }
    val tap: () -> Unit = { if (on) Assistant.manualWake() else toggle() }

    Box(Modifier.fillMaxSize()) {
        if (eye) GreenEye(st, Modifier.fillMaxSize(), tap)

        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("HALKA", !eye) { eye = false; p.eyeMode = false }
                    Chip("GÖZ", eye) { eye = true; p.eyeMode = true }
                }
                N("☰", 26.sp, modifier = Modifier.clickable(onClick = onMenu).padding(horizontal = 8.dp))
            }

            Spacer(Modifier.weight(1f))

            if (!eye) {
                VoiceRing(st, Modifier.fillMaxWidth(0.82f).align(Alignment.CenterHorizontally), tap)
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (status.isNotBlank()) N(status, 11.sp, 0.7f)
                N(
                    when {
                        !on -> "Dinleme kapalı — aşağıdan başlat"
                        st == AssistantState.IDLE -> "${p.userName} — \"LUSİFER\" de"
                        st == AssistantState.WAKE -> "Evet efendim"
                        st == AssistantState.LISTENING -> "Dinliyorum..."
                        st == AssistantState.THINKING -> "Düşünüyorum..."
                        else -> "Konuşuyor..."
                    }, 13.sp, 0.9f, bold = true
                )
                if (heard.isNotBlank()) N("› $heard", 12.sp, 0.7f)
                if (said.isNotBlank()) N(said.take(220), 12.sp, 0.9f)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonButton(if (on) "DURDUR" else "DİNLE", onClick = toggle)
                NeonField(text, { text = it }, "Yazılı komut", Modifier.weight(1f))
                NeonButton("›") {
                    if (text.isNotBlank()) { Assistant.submit(text.trim()); text = "" }
                }
            }
        }
    }
}

@Composable
fun MenuScreen(onPick: (Screen) -> Unit, onBack: () -> Unit) {
    PageFrame("Menü", onBack) {
        Screen.values().filter { it != Screen.MENU }.forEachIndexed { i, s ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .border(1.dp, Neon.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onPick(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                N("${i + 1}", 16.sp, 0.5f, modifier = Modifier.width(28.dp))
                Column {
                    N(s.title, 16.sp, bold = true)
                    if (s.desc.isNotEmpty()) N(s.desc, 11.sp, 0.6f)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(current: String, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF050A06),
        title = { N("Size nasıl hitap edeyim?", 16.sp, bold = true) },
        text = { NeonField(name, { name = it }, "Kullanıcı adı") },
        confirmButton = { TextButton({ onOk(name) }) { N("TAMAM", 14.sp, bold = true) } }
    )
}
