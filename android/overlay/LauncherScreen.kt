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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.LauncherSettings
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.delay

private const val EKORA_SERVER = "play.ekoramc.fun"

@Composable
fun LauncherScreen(
    backStackViewModel: ScreenBackStackViewModel,
    navigateToVersions: (Version) -> Unit,
    onLaunchGame: (Version?) -> Unit,
    onQuickPlayServer: (Version?, String) -> Unit,
    onOpenLink: (String) -> Unit,
    startGuideOnce: (GuideKeys.Keys) -> Unit,
) {
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val refreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()
    val visible = backStackViewModel.mainScreen.currentKey == NormalNavKey.LauncherMain
    val ready = account != null && version?.isValid() == true && !refreshing
    var countdown by remember { mutableIntStateOf(5) }
    var cancelled by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val autoStart = LauncherSettings.ekoraAutoStart.state
    val quickPlay by rememberUpdatedState(onQuickPlayServer)
    LaunchedEffect(ready, visible, autoStart, cancelled, attempted) {
        if (ready && visible && autoStart && !cancelled && !attempted) {
            for (second in 5 downTo 1) { countdown = second; delay(1000) }
            attempted = true
            quickPlay(version, EKORA_SERVER)
        }
    }
    val accountAction = {
        cancelled = true
        backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager(FirstLoginMenu.NORMAL))
    }
    val versionAction = {
        cancelled = true
        backStackViewModel.mainScreen.navigateTo(NormalNavKey.VersionsManager)
    }
    Row(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF10162D), Color(0xFF21183D), Color(0xFF092B39)))
        ).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ekora_logo), contentDescription = "Ekora logosu", modifier = Modifier.size(64.dp))
                Column {
                    Text("EKORA", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text("LAUNCHER • ANDROID", color = Color(0xFF67E8F9), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("Dünyan seni bekliyor.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Java Edition • Dokunmatik kontroller • Mod ve sürüm yönetimi", color = Color(0xFFC7D2FE))
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF15253D)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("EKORAMC", color = Color(0xFF67E8F9), style = MaterialTheme.typography.titleLarge)
                    Text(EKORA_SERVER, color = Color.White)
                    Text("Oyna düğmesi Minecraft’ı açar ve Ekora sunucusuna bağlanmayı dener.", color = Color(0xFFCBD5E1))
                }
            }
            Text("İlk kullanım: oyuncu adını ekle, sunucuyla uyumlu bir Minecraft sürümü indir ve Oyna’ya dokun. Oyun dosyaları ilk indirmede internet gerektirir.", color = Color(0xFFCBD5E1))
            Text("Yerel profil sunucuda hesap doğrulaması sağlamaz; sunucunun giriş kuralları geçerlidir.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = Color(0xFF334155))
            Text("Zalith Launcher 2 tabanlı gayriresmî değiştirilmiş sürüm. © 2025–2026 MovTery ve katkıda bulunanlar. GPLv3+. Mojang veya Microsoft tarafından onaylanmış değildir.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
        Surface(modifier = Modifier.widthIn(max = 300.dp).weight(0.65f), shape = RoundedCornerShape(24.dp), color = Color(0xFF161D32)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OYUNA HAZIRLAN", color = Color.White, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = accountAction, modifier = Modifier.fillMaxWidth()) {
                    Text(account?.username ?: "1. Oyuncu adı ekle")
                }
                OutlinedButton(onClick = versionAction, modifier = Modifier.fillMaxWidth()) {
                    Text(version?.getVersionName() ?: "2. Minecraft sürümü indir")
                }
                version?.let { selected ->
                    TextButton(onClick = { cancelled = true; navigateToVersions(selected) }) { Text("Sürüm ve mod ayarları") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoStart, onCheckedChange = { LauncherSettings.ekoraAutoStart.save(it); cancelled = !it })
                    Text("Hazır olduğunda otomatik bağlan", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (ready && autoStart && !cancelled && !attempted) {
                    Text("$countdown saniye sonra bağlanılıyor…", color = Color(0xFF67E8F9))
                    TextButton(onClick = { cancelled = true }) { Text("Bu sefer iptal et") }
                }
                Button(
                    onClick = { attempted = true; quickPlay(version, EKORA_SERVER) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE), contentColor = Color(0xFF082F49))
                ) { Text("EKORA’DA OYNA", style = MaterialTheme.typography.titleMedium) }
                Text("Oyun kendi uygulama klasöründe saklanır. Kişilere, fotoğraflara, mikrofona veya tüm dosyalara erişim izni istenmez.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
