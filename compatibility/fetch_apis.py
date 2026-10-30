"""Download pinned official Spigot APIs, verifying SHA-256 before using any file."""
from pathlib import Path
import hashlib,json,urllib.request,os

root=Path(__file__).resolve().parent
cache=root/'cache'
cache.mkdir(exist_ok=True)
entries=[]
for entry in json.loads((root/'versions.json').read_text(encoding='utf-8')):
    destination=cache/entry['version']/'spigot-api.jar'
    destination.parent.mkdir(exist_ok=True)
    if destination.exists():
        assert hashlib.sha256(destination.read_bytes()).hexdigest()==entry['sha256'],f'Checksum mismatch: {destination}'
    else:
        with urllib.request.urlopen(entry['url'],timeout=90) as response:
            data=response.read()
        assert hashlib.sha256(data).hexdigest()==entry['sha256'],f'Download checksum mismatch: {entry["version"]}'
        temporary=destination.with_suffix('.part')
        temporary.write_bytes(data)
        os.replace(temporary,destination)
    entries.append({'version':entry['version'],'file':str(destination.resolve()),'snapshot':entry['snapshot'],'sha256':entry['sha256']})
(cache/'apis.json').write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
print(f'Verified {len(entries)} official APIs; manifest: {cache/"apis.json"}')
