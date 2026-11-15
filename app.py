import json
import os
import shutil
import sys
import threading
from pathlib import Path
from PySide6.QtCore import QObject, Signal, Slot, QUrl, QLockFile, QTimer
from PySide6.QtGui import QIcon, QDesktopServices
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox
from PySide6.QtWebChannel import QWebChannel
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineSettings, QWebEngineProfile, QWebEngineUrlRequestInterceptor
from core import Launcher, validate_settings
from security import asset_allowed, same_local_file, scoped, no_links, is_admin, redact_log
from i18n import error_tr

ASSETS=Path(getattr(sys,'_MEIPASS',Path(__file__).resolve().parent))/'assets'

class AssetRequests(QWebEngineUrlRequestInterceptor):
    def interceptRequest(self,info):
        if not asset_allowed(info.requestUrl().toString(),ASSETS): info.block(True)

class LocalPage(QWebEnginePage):
    def acceptNavigationRequest(self,url,kind,main):
        return url.isLocalFile() and same_local_file(url.toLocalFile(),ASSETS/'index.html')
    def createWindow(self,kind): return None
    def chooseFiles(self,mode,old,types): return []

class Bridge(QObject):
    event=Signal(str)
    def __init__(self,window):
        super().__init__(); self.window=window
        self.launcher=Launcher(emit=self.send)
        config_file=ASSETS.parent/'release-config.json'
        if config_file.exists():
            config=json.loads(config_file.read_text(encoding='utf-8'))
            self.launcher.default_api_url=validate_settings({'apiUrl':config.get('apiBaseUrl','')})['apiUrl']
        self.connection_inflight=False
        self.connection_timer=QTimer(self); self.connection_timer.setInterval(30000)
        self.connection_timer.timeout.connect(self.refresh_connection); self.connection_timer.start()
    def send(self,kind,data): self.event.emit(json.dumps({'kind':kind,'data':data},ensure_ascii=False))
    def worker(self,fn):
        def run():
            try: fn()
            except Exception as e: self.launcher.record_error(e); self.send('error',{'message':error_tr(e)})
        threading.Thread(target=run,daemon=True).start()
    @Slot(str)
    def request(self,raw):
        try:
            if len(raw)>100000: raise ValueError('İstek boyutu çok büyük.')
            page_url=self.window.page.url()
            if not page_url.isLocalFile() or not same_local_file(page_url.toLocalFile(),ASSETS/'index.html'):
                raise ValueError('Geçersiz arayüz kaynağı.')
            msg=json.loads(raw); action=msg['action']; data=msg.get('data',{})
            if action=='init':
                self.send('init',self.launcher.snapshot()); self.send('waypoints',self.launcher.waypoints()); self.mods(); self.refresh_connection()
            elif action=='save':
                self.send('saved',self.launcher.save(data)); self.refresh_connection()
            elif action=='legal':
                links={'eula':'https://www.minecraft.net/en-us/eula','guidelines':'https://www.minecraft.net/en-us/usage-guidelines','privacy':'https://privacy.microsoft.com/privacystatement'}
                QDesktopServices.openUrl(QUrl(links[data['name']]))
            elif action=='launch': self.worker(lambda:self.launcher.launch())
            elif action=='repair': self.worker(lambda:self.launcher.launch(repair=True))
            elif action=='cancel': self.launcher.cancel.set()
            elif action=='versions':
                def versions():
                    import minecraft_launcher_lib as m
                    self.send('versions',[v['id'] for v in m.utils.get_version_list() if v['type']=='release'][:60])
                self.worker(versions)
            elif action=='api':
                section=data['section']
                self.worker(lambda:self.send('api',{'section':section,'payload':self.launcher.public_api(section)}))
            elif action=='waypoints': self.send('waypoints',self.launcher.save_waypoints(data))
            elif action=='folder':
                folders={'game':self.launcher.game,'logs':self.launcher.root,'mods':self.launcher.game/'mods',
                         'resourcepacks':self.launcher.game/'resourcepacks','disabledMods':self.launcher.game/'disabled-mods'}
                p=folders[data['name']]; no_links(p); p.mkdir(exist_ok=True)
                QDesktopServices.openUrl(QUrl.fromLocalFile(str(p)))
            elif action=='diagnostics': self.worker(lambda:self.send('diagnostics',self.launcher.diagnostics()))
            elif action=='logs':
                chunks=[]
                for name in ('launcher-error.log','minecraft.log'):
                    p=scoped(self.launcher.root,name)
                    if p.exists():
                        with p.open('rb') as f:
                            f.seek(max(0,p.stat().st_size-40000))
                            chunks.append(name+'\n'+redact_log(f.read(40000).decode('utf-8','replace')))
                self.send('logs','\n\n'.join(chunks) or 'Henüz oyun günlüğü yok.')
            elif action=='java':
                path,_=QFileDialog.getOpenFileName(self.window,'Java çalıştırılabilir dosyası seç',filter='Java (java.exe javaw.exe java);;Tüm dosyalar (*)')
                if path: self.send('java',path)
            elif action=='import': self.import_file(data['kind'])
            elif action=='toggleMod':
                if self.launcher.busy: raise ValueError('Önce oyunu kapat.')
                name=data['name']
                if name.endswith('.disabled') and not self.launcher.settings['externalMods']: raise ValueError('Bilinmeyen modu yeniden etkinleştirmek için harici mod izni gerekir.')
                if Path(name).name!=name or not name.endswith(('.jar','.jar.disabled')): raise ValueError('Geçersiz mod adı.')
                p=scoped(self.launcher.game,'mods',name)
                target=p.with_name(name[:-9] if name.endswith('.disabled') else name+'.disabled')
                no_links(target)
                if target.exists(): raise ValueError('Hedef dosya zaten var.')
                p.rename(target); self.mods()
            else: raise ValueError('Bilinmeyen işlem.')
        except Exception as e: self.launcher.record_error(e); self.send('error',{'message':error_tr(e)})
    def refresh_connection(self):
        if self.connection_inflight:return
        if not (self.launcher.settings['apiUrl'] or self.launcher.default_api_url):
            self.send('connection',{'state':'unconfigured','message':'Sunucu panel bağlantısı bekleniyor'});return
        self.connection_inflight=True
        def check():
            try:
                d=self.launcher.public_api('status')
                if not isinstance(d,dict) or type(d.get('online')) is not bool: raise ValueError('Geçersiz durum yanıtı.')
                players=d.get('players',{}).get('online') if isinstance(d.get('players'),dict) else None
                if type(players) is not int or players<0:players=None
                self.send('connection',{'state':'connected','online':d['online'],'players':players})
            except Exception:
                self.send('connection',{'state':'retrying','message':'Panel bağlantısı kurulamadı; yeniden denenecek'})
            finally:self.connection_inflight=False
        self.worker(check)
    def mods(self):
        p=scoped(self.launcher.game,'mods'); p.mkdir(exist_ok=True)
        self.send('mods',[{'name':f.name,'enabled':f.name.endswith('.jar')} for f in p.iterdir() if not f.is_symlink() and f.is_file() and f.name.endswith(('.jar','.jar.disabled'))])
    def import_file(self,kind):
        if self.launcher.busy: raise ValueError('Önce oyunu kapat.')
        if kind not in ('mods','resourcepacks'): raise ValueError('Geçersiz paket türü.')
        if kind=='mods' and not self.launcher.settings['externalMods']: raise ValueError('Harici modlar varsayılan olarak kapalı. Güvenlik ekranından izin verebilirsin.')
        ext='.jar' if kind=='mods' else '.zip'
        path,_=QFileDialog.getOpenFileName(self.window,'Güvendiğin '+('Fabric modunu' if kind=='mods' else 'kaynak paketini')+' seç',filter='Paket (*'+ext+')')
        if not path:return
        source=no_links(Path(path))
        if source.stat().st_size>512*1024*1024: raise ValueError('Paket 512 MB sınırını aşıyor.')
        if source.suffix.lower()!=ext: raise ValueError('Geçersiz dosya uzantısı.')
        dest=scoped(self.launcher.game,kind); dest.mkdir(exist_ok=True); target=scoped(dest,source.name)
        if target.exists(): raise ValueError('Bu isimde dosya zaten var. Önce klasörden düzenle.')
        shutil.copy2(source,target); self.mods(); self.send('notice',{'message':'Paket eklendi. Seçtiğin oyun sürümüyle uyumluluğunu kontrol et.'})

class Window(QMainWindow):
    def __init__(self):
        super().__init__(); self.setWindowTitle('Ekora Launcher'); self.resize(1260,840); self.setMinimumSize(960,680)
        self.setWindowIcon(QIcon(str(ASSETS/'logo.png')))
        self.view=QWebEngineView(); self.setCentralWidget(self.view)
        self.profile=QWebEngineProfile(self.view)  # off-the-record: no persistent browser profile
        self.profile.setHttpAcceptLanguage('tr-TR,tr;q=0.9')
        self.interceptor=AssetRequests(self.profile); self.profile.setUrlRequestInterceptor(self.interceptor)
        self.profile.downloadRequested.connect(lambda item:item.cancel())
        self.page=LocalPage(self.profile,self.view); self.view.setPage(self.page)
        self.page.permissionRequested.connect(lambda permission:permission.deny())
        for name in ('JavascriptCanAccessClipboard','JavascriptCanPaste','JavascriptCanOpenWindows','LocalStorageEnabled','FullScreenSupportEnabled','ScreenCaptureEnabled'):
            self.page.settings().setAttribute(getattr(QWebEngineSettings.WebAttribute,name),False)
        self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls,False)
        self.bridge=Bridge(self); self.channel=QWebChannel(); self.channel.registerObject('ekora',self.bridge)
        self.page.setWebChannel(self.channel); self.view.setUrl(QUrl.fromLocalFile(str(ASSETS/'index.html')))
    def closeEvent(self,event):
        if self.bridge.launcher.busy:
            QMessageBox.information(self,'İşlem devam ediyor','Kapatmadan önce kurulumu iptal et veya Minecraft’ı kapat.'); event.ignore()
        else: event.accept()

def main():
    app=QApplication(sys.argv); app.setApplicationName('EkoraLauncher'); app.setOrganizationName('EkoraMC')
    app.setWindowIcon(QIcon(str(ASSETS/'logo.png')))
    if is_admin():
        QMessageBox.warning(None,'Normal kullanıcı olarak aç','Ekora Launcher yönetici yetkisi istemez. Uygulamayı kapatıp normal çift tıklamayla aç.'); return 1
    state=Launcher(); lock=QLockFile(str(state.root/'launcher.lock'))
    if not lock.tryLock(0):
        QMessageBox.information(None,'Ekora Launcher','Launcher zaten açık.'); return 0
    w=Window(); w.show()
    return app.exec()

if __name__=='__main__':
    if '--smoke-test' in sys.argv:
        from smoke_test import run
        sys.exit(run())
    sys.exit(main())
