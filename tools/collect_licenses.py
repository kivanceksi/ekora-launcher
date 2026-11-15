"""Copy installed distributions' license/notice files into the build output.
Not a substitute for a license audit; inventory records missing notices.
"""
from importlib.metadata import distributions
from pathlib import Path
import json
import sys
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True);inventory=[]
for dist in distributions():
    name=dist.metadata.get('Name','unknown');found=[]
    for entry in dist.files or []:
        leaf=Path(str(entry)).name.lower()
        if any(word in leaf for word in ('license','copying','notice')) and not leaf.endswith(('.py','.pyc','.pyd','.exe')):
            src=Path(dist.locate_file(entry))
            if src.is_file() and src.stat().st_size<2_000_000:
                safe=''.join(c for c in name if c.isalnum() or c in '-_')
                target=out/safe;target.mkdir(exist_ok=True)
                dest=target/(str(len(found))+'-'+src.name)
                dest.write_bytes(src.read_bytes());found.append(dest.name)
    inventory.append({'name':name,'version':dist.version,'license':dist.metadata.get('License'),'notices':found})
(out/'dependency-inventory.json').write_text(json.dumps(inventory,indent=2,ensure_ascii=False),encoding='utf-8')
