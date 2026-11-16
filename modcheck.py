"""Reads mod metadata from jar files and finds problems before Minecraft starts.

Fabric/Quilt version predicates are matched with a small, dependency-free
implementation of Fabric's semantic-version rules (=, >, >=, <, <=, ~, ^, x, *).
"""
import io
import json
import re
import zipfile
from pathlib import Path

BUILTIN_IDS = {'minecraft', 'java', 'fabricloader', 'fabric-loader', 'mixinextras', 'quilt_loader'}
_NUM = re.compile(r'^\d+$')


def parse_version(text):
    """Returns (numbers, prerelease) or None. prerelease None = release."""
    if not isinstance(text, str):
        return None
    text = text.strip().split('+', 1)[0]
    if not text:
        return None
    pre = None
    if '-' in text:
        text, pre = text.split('-', 1)
    parts = text.split('.')
    nums = []
    for p in parts:
        if p in ('x', 'X', '*'):
            nums.append(None)
        elif _NUM.match(p):
            nums.append(int(p))
        else:
            m = re.match(r'^(\d+)(.*)$', p)
            if not m:
                return None
            nums.append(int(m.group(1)))
            pre = (m.group(2).lstrip('-.') + ('.' + pre if pre else '')) or pre or ''
            break
    return nums, pre


def _cmp_pre(a, b):
    if a is None and b is None:
        return 0
    if a is None:
        return 1
    if b is None:
        return -1
    pa, pb = a.split('.') if a else [], b.split('.') if b else []
    for x, y in zip(pa, pb):
        if x == y:
            continue
        if _NUM.match(x) and _NUM.match(y):
            return -1 if int(x) < int(y) else 1
        if _NUM.match(x):
            return -1
        if _NUM.match(y):
            return 1
        return -1 if x < y else 1
    return (len(pa) > len(pb)) - (len(pa) < len(pb))


def compare(a, b):
    """Compares two parsed versions (wildcards treated as 0)."""
    na, nb = [n or 0 for n in a[0]], [n or 0 for n in b[0]]
    size = max(len(na), len(nb))
    na += [0] * (size - len(na))
    nb += [0] * (size - len(nb))
    if na != nb:
        return -1 if na < nb else 1
    return _cmp_pre(a[1], b[1])


def _single(pred, ver):
    pred = pred.strip()
    if pred in ('', '*'):
        return True
    m = re.match(r'^(>=|<=|>|<|=|~|\^)?\s*(.+)$', pred)
    op, target = m.group(1) or '', m.group(2)
    t = parse_version(target)
    if t is None:
        return pred == ver
    v = parse_version(ver)
    if v is None:
        return False
    if None in t[0]:  # 1.21.x style wildcard
        fixed = []
        for n in t[0]:
            if n is None:
                break
            fixed.append(n)
        return v[0][:len(fixed)] == fixed and (op in ('', '=') or True)
    c = compare(v, t)
    if op in ('', '='):
        return c == 0
    if op == '>=':
        return c >= 0
    if op == '<=':
        return c <= 0
    if op == '>':
        return c > 0
    if op == '<':
        return c < 0
    if op == '~':  # same major.minor, >= target
        upper = list(t[0][:2]) + [0]
        if len(t[0]) >= 2:
            upper[1] += 1
        else:
            upper[0] += 1
            upper[1] = 0
        return c >= 0 and compare(v, (upper, '')) < 0
    if op == '^':  # same major, >= target
        upper = [t[0][0] + 1]
        return c >= 0 and compare(v, (upper, '')) < 0
    return False


def matches(predicate, version):
    """Fabric dependency value: string (space = AND) or list (OR)."""
    if version is None:
        return True
    if isinstance(predicate, list):
        return any(matches(p, version) for p in predicate) if predicate else True
    if not isinstance(predicate, str):
        return True
    return all(_single(p, version) for p in predicate.split()) if predicate.strip() else True


def describe(predicate):
    if isinstance(predicate, list):
        return ' veya '.join(describe(p) for p in predicate)
    p = str(predicate).strip()
    if p in ('', '*'):
        return 'herhangi bir sürüm'
    if re.match(r'^[\d.]+$', p):
        return p
    return p.replace('>=', '≥').replace('<=', '≤')


def _loads(raw):
    text = raw.decode('utf-8-sig', 'replace')
    try:
        return json.loads(text, strict=False)
    except ValueError:
        # tolerate trailing commas that some mods ship with
        return json.loads(re.sub(r',\s*([}\]])', r'\1', text), strict=False)


def _read_meta(zf, depth=0):
    names = set(zf.namelist())
    info = {'loader': None, 'id': None, 'name': None, 'version': None, 'depends': {}, 'breaks': {}, 'provides': set()}
    if 'fabric.mod.json' in names:
        data = _loads(zf.read('fabric.mod.json'))
        info.update(loader='fabric', id=str(data.get('id') or ''), name=str(data.get('name') or data.get('id') or ''),
                    version=str(data.get('version') or ''))
        deps = data.get('depends') or {}
        info['depends'] = {str(k): v for k, v in deps.items()} if isinstance(deps, dict) else {}
        brk = data.get('breaks') or {}
        info['breaks'] = {str(k): v for k, v in brk.items()} if isinstance(brk, dict) else {}
        prov = data.get('provides') or []
        info['provides'] = {str(p) for p in prov if isinstance(p, str)}
        if depth < 2:
            for j in data.get('jars') or []:
                f = j.get('file') if isinstance(j, dict) else None
                if isinstance(f, str) and f in names:
                    try:
                        with zipfile.ZipFile(io.BytesIO(zf.read(f))) as inner:
                            sub = _read_meta(inner, depth + 1)
                        if sub['id']:
                            info['provides'].add(sub['id'])
                        info['provides'] |= sub['provides']
                    except (zipfile.BadZipFile, ValueError, KeyError, OSError):
                        pass
    elif 'quilt.mod.json' in names:
        data = _loads(zf.read('quilt.mod.json')).get('quilt_loader', {})
        info.update(loader='quilt', id=str(data.get('id') or ''), version=str(data.get('version') or ''))
        meta = data.get('metadata') or {}
        info['name'] = str(meta.get('name') or info['id'])
        for d in data.get('depends') or []:
            if isinstance(d, str):
                info['depends'][d] = '*'
            elif isinstance(d, dict) and d.get('id') and not d.get('optional'):
                info['depends'][str(d['id'])] = d.get('versions', '*')
        for p in data.get('provides') or []:
            info['provides'].add(p if isinstance(p, str) else str(p.get('id', '')))
    elif 'META-INF/neoforge.mods.toml' in names:
        info['loader'] = 'neoforge'
    elif 'META-INF/mods.toml' in names or 'mcmod.info' in names:
        info['loader'] = 'forge'
    return info


def read_jar(path):
    try:
        with zipfile.ZipFile(path) as zf:
            info = _read_meta(zf)
    except (zipfile.BadZipFile, OSError, ValueError, KeyError, AttributeError):
        info = {'loader': 'broken', 'id': None, 'name': None, 'version': None, 'depends': {}, 'breaks': {}, 'provides': set()}
    info['file'] = Path(path).name
    if not info.get('name'):
        info['name'] = info['id'] or Path(path).stem
    return info


def check(mods_dir, mc_version, loader, loader_version=None, java_major=None, titles=None):
    """Returns a list of issue dicts for enabled jars in mods_dir."""
    titles = titles or {}
    mods = []
    p = Path(mods_dir)
    if p.is_dir():
        for f in sorted(p.iterdir()):
            if f.is_file() and f.suffix.lower() == '.jar' and not f.is_symlink():
                mods.append(read_jar(f))
    issues = []
    present = {}
    for m in mods:
        for i in [m['id'], *m['provides']]:
            if i:
                present.setdefault(i, m)
    seen = {}
    for m in mods:
        label = titles.get(m['file']) or m['name']
        if m['loader'] == 'broken':
            issues.append({'type': 'broken', 'file': m['file'], 'mod': label,
                           'text': f"{label} dosyası bozuk ya da bir mod dosyası değil."})
            continue
        if m['loader'] in ('forge', 'neoforge'):
            issues.append({'type': 'wrongLoader', 'file': m['file'], 'mod': label,
                           'text': f"{label} {'NeoForge' if m['loader'] == 'neoforge' else 'Forge'} için yapılmış. Fabric'te çalışmaz."})
            continue
        if m['loader'] == 'quilt' and loader == 'fabric':
            issues.append({'type': 'wrongLoader', 'file': m['file'], 'mod': label,
                           'text': f"{label} Quilt için yapılmış. Bu kurulum Fabric kullanıyor."})
            continue
        if m['loader'] is None:
            continue
        if m['id'] in seen:
            issues.append({'type': 'duplicate', 'file': m['file'], 'mod': label, 'other': seen[m['id']]['file'],
                           'text': f"{label} iki kez kurulu ({seen[m['id']]['file']} ve {m['file']})."})
            continue
        seen[m['id']] = m
        for dep, pred in m['depends'].items():
            if dep == 'minecraft':
                if not matches(pred, mc_version):
                    issues.append({'type': 'wrongVersion', 'file': m['file'], 'mod': label, 'needs': describe(pred), 'id': m['id'],
                                   'text': f"{label}, Minecraft {describe(pred)} istiyor. Bu kurulum {mc_version}."})
            elif dep in ('fabricloader', 'fabric-loader'):
                if loader_version and not matches(pred, loader_version):
                    issues.append({'type': 'loaderOld', 'file': m['file'], 'mod': label, 'needs': describe(pred),
                                   'text': f"{label}, Fabric Loader {describe(pred)} istiyor (kurulu: {loader_version})."})
            elif dep == 'java':
                if java_major and not matches(pred, str(java_major)):
                    issues.append({'type': 'java', 'file': m['file'], 'mod': label, 'needs': describe(pred),
                                   'text': f"{label}, Java {describe(pred)} istiyor."})
            elif dep in BUILTIN_IDS:
                continue
            else:
                other = present.get(dep)
                if other is None:
                    issues.append({'type': 'missing', 'file': m['file'], 'mod': label, 'dep': dep,
                                   'text': f"{label} için gereken '{dep}' modu eksik."})
                elif other['id'] == dep and other['version'] and not matches(pred, other['version']):
                    issues.append({'type': 'depVersion', 'file': m['file'], 'mod': label, 'dep': dep, 'depFile': other['file'],
                                   'needs': describe(pred),
                                   'text': f"{label}, '{titles.get(other['file']) or other['name']}' modunun {describe(pred)} sürümünü istiyor (kurulu: {other['version']})."})
        for dep, pred in m['breaks'].items():
            other = present.get(dep)
            if other is not None and other is not m and matches(pred, other['version']):
                issues.append({'type': 'conflict', 'file': m['file'], 'mod': label, 'depFile': other['file'],
                               'text': f"{label} ile '{titles.get(other['file']) or other['name']}' birlikte çalışmaz."})
    # one 'missing' entry per dependency id is enough
    out, keys = [], set()
    for i in issues:
        k = (i['type'], i.get('dep') or i['file'])
        if k not in keys:
            keys.add(k)
            out.append(i)
    return out
