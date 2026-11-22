"""Trust only the release's exact, bundled mod hashes; preserve unknown files."""
import hashlib
import json
import re
import shutil
from pathlib import Path
from security import scoped, no_links

def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def load_manifest(assets,version):
    file=scoped(assets,'managed-mods.json')
    if not file.exists():raise ValueError('Launcher paket listesi eksik. Uygulamayı onar veya güncelle.')
    data=json.loads(file.read_text(encoding='utf-8'))
    if not isinstance(data,dict) or data.get('schemaVersion')!=1 or not isinstance(data.get('mods'),list):raise ValueError('Geçersiz launcher paket listesi.')
    result={};seen=set()
    for entry in data['mods']:
        if not isinstance(entry,dict):raise ValueError('Geçersiz mod kaydı.')
        name=entry.get('file','');sha=entry.get('sha256','')
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*\.jar',name) or not re.fullmatch(r'[a-f0-9]{64}',sha):raise ValueError('Geçersiz mod adı veya doğrulama özeti.')
        if name.casefold() in seen:raise ValueError('Tekrarlanan launcher mod adı.')
        seen.add(name.casefold())
        prefix=entry.get('replaces','')
        if prefix and (not isinstance(prefix,str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{2,40}',prefix) or not name.startswith(prefix)):
            raise ValueError('Geçersiz launcher mod önek kaydı.')
        if entry.get('minecraft')==version:
            if entry.get('loader')!='fabric':raise ValueError('Desteklenmeyen launcher mod türü.')
            result[name]=entry
    return result

def preserve_disabled(source,game):
    target_dir=scoped(game,'disabled-mods');target_dir.mkdir(exist_ok=True)
    target=scoped(target_dir,source.name)
    n=1
    while target.exists():
        target=scoped(target_dir,source.stem+'-'+str(n)+source.suffix);n+=1
    source.rename(target);return target.name

def prepare_mods(game,assets,version,allow_external=False,known=()):
    game=Path(game);assets=Path(assets)
    mods=scoped(game,'mods');mods.mkdir(exist_ok=True)
    entries=load_manifest(assets,version)
    # Validate all bundled sources before moving or writing any files.
    for name,entry in entries.items():
        source=scoped(assets,'managed-mods',name)
        if not source.is_file() or digest(source)!=entry['sha256']:
            raise ValueError('Gerekli Ekora paketi doğrulanamadı: '+name+'. Launcher güncellemesi gerekiyor.')
    restored=[];disabled=[]
    for name,entry in entries.items():
        target=scoped(mods,name)
        if not target.exists() or digest(target)!=entry['sha256']:
            if target.exists():disabled.append(preserve_disabled(target,game))
            tmp=scoped(mods,name+'.installing')
            shutil.copyfile(scoped(assets,'managed-mods',name),tmp)
            if digest(tmp)!=entry['sha256']:
                tmp.unlink();raise ValueError('Ekora mod kopyası doğrulanamadı.')
            tmp.replace(target);restored.append(name)
    # Aynı modun eski sürümleri (ör. ekora-istemci-1.0.0.jar → 1.1.0) harici mod izni açık olsa bile ayrılır:
    # aynı mod kimliği iki kez yüklenirse Fabric açılmaz.
    prefixes=[e['replaces'] for e in entries.values() if e.get('replaces')]
    if prefixes:
        for p in sorted(mods.iterdir()):
            no_links(p)
            if p.is_file() and p.suffix.lower()=='.jar' and p.name not in entries and any(p.name.startswith(x) for x in prefixes):
                disabled.append(preserve_disabled(p,game))
    if not allow_external:
        for p in sorted(mods.iterdir()):
            no_links(p)
            if p.is_file() and p.suffix.lower()=='.jar' and p.name not in entries and p.name not in known:
                disabled.append(preserve_disabled(p,game))
    return {'managed':list(entries),'restored':restored,'disabled':disabled,'fabricRequired':bool(entries)}
