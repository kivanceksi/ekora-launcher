"""Local scope checks; these are app-level controls, not an OS sandbox."""
import os
from pathlib import Path
from urllib.parse import urlsplit

ASSET_NAMES = {'index.html','style.css','ui.js','logo.png','logo.ico'}

def no_links(path):
    p=Path(path).absolute()
    for item in [p,*p.parents]:
        if item.is_symlink() or (hasattr(item,'is_junction') and item.is_junction()):
            raise ValueError('Güvenlik kontrolü: yönlendirilmiş dosya veya klasör kullanılamaz.')
    return p

def scoped(root,*parts):
    root=no_links(root)
    target=no_links(root.joinpath(*parts))
    try: target.relative_to(root)
    except ValueError: raise ValueError('Uygulama klasörü dışına erişim engellendi.') from None
    if '..' in target.parts: raise ValueError('Geçersiz dosya yolu.')
    return target

def same_local_file(actual,expected,*,path_module=None):
    """Compare native absolute paths after resolution and platform case folding.

    Qt uses forward slashes on Windows; Path commonly uses backslashes.
    path_module is injectable for Windows-path regression tests on Linux.
    """
    paths=path_module or os.path
    try:
        actual=os.fspath(actual);expected=os.fspath(expected)
        if not actual or not expected or not paths.isabs(actual) or not paths.isabs(expected):return False
        return paths.normcase(paths.realpath(actual))==paths.normcase(paths.realpath(expected))
    except (TypeError,ValueError,OSError):return False


def asset_allowed(url,assets):
    if url=='qrc:///qtwebchannel/qwebchannel.js':return True
    u=urlsplit(url)
    if u.scheme!='file' or u.netloc not in ('','localhost'):return False
    from urllib.request import url2pathname
    p=Path(url2pathname(u.path))
    try:
        return p.name in ASSET_NAMES and same_local_file(p,Path(assets)/p.name) and not p.is_symlink()
    except (OSError,ValueError):return False

def is_admin():
    if os.name!='nt':return False
    import ctypes
    return bool(ctypes.windll.shell32.IsUserAnAdmin())

def redact_log(text):
    import re
    text=re.sub(r'(?i)(/(?:login|register)\s+)\S+(?:\s+\S+)?',r'\1[GİZLENDİ]',text)
    text=re.sub(r'(?i)(--accessToken\s+)\S+',r'\1[GİZLENDİ]',text)
    text=re.sub(r'(?i)((?:access_token|refresh_token|authorization|password)\s*[:=]\s*)[^\s,;]+',r'\1[GİZLENDİ]',text)
    return text
