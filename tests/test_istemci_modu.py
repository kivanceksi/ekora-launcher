"""0.8: Ekora İstemci modu launcher ile gelir (managed-mods)."""
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from managed_mods import prepare_mods, digest, load_manifest
import modcheck

ASSETS = Path(__file__).resolve().parents[1] / 'assets'


class IstemciModuTests(unittest.TestCase):
    def test_gercek_liste_dogrulanir(self):
        entries = load_manifest(ASSETS, '26.3')
        self.assertTrue(entries, 'EkoraMC sürümü için Ekora İstemci listede olmalı')
        for name, e in entries.items():
            jar = ASSETS / 'managed-mods' / name
            self.assertTrue(jar.is_file(), name)
            self.assertEqual(digest(jar), e['sha256'])
            self.assertEqual(e['loader'], 'fabric')
            self.assertTrue(name.startswith(e['replaces']))
        self.assertEqual(load_manifest(ASSETS, '1.21.1'), {}, 'başka sürümlerin kurulumuna dokunulmaz')

    def test_jar_kendi_kendine_yeter(self):
        """Fabric API modülleri gömülü: ayrıca Fabric API gerekmez; mod denetimi sorun bulmaz."""
        name = next(iter(load_manifest(ASSETS, '26.3')))
        with zipfile.ZipFile(ASSETS / 'managed-mods' / name) as z:
            meta = json.loads(z.read('fabric.mod.json'))
            gomulu = [n for n in z.namelist() if n.startswith('META-INF/jars/')]
        self.assertEqual(meta['id'], 'ekora')
        self.assertEqual(meta['environment'], 'client')
        for m in ('fabric-api-base', 'fabric-networking-api-v1', 'fabric-key-mapping-api-v1', 'fabric-lifecycle-events-v1', 'fabric-rendering-v1'):
            self.assertTrue(any(m in g for g in gomulu), m)
        with tempfile.TemporaryDirectory() as t:
            mods = Path(t) / 'mods'
            mods.mkdir()
            (mods / name).write_bytes((ASSETS / 'managed-mods' / name).read_bytes())
            sorunlar = modcheck.check(mods, '26.3', 'fabric', '0.20.1', 25)
            self.assertEqual(sorunlar, [], sorunlar)

    def test_kurulum_ve_eski_surum_ayrilir(self):
        with tempfile.TemporaryDirectory() as t:
            game = Path(t) / 'game'
            (game / 'mods').mkdir(parents=True)
            (game / 'mods' / 'ekora-istemci-0.9.0.jar').write_bytes(b'eski')
            (game / 'mods' / 'sodium.jar').write_bytes(b'kullanici modu')
            r = prepare_mods(game, ASSETS, '26.3', allow_external=True)
            self.assertTrue(r['fabricRequired'])
            self.assertIn('ekora-istemci-0.9.0.jar', r['disabled'])
            self.assertTrue((game / 'mods' / 'sodium.jar').exists(), 'harici izinle kullanıcı modu kalır')
            self.assertTrue((game / 'disabled-mods' / 'ekora-istemci-0.9.0.jar').exists())
            name = next(iter(load_manifest(ASSETS, '26.3')))
            self.assertEqual(digest(game / 'mods' / name), digest(ASSETS / 'managed-mods' / name))


class ResmiKurulumTests(unittest.TestCase):
    """EkoraMC kurulumu ilk açılışta Fabric'e geçer ve Ekora İstemci kurulur."""

    def test_resmi_kurulum_fabric_ve_mod(self):
        from unittest.mock import patch, MagicMock
        from stub_library import install_stub_if_missing
        install_stub_if_missing()
        from core import Launcher, DEFAULTS
        import minecraft_launcher_lib as mll
        with tempfile.TemporaryDirectory() as t:
            olaylar = []
            l = Launcher(t, lambda k, d: olaylar.append((k, d)))
            l.save({**DEFAULTS, 'username': 'EkoraTest'})
            fabric = MagicMock()
            fabric.get_latest_loader_version.return_value = '0.20.1'
            with patch.object(mll, 'fabric', fabric, create=True), \
                    patch('minecraft_launcher_lib.install.install_minecraft_version'), \
                    patch('minecraft_launcher_lib.command.get_minecraft_command', return_value=['java', 'test']), \
                    patch('core.subprocess.Popen') as popen, \
                    patch('core.Launcher._java_for', return_value=('java', 25)), \
                    patch('modcheck.check', return_value=[]):
                popen.return_value.wait.return_value = 0
                l.launch()
            p = l.profiles.get('ekoramc')
            self.assertEqual(p['loader'], 'fabric')
            self.assertTrue(fabric.install_fabric.called)
            mods = Path(l.profiles.dir_of(p)) / 'mods'
            self.assertTrue((mods / 'ekora-istemci-1.0.0.jar').is_file())
            self.assertTrue(any(k == 'notice' and 'Fabric' in d.get('message', '') for k, d in olaylar))


if __name__ == '__main__':
    unittest.main()
