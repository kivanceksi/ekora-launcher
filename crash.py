"""Turns the end of a Minecraft log into a short Turkish explanation with fixes."""
import re

_REQ = re.compile(r"Mod '(?P<mod>[^']+)' \((?P<id>[^)]+)\) (?P<ver>\S+) requires (?P<req>.+?) of '(?P<dep>[^']+)' "
                  r"\((?P<depid>[^)]+)\)(?:, which is missing!|, but only the wrong version is present: (?P<have>\S+?)!)")
_INCOMPAT = re.compile(r"Mod '(?P<mod>[^']+)' \((?P<id>[^)]+)\) \S+ is incompatible with .+? of '(?P<dep>[^']+)' \((?P<depid>[^)]+)\)")
_MIXIN_MOD = re.compile(r"(?:Mixin apply(?: for mod)? (?P<a>[\w.-]+) failed|from mod (?P<b>[\w.-]+)|Mod ID: '(?P<c>[\w.-]+)')")


def _version_of(req):
    m = re.search(r'(\d+\.\d+(?:\.\d+)?)', req)
    return m.group(1) if m else None


def analyze(log, profile_version=None, ram=None):
    """Returns {'title','details':[...],'actions':[...]} or None when nothing is recognized."""
    if not log:
        return None
    text = log[-200000:]
    details, actions = [], []

    reqs = list(_REQ.finditer(text))
    if reqs:
        wanted = set()
        missing = []
        for m in reqs:
            depid = m.group('depid')
            if depid == 'minecraft':
                v = _version_of(m.group('req'))
                if v:
                    wanted.add(v)
                details.append(f"{m.group('mod')} {m.group('ver')} Minecraft {m.group('req').replace('version ', '')} için yapılmış, "
                               f"ama oyun {m.group('have') or profile_version} sürümüyle açıldı.")
            elif m.group('have') is None:
                missing.append(depid)
                details.append(f"{m.group('mod')} için gereken {m.group('dep')} modu kurulu değil.")
            else:
                details.append(f"{m.group('mod')}, {m.group('dep')} modunun başka bir sürümünü istiyor "
                               f"(gereken: {m.group('req').replace('version ', '')}, kurulu: {m.group('have')}).")
        for m in _INCOMPAT.finditer(text):
            details.append(f"{m.group('mod')} ile {m.group('dep')} birlikte çalışmıyor.")
        actions.append({'kind': 'fixMods', 'label': 'Modları otomatik düzelt'})
        if len(wanted) == 1 and profile_version and profile_version not in wanted:
            actions.append({'kind': 'switchVersion', 'version': next(iter(wanted)),
                            'label': f"Bu kurulumu Minecraft {next(iter(wanted))} yap"})
        return {'title': 'Bazı modlar bu oyun sürümüyle uyumsuz', 'details': details[:12], 'actions': actions}

    if 'UnsupportedClassVersionError' in text or 'compiled by a more recent version of the Java Runtime' in text:
        return {'title': 'Java sürümü uyumsuz',
                'details': ['Oyun ya da bir mod, seçili Java sürümünden daha yeni bir Java istiyor.'],
                'actions': [{'kind': 'resetJava', 'label': 'Java seçimini otomatiğe al'}]}
    if 'Could not reserve enough space' in text or 'Invalid maximum heap size' in text or 'Could not create the Java Virtual Machine' in text:
        return {'title': 'Ayrılan bellek (RAM) fazla',
                'details': ['Bilgisayar, ayarlardaki kadar belleği oyuna veremedi.'],
                'actions': [{'kind': 'setRam', 'value': 3072, 'label': "Belleği 3 GB yap"}]}
    if 'java.lang.OutOfMemoryError' in text:
        nxt = min(16384, (ram or 4096) + 2048)
        return {'title': 'Oyunun belleği yetmedi',
                'details': ['Oyun, ayrılan belleğin tamamını kullandı ve kapandı. Çok modlu kurulumlar daha fazla bellek ister.'],
                'actions': [{'kind': 'setRam', 'value': nxt, 'label': f"Belleği {nxt // 1024} GB yap"}]}
    if any(x in text for x in ('Pixel format not accelerated', 'WGL_ARB_create_context', 'GLFW error 65542', 'GLFW error 65543',
                               'The driver does not appear to support OpenGL')):
        return {'title': 'Ekran kartı sürücüsü sorunu',
                'details': ['Ekran kartı sürücün oyunun istediği OpenGL sürümünü desteklemiyor ya da güncel değil.',
                            'Ekran kartının (NVIDIA, AMD veya Intel) sitesinden en yeni sürücüyü kur.'],
                'actions': []}
    if 'Incompatible mods found' in text or 'Mod resolution failed' in text:
        return {'title': 'Modlar birbiriyle uyumsuz', 'details': ['Fabric, modları birlikte yükleyemedi.'],
                'actions': [{'kind': 'fixMods', 'label': 'Modları otomatik düzelt'}]}
    blamed = []
    for m in _MIXIN_MOD.finditer(text):
        mod = m.group('a') or m.group('b') or m.group('c')
        if mod and mod not in blamed and mod not in ('minecraft', 'fabricloader', 'java'):
            blamed.append(mod)
    if blamed:
        return {'title': 'Bir mod oyunu çökertti',
                'details': [f"Sorun büyük ihtimalle şu modda: {', '.join(blamed[:4])}.",
                            'Modu devre dışı bırakıp yeniden dene ya da Modlar bölümünden güncelle.'],
                'actions': [{'kind': 'disableIds', 'ids': blamed[:4], 'label': 'Bu modu devre dışı bırak'},
                            {'kind': 'updateMods', 'label': 'Modları güncelle'}]}
    if '---- Minecraft Crash Report ----' in text or 'Exception in thread' in text:
        return {'title': 'Oyun çöktü',
                'details': ['Ayrıntılar aşağıdaki günlükte. Modlu kurulumdaysan önce modları güncellemeyi dene.'],
                'actions': [{'kind': 'updateMods', 'label': 'Modları güncelle'}]}
    return None
