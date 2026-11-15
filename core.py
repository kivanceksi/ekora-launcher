"""Ekora Launcher: disk, installation and process layer. No UI dependency."""
import hashlib
import json
import os
import re
import subprocess
import threading
import urllib.request
import uuid
import sys
from managed_mods import prepare_mods
from pathlib import Path
from urllib.parse import urlsplit
from security import no_links, scoped, redact_log
from i18n import status_tr, error_tr

VERSION = '0.3.2'
SERVER = 'play.ekoramc.fun'
DEFAULTS = {'username':'', 'version':'1.21.1', 'ram':4096, 'autoStart':True,
            'countdown':8, 'fabric':False, 'apiUrl':'', 'javaPath':'', 'theme':'violet', 'language':'tr_tr', 'externalMods':False, 'onboardingDone':False}

class Cancelled(Exception): pass

def offline_uuid(name):
    return str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(), version=3))

def validate_settings(data):
    if not isinstance(data,dict): raise ValueError('Geçersiz ayar dosyası.')
    s = {**DEFAULTS, **{k:v for k,v in data.items() if k in DEFAULTS}}
    if not isinstance(s['username'],str): raise ValueError('Geçersiz oyuncu adı.')
    if s['username'] and not re.fullmatch(r'[A-Za-z0-9_]{3,16}', s['username']):
        raise ValueError('Oyuncu adı 3–16 karakter; yalnız harf, rakam ve alt çizgi olabilir.')
    if not isinstance(s['version'],str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,59}',s['version']):
        raise ValueError('Geçersiz sürüm.')
    for key, low, high in [('ram',1024,32768),('countdown',3,30)]:
        if type(s[key]) is not int or not low <= s[key] <= high: raise ValueError('Geçersiz '+key)
    for key in ['autoStart','fabric','externalMods','onboardingDone']:
        if type(s[key]) is not bool: raise ValueError('Geçersiz '+key)
    for key in ['apiUrl','javaPath','theme']:
        if not isinstance(s[key],str): raise ValueError('Geçersiz '+key)
    if s['apiUrl']:
        u=urlsplit(s['apiUrl'])
        if u.scheme!='https' or not u.hostname or u.username or u.password or u.query or u.fragment:
            raise ValueError('API adresi kullanıcı bilgisi içermeyen bir HTTPS adresi olmalı.')
        s['apiUrl']=s['apiUrl'].rstrip('/')
    if s['language'] not in ('tr_tr','en_us'): raise ValueError('Geçersiz oyun dili.')
    if s['javaPath'] and (not Path(s['javaPath']).is_absolute() or Path(s['javaPath']).name.lower() not in ('java.exe','javaw.exe','java')):
        raise ValueError('Yalnız Java çalıştırılabilir dosyası seçilebilir.')
    if s['theme'] not in ('violet','cyan'): s['theme']='violet'
    return s

def atomic_json(path,data):
    no_links(path); no_links(path.with_suffix('.tmp'))
    path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_suffix('.tmp')
    tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    os.replace(tmp,path)

def read_json_url(url):
    req=urllib.request.Request(url,headers={'User-Agent':'EkoraLauncher/'+VERSION})
    class HTTPSOnly(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,request,fp,code,msg,headers,newurl):
            if urlsplit(newurl).scheme!='https': raise ValueError('Güvensiz yönlendirme engellendi.')
            return super().redirect_request(request,fp,code,msg,headers,newurl)
    with urllib.request.build_opener(HTTPSOnly()).open(req,timeout=15) as r:
        body=r.read(2_000_001)
        if len(body)>2_000_000: raise ValueError('Sunucu yanıtı çok büyük.')
        return json.loads(body)

class Launcher:
    def __init__(self,root=None,emit=None,auth=None,assets=None):
        self.assets=Path(assets or Path(getattr(sys,"_MEIPASS",Path(__file__).resolve().parent))/"assets")
        self.auth=auth
        self.root=Path(root or os.getenv('EKORA_HOME') or Path(os.getenv('APPDATA',Path.home()))/'EkoraLauncher')
        no_links(self.root)
        self.root.mkdir(parents=True,exist_ok=True)
        self.game=scoped(self.root,'minecraft'); self.game.mkdir(exist_ok=True)
        self.emit=emit or (lambda kind,data:None)
        self.settings=dict(DEFAULTS)
        self.warning=''
        self.default_api_url=''
        try:
            if scoped(self.root,'settings.json').exists():
                self.settings=validate_settings(json.loads(scoped(self.root,'settings.json').read_text(encoding='utf-8')))
        except (ValueError,TypeError,OSError):
            self.warning='Ayar dosyası okunamadı. Varsayılanlar kullanılıyor; kaydetmeden önce kontrol et.'
        self.cancel=threading.Event(); self.process=None
        self.lock=threading.Lock(); self.busy=False
    def save(self,data):
        if self.busy: raise ValueError('Kurulum veya oyun sürerken ayarları değiştiremezsin.')
        self.settings=validate_settings(data); atomic_json(scoped(self.root,'settings.json'),self.settings)
        return self.settings
    def installed(self):
        return sorted(p.parent.name for p in (self.game/'versions').glob('*/*.json') if p.name==p.parent.name+'.json')
    def snapshot(self):
        return {'settings':self.settings,'installed':self.installed(),'version':VERSION,'server':SERVER,
                'gamePath':str(self.game),'warning':self.warning,'native':True}
    def check(self):
        if self.cancel.is_set(): raise Cancelled()
    def status(self,text):
        self.check(); self.emit('progress',{'message':status_tr(text)})
    def launch(self,repair=False):
        with self.lock:
            if self.busy: raise ValueError('Oyun veya kurulum zaten çalışıyor.')
            self.busy=True; self.cancel.clear()
        try:
            self.emit('state',{'state':'installing'})
            s=validate_settings(dict(self.settings))
            if not s['username']: raise ValueError('Önce Hesabım bölümünde oyuncu adını kaydet.')
            account={'name':s['username'],'id':offline_uuid(s['username']),'access_token':'0'}
            self.audit_tree()
            packages=prepare_mods(self.game,self.assets,s['version'],s['externalMods'])
            if packages['disabled']:
                self.emit('notice',{'message':str(len(packages['disabled']))+' bilinmeyen veya değiştirilmiş mod silinmeden disabled-mods klasörüne ayrıldı. Oyun hazırlanıyor.'})
            if packages['fabricRequired']: s['fabric']=True
            import minecraft_launcher_lib as m
            maxp=[1]
            def maximum(value): maxp[0]=max(1,int(value))
            def progress(value):
                self.check(); self.emit('progress',{'percent':min(100,int(value/maxp[0]*100))})
            cb={'setStatus':self.status,'setProgress':progress,'setMax':maximum}
            self.status('Minecraft dosyaları kontrol ediliyor…')
            m.install.install_minecraft_version(s['version'],str(self.game),callback=cb)
            selected=s['version']
            if s['fabric']:
                self.status('Fabric kuruluyor…')
                loader=m.fabric.get_latest_loader_version()
                base=json.loads((self.game/'versions'/s['version']/(s['version']+'.json')).read_text(encoding='utf-8'))
                java=s['javaPath'] or m.runtime.get_executable_path(base.get('javaVersion',{}).get('component','java-runtime-delta'),str(self.game))
                m.fabric.install_fabric(s['version'],str(self.game),loader_version=loader,callback=cb,java=java)
                selected='fabric-loader-'+loader+'-'+s['version']
            self.check()
            if repair:
                self.emit('state',{'state':'ready','message':'Dosya kontrolü tamamlandı.'}); return
            options={'username':account['name'],'uuid':account['id'],'token':account['access_token'],
                     'jvmArguments':['-Xms512M','-Xmx'+str(s['ram'])+'M'],
                     'launcherName':'EkoraLauncher','launcherVersion':VERSION,
                     'quickPlayMultiplayer':SERVER,'quickPlayPath':str(self.game/'quickPlay.json')}
            parts=tuple(int(n) for n in s['version'].split('.') if n.isdigit())
            if parts < (1,20):
                options.pop('quickPlayMultiplayer',None)
                options.update({'server':SERVER,'port':'25565'})
            if s['javaPath']:
                if not Path(s['javaPath']).is_file(): raise ValueError('Seçilen Java dosyası bulunamadı.')
                options['executablePath']=s['javaPath']
            self.write_game_language(s['language'])
            self.status('EkoraMC için Minecraft başlatılıyor…')
            command=m.command.get_minecraft_command(selected,str(self.game),options)
            self.check()
            with scoped(self.root,'minecraft.log').open('w',encoding='utf-8') as out:
                self.process=subprocess.Popen(command,cwd=self.game,stdout=out,stderr=subprocess.STDOUT,
                                              creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
                self.emit('state',{'state':'running','message':'Minecraft açık. Sunucu giriş şifren oyun içinde istenebilir.'})
                code=self.process.wait()
            self.process=None
            if code: raise RuntimeError('Minecraft '+str(code)+' koduyla kapandı. Günlükler bölümünü incele.')
            self.emit('state',{'state':'ready','message':'Minecraft kapandı.'})
        except Cancelled:
            self.emit('state',{'state':'ready','message':'Kurulum iptal edildi.'})
        except Exception as exc:
            self.record_error(exc)
            self.emit('launchError',{'message':error_tr(exc)})
        finally:
            self.busy=False
    def public_api(self,section):
        if section not in ('status','news','map','features'): raise ValueError('Geçersiz bölüm.')
        base=self.settings['apiUrl'] or self.default_api_url
        if not base: raise ValueError('Sunucu API adresi henüz ayarlanmadı.')
        return read_json_url(base+'/v1/public/'+section)
    def waypoints(self):
        p=scoped(self.root,'waypoints.json')
        try: return json.loads(p.read_text(encoding='utf-8')) if p.exists() else []
        except (ValueError,OSError): return []
    def save_waypoints(self,items):
        if not isinstance(items,list) or len(items)>500: raise ValueError('En fazla 500 konum kaydedilebilir.')
        clean=[]
        for w in items:
            if not isinstance(w,dict) or not isinstance(w.get('name'),str) or not 1<=len(w['name'])<=60: raise ValueError('Geçersiz konum adı.')
            if w.get('world') not in ('overworld','nether','end'): raise ValueError('Geçersiz dünya.')
            for k in ('x','y','z'):
                if type(w.get(k)) is not int or abs(w[k])>30_000_000: raise ValueError('Geçersiz koordinat.')
            clean.append({k:w[k] for k in ('name','world','x','y','z')})
        atomic_json(scoped(self.root,'waypoints.json'),clean); return clean

    def audit_tree(self):
        # Inspect only Ekora's own tree; never follow links into personal folders.
        no_links(self.root)
        for base,dirs,files in os.walk(self.root,followlinks=False):
            for name in dirs+files: no_links(Path(base)/name)
    def write_game_language(self,language):
        p=scoped(self.game,'options.txt')
        text=p.read_text(encoding='utf-8') if p.exists() else ''
        lines=[line for line in text.splitlines() if not line.startswith('lang:')]
        lines.append('lang:'+language)
        tmp=scoped(self.game,'options.txt.tmp')
        tmp.write_text('\n'.join(lines)+'\n',encoding='utf-8'); os.replace(tmp,p)
    def record_error(self,exc):
        try:
            p=scoped(self.root,'launcher-error.log')
            p.write_text(type(exc).__name__+': '+error_tr(exc)[-20000:],encoding='utf-8')
        except (OSError,ValueError): pass
    def diagnostics(self):
        import shutil
        disk=shutil.disk_usage(self.root)
        return {'freeGB':round(disk.free/1024**3,1),'installed':self.installed(),
                'gamePath':str(self.game),'version':VERSION,
                'language':self.settings['language'],'externalMods':self.settings['externalMods']}
