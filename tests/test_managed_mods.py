import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from managed_mods import prepare_mods,digest
from core import Launcher,DEFAULTS

class ManagedModsTests(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory();self.root=Path(self.t.name);self.game=self.root/'game';self.assets=self.root/'assets'
        (self.game/'mods').mkdir(parents=True);(self.assets/'managed-mods').mkdir(parents=True)
        self.manifest([])
    def tearDown(self):self.t.cleanup()
    def manifest(self,entries):
        (self.assets/'managed-mods.json').write_text(json.dumps({'schemaVersion':1,'mods':entries}))
    def managed(self):
        p=self.assets/'managed-mods'/'ekora.jar';p.write_bytes(b'known fixture')
        self.manifest([{'file':p.name,'sha256':digest(p),'minecraft':'1.21.1','loader':'fabric'}]);return p
    def test_unknown_does_not_block_or_delete(self):
        (self.game/'mods'/'user.jar').write_bytes(b'unknown')
        r=prepare_mods(self.game,self.assets,'1.21.1')
        self.assertEqual(r['disabled'],['user.jar']);self.assertEqual((self.game/'disabled-mods'/'user.jar').read_bytes(),b'unknown')
        self.assertFalse((self.game/'mods'/'user.jar').exists())
    def test_required_works_with_external_permission_off(self):
        src=self.managed();r=prepare_mods(self.game,self.assets,'1.21.1',False)
        self.assertTrue(r['fabricRequired']);self.assertEqual((self.game/'mods'/src.name).read_bytes(),src.read_bytes())
    def test_same_name_not_trusted(self):
        src=self.managed();(self.game/'mods'/src.name).write_bytes(b'changed')
        prepare_mods(self.game,self.assets,'1.21.1')
        self.assertEqual((self.game/'mods'/src.name).read_bytes(),src.read_bytes())
        self.assertEqual((self.game/'disabled-mods'/src.name).read_bytes(),b'changed')
    def test_tampered_bundle_fails_before_changes(self):
        src=self.managed();src.write_bytes(b'tampered');(self.game/'mods'/'user.jar').write_bytes(b'keep')
        with self.assertRaises(ValueError):prepare_mods(self.game,self.assets,'1.21.1')
        self.assertTrue((self.game/'mods'/'user.jar').exists())
    def test_explicit_external_permission_keeps_user_mod(self):
        (self.game/'mods'/'user.jar').write_bytes(b'external')
        r=prepare_mods(self.game,self.assets,'1.21.1',True)
        self.assertEqual(r['disabled'],[]);self.assertTrue((self.game/'mods'/'user.jar').exists())
    def test_collision_preserves_both(self):
        d=self.game/'disabled-mods';d.mkdir();(d/'user.jar').write_bytes(b'old')
        (self.game/'mods'/'user.jar').write_bytes(b'new')
        prepare_mods(self.game,self.assets,'1.21.1')
        self.assertEqual((d/'user.jar').read_bytes(),b'old');self.assertEqual((d/'user-1.jar').read_bytes(),b'new')
    def test_unrelated_game_version_not_authorized(self):
        src=self.managed();(self.game/'mods'/src.name).write_bytes(src.read_bytes())
        r=prepare_mods(self.game,self.assets,'1.20.1')
        self.assertFalse(r['fabricRequired']);self.assertIn(src.name,r['disabled'])
    def test_path_escape_rejected(self):
        self.manifest([{'file':'../outside.jar','sha256':'a'*64,'minecraft':'1.21.1','loader':'fabric'}])
        with self.assertRaises(ValueError):prepare_mods(self.game,self.assets,'1.21.1')
    def test_repeat_does_not_move_again(self):
        self.managed();prepare_mods(self.game,self.assets,'1.21.1');r=prepare_mods(self.game,self.assets,'1.21.1')
        self.assertEqual(r['restored'],[]);self.assertEqual(r['disabled'],[])
