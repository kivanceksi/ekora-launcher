# Ekora Launcher Android 0.3.0

Android 8+ / ARM64. Unofficial modified version of Zalith Launcher 2.
Copyright (C) 2025–2026 MovTery and contributors. Ekora modifications 2026.
Licensed under GPL-3.0-or-later, including upstream additional notices in README.
This program is not affiliated with or approved by Mojang, Microsoft or Zalith.

Pinned upstream: https://github.com/ZalithLauncher/ZalithLauncher2
Commit: 36b8ef87f3ce498c2865b483e3b3a0096acbefc7

Changes: unique application ID fun.ekoramc.launcher; original Ekora logo; Turkish
language and purple/cyan dark home screen; optional five-second auto connection
to play.ekoramc.fun after profile and compatible version are available; local
profile menu; Ekora home screen with live server status, one-tap EkoraMC
installation (server version + Fabric + Fabric API), verified Modrinth FPS pack,
shortcuts to the Modrinth mod/shader/resource pack/modpack browsers and an
update notice that links to the Ekora download page; an "EkoraMC" touch control
layout (F swap-hands for RPG skills, Q drop, Merkez/K for the Ekora client, toggleable RPG skill row sending
/ys 1-4) installed and selected once; in-game "Tuş Gönder" keyboard keeps game focus so sent keys reach the game; upstream APK updater disabled; no broad storage, microphone or
sensor permission, no VPN service. Game downloads remain in app-owned storage.
Minecraft itself is not redistributed. No guarantee is made regarding server
availability, every GPU, Minecraft version, or mods. Advanced upstream features
requiring removed permissions are unavailable in this distribution.

Rebuild: clone the exact upstream commit, run `python android/apply_overlay.py
/path/to/upstream` from the Ekora source repository, then build using the
android.yml workflow and JDK21/Android SDK/NDK. Full modified sources and all
original license notices accompany the APK. The APK must be signed with your
own release key. The private release key is never in GitHub or public sources.
Future APKs must use the same private key to update an existing installation.
