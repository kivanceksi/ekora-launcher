"""Game installations ("Kurulumlar"). Each profile has its own game folder
(mods, saves, options) while versions, libraries and assets stay shared."""
import json
import re
import shutil
import time
from pathlib import Path
from security import scoped, no_links

LOADERS = ('vanilla', 'fabric', 'quilt')
OFFICIAL_ID = 'ekoramc'
_ID = re.compile(r'^[a-z0-9][a-z0-9-]{0,39}$')
_VER = re.compile(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,59}$')


def _atomic(path, data):
    no_links(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    tmp.replace(path)


def clean(p):
    if not isinstance(p, dict):
        raise ValueError('Geçersiz kurulum.')
    out = {}
    out['id'] = str(p.get('id', ''))
    if not _ID.match(out['id']):
        raise ValueError('Geçersiz kurulum kimliği.')
    name = str(p.get('name', '')).strip()
    if not 1 <= len(name) <= 40:
        raise ValueError('Kurulum adı 1–40 karakter olmalı.')
    out['name'] = name
    out['version'] = str(p.get('version', ''))
    if not _VER.match(out['version']):
        raise ValueError('Geçersiz Minecraft sürümü.')
    out['loader'] = p.get('loader') if p.get('loader') in LOADERS else 'vanilla'
    lv = str(p.get('loaderVersion') or '')
    out['loaderVersion'] = lv if re.match(r'^[A-Za-z0-9._+-]{0,40}$', lv) else ''
    d = str(p.get('dir', ''))
    if d and not re.match(r'^profiles/[a-z0-9][a-z0-9-]{0,39}$', d):
        raise ValueError('Geçersiz kurulum klasörü.')
    out['dir'] = d
    out['joinServer'] = bool(p.get('joinServer', False))
    out['official'] = out['id'] == OFFICIAL_ID
    out['color'] = p.get('color') if p.get('color') in ('violet', 'cyan', 'green', 'amber', 'pink', 'blue') else 'violet'
    for k in ('created', 'lastPlayed'):
        v = p.get(k, 0)
        out[k] = int(v) if isinstance(v, (int, float)) and v >= 0 else 0
    src = p.get('source')
    out['source'] = {k: str(v)[:80] for k, v in src.items() if k in ('type', 'projectId', 'versionId', 'title', 'versionNumber')} \
        if isinstance(src, dict) else None
    return out


class Profiles:
    def __init__(self, root, game, server_version):
        self.root = Path(root)
        self.game = Path(game)
        self.server_version = server_version
        self.file = scoped(self.root, 'profiles.json')
        self.items = []
        self.selected = OFFICIAL_ID

    # ---------- storage ----------
    def load(self, legacy_settings=None):
        data = None
        if self.file.exists():
            try:
                data = json.loads(self.file.read_text(encoding='utf-8'))
            except (ValueError, OSError):
                data = None
        if isinstance(data, dict) and isinstance(data.get('profiles'), list):
            items = []
            for p in data['profiles']:
                try:
                    items.append(clean(p))
                except ValueError:
                    pass
            self.items = items
            self.selected = str(data.get('selected') or OFFICIAL_ID)
        else:
            self.items = []
            self._migrate(legacy_settings or {})
        self._ensure_official()
        if not self.get(self.selected):
            self.selected = OFFICIAL_ID
        self.save()
        return self

    def save(self):
        _atomic(self.file, {'schemaVersion': 1, 'selected': self.selected, 'profiles': self.items})

    def _ensure_official(self):
        off = self.get(OFFICIAL_ID)
        if off is None:
            off = clean({'id': OFFICIAL_ID, 'name': 'EkoraMC', 'version': self.server_version, 'loader': 'vanilla',
                         'dir': 'profiles/' + OFFICIAL_ID, 'joinServer': True, 'color': 'violet', 'created': int(time.time())})
            self.items.insert(0, off)
        if off['version'] != self.server_version:
            off['version'] = self.server_version
            off['loaderVersion'] = ''
        off['joinServer'] = True
        off['name'] = 'EkoraMC'
        off['dir'] = 'profiles/' + OFFICIAL_ID
        self.dir_of(off).mkdir(parents=True, exist_ok=True)

    def _migrate(self, old):
        """0.3/0.4 kept everything directly in the game folder. Keep that folder as
        'Önceki kurulum' so nothing is lost, and copy the player's options."""
        legacy_files = [n for n in ('mods', 'saves', 'options.txt', 'resourcepacks', 'screenshots') if (self.game / n).exists()]
        mods = self.game / 'mods'
        has_mods = mods.is_dir() and any(mods.glob('*.jar*'))
        version = str(old.get('version') or '')
        if legacy_files and _VER.match(version or 'x'):
            loader = 'fabric' if (old.get('fabric') or has_mods) else 'vanilla'
            self.items.append(clean({'id': 'onceki', 'name': 'Önceki kurulum', 'version': version or self.server_version,
                                     'loader': loader, 'dir': '', 'joinServer': version == self.server_version,
                                     'color': 'amber', 'created': int(time.time())}))
        target = self.game / 'profiles' / OFFICIAL_ID
        target.mkdir(parents=True, exist_ok=True)
        for name in ('options.txt', 'servers.dat'):
            src = self.game / name
            if src.is_file() and not src.is_symlink() and not (target / name).exists():
                try:
                    shutil.copy2(src, target / name)
                except OSError:
                    pass

    # ---------- queries ----------
    def get(self, pid):
        return next((p for p in self.items if p['id'] == pid), None)

    def need(self, pid):
        p = self.get(pid)
        if p is None:
            raise ValueError('Kurulum bulunamadı.')
        return p

    def current(self):
        return self.need(self.selected)

    def dir_of(self, p):
        return scoped(self.game, *p['dir'].split('/')) if p['dir'] else self.game

    # ---------- changes ----------
    def _new_id(self, name):
        base = re.sub(r'[^a-z0-9]+', '-', name.lower().translate(str.maketrans('çğıöşüâî', 'cgiosuai'))).strip('-')[:24] or 'kurulum'
        if base == OFFICIAL_ID:
            base = 'kurulum'
        pid, n = base, 2
        while self.get(pid) or (self.game / 'profiles' / pid).exists():
            pid = f'{base}-{n}'
            n += 1
        return pid

    def create(self, name, version, loader, join_server=False, color='cyan', source=None):
        if len(self.items) >= 40:
            raise ValueError('En fazla 40 kurulum oluşturabilirsin.')
        pid = self._new_id(str(name))
        p = clean({'id': pid, 'name': name, 'version': version, 'loader': loader, 'dir': 'profiles/' + pid,
                   'joinServer': join_server, 'color': color, 'created': int(time.time()), 'source': source})
        self.dir_of(p).mkdir(parents=True, exist_ok=True)
        self.items.append(p)
        self.save()
        return p

    def update(self, pid, changes):
        p = self.need(pid)
        merged = dict(p)
        for k in ('name', 'version', 'loader', 'joinServer', 'color'):
            if k in changes:
                merged[k] = changes[k]
        if p['official']:
            merged['version'] = self.server_version
            merged['joinServer'] = True
            merged['name'] = 'EkoraMC'
        if merged['version'] != p['version'] or merged['loader'] != p['loader']:
            merged['loaderVersion'] = ''
        new = clean(merged)
        p.clear()
        p.update(new)
        self.save()
        return p

    def set_loader_version(self, pid, lv):
        p = self.need(pid)
        p['loaderVersion'] = lv
        self.save()

    def touch(self, pid):
        p = self.need(pid)
        p['lastPlayed'] = int(time.time())
        self.save()

    def select(self, pid):
        self.need(pid)
        self.selected = pid
        self.save()

    def delete(self, pid):
        p = self.need(pid)
        if p['official']:
            raise ValueError('EkoraMC kurulumu silinemez.')
        removed_files = False
        if p['dir']:
            d = self.dir_of(p)
            if d.exists():
                no_links(d)
                for base, dirs, files in __import__('os').walk(d, followlinks=False):
                    for n in dirs + files:
                        no_links(Path(base) / n)
                shutil.rmtree(d)
                removed_files = True
        self.items = [x for x in self.items if x['id'] != pid]
        if self.selected == pid:
            self.selected = OFFICIAL_ID
        self.save()
        return removed_files
