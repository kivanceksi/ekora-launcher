"""Installs Modrinth mod packs (.mrpack) as a new installation."""
import json
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PurePosixPath
from security import scoped, no_links
from modrinth import PACK_HOSTS, primary_file, pick, safe_name, Cancelled, Content

LOADER_KEYS = {'fabric-loader': 'fabric', 'quilt-loader': 'quilt'}


def _inside(base, rel):
    rel = str(rel).replace('\\', '/')
    pp = PurePosixPath(rel)
    if pp.is_absolute() or any(part in ('..', '') for part in pp.parts) or ':' in rel:
        raise ValueError('Paket, kurulum klasörünün dışına dosya yazmaya çalıştı; durduruldu.')
    return scoped(base, *pp.parts)


def read_index(path):
    with zipfile.ZipFile(path) as zf:
        idx = json.loads(zf.read('modrinth.index.json').decode('utf-8'))
    if idx.get('game') != 'minecraft' or not isinstance(idx.get('files'), list):
        raise ValueError('Geçersiz mod paketi.')
    deps = idx.get('dependencies') or {}
    if 'forge' in deps or 'neoforge' in deps:
        raise ValueError('Bu paket Forge/NeoForge için. Ekora Launcher şimdilik Fabric ve Quilt paketlerini kurar.')
    loader, lv = 'vanilla', ''
    for k, name in LOADER_KEYS.items():
        if k in deps:
            loader, lv = name, str(deps[k])
    return idx, str(deps.get('minecraft', '')), loader, lv


def install_pack(api, profiles, project, cache_dir, game_version=None, cancel=None, emit=None, version_id=None):
    """Downloads the pack, creates an installation and fills it. Returns the new profile."""
    emit = emit or (lambda k, d: None)
    v = api.version(version_id) if version_id else None
    if not v:
        params = [game_version] if game_version else None
        v = pick(api.versions(project['id'], ['fabric', 'quilt'], params), ['fabric', 'quilt'])
    if not v:
        raise ValueError(f"{project.get('title')} için uygun bir paket sürümü bulunamadı.")
    f = primary_file(v)
    if not f['filename'].endswith('.mrpack'):
        raise ValueError('Bu sürümde .mrpack dosyası yok.')
    cache = Path(cache_dir)
    cache.mkdir(parents=True, exist_ok=True)
    pack_file = cache / safe_name(f['filename'])
    emit('progress', {'message': f"Paket indiriliyor: {project.get('title')}", 'percent': 2})
    api.download(f['url'], pack_file, f.get('hashes', {}).get('sha512'), f.get('hashes', {}).get('sha1'), f.get('size'), cancel=cancel)
    idx, mc, loader, lv = read_index(pack_file)
    if not mc:
        raise ValueError('Paket Minecraft sürümünü belirtmiyor.')
    title = (project.get('title') or 'Mod paketi')[:30]
    p = profiles.create(title, mc, loader, join_server=False, color='green',
                        source={'type': 'modpack', 'projectId': project['id'], 'versionId': v['id'], 'title': project.get('title', ''),
                                'versionNumber': v.get('version_number', '')})
    if lv:
        profiles.set_loader_version(p['id'], lv)
    game = profiles.dir_of(p)
    content = Content(game)
    try:
        files = [x for x in idx['files'] if (x.get('env') or {}).get('client', 'required') == 'required']
        total = max(1, len(files))
        done = [0]

        def one(x):
            if cancel is not None and cancel.is_set():
                raise Cancelled()
            target = _inside(game, x['path'])
            hashes = x.get('hashes') or {}
            last = None
            for url in x.get('downloads') or []:
                try:
                    api.download(url, target, hashes.get('sha512'), hashes.get('sha1'), x.get('fileSize'), hosts=PACK_HOSTS, cancel=cancel)
                    last = None
                    break
                except (ConnectionError, ValueError) as e:
                    last = e
            if last:
                raise last
            done[0] += 1
            emit('progress', {'message': f'Paket dosyaları indiriliyor ({done[0]}/{total})', 'percent': 5 + int(done[0] / total * 85)})
            return x['path'], hashes

        with ThreadPoolExecutor(max_workers=6) as ex:
            results = list(ex.map(one, files))
        emit('progress', {'message': 'Paket ayarları kopyalanıyor…', 'percent': 92})
        with zipfile.ZipFile(pack_file) as zf:
            for prefix in ('overrides/', 'client-overrides/'):
                for info in zf.infolist():
                    if not info.filename.startswith(prefix) or info.is_dir():
                        continue
                    rel = info.filename[len(prefix):]
                    if not rel:
                        continue
                    target = _inside(game, rel)
                    no_links(target)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with zf.open(info) as src, open(target, 'wb') as out:
                        out.write(src.read(600 * 1024 * 1024))
                    results.append((rel, {}))
        for rel, hashes in results:
            parts = rel.replace('\\', '/').split('/')
            if len(parts) == 2 and parts[0] in ('mods', 'resourcepacks', 'shaderpacks'):
                content.record(parts[0], parts[1], {'source': 'pack', 'title': Path(parts[1]).stem,
                                                    'sha1': hashes.get('sha1', ''), 'pack': project.get('title', '')})
        content.save()
    except BaseException:
        try:
            profiles.delete(p['id'])
        except (ValueError, OSError):
            pass
        raise
    finally:
        pack_file.unlink(missing_ok=True)
    emit('progress', {'message': 'Mod paketi kuruldu.', 'percent': 100})
    return p
