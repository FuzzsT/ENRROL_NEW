#!/usr/bin/env python3
from pathlib import Path
import sys,tempfile,subprocess,json
ROOT=Path(__file__).resolve().parents[2]

def run(*args):
    p=subprocess.run([sys.executable,'-m','tools.offline_bundle',*args],cwd=ROOT,text=True,capture_output=True)
    return p

with tempfile.TemporaryDirectory() as td:
    td=Path(td); b=td/'bundle'
    p=run('create',str(b),'--bundle-id','demo','--key-id','prod-01')
    assert p.returncode==0,p.stderr
    data=json.loads(p.stdout); assert data['status']=='CREATED'
    v=run('validate',str(b)); assert json.loads(v.stdout)['status']=='VALID'
    preview=run('preview',str(b),'--api','36','--mode','DEVICE_OWNER')
    out=json.loads(preview.stdout)
    assert out['status']=='HOST_PREVIEW_VALID'
    assert out['deviceCapabilityStatus']=='DEVICE_VERIFICATION_REQUIRED'
print('test_101_offline_bundle_cli: PASS')

launcher=ROOT/'tools/bundle-tool'
assert launcher.is_file(), launcher
assert launcher.stat().st_mode & 0o111
h=subprocess.run([str(launcher),'--help'],cwd=ROOT,text=True,capture_output=True)
assert h.returncode==0 and 'bundle-tool' in h.stdout
print('test_101_bundle_tool_launcher: PASS')

with tempfile.TemporaryDirectory() as td:
    td=Path(td); b=td/'bundle'; run('create',str(b),'--bundle-id','meta','--key-id','k1')
    apk=td/'mystery.apk'; apk.write_bytes(b'not-a-real-apk')
    missing=run('add-apk',str(b),str(apk),'--role','base')
    assert missing.returncode==2, missing.stderr
    try: md=json.loads(missing.stdout)
    except Exception: raise AssertionError(f'add-apk must return JSON, got stdout={missing.stdout!r} stderr={missing.stderr!r}')
    assert md['status']=='APK_METADATA_UNAVAILABLE',md
print('test_101_bundle_tool_metadata_guard: PASS')
