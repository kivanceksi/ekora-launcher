"""Test-only stand-in when the optional download library is unavailable.
Never imported by application code. Unexpected network calls fail closed.
"""
import importlib.util
import sys
import types

def install_stub_if_missing():
    if importlib.util.find_spec('minecraft_launcher_lib') is not None:return False
    root=types.ModuleType('minecraft_launcher_lib');root.__path__=[]
    def unexpected(*a,**k):raise AssertionError('Testte beklenmeyen kütüphane çağrısı')
    for name,methods in {'install':['install_minecraft_version'],'command':['get_minecraft_command'],'microsoft_account':['complete_login','get_secure_login_data']}.items():
        module=types.ModuleType('minecraft_launcher_lib.'+name)
        for method in methods:setattr(module,method,unexpected)
        setattr(root,name,module);sys.modules[module.__name__]=module
    sys.modules['minecraft_launcher_lib']=root
    return True
