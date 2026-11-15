"""Install/open Android release binary in a disposable emulator; no game/account login."""
from pathlib import Path
import re, subprocess, time, xml.etree.ElementTree as ET, json
out=Path('qa');out.mkdir(exist_ok=True)
def adb(*args,check=True):
    return subprocess.run(['adb',*args],capture_output=True,check=check).stdout

def tree():
    adb('shell','uiautomator','dump','/sdcard/ekora-ui.xml',check=False)
    raw=adb('shell','cat','/sdcard/ekora-ui.xml',check=False)
    (out/'ui.xml').write_bytes(raw)
    try:return ET.fromstring(raw)
    except ET.ParseError:return ET.Element('empty')

def tap(node):
    b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(b)==4:adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))

adb('install','-r','qa-test.apk')
adb('logcat','-c')
adb('shell','am','start','-W','-n','fun.ekoramc.launcher/com.movtery.zalithlauncher.ui.activities.SplashActivity')
try:
    for attempt in range(60):
        time.sleep(3)
        root=tree();nodes=list(root.iter('node'))
        texts=[n.get('text','') for n in nodes]
        if any('EKORA’DA OYNA' in t for t in texts):
            assert any('Oyuncu adı ekle' in t for t in texts),texts
            assert adb('shell','pidof','fun.ekoramc.launcher',check=False).strip(), 'Process exited'
            (out/'result.json').write_text(json.dumps({'installed':True,'home_visible':True,'turkish':True,'game_session_tested':False}))
            print('PASS: release APK installed and Turkish Ekora home appeared in Android emulator.')
            break
        for n in nodes:
            if n.get('checkable')=='true' and n.get('checked')=='false':tap(n);break
        else:
            for n in nodes:
                if n.get('text') in ('Başlayalım','Yükle','Tamam','Get Started','Install') and n.get('enabled')=='true':tap(n);break
    else:raise RuntimeError('Ekora home did not appear within the test timeout')
finally:
    (out/'screen.png').write_bytes(adb('exec-out','screencap','-p',check=False))
    (out/'logcat.txt').write_bytes(adb('logcat','-d',check=False))
