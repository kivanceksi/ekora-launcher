"""Reproducible Ekora changes to the pinned, unmodified GPLv3+ upstream."""
from pathlib import Path
import re, shutil, sys, json
import xml.etree.ElementTree as ET
root=Path(sys.argv[1]).resolve()
ours=Path(__file__).resolve().parent
app=root/'ZalithLauncher'
java=app/'src/main/java/com/movtery/zalithlauncher'
def edit(path,old,new):
    text=path.read_text()
    if old not in text: raise RuntimeError(f'Upstream changed: {path}: {old[:70]}')
    path.write_text(text.replace(old,new),encoding='utf-8')
def body(path,signature,replacement):
    text=path.read_text()
    pos=text.index('(',text.index(signature)); depth=1; pos+=1
    while depth:
        if text[pos]=='(': depth+=1
        elif text[pos]==')': depth-=1
        pos+=1
    start=text.index('{',pos); depth=1; end=start+1
    while depth:
        if text[end]=='{': depth+=1
        elif text[end]=='}': depth-=1
        end+=1
    path.write_text(text[:start]+'{\n'+replacement+'\n}'+text[end:],encoding='utf-8')
p=app/'gradle.properties'
for old,new in [('launcher_name=ZalithLauncher','launcher_name=EkoraLauncher'),('launcher_app_name=Zalith Launcher','launcher_app_name=Ekora Launcher'),('launcher_short_name=ZL2','launcher_short_name=Ekora'),('launcher_version_code=200049','launcher_version_code=1'),('launcher_version_name=2.8.2','launcher_version_name=0.1.0'),('url_home=https://github.com/ZalithLauncher/ZalithLauncher2','url_home=https://play.ekoramc.fun')]: edit(p,old,new)
p=app/'build.gradle.kts'
edit(p,'applicationId = zalithPackageName','applicationId = "fun.ekoramc.launcher"')
edit(p,'applicationIdSuffix = ".v2"','// Ekora has its own stable Android application ID.')
edit(p,'signingConfig = signingConfigs.getByName("releaseBuild")','// Signed offline with the private Ekora release key after build.')
# Pin application providers to our own identity; never claim upstream identity.
p=app/'src/main/AndroidManifest.xml'
text=p.read_text()
for permission in ['WRITE_EXTERNAL_STORAGE','READ_EXTERNAL_STORAGE','MANAGE_EXTERNAL_STORAGE','RECORD_AUDIO','HIGH_SAMPLING_RATE_SENSORS','FOREGROUND_SERVICE_CONNECTED_DEVICE']:
    text=re.sub(r'\s*<uses-permission android:name="android.permission.'+permission+r'"[^>]*/>', '',text)
text=text.replace('${packageName}','${applicationId}').replace('android:allowBackup="true"','android:allowBackup="false"')
text=text.replace('android:preserveLegacyExternalStorage="true"','').replace('android:requestLegacyExternalStorage="true"','')
# LAN VPN tunnelling is unnecessary for direct Ekora server connections.
text=re.sub(r'\s*<service\s+android:name=".terracotta.TerracottaVPNService".*?</service>','',text,flags=re.S)
p.write_text(text)
# Keep app-specific storage, disable requests to access all personal files.
body(java/'utils/StorageUtils.kt','fun checkStoragePermissionsForInit(', '    canHandlePermission = false\n    hasStoragePermission = false')
body(java/'utils/StorageUtils.kt','fun checkStoragePermissions(', '    onDialogCancel()')
p=java/'setting/LauncherSettings.kt'
edit(p,'AppLanguage.FOLLOW_SYSTEM','AppLanguage.TURKISH')
edit(p,'DarkMode.FollowSystem','DarkMode.Enable')
edit(p,'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorThemeType.DYNAMIC\n        else ColorThemeType.EMBERMIRE','ColorThemeType.CUSTOM')
edit(p,'Color.Blue.toArgb()','Color(0xFFA855F7).toArgb()')
edit(p,'object LauncherSettings : SettingsRegistry() {','object LauncherSettings : SettingsRegistry() {\n    val ekoraAutoStart = boolSetting("ekoraAutoStart", true)')
body(java/'game/launch/LanguageHelper.kt','private fun getLanguage(', '    return if (versionId.isLowerOrEqualVer("1.10.2")) "tr_TR" else "tr_tr"')
# Do not offer upstream APK updates that would install another app.
p=java/'ui/activities/MainActivity.kt'
edit(p,'launcherUpgradeViewModel.checkOnAppStart()','// Ekora updates are distributed separately.')
body(p,'private fun checkUpdate()', '    Toast.makeText(this, "Ekora güncellemelerini indirme sitemizden takip edebilirsin.", Toast.LENGTH_LONG).show()')
# Local profiles only in this initial Ekora release. Keep original license notices.
p=java/'ui/screens/content/elements/AccountElements.kt'
body(p,'fun LoginMenuDialog(', '    androidx.compose.runtime.LaunchedEffect(Unit) {\n        onDismissRequest()\n        onLocalLogin()\n    }')
# When the upstream first-login flow asks for Microsoft, open the local menu instead.
p=java/'ui/screens/content/AccountManageScreen.kt'
edit(p,'actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))','actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login))')
shutil.copyfile(ours/'overlay/LauncherScreen.kt', java/'ui/screens/content/LauncherScreen.kt')
res=app/'src/main/res'
shutil.copyfile(ours.parent/'assets/logo.png',res/'drawable/ekora_logo.png')
# Use the user's unmodified original logo, with adaptive-icon safe padding.
for p in res.glob('mipmap-*/ic_launcher*'):
    if p.is_file(): p.unlink()
(res/'mipmap-anydpi-v26').mkdir(exist_ok=True)
icon='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
 <background android:drawable="@color/ekora_icon_background"/>
 <foreground><inset android:drawable="@drawable/ekora_logo" android:inset="18%"/></foreground>
</adaptive-icon>'''
for name in ['ic_launcher','ic_launcher_round']:
    (res/f'mipmap-anydpi-v26/{name}.xml').write_text(icon)
(res/'values/ekora.xml').write_text('<resources><color name="ekora_icon_background">#10162D</color></resources>')
# The splash resource also uses the user's logo.
(res/'drawable/ic_launcher_foreground.xml').write_text('<inset xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/ekora_logo" android:inset="18%"/>')
shutil.copyfile(ours/'README.md',root/'EKORA-CHANGES.md')
print('Ekora overlay applied; local profile, scoped storage, Turkish UI, custom home and server quick play.')

# Turkish first-run flow and accurate Ekora-specific data notices.
tr = res/'values-tr/strings.xml'
tree = ET.parse(tr); resources = tree.getroot()
translations = {
 'welcome_screen_subtitle': 'Ekora dünyasına hoş geldin.',
 'welcome_screen_intro_primary': '%1$s, Android için Minecraft Java Edition başlatıcısıdır.',
 'welcome_screen_intro_core': 'Zalith Launcher 2 / PojavLauncher tabanlı gayriresmî değiştirilmiş sürüm.',
 'welcome_screen_agreement_title': 'Kullanım ve veri bilgileri',
 'welcome_screen_agreement_confirmation': 'Yukarıdaki belgeleri okudum ve kabul ediyorum.',
 'welcome_screen_start': 'Başlayalım',
 'agreement_content_unavailable': 'Bu belge açılamadı.',
 'agreement_update_title': 'Bilgiler güncellendi',
 'agreement_update_message': 'Devam etmeden önce güncel kullanım ve veri bilgilerini incele.',
 'splash_screen_agree': 'Yükle',
 'splash_screen_installing': 'Oyun bileşenleri hazırlanıyor…'
}
for name, value in translations.items():
    node = next((e for e in resources if e.get('name') == name), None)
    if node is None: node = ET.SubElement(resources, 'string', {'name': name})
    node.text = value
ET.indent(tree)
tree.write(tr, encoding='utf-8', xml_declaration=True)
legal=app/'src/main/assets/legal'
(legal/'ekora').mkdir(exist_ok=True)
for name in ['kullanim.md','gizlilik.md']: shutil.copyfile(ours/'overlay'/name,legal/'ekora'/name)
(legal/'data.json').write_text(json.dumps({'version':'ekora-0.1.0-2026-11-15','files':[{'effective_region':'global','data_list':[{'title':'Ekora Kullanım Bilgileri','path':'legal/ekora/kullanim.md'},{'title':'Ekora Veri ve İzin Bilgileri','path':'legal/ekora/gizlilik.md'}]}]},ensure_ascii=False),encoding='utf-8')
