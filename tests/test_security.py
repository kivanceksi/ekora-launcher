import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch,MagicMock
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from core import Launcher,validate_settings
from security import scoped,asset_allowed,redact_log
from i18n import status_tr,error_tr

class SecurityTests(unittest.TestCase):
    def test_parent_escape(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(ValueError):scoped(Path(t),'..','secret')
    def test_symlink_escape(self):
        with tempfile.TemporaryDirectory() as t,tempfile.TemporaryDirectory() as other:
            p=Path(t)/'outside'
            try:p.symlink_to(other,target_is_directory=True)
            except OSError:self.skipTest('Symlink privilege unavailable')
            with self.assertRaises(ValueError):scoped(Path(t),'outside','secret')
    def test_assets_only(self):
        p=Path(tempfile.gettempdir()).resolve()/'ekora-assets'
        self.assertTrue(asset_allowed((p/'index.html').as_uri(),p))
        self.assertTrue(asset_allowed('qrc:///qtwebchannel/qwebchannel.js',p))
        for url in ['https://evil.example/x','file:///etc/passwd','data:text/html,hello','file:///tmp/ekora-assets/../../secret']:
            self.assertFalse(asset_allowed(url,p))
    def test_language_preserves_settings(self):
        with tempfile.TemporaryDirectory() as t:
            l=Launcher(t);p=l.game/'options.txt';p.write_text('music:0.3\nlang:en_us\nfov:0.5\n')
            l.write_game_language(l.game,'tr_tr');text=p.read_text()
            self.assertIn('music:0.3',text);self.assertIn('fov:0.5',text);self.assertIn('lang:tr_tr',text);self.assertNotIn('en_us',text)
    def test_no_download_without_profile(self):
        with tempfile.TemporaryDirectory() as t:
            l=Launcher(t);l.launch();self.assertFalse((l.game/'options.txt').exists());self.assertFalse(l.busy)
    def test_no_account_data_on_disk(self):
        with tempfile.TemporaryDirectory() as t:
            l=Launcher(t);l.save({'access_token':'secret','refresh_token':'secret'})
            self.assertNotIn('secret',(l.root/'settings.json').read_text())
    def test_dangerous_java_path(self):
        with self.assertRaises(ValueError):validate_settings({'javaPath':'C:/malware.exe'})
    def test_mods_disabled_default(self):self.assertFalse(validate_settings({})['externalMods'])
    def test_only_ekora_tree_inspected(self):
        with tempfile.TemporaryDirectory() as t:
            l=Launcher(Path(t)/'ekora');private=Path(t)/'private.txt';private.write_text('private')
            l.audit_tree();self.assertEqual(private.read_text(),'private')
    def test_redaction(self):
        s=redact_log('/login secret123\naccess_token=private\npassword=another')
        for secret in ['secret123','private','another']:self.assertNotIn(secret,s)
    def test_turkish_progress(self):
        self.assertEqual(status_tr('Install java runtime'),'Oyuna uygun Java hazırlanıyor…')
        self.assertTrue(status_tr('Download https://example.org/file.jar').startswith('İndiriliyor:'))
        self.assertEqual(error_tr(TimeoutError()),'İndirme sunucusuna ulaşılamadı. İnternet bağlantını kontrol edip yeniden dene.')
    def test_malformed_settings(self):
        for data in [[],{'ram':'..'},{'username':123},{'language':'../en'}]:
            with self.assertRaises(ValueError):validate_settings(data)
