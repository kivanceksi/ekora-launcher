import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from core import Launcher, DEFAULTS, validate_settings, offline_uuid, SERVER
from stub_library import install_stub_if_missing
install_stub_if_missing()


def jar(path, meta):
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('fabric.mod.json', json.dumps(meta))


class CoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.events = []
        self.l = Launcher(self.tmp.name, lambda k, d: self.events.append((k, d)))

    def tearDown(self):
        self.tmp.cleanup()

    def kinds(self):
        return [k for k, _ in self.events]

    def test_offline_identity_matches_java(self):
        self.assertEqual(offline_uuid('Notch'), 'b50ad385-829d-3141-a216-7e7d7539ba7f')

    def test_bad_values(self):
        for key, value in [('username', '../x'), ('ram', True), ('ram', 999999), ('countdown', 0), ('apiUrl', 'http://x')]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate_settings({key: value})

    def test_old_keys_are_ignored(self):
        s = validate_settings({'version': '1.21.1', 'fabric': True, 'username': 'Ali_1'})
        self.assertNotIn('version', s)
        self.assertEqual(s['username'], 'Ali_1')

    def test_settings_persist(self):
        self.l.save({**DEFAULTS, 'username': 'EkoraTest', 'ram': 6144})
        self.assertEqual(Launcher(self.tmp.name).settings['ram'], 6144)

    def test_corrupt_settings_recover(self):
        (self.l.root / 'settings.json').write_text('{oops')
        l = Launcher(self.tmp.name)
        self.assertTrue(l.warning)

    def test_official_profile_exists_with_server_version(self):
        snap = self.l.snapshot()
        off = next(p for p in snap['profiles'] if p['official'])
        self.assertEqual(off['version'], '26.3')
        self.assertTrue(off['joinServer'])
        self.assertTrue(Path(off['path']).is_dir())

    def test_migration_keeps_old_folder(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            (root / 'minecraft' / 'mods').mkdir(parents=True)
            (root / 'minecraft' / 'mods' / 'litematica.jar').write_bytes(b'x')
            (root / 'minecraft' / 'options.txt').write_text('fov:0.5\n')
            (root / 'settings.json').write_text(json.dumps({**DEFAULTS, 'version': '1.21.1', 'fabric': True, 'username': 'Ali_1'}))
            l = Launcher(t)
            old = l.profiles.get('onceki')
            self.assertIsNotNone(old)
            self.assertEqual((old['version'], old['loader'], old['dir']), ('1.21.1', 'fabric', ''))
            self.assertEqual(l.settings['username'], 'Ali_1')
            self.assertTrue((root / 'minecraft' / 'profiles' / 'ekoramc' / 'options.txt').exists())
            self.assertEqual(l.profiles.selected, 'ekoramc')
            # second start must not migrate again
            Launcher(t)
            self.assertEqual(len(json.loads((root / 'profiles.json').read_text())['profiles']), 2)

    def test_launch_without_username(self):
        self.l.launch()
        self.assertFalse(self.l.busy)
        self.assertEqual(self.events[-1][0], 'launchError')

    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command', return_value=['java', 'test'])
    @patch('core.subprocess.Popen')
    @patch('core.Launcher._java_for', return_value=('java', 25))
    def test_official_launch_uses_quickplay_and_own_folder(self, java, popen, command, install):
        self.l.save({**DEFAULTS, 'username': 'EkoraTest'})
        popen.return_value.wait.return_value = 0
        self.l.launch()
        options = command.call_args.args[2]
        self.assertEqual(options['quickPlayMultiplayer'], SERVER)
        self.assertTrue(options['gameDirectory'].endswith(str(Path('profiles') / 'ekoramc')))
        self.assertEqual(command.call_args.args[0], '26.3')
        self.assertFalse(self.l.busy)
        self.assertIsNone(self.l.running)
        self.assertEqual(self.events[-1][1]['state'], 'ready')

    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command', return_value=['java', 'test'])
    @patch('core.subprocess.Popen')
    @patch('core.Launcher._java_for', return_value=('java', 25))
    def test_custom_profile_without_server(self, java, popen, command, install):
        self.l.save({**DEFAULTS, 'username': 'EkoraTest'})
        p = self.l.profiles.create('Yaratıcı', '1.21.11', 'vanilla', join_server=False)
        popen.return_value.wait.return_value = 0
        self.l.launch(pid=p['id'])
        options = command.call_args.args[2]
        self.assertNotIn('quickPlayMultiplayer', options)
        self.assertEqual(command.call_args.args[0], '1.21.11')

    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.fabric.install_fabric')
    @patch('minecraft_launcher_lib.fabric.get_latest_loader_version', return_value='0.20.1')
    @patch('core.subprocess.Popen')
    @patch('core.Launcher._java_for', return_value=('java', 25))
    def test_wrong_version_mod_stops_launch_with_issues(self, java, popen, latest, fabric, install):
        self.l.save({**DEFAULTS, 'username': 'EkoraTest', 'externalMods': True})
        mods = Path(self.l.profiles.dir_of(self.l.profiles.get('ekoramc'))) / 'mods'
        mods.mkdir(exist_ok=True)
        jar(mods / 'litematica-1.21.11.jar', {'id': 'litematica', 'name': 'Litematica', 'version': '0.26.16',
                                              'depends': {'minecraft': '1.21.11', 'malilib': '*'}})
        with patch('modrinth.Manager.identify', return_value=0):
            self.l.launch()
        popen.assert_not_called()
        issues = dict(self.events)['modIssues']['issues']
        types = sorted(i['type'] for i in issues)
        self.assertEqual(types, ['missing', 'wrongVersion'])
        self.assertEqual(self.l.profiles.get('ekoramc')['loader'], 'fabric')  # switched automatically
        self.assertEqual(self.l.profiles.get('ekoramc')['loaderVersion'], '0.20.1')

    @patch('minecraft_launcher_lib.install.install_minecraft_version')
    @patch('minecraft_launcher_lib.command.get_minecraft_command', return_value=['java', 'test'])
    @patch('core.subprocess.Popen')
    @patch('core.Launcher._java_for', return_value=('java', 25))
    def test_crash_report_is_sent(self, java, popen, command, install):
        self.l.save({**DEFAULTS, 'username': 'EkoraTest'})

        def fake(cmd, cwd, stdout, **kw):
            stdout.write("Caused by: java.lang.OutOfMemoryError: Java heap space\n")

            class P:
                def wait(self):
                    return 1
            return P()
        popen.side_effect = fake
        self.l.launch()
        crash = dict(self.events)['crash']
        self.assertEqual(crash['actions'][0]['kind'], 'setRam')
        self.assertEqual(self.events[-1][0], 'launchError')

    def test_status_uses_ping(self):
        with patch('serverping.ping', return_value={'online': True, 'players': {'online': 3, 'max': 50}}) as p:
            self.assertTrue(self.l.public_api('status')['online'])
            p.assert_called_with('play.ekoramc.fun', 25567)

    def test_waypoints(self):
        w = {'name': 'Ev', 'world': 'overworld', 'x': 10, 'y': 64, 'z': -3}
        self.l.save_waypoints([w])
        self.assertEqual(self.l.waypoints(), [w])


if __name__ == '__main__':
    unittest.main()
