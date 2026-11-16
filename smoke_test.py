"""Packaged Windows UI smoke test. Does not download or start Minecraft."""
import json
import os
import tempfile
from pathlib import Path

def run():
    from PySide6.QtWidgets import QApplication
    from PySide6.QtCore import QTimer
    import app as launcher_app
    out=os.environ.get('EKORA_SMOKE_RESULT')
    if not out:raise RuntimeError('EKORA_SMOKE_RESULT required')
    with tempfile.TemporaryDirectory(prefix='EkoraSmoke-') as t:
        os.environ['EKORA_HOME']=t
        a=QApplication.instance() or QApplication([])
        w=launcher_app.Window();w.show()
        from core import VERSION
        result={'bridge':False,'profile':False,'version':VERSION}
        def end():
            Path(out).write_text(json.dumps(result),encoding='utf-8')
            w.close();a.exit(0 if result['bridge'] and result['profile'] else 1)
        def profile(value):result['profile']=value is True;end()
        def check_profile():
            w.page.runJavaScript("document.getElementById('sideName').textContent === 'EkoraSmoke'",profile)
        def bridge(value):
            result['bridge']=value is True
            if not result['bridge']:end();return
            w.page.runJavaScript("settings.onboardingDone=true; settings.autoStart=false; document.getElementById('username').value='EkoraSmoke'; document.getElementById('saveProfile').click();")
            QTimer.singleShot(1500,check_profile)
        QTimer.singleShot(4000,lambda:w.page.runJavaScript("document.getElementById('previewBanner').hidden",bridge))
        QTimer.singleShot(20000,end)
        return a.exec()
