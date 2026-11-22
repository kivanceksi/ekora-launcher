import json
import os
import re
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
from core import Launcher, validate_settings, VERSION
from ekora_hesap import HesapHatasi
from security import asset_allowed, same_local_file, scoped, no_links, is_admin, redact_log
from i18n import error_tr

ASSETS = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent)) / 'assets'
_SLUG = re.compile(r'^[A-Za-z0-9_-]{2,64}$')
CONTENT_FOLDERS = ('mods', 'resourcepacks', 'shaderpacks')


class AssetRequests(QWebEngineUrlRequestInterceptor):
    def interceptRequest(self, info):
        if not asset_allowed(info.requestUrl().toString(), ASSETS):
            info.block(True)


class LocalPage(QWebEnginePage):
    def acceptNavigationRequest(self, url, kind, main):
        return url.isLocalFile() and same_local_file(url.toLocalFile(), ASSETS / 'index.html')

    def createWindow(self, kind):
        return None

    def chooseFiles(self, mode, old, types):
        return []


class Bridge(QObject):
    event = Signal(str)
    windowOp = Signal(str)

    def __init__(self, window):
        super().__init__()
        self.window = window
        self.launcher = Launcher(emit=self.send)
        self.connection_inflight = False
        self.connection_timer = QTimer(self)
        self.connection_timer.setInterval(30000)
        self.connection_timer.timeout.connect(self.refresh_connection)
        self.connection_timer.start()
        self.windowOp.connect(self.window_op)

    def send(self, kind, data):
        if kind == 'state' and self.launcher.settings.get('closeOnLaunch'):
            self.windowOp.emit('min' if data.get('state') == 'running' else 'restore')
        self.event.emit(json.dumps({'kind': kind, 'data': data}, ensure_ascii=False))

    @Slot(str)
    def window_op(self, op):
        if op == 'min':
            self.window.showMinimized()
        elif self.window.isMinimized():
            self.window.showNormal()
            self.window.activateWindow()

    def worker(self, fn):
        def run():
            try:
                fn()
            except Exception as e:
                self.launcher.record_error(e)
                self.send('error', {'message': error_tr(e)})
        threading.Thread(target=run, daemon=True).start()

    def job(self, text, pid, fn):
        """Runs fn inside the launcher's exclusive job, reporting errors in Turkish."""
        def run():
            with self.launcher.job(text, pid):
                fn()
        self.worker(run)

    # ------------------------------------------------------------------ requests
    @Slot(str)
    def request(self, raw):
        try:
            if len(raw) > 200000:
                raise ValueError('İstek boyutu çok büyük.')
            page_url = self.window.page.url()
            if not page_url.isLocalFile() or not same_local_file(page_url.toLocalFile(), ASSETS / 'index.html'):
                raise ValueError('Geçersiz arayüz kaynağı.')
            msg = json.loads(raw)
            action = msg['action']
            data = msg.get('data', {})
            if not isinstance(data, (dict, list)):
                raise ValueError('Geçersiz istek.')
            handler = getattr(self, 'do_' + action, None) if re.fullmatch(r'[A-Za-z]{2,30}', str(action)) else None
            if handler is None:
                raise ValueError('Bilinmeyen işlem.')
            handler(data)
        except Exception as e:
            self.launcher.record_error(e)
            self.send('error', {'message': error_tr(e)})

    def pid(self, data):
        pid = data.get('profile') if isinstance(data, dict) else None
        return self.launcher.profiles.need(pid or self.launcher.profiles.selected)['id']

    # --- basics
    def do_init(self, data):
        self.send('init', self.launcher.snapshot())
        self.send('waypoints', self.launcher.waypoints())
        self.refresh_connection()

        def upd():
            u = self.launcher.check_launcher_update()
            if u:
                self.send('launcherUpdate', u)
        self.worker(upd)

    def do_save(self, data):
        self.send('saved', self.launcher.save(data))

    def do_legal(self, data):
        links = {'eula': 'https://www.minecraft.net/en-us/eula', 'guidelines': 'https://www.minecraft.net/en-us/usage-guidelines',
                 'privacy': 'https://privacy.microsoft.com/privacystatement', 'modrinth': 'https://modrinth.com/legal/terms',
                 'download': self.launcher.config['downloadPage']}
        QDesktopServices.openUrl(QUrl(links[data['name']]))

    def do_openModrinth(self, data):
        slug, kind = str(data.get('slug', '')), str(data.get('type', 'mod'))
        if not _SLUG.match(slug) or kind not in ('mod', 'resourcepack', 'shader', 'modpack'):
            raise ValueError('Geçersiz bağlantı.')
        QDesktopServices.openUrl(QUrl(f'https://modrinth.com/{kind}/{slug}'))

    def do_launch(self, data):
        pid = self.pid(data)
        skip = bool(data.get('skipCheck'))
        self.worker(lambda: self.launcher.launch(pid=pid, skip_check=skip))

    def do_repair(self, data):
        pid = self.pid(data)
        self.worker(lambda: self.launcher.launch(repair=True, pid=pid))

    def do_cancel(self, data):
        self.launcher.cancel.set()

    def do_versions(self, data):
        def versions():
            import minecraft_launcher_lib as m
            snaps = bool(data.get('snapshots'))
            out = [v['id'] for v in m.utils.get_version_list() if v['type'] == 'release' or (snaps and v['type'] == 'snapshot')]
            self.send('versions', out[:120])
        self.worker(versions)

    def do_api(self, data):
        section = data['section']
        self.worker(lambda: self.send('api', {'section': section, 'payload': self.launcher.public_api(section)}))

    def do_waypoints(self, data):
        self.send('waypoints', self.launcher.save_waypoints(data))

    def do_folder(self, data):
        L = self.launcher
        if data.get('name') == 'logs':
            p = L.root
        else:
            base = L.profiles.dir_of(L.profiles.need(self.pid(data)))
            sub = {'game': None, 'mods': 'mods', 'resourcepacks': 'resourcepacks', 'shaderpacks': 'shaderpacks',
                   'disabledMods': 'disabled-mods', 'screenshots': 'screenshots', 'saves': 'saves', 'crashReports': 'crash-reports'}[data['name']]
            p = scoped(base, sub) if sub else base
        no_links(p)
        p.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(p)))

    def do_diagnostics(self, data):
        self.worker(lambda: self.send('diagnostics', self.launcher.diagnostics()))

    def do_logs(self, data):
        chunks = []
        for name in ('launcher-error.log', 'minecraft.log'):
            p = scoped(self.launcher.root, name)
            if p.exists():
                with p.open('rb') as f:
                    f.seek(max(0, p.stat().st_size - 60000))
                    chunks.append(name + '\n' + redact_log(f.read(60000).decode('utf-8', 'replace')))
        self.send('logs', '\n\n'.join(chunks) or 'Henüz oyun günlüğü yok.')

    def do_java(self, data):
        path, _ = QFileDialog.getOpenFileName(self.window, 'Java çalıştırılabilir dosyası seç', filter='Java (java.exe javaw.exe java);;Tüm dosyalar (*)')
        if path:
            self.send('java', path)

    # --- Ekora hesabı (site) ---
    def _hesap_gonder(self, zorla=False):
        H = self.launcher.hesap
        d = dict(H.durum(zorla=zorla))
        if d.get('mcAd'):
            d['avatar'] = H.avatar(d['mcAd'])
        self.send('hesap', d)

    def do_hesapGiris(self, data):
        eposta, sifre = str(data.get('eposta', '')), str(data.get('sifre', ''))
        def run():
            try:
                self.launcher.hesap.giris(eposta, sifre)
                self._hesap_gonder()
            except HesapHatasi as e:
                self.send('hesapHata', {'message': str(e)})
        self.worker(run)

    def do_hesapCikis(self, data):
        def run():
            self.launcher.hesap.cikis()
            self.send('hesap', {'girisli': False})
        self.worker(run)

    def do_hesapDurum(self, data):
        def run():
            try:
                self._hesap_gonder(zorla=bool(data.get('yenile')))
            except HesapHatasi as e:
                self.send('hesap', {'girisli': self.launcher.hesap.girisli(), 'hata': str(e)})
        self.worker(run)

    def do_haberler(self, data):
        def run():
            H = self.launcher.hesap
            try:
                self.send('haberler', {'haberler': H.haberler(), 'etkinlikler': H.etkinlikler()})
            except HesapHatasi as e:
                self.send('haberler', {'hata': str(e)})
        self.worker(run)

    def do_siralamaMeta(self, data):
        def run():
            try:
                self.send('siralamaMeta', {'liste': self.launcher.hesap.siralama_meta()})
            except HesapHatasi as e:
                self.send('siralamaMeta', {'hata': str(e)})
        self.worker(run)

    def do_siralama(self, data):
        mod, olcu = str(data.get('mod', '')), str(data.get('olcu', ''))
        def run():
            H = self.launcher.hesap
            try:
                rows = H.siralama(mod, olcu)
                for r in rows[:3]:
                    r['avatar'] = H.avatar(r['ad'])
                self.send('siralama', {'mod': mod, 'olcu': olcu, 'satirlar': rows})
            except HesapHatasi as e:
                self.send('siralama', {'mod': mod, 'olcu': olcu, 'hata': str(e)})
        self.worker(run)

    def do_oyuncu(self, data):
        ad = str(data.get('ad', ''))
        def run():
            H = self.launcher.hesap
            try:
                self.send('oyuncu', {'ad': ad, 'istatistik': H.oyuncu(ad), 'cezalar': H.cezalar(ad, 20)})
            except HesapHatasi as e:
                self.send('oyuncu', {'ad': ad, 'hata': str(e)})
        self.worker(run)

    def do_cezalar(self, data):
        ad = str(data.get('ad', '') or '').strip()
        def run():
            try:
                self.send('cezalar', {'ad': ad, 'liste': self.launcher.hesap.cezalar(ad or None)})
            except HesapHatasi as e:
                self.send('cezalar', {'ad': ad, 'hata': str(e)})
        self.worker(run)

    def do_site(self, data):
        QDesktopServices.openUrl(QUrl(self.launcher.hesap.site_adresi(str(data.get('yol', '')))))

    # --- profiles
    def send_profiles(self):
        self.send('profiles', self.launcher.profiles_payload())

    def do_selectProfile(self, data):
        self.launcher.profiles.select(str(data.get('id')))
        self.send_profiles()

    def do_createProfile(self, data):
        L = self.launcher
        if L.busy:
            raise ValueError('Başka bir işlem sürüyor.')
        p = L.profiles.create(str(data.get('name', '')).strip() or 'Yeni kurulum', str(data.get('version', '')),
                              str(data.get('loader', 'fabric')), bool(data.get('joinServer')), str(data.get('color', 'cyan')))
        L.profiles.select(p['id'])
        self.send_profiles()
        self.send('notice', {'message': f"“{p['name']}” kurulumu oluşturuldu. İlk açılışta oyun dosyaları iner."})

    def do_updateProfile(self, data):
        L = self.launcher
        pid = self.pid({'profile': data.get('id')})
        if L.busy or L.running == pid:
            raise ValueError('Önce süren işlemi bitir veya oyunu kapat.')
        L.profiles.update(pid, data.get('changes') or {})
        self.send_profiles()
        self.send('notice', {'message': 'Kurulum güncellendi.'})

    def do_deleteProfile(self, data):
        L = self.launcher
        pid = self.pid({'profile': data.get('id')})
        if L.busy or L.running == pid:
            raise ValueError('Önce süren işlemi bitir veya oyunu kapat.')
        removed = L.profiles.delete(pid)
        self.send_profiles()
        self.send('notice', {'message': 'Kurulum silindi.' if removed else 'Kurulum listeden kaldırıldı. Eski dosyaların oyun klasöründe duruyor.'})

    # --- content of an installation
    def send_content(self, pid, identify=True):
        c = self.launcher.content(pid)
        payload = {'profile': pid}
        for folder in CONTENT_FOLDERS:
            payload[folder] = c.listing(folder)
        self.send('content', payload)
        if identify:
            def ident():
                L = self.launcher
                if L.busy:
                    return
                try:
                    with L.job('Modlar tanınıyor…', None):
                        found = L.manager().identify(L.content(pid))
                    if found:
                        self.send_content(pid, identify=False)
                except (ConnectionError, ValueError, OSError):
                    pass
            self.worker(ident)

    def do_content(self, data):
        self.send_content(self.pid(data))

    def _content_file(self, data):
        pid = self.pid(data)
        folder, name = str(data.get('folder')), str(data.get('file'))
        if folder not in CONTENT_FOLDERS or Path(name).name != name:
            raise ValueError('Geçersiz dosya.')
        if self.launcher.busy or self.launcher.running == pid:
            raise ValueError('Önce süren işlemi bitir veya oyunu kapat.')
        c = self.launcher.content(pid)
        f = scoped(c.folder(folder), name)
        if not f.is_file():
            raise ValueError('Dosya bulunamadı.')
        return pid, c, folder, f

    def do_toggleContent(self, data):
        pid, c, folder, f = self._content_file(data)
        if f.name.endswith('.disabled') and folder == 'mods' and not self.launcher.settings['externalMods'] \
                and c.base(f.name) not in c.known_files():
            raise ValueError('Bilinmeyen bir modu etkinleştirmek için Güvenlik bölümünden harici mod izni ver.')
        target = f.with_name(f.name[:-9] if f.name.endswith('.disabled') else f.name + '.disabled')
        no_links(target)
        if target.exists():
            raise ValueError('Hedef dosya zaten var.')
        f.rename(target)
        self.send_content(pid, identify=False)

    def do_removeContent(self, data):
        pid, c, folder, f = self._content_file(data)
        c.forget(folder, f.name)
        f.unlink()
        c.save()
        self.send_content(pid, identify=False)
        self.send_profiles()
        self.send('notice', {'message': 'Silindi: ' + c.base(f.name)})

    def do_importContent(self, data):
        L = self.launcher
        pid = self.pid(data)
        kind = data.get('kind')
        if L.busy or L.running == pid:
            raise ValueError('Önce süren işlemi bitir veya oyunu kapat.')
        if kind not in CONTENT_FOLDERS:
            raise ValueError('Geçersiz paket türü.')
        ext = '.jar' if kind == 'mods' else '.zip'
        label = {'mods': 'Fabric modunu', 'resourcepacks': 'kaynak paketini', 'shaderpacks': 'shader paketini'}[kind]
        paths, _ = QFileDialog.getOpenFileNames(self.window, 'Güvendiğin ' + label + ' seç', filter='Paket (*' + ext + ')')
        if not paths:
            return
        c = L.content(pid)
        dest = c.folder(kind)
        added = 0
        for path in paths[:50]:
            source = no_links(Path(path))
            if source.stat().st_size > 600 * 1024 * 1024 or source.suffix.lower() != ext:
                continue
            target = scoped(dest, source.name)
            if target.exists():
                continue
            shutil.copy2(source, target)
            added += 1
        if kind == 'mods' and added and not L.settings['externalMods']:
            self.send('notice', {'message': f'{added} dosya eklendi. Modrinth’te tanınmayan modlar için Güvenlik bölümünden harici mod izni gerekir.'})
        else:
            self.send('notice', {'message': f'{added} dosya eklendi.'})
        self.send_content(pid)
        self.send_profiles()

    # --- Modrinth
    def do_search(self, data):
        L = self.launcher
        req = {k: data.get(k) for k in ('query', 'type', 'version', 'loader', 'offset', 'sort', 'category', 'token')}

        def run():
            res = L.api.search(str(req.get('query') or ''), str(req.get('type') or 'mod'), req.get('version') or None,
                               str(req.get('loader') or 'fabric'), int(req.get('offset') or 0), 20,
                               str(req.get('sort') or 'relevance'), req.get('category'))
            self.send('searchResults', {**res, 'request': req})
        self.worker(run)

    def do_installedIds(self, data):
        pid = self.pid(data)
        c = self.launcher.content(pid)
        ids = sorted({e.get('projectId') for e in c.data['files'].values() if e.get('projectId')})
        self.send('installedIds', {'profile': pid, 'ids': ids})

    def do_icons(self, data):
        urls = [u for u in (data.get('urls') or [])[:60] if isinstance(u, str)]
        L = self.launcher

        def run():
            from concurrent.futures import ThreadPoolExecutor
            cache = scoped(L.root, 'cache', 'icons')
            with ThreadPoolExecutor(max_workers=6) as ex:
                res = dict(zip(urls, ex.map(lambda u: L.api.icon(u, cache), urls)))
            self.send('icons', {u: d for u, d in res.items() if d})
        self.worker(run)

    def _ensure_loader(self, pid):
        L = self.launcher
        p = L.profiles.need(pid)
        if p['loader'] == 'vanilla':
            L.profiles.update(pid, {'loader': 'fabric'})
            self.send('notice', {'message': 'Modlar için bu kurulumda Fabric açıldı.'})

    def after_install(self, pid, titles, warnings, project_id=None):
        if titles:
            self.send('notice', {'message': 'Kuruldu: ' + ', '.join(titles[:6]) + (' …' if len(titles) > 6 else '')})
        for w in warnings[:3]:
            self.send('notice', {'message': w})
        self.send('installed', {'profile': pid, 'projectId': project_id, 'titles': titles})
        self.send_content(pid, identify=False)
        self.send_profiles()
        self.do_installedIds({'profile': pid})

    def do_install(self, data):
        L = self.launcher
        pid = self.pid(data)
        ptype = str(data.get('type', 'mod'))
        project = str(data.get('projectId', ''))
        if ptype not in ('mod', 'resourcepack', 'shader') or not _SLUG.match(project):
            raise ValueError('Geçersiz içerik.')
        meta = {'title': str(data.get('title') or '')[:80], 'slug': str(data.get('slug') or '')[:64],
                'icon_url': str(data.get('iconUrl') or '')[:300]}

        def run():
            from modrinth import IRIS, NotAvailable
            mgr = L.manager()
            titles, warnings = [], []
            if ptype in ('mod', 'shader'):
                self._ensure_loader(pid)
            p = L.profiles.need(pid)
            c = L.content(pid)
            if ptype == 'shader' and c.find_project('mods', IRIS)[0] is None:
                try:
                    a, w = mgr.install(c, IRIS, 'mod', p, cancel=L.cancel, emit=self.send)
                    titles += a
                    warnings += w
                except NotAvailable:
                    warnings.append(f"Shader'lar için gereken Iris, Minecraft {p['version']} için yok.")
            a, w = mgr.install(c, project, ptype, p, cancel=L.cancel, emit=self.send, meta=meta if meta['title'] else None)
            self.after_install(pid, titles + a, warnings + w, project)
        self.job('İçerik kuruluyor…', pid, run)

    def do_installPack(self, data):
        L = self.launcher
        project = str(data.get('projectId', ''))
        if not _SLUG.match(project):
            raise ValueError('Geçersiz paket.')

        def run():
            from modpaket import install_pack
            info = L.api.project(project)
            if not info:
                raise ValueError('Paket bulunamadı.')
            p = install_pack(L.api, L.profiles, info, scoped(L.root, 'cache'), data.get('version') or None,
                             cancel=L.cancel, emit=self.send)
            L.profiles.select(p['id'])
            self.send_profiles()
            self.send('notice', {'message': f"“{p['name']}” mod paketi yeni bir kurulum olarak hazırlandı."})
            self.send('packInstalled', {'profile': p['id']})
        self.job('Mod paketi kuruluyor…', None, run)

    def do_perfPack(self, data):
        L = self.launcher
        pid = self.pid(data)

        def run():
            from modrinth import PERFORMANCE, NotAvailable
            self._ensure_loader(pid)
            p = L.profiles.need(pid)
            c = L.content(pid)
            mgr = L.manager()
            titles, missing = [], []
            for slug in PERFORMANCE:
                try:
                    info = L.api.project(slug)
                    if not info:
                        continue
                    if c.find_project('mods', info['id'])[0] is not None:
                        continue
                    a, w = mgr.install(c, info['id'], 'mod', p, cancel=L.cancel, emit=self.send, meta=info)
                    titles += a
                except NotAvailable as e:
                    missing.append(e.title)
            warnings = [f"Bu sürüm için henüz yok: {', '.join(missing)}."] if missing else []
            if not titles and not missing:
                self.send('notice', {'message': 'FPS paketindeki modlar bu kurulumda zaten var.'})
            self.after_install(pid, titles, warnings)
        self.job('FPS paketi kuruluyor…', pid, run)

    def do_checkUpdates(self, data):
        L = self.launcher
        pid = self.pid(data)

        def run():
            items = L.manager().check_updates(L.content(pid), L.profiles.need(pid))
            self.send('updates', {'profile': pid, 'items': items})
        self.worker(run)

    def do_applyUpdates(self, data):
        L = self.launcher
        pid = self.pid(data)
        items = [i for i in (data.get('items') or []) if isinstance(i, dict)][:200]

        def run():
            mgr = L.manager()
            p = L.profiles.need(pid)
            todo = items or mgr.check_updates(L.content(pid), p)
            titles, warnings = [], []
            for it in todo:
                if it.get('folder') not in CONTENT_FOLDERS or not _SLUG.match(str(it.get('projectId', ''))) \
                        or not _SLUG.match(str(it.get('versionId', ''))):
                    continue
                try:
                    a, w = mgr.apply_update(L.content(pid), it, p, cancel=L.cancel, emit=self.send)
                    titles += a
                    warnings += w
                except Exception as e:
                    warnings.append(f"{it.get('title')}: {error_tr(e)}")
            if not todo:
                self.send('notice', {'message': 'Her şey güncel.'})
            self.after_install(pid, titles, warnings)
            self.send('updates', {'profile': pid, 'items': []})
        self.job('Güncellemeler kuruluyor…', pid, run)

    def do_fixIssues(self, data):
        L = self.launcher
        pid = self.pid(data)
        relaunch = bool(data.get('relaunch'))

        def run():
            import modcheck
            mgr = L.manager()
            p = L.profiles.need(pid)
            msgs = []
            c = L.content(pid)
            for _ in range(3):  # fixing one problem can reveal the next (e.g. a missing dependency)
                d = L.profiles.dir_of(p)
                issues = modcheck.check(scoped(d, 'mods'), p['version'], p['loader'], p.get('loaderVersion') or None, None, c.titles())
                if not issues:
                    break
                for issue in issues:
                    try:
                        m = mgr.fix_issue(c, issue, p, cancel=L.cancel, emit=self.send)
                    except Exception as e:
                        mgr.disable(c, issue['file'])
                        m = f"{issue['mod']} devre dışı bırakıldı ({error_tr(e)})"
                    if m:
                        msgs.append(m)
                    if issue['type'] == 'loaderOld':
                        L.profiles.set_loader_version(pid, '')
                        msgs.append('Fabric Loader en yeni sürüme güncellenecek.')
                c = L.content(pid)
            self.send('fixed', {'profile': pid, 'messages': msgs, 'relaunch': relaunch})
            self.send_content(pid, identify=False)
            self.send_profiles()
        self.job('Modlar düzeltiliyor…', pid, run)

    def do_launchCheck(self, data):
        L = self.launcher
        pid = self.pid(data)

        def run():
            import modcheck
            p = L.profiles.need(pid)
            c = L.content(pid)
            try:
                L.manager().identify(c)
            except (ConnectionError, ValueError, OSError):
                pass
            issues = modcheck.check(scoped(L.profiles.dir_of(p), 'mods'), p['version'],
                                    p['loader'] if p['loader'] != 'vanilla' else 'fabric', p.get('loaderVersion') or None, None, c.titles())
            self.send('modIssues' if issues else 'noIssues', {'profile': pid, 'issues': issues})
        self.worker(run)

    def do_crashAction(self, data):
        L = self.launcher
        pid = self.pid(data)
        a = data.get('action') or {}
        kind = a.get('kind')
        if kind == 'switchVersion':
            p = L.profiles.need(pid)
            if p['official']:
                raise ValueError('EkoraMC kurulumunun sürümü sunucuya göre sabittir. Modları düzeltmeyi dene.')
            L.profiles.update(pid, {'version': str(a.get('version'))})
            self.send_profiles()
            self.send('notice', {'message': f"Kurulum Minecraft {a.get('version')} yapıldı."})
        elif kind == 'setRam':
            v = int(a.get('value', 4096))
            self.send('saved', L.save({**L.settings, 'ram': max(1024, min(16384, v))}))
        elif kind == 'resetJava':
            self.send('saved', L.save({**L.settings, 'javaPath': ''}))
        elif kind == 'disableIds':
            import modcheck
            ids = {str(i) for i in a.get('ids') or []}
            c = L.content(pid)
            n = 0
            for f in c.files('mods'):
                if f.name.endswith('.jar') and modcheck.read_jar(f).get('id') in ids:
                    L.manager().disable(c, f.name)
                    n += 1
            self.send('notice', {'message': f'{n} mod devre dışı bırakıldı.'})
            self.send_content(pid, identify=False)
        elif kind == 'updateMods':
            self.do_applyUpdates({'profile': pid})
        elif kind == 'fixMods':
            self.do_fixIssues({'profile': pid})
        else:
            raise ValueError('Bilinmeyen işlem.')

    # ------------------------------------------------------------------ status
    def refresh_connection(self):
        if self.connection_inflight:
            return
        self.connection_inflight = True

        def check():
            try:
                d = self.launcher.server_status()
                self.send('connection', {'state': 'connected', 'online': d['online'], 'players': d['players']['online'] if d['online'] else None,
                                         'max': d['players']['max'], 'version': d.get('version', ''), 'motd': d.get('motd', ''),
                                         'latency': d.get('latency')})
            finally:
                self.connection_inflight = False
        self.worker(check)


class Window(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('Ekora Launcher')
        self.resize(1280, 860)
        self.setMinimumSize(980, 680)
        self.setWindowIcon(QIcon(str(ASSETS / 'logo.png')))
        self.view = QWebEngineView()
        self.setCentralWidget(self.view)
        self.profile = QWebEngineProfile(self.view)  # off-the-record: no persistent browser profile
        self.profile.setHttpAcceptLanguage('tr-TR,tr;q=0.9')
        self.interceptor = AssetRequests(self.profile)
        self.profile.setUrlRequestInterceptor(self.interceptor)
        self.profile.downloadRequested.connect(lambda item: item.cancel())
        self.page = LocalPage(self.profile, self.view)
        self.view.setPage(self.page)
        self.page.permissionRequested.connect(lambda permission: permission.deny())
        for name in ('JavascriptCanAccessClipboard', 'JavascriptCanPaste', 'JavascriptCanOpenWindows', 'LocalStorageEnabled',
                     'FullScreenSupportEnabled', 'ScreenCaptureEnabled'):
            self.page.settings().setAttribute(getattr(QWebEngineSettings.WebAttribute, name), False)
        self.page.settings().setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls, False)
        self.bridge = Bridge(self)
        self.channel = QWebChannel()
        self.channel.registerObject('ekora', self.bridge)
        self.page.setWebChannel(self.channel)
        self.view.setUrl(QUrl.fromLocalFile(str(ASSETS / 'index.html')))

    def closeEvent(self, event):
        L = self.bridge.launcher
        if L.busy or L.running:
            QMessageBox.information(self, 'İşlem devam ediyor', 'Kapatmadan önce süren işlemi iptal et veya Minecraft’ı kapat.')
            event.ignore()
        else:
            event.accept()


def main():
    app = QApplication(sys.argv)
    app.setApplicationName('EkoraLauncher')
    app.setOrganizationName('EkoraMC')
    app.setWindowIcon(QIcon(str(ASSETS / 'logo.png')))
    if is_admin():
        QMessageBox.warning(None, 'Normal kullanıcı olarak aç', 'Ekora Launcher yönetici yetkisi istemez. Uygulamayı kapatıp normal çift tıklamayla aç.')
        return 1
    root = Path(os.getenv('EKORA_HOME') or Path(os.getenv('APPDATA', Path.home())) / 'EkoraLauncher')
    root.mkdir(parents=True, exist_ok=True)
    lock = QLockFile(str(root / 'launcher.lock'))
    if not lock.tryLock(0):
        QMessageBox.information(None, 'Ekora Launcher', 'Launcher zaten açık.')
        return 0
    w = Window()
    w.show()
    return app.exec()


if __name__ == '__main__':
    if '--smoke-test' in sys.argv:
        from smoke_test import run
        sys.exit(run())
    sys.exit(main())
