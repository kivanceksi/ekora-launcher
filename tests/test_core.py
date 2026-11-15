import json
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from core import Launcher, DEFAULTS, validate_settings, offline_uuid, SERVER
from stub_library import install_stub_if_missing
install_stub_if_missing()

class CoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.events=[]
        auth=MagicMock();auth.verified.return_value={'name':'EkoraTest','id':'a'*32,'access_token':'test-token'}
        self.l=Launcher(self.tmp.name,lambda k,d:self.events.append((k,d)),auth=auth)
    def tearDown(self):self.tmp.cleanup()
    def test_offline_identity_matches_java(self):
        self.assertEqual(offline_uuid('Notch'),'b50ad385-829d-3141-a216-7e7d7539ba7f')
        self.assertNotEqual(offline_uuid('Notch'),offline_uuid('notch'))
    def test_bad_names_paths_and_urls(self):
        for key,value in [('username','../x'),('version','../x'),('ram',True),('ram',999999),('countdown',0),('apiUrl','http://x'),('apiUrl','https://user:pass@host')]:
            with self.subTest(key=key,value=value),self.assertRaises(ValueError): validate_settings({key:value})
    def test_settings_persist(self):
        self.l.save({**DEFAULTS,'username':'EkoraTest','ram':6144})
        self.assertEqual(Launcher(self.tmp.name).settings['ram'],6144)
    def test_corrupt_settings_recover(self):
        (self.l.root/'settings.json').write_text('{oops')
        l=Launcher(self.tmp.name); self.assertTrue(l.warning); self.assertEqual(l.settings['username'],'')
    def test_busy_settings_rejected(self):
        self.l.busy=True
        with self.assertRaises(ValueError):self.l.save(DEFAULTS)
    def test_waypoint_validation(self):
        w={'name':'Ev','world':'overworld','x':10,'y':64,'z':-3}
        self.l.save_waypoints([w]); self.assertEqual(self.l.waypoints(),[w])
        with self.assertRaises(ValueError):self.l.save_waypoints([{**w,'x':'12'}])
        self.assertEqual(self.l.waypoints(),[w])
    def test_api_unconfigured(self):
        with self.assertRaises(ValueError):self.l.public_api('status')
    def test_launch_missing_profile_recovers(self):
        self.l.auth=None
        self.l.launch(); self.assertFalse(self.l.busy); self.assertEqual(self.events[-1][0],'launchError')
    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command',return_value=['java','test'])
    @patch('core.subprocess.Popen')
    def test_modern_launch_uses_quickplay(self,popen,command,install):
        self.l.save({**DEFAULTS,'username':'EkoraTest'})
        popen.return_value.wait.return_value=0
        self.l.launch()
        options=command.call_args.args[2]
        self.assertEqual(options['quickPlayMultiplayer'],SERVER);self.assertNotIn('server',options)
        self.assertEqual(options['jvmArguments'],['-Xms512M','-Xmx4096M'])
        self.assertFalse(self.l.busy); self.assertEqual(self.events[-1][1]['state'],'ready')
    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command',return_value=['java','test'])
    @patch('core.subprocess.Popen')
    def test_legacy_launch(self,popen,command,install):
        self.l.save({**DEFAULTS,'username':'EkoraTest','version':'1.18.2'})
        popen.return_value.wait.return_value=0; self.l.launch()
        options=command.call_args.args[2]; self.assertEqual(options['server'],SERVER); self.assertNotIn('quickPlayMultiplayer',options)
    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('core.subprocess.Popen')
    def test_repair_never_launches(self,popen,install):
        self.l.save({**DEFAULTS,'username':'EkoraTest'});self.l.launch(repair=True);popen.assert_not_called()
    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('core.subprocess.Popen')
    def test_cancel_never_launches(self,popen,install):
        self.l.save({**DEFAULTS,'username':'EkoraTest'})
        install.side_effect=lambda *a,**k:self.l.cancel.set()
        self.l.launch();popen.assert_not_called();self.assertFalse(self.l.busy)
    @patch('minecraft_launcher_lib.install.install_minecraft_version',side_effect=OSError('network failure'))
    def test_download_failure_unlocks(self,install):
        self.l.save({**DEFAULTS,'username':'EkoraTest'});self.l.launch()
        self.assertFalse(self.l.busy);self.assertEqual(self.events[-1][0],'launchError')
    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command',return_value=['java','test'])
    @patch('core.subprocess.Popen')
    def test_local_profile_without_microsoft(self,popen,command,install):
        self.l.auth=None
        self.l.save({**DEFAULTS,'username':'LocalPlayer'})
        popen.return_value.wait.return_value=0
        self.l.launch()
        options=command.call_args.args[2]
        self.assertEqual(options['username'],'LocalPlayer')
        self.assertEqual(options['uuid'],offline_uuid('LocalPlayer'))
        self.assertEqual(options['token'],'0')
    def test_installed_filter(self):
        p=self.l.game/'versions'/'1.21.1';p.mkdir(parents=True);(p/'1.21.1.json').write_text('{}');(p/'other.json').write_text('{}')
        self.assertEqual(self.l.installed(),['1.21.1'])

if __name__=='__main__':unittest.main()
