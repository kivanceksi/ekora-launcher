import ntpath
import posixpath
import unittest
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from security import same_local_file

class UIOriginTests(unittest.TestCase):
    def test_windows_qt_forward_slashes(self):
        self.assertTrue(same_local_file('C:/Users/Oyuncu/AppData/Local/Programs/EkoraLauncher/assets/index.html',r'C:\Users\Oyuncu\AppData\Local\Programs\EkoraLauncher\assets\index.html',path_module=ntpath))
    def test_windows_case(self):
        self.assertTrue(same_local_file('c:/users/PLAYER/assets/index.html',r'C:\Users\player\assets\index.html',path_module=ntpath))
    def test_spaces_and_turkish(self):
        self.assertTrue(same_local_file('C:/Users/Çağrı Ekşi/assets/index.html',r'C:\Users\Çağrı Ekşi\assets\index.html',path_module=ntpath))
    def test_other_file_rejected(self):
        self.assertFalse(same_local_file('C:/Users/player/Downloads/index.html',r'C:\Users\player\AppData\Local\Programs\EkoraLauncher\assets\index.html',path_module=ntpath))
    def test_escape_rejected(self):
        self.assertFalse(same_local_file('C:/Ekora/assets/../../other/index.html',r'C:\Ekora\assets\index.html',path_module=ntpath))
    def test_relative_and_empty_rejected(self):
        for path in ('','assets/index.html','https://evil.example/index.html'):
            self.assertFalse(same_local_file(path,r'C:\Ekora\assets\index.html',path_module=ntpath))
    def test_linux_case_sensitive(self):
        self.assertFalse(same_local_file('/tmp/Ekora/assets/index.html','/tmp/ekora/assets/index.html',path_module=posixpath))
    def test_linux_matching(self):
        self.assertTrue(same_local_file('/tmp/ekora/assets/index.html','/tmp/ekora/assets/index.html',path_module=posixpath))
