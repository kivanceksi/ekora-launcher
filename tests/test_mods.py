import hashlib
import io
import json
import sys
import tempfile
import threading
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import modcheck
import crash
from modrinth import Manager, Content, pick, NotAvailable
from profiles import Profiles

FIX = Path(__file__).with_name('fixtures')

USER_LOG = """[13:11:38] [main/ERROR]: Incompatible mods found!
net.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible with the game or each other!
A potential solution has been determined, this may resolve your problem:
	 - Replace 'Minecraft' (minecraft) 1.21.1 with version 1.21.11.
More details:
	 - Mod 'Litematica' (litematica) 0.26.16 requires version 1.21.11 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!
	 - Mod 'MaLiLib' (malilib) 0.27.20 requires version 1.21.11 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!
"""


def jar_bytes(meta, nested=None, extra=None):
    b = io.BytesIO()
    with zipfile.ZipFile(b, 'w') as z:
        if meta is not None:
            z.writestr('fabric.mod.json', json.dumps(meta))
        for name, data in (nested or {}).items():
            z.writestr(name, data)
        for name, data in (extra or {}).items():
            z.writestr(name, data)
    return b.getvalue()


class Predicates(unittest.TestCase):
    def test_matches(self):
        m = modcheck.matches
        self.assertTrue(m('1.21.11', '1.21.11'))
        self.assertFalse(m('1.21.11', '1.21.1'))
        self.assertTrue(m('>=1.21.9 <=1.21.11', '1.21.10'))
        self.assertFalse(m('>=1.21.9 <=1.21.11', '26.3'))
        self.assertTrue(m('~26.3', '26.3'))
        self.assertTrue(m('~1.21.1', '1.21.5'))
        self.assertFalse(m('~1.21.1', '1.22'))
        self.assertTrue(m('1.21.x', '1.21.11'))
        self.assertTrue(m(['1.21.11', '26.3'], '26.3'))
        self.assertTrue(m('*', 'anything'))
        self.assertTrue(m('>=0.16.0', '0.20.1'))
        self.assertFalse(m('>=0.21.0', '0.20.1'))
        self.assertTrue(m('>=26.3-', '26.3'))
        self.assertTrue(m('>=26.3-alpha.1', '26.3'))
        self.assertFalse(m('>=26.3', '26.3-rc.1'))
        self.assertTrue(m('^0.20.0', '0.20.1'))


class Check(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.mods = Path(self.t.name)

    def tearDown(self):
        self.t.cleanup()

    def put(self, name, data):
        (self.mods / name).write_bytes(data)

    def test_user_case(self):
        self.put('litematica.jar', jar_bytes({'id': 'litematica', 'name': 'Litematica', 'version': '0.26.16',
                                              'depends': {'minecraft': '1.21.11', 'malilib': '>=0.27.0', 'fabricloader': '>=0.16'}}))
        self.put('malilib.jar', jar_bytes({'id': 'malilib', 'name': 'MaLiLib', 'version': '0.27.20', 'depends': {'minecraft': '1.21.11'}}))
        issues = modcheck.check(self.mods, '1.21.1', 'fabric', '0.20.1')
        self.assertEqual([i['type'] for i in issues], ['wrongVersion', 'wrongVersion'])
        self.assertIn('1.21.11', issues[0]['text'])
        self.assertEqual(modcheck.check(self.mods, '1.21.11', 'fabric', '0.20.1'), [])

    def test_missing_nested_forge_duplicate_breaks(self):
        api = jar_bytes({'id': 'fabric-api', 'version': '0.166.2', 'provides': ['fabric'],
                         'jars': [{'file': 'META-INF/jars/fabric-api-base.jar'}]},
                        nested={'META-INF/jars/fabric-api-base.jar': jar_bytes({'id': 'fabric-api-base', 'version': '1.0'})})
        self.put('fabric-api.jar', api)
        self.put('needs.jar', jar_bytes({'id': 'needs', 'depends': {'fabric-api-base': '*', 'fabric': '*', 'cloth-config': '*'},
                                         'breaks': {'optifabric': '*'}}))
        self.put('zz-needs-copy.jar', jar_bytes({'id': 'needs'}))
        self.put('forge.jar', jar_bytes(None, extra={'META-INF/mods.toml': 'modLoader="javafml"'}))
        self.put('optifabric.jar', jar_bytes({'id': 'optifabric', 'version': '1'}))
        self.put('broken.jar', b'not a zip')
        issues = modcheck.check(self.mods, '26.3', 'fabric')
        types = sorted(i['type'] for i in issues)
        self.assertEqual(types, ['broken', 'conflict', 'duplicate', 'missing', 'wrongLoader'])
        missing = next(i for i in issues if i['type'] == 'missing')
        self.assertEqual(missing['dep'], 'cloth-config')

    def test_lenient_json(self):
        raw = b'{"id":"x","name":"X\n mod","depends":{"minecraft":"26.3",},}'
        b = io.BytesIO()
        with zipfile.ZipFile(b, 'w') as z:
            z.writestr('fabric.mod.json', raw)
        self.put('x.jar', b.getvalue())
        self.assertEqual(modcheck.check(self.mods, '26.3', 'fabric'), [])


class Crash(unittest.TestCase):
    def test_user_log(self):
        r = crash.analyze(USER_LOG, '1.21.1')
        self.assertEqual(r['title'], 'Bazı modlar bu oyun sürümüyle uyumsuz')
        kinds = [a['kind'] for a in r['actions']]
        self.assertEqual(kinds, ['fixMods', 'switchVersion'])
        self.assertEqual(r['actions'][1]['version'], '1.21.11')
        self.assertIn('Litematica', r['details'][0])

    def test_missing_dep_and_others(self):
        r = crash.analyze("Mod 'Litematica' (litematica) 0.29.1 requires any version of 'MaLiLib' (malilib), which is missing!", '26.3')
        self.assertIn('MaLiLib', r['details'][0])
        self.assertEqual(crash.analyze('Error: Could not create the Java Virtual Machine.')['actions'][0]['kind'], 'setRam')
        self.assertEqual(crash.analyze('java.lang.UnsupportedClassVersionError: x')['actions'][0]['kind'], 'resetJava')
        self.assertEqual(crash.analyze('GLFW error 65542: WGL: The driver does not appear to support OpenGL')['title'], 'Ekran kartı sürücüsü sorunu')
        self.assertIsNone(crash.analyze('everything fine'))


class FakeApi:
    """Serves recorded Modrinth responses and fake file bodies."""

    def __init__(self):
        self.files = {}
        self.p = {}
        self.versions_by_project = {}
        self.downloads = []

    def add(self, pid, slug, title, versions):
        self.p[pid] = {'id': pid, 'slug': slug, 'title': title, 'icon_url': f'https://cdn.modrinth.com/{pid}.png'}
        self.versions_by_project[pid] = versions
        for v in versions:
            for f in v['files']:
                body = f'{pid}-{v["id"]}'.encode()
                f['hashes'] = {'sha1': hashlib.sha1(body).hexdigest(), 'sha512': hashlib.sha512(body).hexdigest()}
                self.files[f['url']] = body

    def project(self, pid):
        return self.p.get(pid) or next((p for p in self.p.values() if p['slug'] == pid), None)

    def projects(self, ids):
        return [self.p[i] for i in ids if i in self.p]

    def versions(self, pid, loaders=None, game_versions=None):
        p = self.project(pid)
        out = []
        for v in self.versions_by_project.get(p['id'] if p else pid, []):
            if loaders and not set(loaders) & set(v['loaders']):
                continue
            if game_versions and not set(game_versions) & set(v['game_versions']):
                continue
            out.append(v)
        return out

    def version(self, vid):
        for vs in self.versions_by_project.values():
            for v in vs:
                if v['id'] == vid:
                    return v
        return None

    def by_hashes(self, sha1s):
        out = {}
        for vs in self.versions_by_project.values():
            for v in vs:
                for f in v['files']:
                    if f['hashes']['sha1'] in sha1s:
                        out[f['hashes']['sha1']] = v
        return out

    def updates(self, sha1s, loaders, game_versions):
        cur = self.by_hashes(sha1s)
        out = {}
        for h, v in cur.items():
            new = pick(self.versions(v['project_id'], loaders, game_versions))
            if new:
                out[h] = new
        return out

    def search(self, query, ptype, version, loader, limit=20, **k):
        return {'hits': [{'id': p['id'], 'slug': p['slug'], 'title': p['title']} for p in self.p.values()
                         if query.lower() in p['slug']]}

    def download(self, url, dest, sha512=None, sha1=None, size=None, hosts=None, cancel=None):
        body = self.files[url]
        assert hashlib.sha512(body).hexdigest() == sha512
        Path(dest).parent.mkdir(parents=True, exist_ok=True)
        Path(dest).write_bytes(body)
        self.downloads.append(url)
        return hashlib.sha1(body).hexdigest(), sha512


def ver(pid, vid, number, gv, loaders=('fabric',), deps=(), vtype='release', fname=None):
    fname = fname or f'{pid}-{number}.jar'
    return {'id': vid, 'project_id': pid, 'version_number': number, 'game_versions': list(gv), 'loaders': list(loaders),
            'version_type': vtype, 'dependencies': [{'dependency_type': 'required', 'project_id': d, 'version_id': None} for d in deps],
            'files': [{'filename': fname, 'url': f'https://cdn.modrinth.com/data/{pid}/{vid}/{fname}', 'primary': True, 'size': 10}]}


class ManagerTests(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.api = FakeApi()
        self.api.add('bEpr0Arc', 'litematica', 'Litematica', [
            ver('bEpr0Arc', 'ZqV316KY', '0.29.1', ['26.3'], deps=['GcWjdA9I']),
            ver('bEpr0Arc', 'OLD11', '0.26.16', ['1.21.11'], deps=['GcWjdA9I']),
        ])
        self.api.add('GcWjdA9I', 'malilib', 'MaLiLib', [
            ver('GcWjdA9I', 'MAL263b', '0.30.0-beta', ['26.3'], vtype='beta'),
            ver('GcWjdA9I', 'MAL263', '0.29.0', ['26.3']),
        ])
        self.api.add('AANobbMI', 'sodium', 'Sodium', [ver('AANobbMI', 'SOD', '0.9.3-beta.5', ['26.3'], vtype='beta')])
        self.m = Manager(self.api, Path(self.t.name) / 'cache')
        self.profile = {'id': 'ekoramc', 'version': '26.3', 'loader': 'fabric'}
        self.game = Path(self.t.name) / 'game'
        self.game.mkdir()
        self.c = Content(self.game)

    def tearDown(self):
        self.t.cleanup()

    def test_pick_prefers_release(self):
        vs = self.api.versions('GcWjdA9I', ['fabric'], ['26.3'])
        self.assertEqual(pick(vs)['id'], 'MAL263')
        self.assertEqual(pick(self.api.versions('AANobbMI', ['fabric'], ['26.3']))['id'], 'SOD')

    def test_install_with_dependency_and_skip_reinstall(self):
        titles, warnings = self.m.install(self.c, 'bEpr0Arc', 'mod', self.profile)
        self.assertEqual(titles, ['Litematica', 'MaLiLib'])
        names = sorted(f.name for f in (self.game / 'mods').iterdir())
        self.assertEqual(names, ['GcWjdA9I-0.29.0.jar', 'bEpr0Arc-0.29.1.jar'])
        e = Content(self.game).entry('mods', 'GcWjdA9I-0.29.0.jar')
        self.assertTrue(e['dependency'])
        self.assertEqual(Content(self.game).known_files(), {'GcWjdA9I-0.29.0.jar', 'bEpr0Arc-0.29.1.jar'})
        n = len(self.api.downloads)
        self.m.install(Content(self.game), 'bEpr0Arc', 'mod', self.profile)
        self.assertEqual(len(self.api.downloads), n)

    def test_not_available(self):
        with self.assertRaises(NotAvailable):
            self.m.install(self.c, 'bEpr0Arc', 'mod', {'id': 'x', 'version': '1.8.9', 'loader': 'fabric'})

    def test_update_replaces_old_file(self):
        p11 = {'id': 'x', 'version': '1.21.11', 'loader': 'fabric'}
        self.api.add('GcWjdA9I', 'malilib', 'MaLiLib', [ver('GcWjdA9I', 'MAL11', '0.27.20', ['1.21.11', '26.3'])])
        self.m.install(self.c, 'bEpr0Arc', 'mod', p11)
        # now a 26.3 profile with the same folder: updates should offer Litematica 0.29.1
        self.api.updates = lambda sha1s, l, g: {h: self.api.version('ZqV316KY') for h, v in self.api.by_hashes(sha1s).items()
                                                if v['project_id'] == 'bEpr0Arc'}
        c = Content(self.game)
        items = self.m.check_updates(c, self.profile)
        self.assertEqual([(i['title'], i['new']) for i in items if i['folder'] == 'mods'], [('Litematica', '0.29.1')])
        self.m.apply_update(c, items[0], self.profile)
        names = sorted(f.name for f in (self.game / 'mods').iterdir())
        self.assertIn('bEpr0Arc-0.29.1.jar', names)
        self.assertNotIn('bEpr0Arc-0.26.16.jar', names)

    def test_fix_wrong_version_and_missing(self):
        mods = self.game / 'mods'
        mods.mkdir()
        # manually added 1.21.11 litematica, identified only by hash
        body = self.api.files['https://cdn.modrinth.com/data/bEpr0Arc/OLD11/bEpr0Arc-0.26.16.jar']
        (mods / 'litematica-old.jar').write_bytes(body)
        issue = {'type': 'wrongVersion', 'file': 'litematica-old.jar', 'mod': 'Litematica'}
        msg = self.m.fix_issue(Content(self.game), issue, self.profile)
        self.assertIn('uygun sürüm kuruldu', msg)
        names = sorted(f.name for f in mods.iterdir())
        self.assertEqual(names, ['GcWjdA9I-0.29.0.jar', 'bEpr0Arc-0.29.1.jar'])
        msg = self.m.fix_issue(Content(self.game), {'type': 'missing', 'file': 'a.jar', 'dep': 'sodium', 'mod': 'A'}, self.profile)
        self.assertIn('kuruldu', msg)

    def test_unknown_fix_disables(self):
        mods = self.game / 'mods'
        mods.mkdir()
        (mods / 'forge.jar').write_bytes(b'x')
        self.m.fix_issue(Content(self.game), {'type': 'wrongLoader', 'file': 'forge.jar', 'mod': 'F'}, self.profile)
        self.assertTrue((mods / 'forge.jar.disabled').exists())


class ProfilesTests(unittest.TestCase):
    def test_create_update_delete(self):
        with tempfile.TemporaryDirectory() as t:
            ps = Profiles(t, Path(t) / 'minecraft', '26.3').load({})
            p = ps.create('Litematica Dünyam', '1.21.11', 'fabric')
            self.assertEqual(p['id'], 'litematica-dunyam')
            self.assertTrue(ps.dir_of(p).is_dir())
            ps.update(p['id'], {'version': '26.3'})
            self.assertEqual(ps.get(p['id'])['version'], '26.3')
            with self.assertRaises(ValueError):
                ps.delete('ekoramc')
            ps.update('ekoramc', {'version': '1.8.9', 'name': 'x'})
            self.assertEqual(ps.get('ekoramc')['version'], '26.3')
            self.assertTrue(ps.delete(p['id']))
            self.assertFalse((Path(t) / 'minecraft' / 'profiles' / p['id']).exists())
            with self.assertRaises(ValueError):
                ps.create('x', '../../evil', 'fabric')

    def test_server_version_change_updates_official(self):
        with tempfile.TemporaryDirectory() as t:
            Profiles(t, Path(t) / 'minecraft', '26.3').load({})
            ps = Profiles(t, Path(t) / 'minecraft', '26.4').load({})
            self.assertEqual(ps.get('ekoramc')['version'], '26.4')


class PackTests(unittest.TestCase):
    def test_mrpack_install_and_traversal(self):
        import modpaket
        with tempfile.TemporaryDirectory() as t:
            api = FakeApi()
            api.add('SOD', 'sodium', 'Sodium', [ver('SOD', 'S1', '1', ['26.3'])])
            sod = api.versions_by_project['SOD'][0]['files'][0]

            def make_pack(files, overrides):
                b = io.BytesIO()
                with zipfile.ZipFile(b, 'w') as z:
                    z.writestr('modrinth.index.json', json.dumps({'formatVersion': 1, 'game': 'minecraft', 'versionId': '1',
                                                                  'name': 'P', 'files': files,
                                                                  'dependencies': {'minecraft': '26.3', 'fabric-loader': '0.20.1'}}))
                    for k, v in overrides.items():
                        z.writestr(k, v)
                return b.getvalue()
            good = make_pack([{'path': 'mods/sodium.jar', 'hashes': sod['hashes'], 'downloads': [sod['url']], 'fileSize': 10,
                               'env': {'client': 'required', 'server': 'optional'}},
                              {'path': 'mods/server-only.jar', 'hashes': sod['hashes'], 'downloads': [sod['url']],
                               'env': {'client': 'unsupported'}}],
                             {'overrides/config/sodium.json': '{}', 'overrides/options.txt': 'fov:1'})
            bad = make_pack([{'path': '../../evil.jar', 'hashes': sod['hashes'], 'downloads': [sod['url']]}], {})
            packs = {}
            for name, data in (('good', good), ('bad', bad)):
                url = f'https://cdn.modrinth.com/data/PACK/{name}/p.mrpack'
                api.files[url] = data
                packs[name] = {'id': 'V' + name, 'project_id': 'PACK', 'version_number': '1', 'game_versions': ['26.3'], 'loaders': ['fabric'],
                               'version_type': 'release', 'dependencies': [],
                               'files': [{'filename': 'p.mrpack', 'url': url, 'primary': True, 'size': len(data),
                                          'hashes': {'sha1': hashlib.sha1(data).hexdigest(), 'sha512': hashlib.sha512(data).hexdigest()}}]}
            api.versions_by_project['PACK'] = [packs['good']]
            ps = Profiles(t, Path(t) / 'minecraft', '26.3').load({})
            p = modpaket.install_pack(api, ps, {'id': 'PACK', 'title': 'Fabulously Optimized'}, Path(t) / 'cache')
            d = ps.dir_of(p)
            self.assertEqual((p['version'], p['loader'], ps.get(p['id'])['loaderVersion']), ('26.3', 'fabric', '0.20.1'))
            self.assertTrue((d / 'mods' / 'sodium.jar').exists())
            self.assertFalse((d / 'mods' / 'server-only.jar').exists())
            self.assertTrue((d / 'config' / 'sodium.json').exists())
            self.assertIn('sodium.jar', Content(d).known_files())
            api.versions_by_project['PACK'] = [packs['bad']]
            with self.assertRaises(ValueError):
                modpaket.install_pack(api, ps, {'id': 'PACK', 'title': 'Kötü'}, Path(t) / 'cache')
            self.assertFalse((Path(t) / 'evil.jar').exists())
            self.assertEqual(len(ps.items), 2)  # failed pack removed its profile


if __name__ == '__main__':
    unittest.main()
