/*
 * Ekora Launcher Android — Ekora additions to Zalith Launcher 2.
 * Copyright (C) 2026 EkoraMC. GPL-3.0-or-later.
 *
 * Server status ping, launcher update check, verified Modrinth mod installs and
 * the one-tap "EkoraMC" installation (Minecraft + Fabric for the server version).
 */
package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.movtery.zalithlauncher.game.download.game.GameDownloadInfo
import com.movtery.zalithlauncher.game.download.game.GameInstaller
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object Ekora {
    const val SERVER_HOST = "play.ekoramc.fun"
    const val SERVER_PORT = 25567
    const val SERVER_VERSION = "26.3"
    const val APP_VERSION = "0.3.0"
    const val VERSION_NAME = "EkoraMC"
    const val WEBSITE = "https://ekoramc.fun"
    const val DOWNLOAD_PAGE = "https://ekoramc.fun/indir"
    const val UPDATE_API = "https://api.github.com/repos/kivanceksi/ekora-launcher/releases/latest"
    const val USER_AGENT = "EkoraLauncher-Android/0.3.0 (ekoramc.fun)"

    /** Light mods that help on phones and work with the mobile renderers. */
    val FPS_PACK = listOf("lithium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix")
}

data class ServerStatus(
    val online: Boolean,
    val players: Int = 0,
    val max: Int = 0,
    val latencyMs: Long = 0
)

// ---------------------------------------------------------------- server ping

private fun writeVarInt(out: OutputStream, value: Int) {
    var v = value
    while (true) {
        if ((v and 0x7F.inv()) == 0) {
            out.write(v)
            return
        }
        out.write((v and 0x7F) or 0x80)
        v = v ushr 7
    }
}

private fun readVarInt(input: InputStream): Int {
    var result = 0
    for (i in 0 until 5) {
        val b = input.read()
        if (b < 0) throw IllegalStateException("closed")
        result = result or ((b and 0x7F) shl (7 * i))
        if ((b and 0x80) == 0) return result
    }
    throw IllegalStateException("varint")
}

private fun varIntAt(data: ByteArray, start: Int): Pair<Int, Int> {
    var result = 0
    var index = start
    for (i in 0 until 5) {
        val b = data[index].toInt() and 0xFF
        index++
        result = result or ((b and 0x7F) shl (7 * i))
        if ((b and 0x80) == 0) return Pair(result, index)
    }
    throw IllegalStateException("varint")
}

/** Minecraft "server list ping". Never throws. */
suspend fun pingEkora(): ServerStatus = withContext(Dispatchers.IO) {
    try {
        val start = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(Ekora.SERVER_HOST, Ekora.SERVER_PORT), 6000)
            socket.soTimeout = 6000
            val host = Ekora.SERVER_HOST.toByteArray(Charsets.UTF_8)
            val handshake = ByteArrayOutputStream()
            writeVarInt(handshake, 0)
            writeVarInt(handshake, 767)
            writeVarInt(handshake, host.size)
            handshake.write(host)
            handshake.write((Ekora.SERVER_PORT shr 8) and 0xFF)
            handshake.write(Ekora.SERVER_PORT and 0xFF)
            writeVarInt(handshake, 1)
            val packet = ByteArrayOutputStream()
            writeVarInt(packet, handshake.size())
            packet.write(handshake.toByteArray())
            writeVarInt(packet, 1)
            writeVarInt(packet, 0)
            val output = socket.getOutputStream()
            output.write(packet.toByteArray())
            output.flush()
            val input = socket.getInputStream()
            val length = readVarInt(input)
            if (length <= 0 || length > 2_000_000) {
                ServerStatus(false)
            } else {
                val data = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(data, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val afterId = varIntAt(data, 0).second
                val jsonInfo = varIntAt(data, afterId)
                val jsonLength = minOf(jsonInfo.first, read - jsonInfo.second)
                val json = JSONObject(String(data, jsonInfo.second, jsonLength, Charsets.UTF_8))
                val players = json.optJSONObject("players")
                ServerStatus(
                    online = true,
                    players = players?.optInt("online", 0) ?: 0,
                    max = players?.optInt("max", 0) ?: 0,
                    latencyMs = System.currentTimeMillis() - start
                )
            }
        }
    } catch (e: Exception) {
        ServerStatus(false)
    }
}

// ---------------------------------------------------------------- http helpers

private fun httpGetText(url: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", Ekora.USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode != 200) null
        else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

fun compareVersions(a: String, b: String): Int {
    val pa = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
    val pb = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/** Returns the newer Android version published on the download page, or null. */
suspend fun checkEkoraUpdate(): String? = withContext(Dispatchers.IO) {
    try {
        val text = httpGetText(Ekora.UPDATE_API) ?: return@withContext null
        val assets = JSONObject(text).optJSONArray("assets") ?: return@withContext null
        val pattern = Regex("EkoraLauncher-(\\d+(?:\\.\\d+)+)-Android\\.apk")
        var best: String? = null
        for (i in 0 until assets.length()) {
            val name = assets.optJSONObject(i)?.optString("name") ?: continue
            val match = pattern.matchEntire(name) ?: continue
            val version = match.groupValues[1]
            if (compareVersions(version, Ekora.APP_VERSION) > 0 && (best == null || compareVersions(version, best) > 0)) {
                best = version
            }
        }
        best
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------- Modrinth

class ModInstallResult(val installed: List<String>, val missing: List<String>, val skipped: List<String>)

private fun pickVersion(versions: JSONArray): JSONObject? {
    for (type in listOf("release", "beta", "alpha")) {
        for (i in 0 until versions.length()) {
            val v = versions.optJSONObject(i) ?: continue
            if (v.optString("version_type") == type) return v
        }
    }
    return null
}

private fun sha512Hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

private fun downloadVerified(url: String, target: File, sha512: String) {
    val parsed = URL(url)
    if (parsed.protocol != "https" || parsed.host != "cdn.modrinth.com") {
        throw IllegalStateException("Güvenilmeyen indirme adresi engellendi.")
    }
    val temp = File(target.parentFile, target.name + ".indiriliyor")
    val digest = MessageDigest.getInstance("SHA-512")
    val connection = parsed.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", Ekora.USER_AGENT)
        if (connection.responseCode != 200) throw IllegalStateException("İndirme başarısız (HTTP ${connection.responseCode}).")
        connection.inputStream.use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > 300L * 1024 * 1024) throw IllegalStateException("Dosya çok büyük.")
                    digest.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
    } catch (e: Exception) {
        temp.delete()
        throw e
    } finally {
        connection.disconnect()
    }
    if (!sha512Hex(digest.digest()).equals(sha512, ignoreCase = true)) {
        temp.delete()
        throw IllegalStateException("İndirilen dosya doğrulanamadı; güvenlik için silindi.")
    }
    if (target.exists()) target.delete()
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
}

/**
 * Installs Modrinth projects (by slug or id) and their required dependencies for
 * Fabric [gameVersion] into [modsDir]. Every file is checked against Modrinth's SHA-512.
 */
suspend fun installModrinthMods(
    projects: List<String>,
    gameVersion: String,
    modsDir: File,
    onStatus: (String) -> Unit = {}
): ModInstallResult = withContext(Dispatchers.IO) {
    modsDir.mkdirs()
    val installed = mutableListOf<String>()
    val missing = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    val seen = HashSet<String>()
    val loaders = URLEncoder.encode("[\"fabric\"]", "UTF-8")
    val games = URLEncoder.encode("[\"$gameVersion\"]", "UTF-8")

    fun installOne(project: String) {
        if (!seen.add(project)) return
        val info = httpGetText("https://api.modrinth.com/v2/project/$project")?.let { JSONObject(it) }
        val title = info?.optString("title")?.takeIf { it.isNotBlank() } ?: project
        val projectId = info?.optString("id")?.takeIf { it.isNotBlank() } ?: project
        seen.add(projectId)
        info?.optString("slug")?.takeIf { it.isNotBlank() }?.let { seen.add(it) }
        val list = httpGetText("https://api.modrinth.com/v2/project/$projectId/version?loaders=$loaders&game_versions=$games")
            ?.let { JSONArray(it) }
        val version = list?.let { pickVersion(it) }
        if (version == null) {
            missing.add(title)
            return
        }
        val files = version.optJSONArray("files") ?: JSONArray()
        var file: JSONObject? = null
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            if (f.optBoolean("primary")) { file = f; break }
            if (file == null) file = f
        }
        val chosen = file
        if (chosen == null) {
            missing.add(title)
            return
        }
        val name = File(chosen.optString("filename")).name
        if (!name.endsWith(".jar") || name.startsWith(".")) {
            missing.add(title)
            return
        }
        val target = File(modsDir, name)
        if (target.exists() || File(modsDir, "$name.disabled").exists()) {
            skipped.add(title)
        } else {
            onStatus("İndiriliyor: $title")
            val sha512 = chosen.optJSONObject("hashes")?.optString("sha512") ?: ""
            if (sha512.isBlank()) {
                missing.add(title)
                return
            }
            downloadVerified(chosen.optString("url"), target, sha512)
            installed.add(title)
        }
        val deps = version.optJSONArray("dependencies") ?: JSONArray()
        for (i in 0 until deps.length()) {
            val dep = deps.optJSONObject(i) ?: continue
            if (dep.optString("dependency_type") != "required") continue
            val depId = dep.optString("project_id")
            if (depId.isNotBlank() && depId != "null") installOne(depId)
        }
    }

    for (project in projects) {
        try {
            installOne(project)
        } catch (e: Exception) {
            missing.add(project)
        }
    }
    ModInstallResult(installed, missing, skipped)
}

fun Version.isEkoraCompatible(): Boolean =
    getVersionInfo()?.minecraftVersion == Ekora.SERVER_VERSION

fun Version.modsFolder(): File = File(getGameDir(), "mods")

// ---------------------------------------------------------------- one-tap setup

class EkoraSetupViewModel : ViewModel() {
    var installer by mutableStateOf<GameInstaller?>(null)
    var busyText by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)

    val isBusy: Boolean get() = installer != null || busyText != null

    /** Installs Minecraft [Ekora.SERVER_VERSION] with the newest stable Fabric as "EkoraMC" and selects it. */
    fun setupEkora(context: Context) {
        if (isBusy) return
        busyText = "Fabric sürümü aranıyor…"
        viewModelScope.launch {
            val loaders = try {
                FabricVersions.fetchFabricLoaderList(Ekora.SERVER_VERSION)
            } catch (e: Exception) {
                null
            }
            val loader = loaders?.firstOrNull { it.stable } ?: loaders?.firstOrNull()
            busyText = null
            if (loader == null) {
                message = "Fabric bilgisi alınamadı. İnternet bağlantını kontrol edip yeniden dene."
                return@launch
            }
            val info = GameDownloadInfo(
                gameVersion = Ekora.SERVER_VERSION,
                customVersionName = Ekora.VERSION_NAME,
                fabric = loader
            )
            installer = GameInstaller(context, info, viewModelScope).also {
                it.installGame(
                    onInstalled = { name ->
                        installer = null
                        afterInstall(name)
                    },
                    onError = { th ->
                        installer = null
                        message = "Kurulum tamamlanamadı: " + (th.message ?: th.javaClass.simpleName) +
                                "\nİnternetini kontrol edip yeniden dene."
                    },
                    onGameAlreadyInstalled = {
                        installer = null
                        afterInstall(Ekora.VERSION_NAME)
                    }
                )
            }
        }
    }

    fun cancel() {
        installer?.cancelInstall()
        installer = null
        busyText = null
    }

    private fun afterInstall(name: String) {
        VersionsManager.refresh("[Ekora] setup", name)
        viewModelScope.launch {
            var version: Version? = null
            for (i in 0 until 60) {
                delay(250)
                version = VersionsManager.versions.value.firstOrNull { it.getVersionName() == name }
                if (version != null && !VersionsManager.isRefreshing.value) break
            }
            val target = version
            if (target == null) {
                message = "EkoraMC kuruldu. Sürüm listesinden seçebilirsin."
                return@launch
            }
            busyText = "Fabric API kuruluyor…"
            val result = try {
                installModrinthMods(listOf("fabric-api"), Ekora.SERVER_VERSION, target.modsFolder()) { busyText = it }
            } catch (e: Exception) {
                null
            }
            busyText = null
            message = if (result != null && result.missing.isEmpty()) "EkoraMC hazır! “EKORA’DA OYNA”ya dokun."
            else "EkoraMC hazır. Fabric API şu an indirilemedi; Mod Mağazası’ndan sonra ekleyebilirsin."
        }
    }

    fun installFpsPack(version: Version) {
        if (isBusy) return
        busyText = "FPS paketi hazırlanıyor…"
        viewModelScope.launch {
            val gameVersion = version.getVersionInfo()?.minecraftVersion ?: Ekora.SERVER_VERSION
            val result = try {
                installModrinthMods(Ekora.FPS_PACK, gameVersion, version.modsFolder()) { busyText = it }
            } catch (e: Exception) {
                null
            }
            busyText = null
            message = when {
                result == null -> "FPS paketi kurulamadı. İnternetini kontrol et."
                result.installed.isEmpty() && result.missing.isEmpty() -> "FPS paketindeki modlar zaten kurulu."
                else -> buildString {
                    if (result.installed.isNotEmpty()) append("Kuruldu: ").append(result.installed.joinToString(", ")).append(".")
                    if (result.missing.isNotEmpty()) append("\nBu sürüm için bulunamadı: ").append(result.missing.joinToString(", ")).append(".")
                }
            }
        }
    }
}
