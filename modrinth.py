"""Modrinth API client and per-installation content manager.

Every download is checked against the SHA-512 (and SHA-1) that Modrinth reports
before it is moved into the game folder. Files are only accepted from Modrinth's
CDN (mod packs may also use the hosts that Modrinth itself allows).
"""
import base64
import hashlib
import json
import re
import threading
import time
from pathlib import Path
from urllib.parse import urlsplit
from security import scoped, no_links

API = 'https://api.modrinth.com/v2'
CDN_HOSTS = {'cdn.modrinth.com'}
PACK_HOSTS = {'cdn.modrinth.com', 'github.com', 'raw.githubusercontent.com', 'gitlab.com',
              'objects.githubusercontent.com', 'release-assets.githubusercontent.com'}
FOLDERS = {'mod': 'mods', 'resourcepack': 'resourcepacks', 'shader': 'shaderpacks'}
SUFFIX = {'mods': ('.jar',), 'resourcepacks': ('.zip',), 'shaderpacks': ('.zip',)}
MAX_FILE = 600 * 1024 * 1024
IRIS = 'YL57xq9U'
FABRIC_API = 'P7dR8mSH'
PERFORMANCE = ['sodium', 'lithium', 'ferrite-core', 'entityculling', 'immediatelyfast', 'dynamic-fps', 'modmenu']
SORTS = ('relevance', 'downloads', 'follows', 'newest', 'updated')
_ID = re.compile(r'^[A-Za-z0-9_-]{2,64}$')


class NotAvailable(Exception):
    def __init__(self, title, version):
        super().__init__(f"{title} için Minecraft {version} sürümü Modrinth'te yok.")
        self.title = title


class Cancelled(Exception):
    pass


def safe_name(name):
    name = Path(str(name)).name
    name = re.sub(r'[^A-Za-z0-9._+()\[\] -]', '_', name).strip(' .')
    if not name or name.startswith('.'):
        raise ValueError('Geçersiz dosya adı.')
    return name[:150]


def file_hashes(path):
    s1, s512 = hashlib.sha1(), hashlib.sha512()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            s1.update(block)
            s512.update(block)
    return s1.hexdigest(), s512.hexdigest()


def loaders_for(ptype, loader):
    if ptype == 'mod':
        return ['quilt', 'fabric'] if loader == 'quilt' else ['fabric']
    if ptype == 'resourcepack':
        return ['minecraft']
    if ptype == 'shader':
        return ['iris', 'optifine']
    if ptype == 'modpack':
        return ['quilt', 'fabric'] if loader == 'quilt' else ['fabric', 'quilt']
    raise ValueError('Desteklenmeyen içerik türü.')


def pick(versions, loaders=None):
    """Newest release, else newest beta, else newest alpha. Prefers the first loader."""
    if not versions:
        return None
    for vt in ('release', 'beta', 'alpha'):
        cands = [v for v in versions if v.get('version_type') == vt]
        if loaders:
            for ld in loaders:
                pref = [v for v in cands if ld in (v.get('loaders') or [])]
                if pref:
                    return pref[0]
        if cands:
            return cands[0]
    return versions[0]


def primary_file(version):
    files = version.get('files') or []
    if not files:
        raise ValueError('Bu sürümde indirilecek dosya yok.')
    return next((f for f in files if f.get('primary')), files[0])


class Api:
    def __init__(self, user_agent):
        import requests
        self._requests = requests
        self.s = requests.Session()
        self.s.headers.update({'User-Agent': user_agent, 'Accept': 'application/json'})
        self._cache = {}
        self._lock = threading.Lock()

    def _call(self, method, path, params=None, body=None, ttl=0):
        key = (method, path, json.dumps(params, sort_keys=True), json.dumps(body, sort_keys=True))
        if ttl:
            with self._lock:
                hit = self._cache.get(key)
            if hit and hit[0] > time.time():
                return hit[1]
        try:
            r = self.s.request(method, API + path, params=params, json=body, timeout=20)
        except self._requests.RequestException:
            raise ConnectionError("Modrinth'e bağlanılamadı. İnternet bağlantını kontrol et.") from None
        if r.status_code == 404:
            return None
        if r.status_code == 429:
            raise ConnectionError("Modrinth şu an çok yoğun. Birkaç saniye sonra yeniden dene.")
        if r.status_code >= 400:
            raise ConnectionError(f"Modrinth isteği başarısız oldu (HTTP {r.status_code}).")
        data = r.json()
        if ttl:
            with self._lock:
                if len(self._cache) > 400:
                    self._cache.clear()
                self._cache[key] = (time.time() + ttl, data)
        return data

    def search(self, query='', ptype='mod', game_version=None, loader='fabric', offset=0, limit=20, sort='relevance', category=None):
        if ptype not in ('mod', 'resourcepack', 'shader', 'modpack'):
            raise ValueError('Geçersiz içerik türü.')
        facets = [[f'project_type:{ptype}']]
        if game_version:
            facets.append([f'versions:{game_version}'])
        if ptype == 'mod':
            facets.append([f'categories:{x}' for x in loaders_for('mod', loader)])
        elif ptype == 'shader':
            facets.append(['categories:iris'])
        elif ptype == 'modpack':
            facets.append(['categories:fabric', 'categories:quilt'])
        if category and re.match(r'^[a-z0-9-]{2,30}$', category):
            facets.append([f'categories:{category}'])
        params = {'query': str(query)[:100], 'facets': json.dumps(facets), 'offset': max(0, int(offset)),
                  'limit': max(1, min(40, int(limit))), 'index': sort if sort in SORTS else 'relevance'}
        data = self._call('GET', '/search', params, ttl=120) or {'hits': [], 'total_hits': 0}
        hits = []
        for h in data.get('hits', []):
            hits.append({'id': h.get('project_id'), 'slug': h.get('slug'), 'title': h.get('title'), 'author': h.get('author'),
                         'description': h.get('description'), 'downloads': h.get('downloads', 0), 'follows': h.get('follows', 0),
                         'iconUrl': h.get('icon_url') or '', 'categories': [c for c in h.get('display_categories') or []
                                                                           if c not in ('fabric', 'forge', 'neoforge', 'quilt', 'liteloader', 'rift', 'ornithe', 'iris', 'optifine', 'canvas', 'vanilla', 'minecraft')][:4],
                         'type': h.get('project_type'), 'updated': h.get('date_modified', '')[:10]})
        return {'hits': hits, 'total': data.get('total_hits', 0), 'offset': data.get('offset', 0)}

    def project(self, pid):
        if not _ID.match(str(pid)):
            raise ValueError('Geçersiz proje.')
        return self._call('GET', f'/project/{pid}', ttl=600)

    def projects(self, ids):
        ids = [i for i in ids if _ID.match(str(i))]
        if not ids:
            return []
        out = []
        for i in range(0, len(ids), 80):
            out += self._call('GET', '/projects', {'ids': json.dumps(ids[i:i + 80])}, ttl=600) or []
        return out

    def versions(self, pid, loaders=None, game_versions=None):
        if not _ID.match(str(pid)):
            raise ValueError('Geçersiz proje.')
        params = {}
        if loaders:
            params['loaders'] = json.dumps(loaders)
        if game_versions:
            params['game_versions'] = json.dumps(game_versions)
        return self._call('GET', f'/project/{pid}/version', params, ttl=120) or []

    def version(self, vid):
        if not _ID.match(str(vid)):
            raise ValueError('Geçersiz sürüm.')
        return self._call('GET', f'/version/{vid}', ttl=600)

    def by_hashes(self, sha1s):
        if not sha1s:
            return {}
        return self._call('POST', '/version_files', body={'hashes': sha1s, 'algorithm': 'sha1'}) or {}

    def updates(self, sha1s, loaders, game_versions):
        if not sha1s:
            return {}
        return self._call('POST', '/version_files/update', body={'hashes': sha1s, 'algorithm': 'sha1', 'loaders': loaders,
                                                                  'game_versions': game_versions}) or {}

    def download(self, url, dest, sha512=None, sha1=None, size=None, hosts=CDN_HOSTS, cancel=None):
        u = urlsplit(str(url))
        if u.scheme != 'https' or u.hostname not in hosts or u.username or u.password:
            raise ValueError('Güvenilmeyen indirme adresi engellendi: ' + str(u.hostname))
        if size and size > MAX_FILE:
            raise ValueError('Dosya çok büyük.')
        if not sha512 and not sha1:
            raise ValueError('Doğrulama özeti olmayan dosya indirilmez.')
        dest = Path(dest)
        no_links(dest)
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_name(dest.name + '.indiriliyor')
        h1, h512, total = hashlib.sha1(), hashlib.sha512(), 0
        try:
            with self.s.get(url, stream=True, timeout=30) as r:
                if r.status_code != 200:
                    raise ConnectionError(f'İndirme başarısız (HTTP {r.status_code}).')
                final = urlsplit(r.url)
                if final.scheme != 'https':
                    raise ValueError('Güvensiz yönlendirme engellendi.')
                with open(tmp, 'wb') as f:
                    for chunk in r.iter_content(256 * 1024):
                        if cancel is not None and cancel.is_set():
                            raise Cancelled()
                        total += len(chunk)
                        if total > MAX_FILE:
                            raise ValueError('Dosya çok büyük.')
                        h1.update(chunk)
                        h512.update(chunk)
                        f.write(chunk)
        except self._requests.RequestException:
            tmp.unlink(missing_ok=True)
            raise ConnectionError('Dosya indirilemedi. İnternet bağlantını kontrol et.') from None
        except BaseException:
            tmp.unlink(missing_ok=True)
            raise
        if (sha512 and h512.hexdigest() != sha512.lower()) or (sha1 and h1.hexdigest() != sha1.lower()):
            tmp.unlink(missing_ok=True)
            raise ValueError('İndirilen dosya doğrulanamadı (özet uyuşmuyor). Güvenlik için silindi.')
        tmp.replace(dest)
        return h1.hexdigest(), h512.hexdigest()

    def icon(self, url, cache_dir):
        u = urlsplit(str(url))
        if u.scheme != 'https' or u.hostname not in CDN_HOSTS:
            return None
        cache_dir = Path(cache_dir)
        name = hashlib.sha1(url.encode()).hexdigest()
        p = cache_dir / name
        data = None
        if p.is_file():
            data = p.read_bytes()
        else:
            try:
                r = self.s.get(url, timeout=15)
            except self._requests.RequestException:
                return None
            if r.status_code != 200 or len(r.content) > 400_000:
                return None
            data = r.content
            try:
                cache_dir.mkdir(parents=True, exist_ok=True)
                p.write_bytes(data)
            except OSError:
                pass
        if data[:8] == b'\x89PNG\r\n\x1a\n':
            mime = 'image/png'
        elif data[:4] == b'RIFF' and data[8:12] == b'WEBP':
            mime = 'image/webp'
        elif data[:3] == b'\xff\xd8\xff':
            mime = 'image/jpeg'
        elif data[:4] == b'GIF8':
            mime = 'image/gif'
        else:
            return None
        return f'data:{mime};base64,' + base64.b64encode(data).decode()


class Content:
    """Mods, resource packs and shader packs of one installation."""

    def __init__(self, game_dir):
        self.dir = Path(game_dir)
        self.file = scoped(self.dir, 'ekora-content.json')
        self.data = {'schemaVersion': 1, 'files': {}}
        if self.file.exists():
            try:
                d = json.loads(self.file.read_text(encoding='utf-8'))
                if isinstance(d, dict) and isinstance(d.get('files'), dict):
                    self.data = d
            except (ValueError, OSError):
                pass

    def save(self):
        no_links(self.file)
        tmp = self.file.with_suffix('.tmp')
        tmp.write_text(json.dumps(self.data, ensure_ascii=False, indent=1), encoding='utf-8')
        tmp.replace(self.file)

    @staticmethod
    def base(name):
        return name[:-9] if name.endswith('.disabled') else name

    def folder(self, name):
        if name not in SUFFIX:
            raise ValueError('Geçersiz klasör.')
        p = scoped(self.dir, name)
        p.mkdir(exist_ok=True)
        return p

    def entry(self, folder, name):
        return self.data['files'].get(folder + '/' + self.base(name))

    def record(self, folder, name, info):
        self.data['files'][folder + '/' + self.base(name)] = info

    def forget(self, folder, name):
        self.data['files'].pop(folder + '/' + self.base(name), None)

    def files(self, folder):
        p = self.folder(folder)
        out = []
        for f in sorted(p.iterdir(), key=lambda x: x.name.lower()):
            if f.is_symlink() or not f.is_file():
                continue
            b = self.base(f.name)
            if not b.lower().endswith(SUFFIX[folder]):
                continue
            out.append(f)
        return out

    def hashes(self, folder, f):
        """sha1 with a size+mtime cache so large folders are not re-hashed every time."""
        e = self.entry(folder, f.name) or {}
        st = f.stat()
        stamp = f'{st.st_size}:{int(st.st_mtime)}'
        if e.get('stamp') == stamp and e.get('sha1'):
            return e['sha1']
        sha1, sha512 = file_hashes(f)
        e = dict(e, sha1=sha1, sha512=sha512, stamp=stamp)
        e.setdefault('source', 'manual')
        self.record(folder, f.name, e)
        return sha1

    def listing(self, folder):
        items = []
        for f in self.files(folder):
            e = self.entry(folder, f.name) or {}
            items.append({'file': f.name, 'enabled': not f.name.endswith('.disabled'), 'title': e.get('title') or self.base(f.name),
                          'version': e.get('versionNumber') or '', 'projectId': e.get('projectId') or '',
                          'iconUrl': e.get('iconUrl') or '', 'source': e.get('source') or 'manual',
                          'dependency': bool(e.get('dependency')), 'size': f.stat().st_size,
                          'slug': e.get('slug') or ''})
        return items

    def find_project(self, folder, project_id):
        for f in self.files(folder):
            e = self.entry(folder, f.name)
            if e and e.get('projectId') == project_id:
                return f, e
        return None, None

    def known_files(self):
        """Enabled mod jars that came from Modrinth or a mod pack (trusted)."""
        return {self.base(k.split('/', 1)[1]) for k, e in self.data['files'].items()
                if k.startswith('mods/') and e.get('source') in ('modrinth', 'pack')}

    def titles(self, folder='mods'):
        return {self.base(k.split('/', 1)[1]): e.get('title') for k, e in self.data['files'].items()
                if k.startswith(folder + '/') and e.get('title')}


class Manager:
    """High level operations used by the launcher bridge."""

    def __init__(self, api, cache_dir):
        self.api = api
        self.cache_dir = Path(cache_dir)

    # ----- install -----
    def resolve(self, project_id, ptype, profile, version_id=None):
        loaders = loaders_for(ptype, profile['loader'] if profile['loader'] != 'vanilla' else 'fabric')
        v = None
        if version_id:
            v = self.api.version(version_id)
            if v and profile['version'] not in (v.get('game_versions') or []):
                v = None
        if not v:
            v = pick(self.api.versions(project_id, loaders, [profile['version']]), loaders)
        return v

    def install(self, content, project_id, ptype, profile, *, dependency=False, version_id=None, seen=None,
                cancel=None, emit=None, meta=None):
        """Installs a project and its required dependencies. Returns (installed_titles, warnings)."""
        seen = seen if seen is not None else set()
        if project_id in seen:
            return [], []
        seen.add(project_id)
        folder = FOLDERS[ptype]
        info = meta or self.api.project(project_id) or {}
        title = info.get('title') or project_id
        v = self.resolve(project_id, ptype, profile, version_id)
        if not v:
            raise NotAvailable(title, profile['version'])
        installed, warnings = [], []
        old_file, old = content.find_project(folder, v['project_id'])
        f = primary_file(v)
        name = safe_name(f['filename'])
        if not name.lower().endswith(SUFFIX[folder]):
            raise ValueError(f'{title}: beklenmeyen dosya türü ({name}).')
        target = scoped(content.folder(folder), name)
        if old and old.get('versionId') == v['id'] and old_file is not None:
            pass  # already up to date
        else:
            if emit:
                emit('progress', {'message': f'İndiriliyor: {title} {v.get("version_number", "")}'})
            if old_file is not None and old_file.name.endswith('.disabled'):
                target = target.with_name(target.name + '.disabled')
            sha1, sha512 = self.api.download(f['url'], target, f.get('hashes', {}).get('sha512'), f.get('hashes', {}).get('sha1'),
                                             f.get('size'), cancel=cancel)
            if old_file is not None and old_file.resolve() != target.resolve():
                content.forget(folder, old_file.name)
                old_file.unlink(missing_ok=True)
            st = target.stat()
            content.record(folder, target.name, {
                'source': 'modrinth', 'projectId': v['project_id'], 'versionId': v['id'], 'versionNumber': v.get('version_number', ''),
                'title': title, 'slug': info.get('slug', ''), 'iconUrl': info.get('icon_url') or info.get('iconUrl') or '',
                'sha1': sha1, 'sha512': sha512, 'stamp': f'{st.st_size}:{int(st.st_mtime)}',
                'dependency': bool(dependency and not (old and not old.get('dependency'))), 'type': ptype,
                'gameVersions': (v.get('game_versions') or [])[:6]})
            content.save()
            installed.append(title)
        for dep in v.get('dependencies') or []:
            if dep.get('dependency_type') != 'required':
                continue
            pid, vid = dep.get('project_id'), dep.get('version_id')
            if not pid and vid:
                dv = self.api.version(vid)
                pid = dv and dv.get('project_id')
            if not pid:
                continue
            existing, _ = content.find_project('mods', pid)
            if existing is not None and pid not in seen:
                seen.add(pid)
                continue
            try:
                a, w = self.install(content, pid, 'mod', profile, dependency=True, version_id=vid, seen=seen, cancel=cancel, emit=emit)
                installed += a
                warnings += w
            except NotAvailable as e:
                warnings.append(f'{title} için gereken {e.title} bu sürümde bulunamadı; mod çalışmayabilir.')
        return installed, warnings

    def install_by_slug_or_id(self, content, key, profile, cancel=None, emit=None):
        info = self.api.project(key)
        if not info:
            hits = self.api.search(key, 'mod', profile['version'], profile['loader'], limit=5)['hits']
            match = next((h for h in hits if h['slug'] == key or (h['title'] or '').lower() == key.lower()), None)
            if not match:
                raise NotAvailable(key, profile['version'])
            info = self.api.project(match['id'])
        return self.install(content, info['id'], 'mod', profile, cancel=cancel, emit=emit, meta=info)

    # ----- identify & updates -----
    def identify(self, content):
        """Looks up files without Modrinth info by hash, so manually added mods get names, icons and updates."""
        todo = {}
        for folder in SUFFIX:
            for f in content.files(folder):
                e = content.entry(folder, f.name) or {}
                if e.get('projectId') or e.get('checked'):
                    continue
                todo[content.hashes(folder, f)] = (folder, f)
        if not todo:
            return 0
        found = self.api.by_hashes(list(todo))
        projects = {p['id']: p for p in self.api.projects(list({v['project_id'] for v in found.values()}))}
        n = 0
        for sha1, (folder, f) in todo.items():
            e = dict(content.entry(folder, f.name) or {})
            v = found.get(sha1)
            if v:
                p = projects.get(v['project_id'], {})
                e.update(source='modrinth', projectId=v['project_id'], versionId=v['id'], versionNumber=v.get('version_number', ''),
                         title=p.get('title') or e.get('title'), slug=p.get('slug', ''), iconUrl=p.get('icon_url') or '',
                         gameVersions=(v.get('game_versions') or [])[:6], identified=True)
                n += 1
            e['checked'] = True
            content.record(folder, f.name, e)
        content.save()
        return n

    def check_updates(self, content, profile):
        out = []
        for folder, ptype in (('mods', 'mod'), ('resourcepacks', 'resourcepack'), ('shaderpacks', 'shader')):
            files = {content.hashes(folder, f): f for f in content.files(folder)}
            if not files:
                continue
            loader = profile['loader'] if profile['loader'] != 'vanilla' else 'fabric'
            res = self.api.updates(list(files), loaders_for(ptype, loader), [profile['version']])
            for sha1, v in res.items():
                f = files.get(sha1)
                e = content.entry(folder, f.name) if f else None
                if not f or not v or (e and e.get('versionId') == v.get('id')):
                    continue
                pf = primary_file(v)
                if pf.get('hashes', {}).get('sha1') == sha1:
                    continue
                out.append({'folder': folder, 'file': f.name, 'title': (e or {}).get('title') or f.name,
                            'current': (e or {}).get('versionNumber') or '', 'new': v.get('version_number', ''),
                            'versionId': v['id'], 'projectId': v['project_id'], 'type': ptype})
        content.save()
        return out

    def apply_update(self, content, item, profile, cancel=None, emit=None):
        e = content.entry(item['folder'], item['file']) or {}
        meta = {'title': e.get('title') or item.get('title'), 'slug': e.get('slug', ''), 'icon_url': e.get('iconUrl', '')}
        if not e.get('projectId'):
            e.update(projectId=item['projectId'], source='modrinth')
            content.record(item['folder'], item['file'], e)
        return self.install(content, item['projectId'], item['type'], profile, version_id=item['versionId'],
                            dependency=bool(e.get('dependency')), cancel=cancel, emit=emit, meta=meta)

    # ----- fixes for modcheck issues -----
    def fix_issue(self, content, issue, profile, cancel=None, emit=None):
        """Returns a Turkish sentence describing what was done."""
        mods = content.folder('mods')
        t = issue['type']
        if t in ('wrongVersion', 'depVersion'):
            fname = issue['file'] if t == 'wrongVersion' else issue.get('depFile')
            f = scoped(mods, fname)
            e = content.entry('mods', fname) or {}
            pid = e.get('projectId')
            if not pid:
                sha1 = content.hashes('mods', f)
                found = self.api.by_hashes([sha1]).get(sha1)
                pid = found and found['project_id']
                if not pid and t == 'wrongVersion' and issue.get('id'):
                    # not a Modrinth file: Modrinth slugs usually equal the Fabric mod id (litematica, malilib, sodium…)
                    info = self.api.project(issue['id'])
                    if info and info.get('project_type', 'mod') == 'mod':
                        pid = info['id']
                if pid:
                    e = dict(e, projectId=pid, source='modrinth')
                    content.record('mods', fname, e)
            if pid:
                try:
                    self.install(content, pid, 'mod', profile, cancel=cancel, emit=emit,
                                 dependency=bool(e.get('dependency')))
                    if f.exists() and not f.name.endswith('.disabled'):
                        self.disable(content, fname)  # an extra copy was installed next to it
                    return f"{issue['mod']}: Minecraft {profile['version']} için uygun sürüm kuruldu."
                except NotAvailable:
                    pass
            self.disable(content, fname)
            return f"{issue['mod']}: {profile['version']} için uygun sürümü yok, devre dışı bırakıldı."
        if t == 'missing':
            try:
                self.install_by_slug_or_id(content, issue['dep'], profile, cancel=cancel, emit=emit)
                return f"Eksik '{issue['dep']}' modu Modrinth'ten kuruldu."
            except NotAvailable:
                self.disable(content, issue['file'])
                return f"'{issue['dep']}' bulunamadı; {issue['mod']} devre dışı bırakıldı."
        if t in ('wrongLoader', 'broken', 'java', 'conflict'):
            self.disable(content, issue['file'])
            return f"{issue['mod']} devre dışı bırakıldı."
        if t == 'duplicate':
            self.disable(content, issue['file'])
            return f"{issue['mod']}: fazladan kopya devre dışı bırakıldı."
        return ''

    @staticmethod
    def disable(content, fname):
        mods = content.folder('mods')
        f = scoped(mods, fname)
        if f.exists() and not f.name.endswith('.disabled'):
            target = f.with_name(f.name + '.disabled')
            if target.exists():
                target.unlink()
            f.rename(target)
