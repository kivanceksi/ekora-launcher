/*
 * Ekora Launcher Android — modified from Zalith Launcher 2.
 * Copyright (C) 2025-2026 MovTery and contributors; Ekora modifications 2026.
 * GPL-3.0-or-later. Original license and attribution retained in source bundle.
 */
package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.LauncherSettings
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.TitleTaskFlowDialog
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF67E8F9)
private val Muted = Color(0xFF94A3B8)
private val Soft = Color(0xFFCBD5E1)
private val CardColor = Color(0xFF15253D)
private val PanelColor = Color(0xFF161D32)
private val NameRegex = Regex("^[A-Za-z0-9_]{3,16}$")

@Composable
fun LauncherScreen(
    backStackViewModel: ScreenBackStackViewModel,
    navigateToVersions: (Version) -> Unit,
    onLaunchGame: (Version?) -> Unit,
    onQuickPlayServer: (Version?, String) -> Unit,
    onOpenLink: (String) -> Unit,
    startGuideOnce: (GuideKeys.Keys) -> Unit,
) {
    val context = LocalContext.current
    val setup: EkoraSetupViewModel = viewModel()
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val refreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()
    val visible = backStackViewModel.mainScreen.currentKey == NormalNavKey.LauncherMain

    val current = version?.takeIf { it.isValid() }
    val compatible = current?.isEkoraCompatible() == true
    val ekoraVersion = versions.firstOrNull { it.isValid() && it.isEkoraCompatible() }
    val hasFabric = current?.getVersionInfo()?.hasLoader(ModLoader.FABRIC) == true
    val ready = account != null && current != null && !refreshing

    var status by remember { mutableStateOf<ServerStatus?>(null) }
    var update by remember { mutableStateOf<String?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(5) }
    var cancelled by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val autoStart = LauncherSettings.ekoraAutoStart.state
    val quickPlay by rememberUpdatedState(onQuickPlayServer)

    LaunchedEffect(Unit) {
        while (true) {
            status = pingEkora()
            delay(30_000)
        }
    }
    LaunchedEffect(Unit) { update = checkEkoraUpdate() }
    LaunchedEffect(ready, compatible, visible, autoStart, cancelled, attempted, setup.isBusy) {
        if (ready && compatible && visible && autoStart && !cancelled && !attempted && !setup.isBusy) {
            for (second in 5 downTo 1) { countdown = second; delay(1000) }
            attempted = true
            quickPlay(current, Ekora.SERVER_HOST)
        }
    }

    // ---- dialogs
    val installer = setup.installer
    if (installer != null) {
        val tasks by installer.tasksFlow.collectAsStateWithLifecycle()
        if (tasks.isNotEmpty()) {
            TitleTaskFlowDialog(
                title = "EkoraMC kuruluyor (Minecraft ${Ekora.SERVER_VERSION} + Fabric)",
                tasks = tasks,
                onCancel = { setup.cancel() }
            )
        }
    }
    setup.busyText?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Biraz bekle") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(text)
                }
            }
        )
    }
    setup.message?.let { text ->
        AlertDialog(
            onDismissRequest = { setup.message = null },
            confirmButton = { TextButton(onClick = { setup.message = null }) { Text("Tamam") } },
            title = { Text("Ekora Launcher") },
            text = { Text(text) }
        )
    }

    val stopAuto = { cancelled = true }

    Row(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF10162D), Color(0xFF21183D), Color(0xFF092B39)))
        ).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ------------------------------------------------ left: info & tools
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ekora_logo), contentDescription = "Ekora logosu", modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text("EKORA", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("LAUNCHER • ANDROID ${Ekora.APP_VERSION}", color = Cyan, style = MaterialTheme.typography.labelLarge)
                }
            }

            update?.let { newVersion ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF14271E)) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ekora Launcher $newVersion çıktı. Yeni özellikler için güncelle.", color = Color(0xFFBFE9CF), modifier = Modifier.weight(1f))
                        Button(onClick = { onOpenLink(Ekora.DOWNLOAD_PAGE) }) { Text("İndir") }
                    }
                }
            }

            // server card
            Surface(shape = RoundedCornerShape(20.dp), color = CardColor) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("EKORAMC", color = Cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(Ekora.SERVER_HOST, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    val s = status
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dot = when {
                            s == null -> Muted
                            s.online -> Color(0xFF5FD38A)
                            else -> Color(0xFFE0A25A)
                        }
                        Box(Modifier.size(10.dp).background(dot, CircleShape))
                        Text(
                            when {
                                s == null -> "Sunucu durumu soruluyor…"
                                s.online -> "Çevrimiçi · ${s.players}/${s.max} oyuncu · ${s.latencyMs} ms"
                                else -> "Sunucuya şu an ulaşılamıyor"
                            },
                            color = Soft
                        )
                    }
                    Text("Sunucu sürümü: Minecraft ${Ekora.SERVER_VERSION}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }

            // setup card
            Surface(shape = RoundedCornerShape(20.dp), color = CardColor) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("HAZIRLIK", color = Cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    // 1. player name
                    if (account == null) {
                        Text("1. Oyuncu adını yaz (sunucudaki adın).", color = Color.White)
                        val valid = NameRegex.matches(nameInput)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
                                singleLine = true,
                                label = { Text("Oyuncu adı") },
                                isError = nameInput.isNotEmpty() && !valid,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { stopAuto(); localLogin(nameInput, null) }, enabled = valid) { Text("Kaydet") }
                        }
                        Text("3–16 karakter: İngilizce harf, rakam ve alt çizgi.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("✓ Oyuncu: ${account?.username}", color = Color.White)
                    }
                    // 2. game version
                    when {
                        compatible -> Text("✓ Oyun hazır: ${current?.getVersionName()} (Minecraft ${Ekora.SERVER_VERSION})", color = Color.White)
                        ekoraVersion != null -> {
                            Text("Seçili sürüm sunucuyla uyumsuz. EkoraMC sürümüne geç.", color = Color(0xFFF1C26B))
                            Button(onClick = { stopAuto(); VersionsManager.saveCurrentVersion(ekoraVersion.getVersionName()) }) {
                                Text("${ekoraVersion.getVersionName()} sürümünü seç")
                            }
                        }
                        else -> {
                            Text(
                                if (current == null) "2. Oyunu tek dokunuşla kur: Minecraft ${Ekora.SERVER_VERSION} + Fabric + Fabric API."
                                else "Seçili sürüm (${current.getVersionName()}) sunucuyla uyumsuz. EkoraMC sürümünü tek dokunuşla kur.",
                                color = Color.White
                            )
                            Button(
                                onClick = { stopAuto(); setup.setupEkora(context) },
                                enabled = !setup.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("EKORAMC’Yİ KUR", style = MaterialTheme.typography.titleMedium) }
                            Text("İlk kurulum yaklaşık 300–500 MB indirir. Mümkünse Wi-Fi kullan.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // tools
            Text("ARAÇLAR", color = Cyan, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("Mod Mağazası", Modifier.weight(1f)) { stopAuto(); backStackViewModel.navigateToDownload(backStackViewModel.downloadModScreen) }
                ToolButton("Shader’lar", Modifier.weight(1f)) { stopAuto(); backStackViewModel.navigateToDownload(backStackViewModel.downloadShadersScreen) }
                ToolButton("Kaynak paketleri", Modifier.weight(1f)) { stopAuto(); backStackViewModel.navigateToDownload(backStackViewModel.downloadResourcePackScreen) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("FPS paketi", Modifier.weight(1f), enabled = current != null && hasFabric && !setup.isBusy) {
                    stopAuto(); current?.let { setup.installFpsPack(it) }
                }
                ToolButton("Modlarım", Modifier.weight(1f), enabled = current != null) { stopAuto(); current?.let { navigateToVersions(it) } }
                ToolButton("Mod paketleri", Modifier.weight(1f)) { stopAuto(); backStackViewModel.navigateToDownload(backStackViewModel.downloadModPackScreen) }
            }
            if (current != null && !hasFabric) {
                Text("FPS paketi ve modlar için Fabric’li bir sürüm gerekir (EKORAMC’Yİ KUR bunu yapar).", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("Web sitesi", Modifier.weight(1f)) { stopAuto(); onOpenLink(Ekora.WEBSITE) }
                ToolButton("Tüm sürümler", Modifier.weight(1f)) { stopAuto(); backStackViewModel.mainScreen.navigateTo(NormalNavKey.VersionsManager) }
                ToolButton("Ayarlar", Modifier.weight(1f)) {
                    stopAuto()
                    backStackViewModel.mainScreen.removeAndNavigateTo(
                        removes = backStackViewModel.clearBeforeNavKeys,
                        screenKey = backStackViewModel.settingsScreen
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155))
            Text("Yerel profil sunucuda hesap doğrulaması sağlamaz; ilk girişte /register, sonra /login yaz.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Zalith Launcher 2 tabanlı gayriresmî değiştirilmiş sürüm. © 2025–2026 MovTery ve katkıda bulunanlar. GPLv3+. Mojang veya Microsoft tarafından onaylanmış değildir. Mod içerikleri Modrinth üzerinden, sahiplerinin lisanslarıyla gelir.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        // ------------------------------------------------ right: play panel
        Surface(modifier = Modifier.widthIn(max = 300.dp).weight(0.65f), shape = RoundedCornerShape(24.dp), color = PanelColor) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OYUNA HAZIRLAN", color = Color.White, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { stopAuto(); backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager(FirstLoginMenu.NORMAL)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(account?.username ?: "Oyuncu adı ekle")
                }
                OutlinedButton(onClick = { stopAuto(); backStackViewModel.mainScreen.navigateTo(NormalNavKey.VersionsManager) }, modifier = Modifier.fillMaxWidth()) {
                    Text(current?.getVersionName() ?: "Sürüm seç")
                }
                if (current != null && !compatible) {
                    Text("Bu sürümle (${current.getVersionInfo()?.minecraftVersion ?: "?"}) EkoraMC’ye giremeyebilirsin. Sunucu ${Ekora.SERVER_VERSION} istiyor.", color = Color(0xFFF1C26B), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoStart, onCheckedChange = { LauncherSettings.ekoraAutoStart.save(it); cancelled = !it })
                    Text("Hazır olduğunda otomatik bağlan", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (ready && compatible && autoStart && !cancelled && !attempted && !setup.isBusy) {
                    Text("$countdown saniye sonra bağlanılıyor…", color = Cyan)
                    TextButton(onClick = { cancelled = true }) { Text("Bu sefer iptal et") }
                }
                Button(
                    onClick = { attempted = true; quickPlay(current, Ekora.SERVER_HOST) },
                    enabled = ready && !setup.isBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE), contentColor = Color(0xFF082F49))
                ) { Text("EKORA’DA OYNA", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = { stopAuto(); onLaunchGame(current) },
                    enabled = ready && !setup.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sunucusuz oyna") }
                Text("Oyun kendi uygulama klasöründe saklanır. Kişilere, fotoğraflara, mikrofona veya tüm dosyalara erişim izni istenmez.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}
