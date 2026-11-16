"""Ekora Launcher: disk, installation and process layer. No UI dependency."""
import hashlib
import json
import os
import re
import subprocess
import threading
import time
import uuid
import sys
from contextlib import contextmanager
from managed_mods import prepare_mods
from pathlib import Path
from urllib.parse import urlsplit
from security import no_links, scoped, redact_log
from i18n import status_tr, error_tr
from profiles import Profiles, LOADERS
import modcheck
import crash
import serverping

VERSION = '0.5.0'
SERVER = 'play.ekoramc.fun'
CONFIG_DEFAULTS = {'serverHost': SERVER, 'serverPort': 25567, 'serverVersion': '26.3', 'updateRepo': 'kivanceksi/ekora-launcher',
                   'downloadPage': 'https://ekoramc.fun/indir', 'publisher': 'EkoraMC'}
DEFAULTS = {'username': '', 'ram': 4096, 'autoStart': True, 'countdown': 8, 'apiUrl': '', 'javaPath': '', 'theme': 'violet',
            'language': 'tr_tr', 'externalMods': False, 'onboardingDone': False, 'closeOnLaunch': False}


class Cancelled(Exception):
    pass


def offline_uuid(name):
    return str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3))


def validate_settings(data):
    if not isinstance(data, dict):
        raise ValueError('Geçersiz ayar dosyası.')
    s = {**DEFAULTS, **{k: v for k, v in data.items() if k in DEFAULTS}}
    if not isinstance(s['username'], str):
        raise ValueError('Geçersiz oyuncu adı.')
    if s['username'] and not re.fullmatch(r'[A-Za-z0-9_]{3,16}', s['username']):
        raise ValueError('Oyuncu adı 3–16 karakter; yalnız harf, rakam ve alt çizgi olabilir.')
    for key, low, high in [('ram', 1024, 32768), ('countdown', 3, 30)]:
        if type(s[key]) is not int or not low <= s[key] <= high:
            raise ValueError('Geçersiz ' + key)
    for key in ['autoStart', 'externalMods', 'onboardingDone', 'closeOnLaunch']:
        if type(s[key]) is not bool:
            raise ValueError('Geçersiz ' + key)
    for key in ['apiUrl', 'javaPath', 'theme']:
        if not isinstance(s[key], str):
            raise ValueError('Geçersiz ' + key)
    if s['apiUrl']:
        u = urlsplit(s['apiUrl'])
        if u.scheme != 'https' or not u.hostname or u.username or u.password or u.query or u.fragment:
            raise ValueError('API adresi kullanıcı bilgisi içermeyen bir HTTPS adresi olmalı.')
        s['apiUrl'] = s['apiUrl'].rstrip('/')
    if s['language'] not in ('tr_tr', 'en_us'):
        raise ValueError('Geçersiz oyun dili.')
    if s['javaPath'] and (not Path(s['javaPath']).is_absolute() or Path(s['javaPath']).name.lower() not in ('java.exe', 'javaw.exe', 'java')):
        raise ValueError('Yalnız Java çalıştırılabilir dosyası seçilebilir.')
    if s['theme'] not in ('violet', 'cyan'):
        s['theme'] = 'violet'
    return s


def atomic_json(path, data):
    no_links(path)
    no_links(path.with_suffix('.tmp'))
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(tmp, path)


def version_tuple(v):
    return tuple(int(x) for x in re.findall(r'\d+', str(v))[:4])


def load_config(assets):
    cfg = dict(CONFIG_DEFAULTS)
    try:
        data = json.loads((Path(assets).parent / 'release-config.json').read_text(encoding='utf-8'))
        if isinstance(data, dict):
            for k in CONFIG_DEFAULTS:
                if k in data and isinstance(data[k], type(CONFIG_DEFAULTS[k])):
                    cfg[k] = data[k]
    except (OSError, ValueError):
        pass
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,59}', cfg['serverVersion']):
        cfg['serverVersion'] = CONFIG_DEFAULTS['serverVersion']
    return cfg


class Launcher:
    def __init__(self, root=None, emit=None, auth=None, assets=None):
        self.assets = Path(assets or Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent)) / "assets")
        self.config = load_config(self.assets)
        self.auth = auth
        self.root = Path(root or os.getenv('EKORA_HOME') or Path(os.getenv('APPDATA', Path.home())) / 'EkoraLauncher')
        no_links(self.root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.game = scoped(self.root, 'minecraft')
        self.game.mkdir(exist_ok=True)
        self.emit = emit or (lambda kind, data: None)
        self.settings = dict(DEFAULTS)
        self.warning = ''
        self.default_api_url = ''
        raw = {}
        try:
            if scoped(self.root, 'settings.json').exists():
                raw = json.loads(scoped(self.root, 'settings.json').read_text(encoding='utf-8'))
                self.settings = validate_settings(raw)
        except (ValueError, TypeError, OSError):
            self.warning = 'Ayar dosyası okunamadı. Varsayılanlar kullanılıyor; kaydetmeden önce kontrol et.'
        self.profiles = Profiles(self.root, self.game, self.config['serverVersion']).load(raw if isinstance(raw, dict) else {})
        self.cancel = threading.Event()
        self.process = None
        self.lock = threading.Lock()
        self.busy = False          # an install / download job is active
        self.running = None        # profile id of the running game
        self.job_text = ''
        self._api = None
        self._status_cache = (0, None)

    # ---------- helpers ----------
    @property
    def api(self):
        if self._api is None:
            from modrinth import Api
            self._api = Api(f'EkoraLauncher/{VERSION} (ekoramc.fun)')
        return self._api

    def manager(self):
        from modrinth import Manager
        return Manager(self.api, scoped(self.root, 'cache'))

    def content(self, pid):
        from modrinth import Content
        return Content(self.profiles.dir_of(self.profiles.need(pid)))

    @contextmanager
    def job(self, text, pid=None):
        with self.lock:
            if self.busy:
                raise ValueError('Başka bir işlem sürüyor. Bitmesini bekle.')
            if pid is not None and self.running == pid:
                raise ValueError('Bu kurulum şu an oyunda açık. Değişiklik için önce Minecraft’ı kapat.')
            self.busy = True
            self.job_text = text
            self.cancel.clear()
        self.emit('job', {'active': True, 'message': text})
        try:
            yield
        finally:
            self.busy = False
            self.job_text = ''
            self.emit('job', {'active': False})

    def save(self, data):
        if self.busy:
            raise ValueError('Kurulum sürerken ayarları değiştiremezsin.')
        self.settings = validate_settings(data)
        atomic_json(scoped(self.root, 'settings.json'), self.settings)
        return self.settings

    def installed(self):
        return sorted(p.parent.name for p in (self.game / 'versions').glob('*/*.json') if p.name == p.parent.name + '.json')

    def profile_view(self, p):
        d = self.profiles.dir_of(p)
        mods = d / 'mods'
        count = sum(1 for f in mods.iterdir() if f.is_file() and f.name.endswith('.jar')) if mods.is_dir() else 0
        return {**p, 'modCount': count, 'path': str(d), 'serverOk': p['version'] == self.config['serverVersion']}

    def snapshot(self):
        return {'settings': self.settings, 'installed': self.installed(), 'version': VERSION, 'server': self.config['serverHost'],
                'serverVersion': self.config['serverVersion'], 'gamePath': str(self.game), 'warning': self.warning, 'native': True,
                'profiles': [self.profile_view(p) for p in self.profiles.items], 'selected': self.profiles.selected,
                'loaders': list(LOADERS), 'downloadPage': self.config['downloadPage']}

    def check(self):
        if self.cancel.is_set():
            raise Cancelled()

    def status(self, text):
        self.check()
        self.emit('progress', {'message': status_tr(text)})

    def _callbacks(self):
        maxp = [1]

        def maximum(value):
            maxp[0] = max(1, int(value))

        def progress(value):
            self.check()
            self.emit('progress', {'percent': min(100, int(value / maxp[0] * 100))})
        return {'setStatus': self.status, 'setProgress': progress, 'setMax': maximum}

    def _java_for(self, version_id, m):
        s = self.settings
        if s['javaPath']:
            if not Path(s['javaPath']).is_file():
                raise ValueError('Seçilen Java dosyası bulunamadı.')
            return s['javaPath'], None
        base = json.loads((self.game / 'versions' / version_id / (version_id + '.json')).read_text(encoding='utf-8'))
        jv = base.get('javaVersion', {})
        return m.runtime.get_executable_path(jv.get('component', 'java-runtime-delta'), str(self.game)), jv.get('majorVersion')

    def prepare_loader(self, p, cb, m, repair=False):
        """Installs Minecraft and the profile's loader. Returns (launch version id, loader version, java major)."""
        self.status('Minecraft dosyaları kontrol ediliyor…')
        m.install.install_minecraft_version(p['version'], str(self.game), callback=cb)
        java, major = self._java_for(p['version'], m)
        if p['loader'] == 'vanilla':
            return p['version'], None, major
        mod = m.fabric if p['loader'] == 'fabric' else m.quilt
        prefix = 'fabric-loader-' if p['loader'] == 'fabric' else 'quilt-loader-'
        lv = p.get('loaderVersion') or ''
        vid = prefix + lv + '-' + p['version'] if lv else ''
        if repair or not lv or not (self.game / 'versions' / vid / (vid + '.json')).exists():
            self.status(('Fabric' if p['loader'] == 'fabric' else 'Quilt') + ' kuruluyor…')
            if not lv or repair:
                try:
                    lv = mod.get_latest_loader_version()
                except Exception:
                    if not lv:
                        raise
            if p['loader'] == 'fabric':
                mod.install_fabric(p['version'], str(self.game), loader_version=lv, callback=cb, java=java)
            else:
                mod.install_quilt(p['version'], str(self.game), loader_version=lv, callback=cb, java=java)
            self.profiles.set_loader_version(p['id'], lv)
            vid = prefix + lv + '-' + p['version']
        return vid, lv, major

    # ---------- launch ----------
    def launch(self, repair=False, pid=None, skip_check=False):
        code = None
        try:
            p = self.profiles.need(pid or self.profiles.selected)
            with self.job('Oyun hazırlanıyor…', p['id']):
                if self.running:
                    raise ValueError('Minecraft zaten açık.')
                self.emit('state', {'state': 'installing'})
                s = validate_settings(dict(self.settings))
                if not s['username']:
                    raise ValueError('Önce Hesabım bölümünde oyuncu adını kaydet.')
                account = {'name': s['username'], 'id': offline_uuid(s['username']), 'access_token': '0'}
                self.audit_tree()
                gdir = self.profiles.dir_of(p)
                gdir.mkdir(parents=True, exist_ok=True)
                content = self.content(p['id'])
                mods_dir = scoped(gdir, 'mods')
                mods_dir.mkdir(exist_ok=True)
                has_mods = any(f.is_file() and f.name.endswith('.jar') for f in mods_dir.iterdir())
                if has_mods:
                    try:
                        self.status('Modlar tanınıyor…')
                        self.manager().identify(content)
                    except (ConnectionError, ValueError, OSError):
                        pass
                packages = prepare_mods(gdir, self.assets, p['version'], s['externalMods'], known=content.known_files())
                if packages['disabled']:
                    self.emit('notice', {'message': str(len(packages['disabled'])) + ' bilinmeyen mod silinmeden disabled-mods klasörüne ayrıldı. Harici mod izni Güvenlik bölümünde.'})
                has_mods = any(f.is_file() and f.name.endswith('.jar') for f in mods_dir.iterdir())
                if (has_mods or packages['fabricRequired']) and p['loader'] == 'vanilla':
                    p = self.profiles.update(p['id'], {'loader': 'fabric'})
                    self.emit('notice', {'message': 'Modların çalışması için bu kurulumda Fabric açıldı.'})
                    self.emit('profiles', self.profiles_payload())
                import minecraft_launcher_lib as m
                cb = self._callbacks()
                selected, lv, major = self.prepare_loader(p, cb, m, repair)
                self.check()
                if repair:
                    self.emit('state', {'state': 'ready', 'message': 'Dosya kontrolü tamamlandı.'})
                    return
                if p['loader'] != 'vanilla' and not skip_check:
                    self.status('Modlar kontrol ediliyor…')
                    issues = modcheck.check(mods_dir, p['version'], p['loader'], lv, major, content.titles())
                    if issues:
                        self.emit('modIssues', {'profile': p['id'], 'issues': issues})
                        self.emit('state', {'state': 'ready', 'message': 'Bazı modlarda sorun bulundu.'})
                        return
                options = {'username': account['name'], 'uuid': account['id'], 'token': account['access_token'],
                           'jvmArguments': ['-Xms512M', '-Xmx' + str(s['ram']) + 'M'],
                           'launcherName': 'EkoraLauncher', 'launcherVersion': VERSION, 'gameDirectory': str(gdir)}
                if p['joinServer']:
                    host, port = self.config['serverHost'], self.config['serverPort']
                    if version_tuple(p['version']) < (1, 20):
                        options.update({'server': host, 'port': str(port)})
                    else:
                        options.update({'quickPlayMultiplayer': host, 'quickPlayPath': str(gdir / 'quickPlay.json')})
                if s['javaPath']:
                    options['executablePath'] = s['javaPath']
                self.write_game_language(gdir, s['language'])
                self.status('Minecraft başlatılıyor…')
                command = m.command.get_minecraft_command(selected, str(self.game), options)
                self.check()
                out = scoped(self.root, 'minecraft.log').open('w', encoding='utf-8')
                self.process = subprocess.Popen(command, cwd=gdir, stdout=out, stderr=subprocess.STDOUT,
                                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                self.running = p['id']
                self.profiles.touch(p['id'])
            self.emit('state', {'state': 'running', 'message': 'Minecraft açık · ' + p['name']})
            try:
                code = self.process.wait()
            finally:
                out.close()
                self.process = None
                self.running = None
            if code:
                log = ''
                try:
                    log = scoped(self.root, 'minecraft.log').read_text(encoding='utf-8', errors='replace')
                except OSError:
                    pass
                report = crash.analyze(log, p['version'], s['ram'])
                if report:
                    self.emit('crash', {**report, 'profile': p['id'], 'code': code})
                raise RuntimeError('Minecraft ' + str(code) + ' koduyla kapandı. ' +
                                   (report['title'] + '.' if report else 'Günlükler bölümünü incele.'))
            self.emit('state', {'state': 'ready', 'message': 'Minecraft kapandı.'})
        except Cancelled:
            self.emit('state', {'state': 'ready', 'message': 'Kurulum iptal edildi.'})
        except Exception as exc:
            self.record_error(exc)
            self.emit('launchError', {'message': error_tr(exc)})

    # ---------- server & updates ----------
    def server_status(self, force=False):
        t, cached = self._status_cache
        if cached and not force and time.time() - t < 10:
            return cached
        r = serverping.ping(self.config['serverHost'], self.config['serverPort'])
        self._status_cache = (time.time(), r)
        return r

    def check_launcher_update(self):
        repo = self.config['updateRepo']
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
            return None
        try:
            import requests
            r = requests.get(f'https://api.github.com/repos/{repo}/releases/latest', timeout=10,
                             headers={'User-Agent': 'EkoraLauncher/' + VERSION, 'Accept': 'application/vnd.github+json'})
            if r.status_code != 200:
                return None
            tag = str(r.json().get('tag_name', '')).lstrip('vV')
        except Exception:
            return None
        if tag and version_tuple(tag) > version_tuple(VERSION):
            return {'version': tag, 'page': self.config['downloadPage']}
        return None

    def profiles_payload(self):
        return {'profiles': [self.profile_view(p) for p in self.profiles.items], 'selected': self.profiles.selected}

    # ---------- misc (unchanged behaviour) ----------
    def public_api(self, section):
        if section == 'status':
            return self.server_status(force=True)
        if section not in ('news', 'map', 'features'):
            raise ValueError('Geçersiz bölüm.')
        return {'enabled': False} if section == 'map' else {}

    def waypoints(self):
        p = scoped(self.root, 'waypoints.json')
        try:
            return json.loads(p.read_text(encoding='utf-8')) if p.exists() else []
        except (ValueError, OSError):
            return []

    def save_waypoints(self, items):
        if not isinstance(items, list) or len(items) > 500:
            raise ValueError('En fazla 500 konum kaydedilebilir.')
        clean = []
        for w in items:
            if not isinstance(w, dict) or not isinstance(w.get('name'), str) or not 1 <= len(w['name']) <= 60:
                raise ValueError('Geçersiz konum adı.')
            if w.get('world') not in ('overworld', 'nether', 'end'):
                raise ValueError('Geçersiz dünya.')
            for k in ('x', 'y', 'z'):
                if type(w.get(k)) is not int or abs(w[k]) > 30_000_000:
                    raise ValueError('Geçersiz koordinat.')
            clean.append({k: w[k] for k in ('name', 'world', 'x', 'y', 'z')})
        atomic_json(scoped(self.root, 'waypoints.json'), clean)
        return clean

    def audit_tree(self):
        # Inspect only Ekora's own tree; never follow links into personal folders.
        no_links(self.root)
        for base, dirs, files in os.walk(self.root, followlinks=False):
            for name in dirs + files:
                no_links(Path(base) / name)

    def write_game_language(self, gdir, language):
        p = scoped(gdir, 'options.txt')
        text = p.read_text(encoding='utf-8', errors='replace') if p.exists() else ''
        lines = [line for line in text.splitlines() if not line.startswith('lang:')]
        lines.append('lang:' + language)
        tmp = scoped(gdir, 'options.txt.tmp')
        tmp.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        os.replace(tmp, p)

    def record_error(self, exc):
        try:
            p = scoped(self.root, 'launcher-error.log')
            p.write_text(time.strftime('%Y-%m-%d %H:%M:%S ') + type(exc).__name__ + ': ' + error_tr(exc)[-20000:], encoding='utf-8')
        except (OSError, ValueError):
            pass

    def diagnostics(self):
        import shutil
        disk = shutil.disk_usage(self.root)
        p = self.profiles.current()
        return {'freeGB': round(disk.free / 1024 ** 3, 1), 'installed': self.installed(),
                'gamePath': str(self.game), 'version': VERSION, 'profile': p['name'], 'profileVersion': p['version'],
                'loader': p['loader'], 'language': self.settings['language'], 'externalMods': self.settings['externalMods']}
