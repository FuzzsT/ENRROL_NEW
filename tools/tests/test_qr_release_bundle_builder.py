#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, subprocess, tempfile, zipfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT/'tools/release/build_qr_release_bundle.py'

APK_NAME = 'custom-enterprise-release.apk'
PRIMARY = [
 APK_NAME,
 'provisioning-qr.png','provisioning.json','provisioning-payload.txt','provisioning-metadata.json','provisioning-validation.json',
 'work-profile-qr.png','work-profile-provisioning.json','work-profile-provisioning-payload.txt','work-profile-provisioning-metadata.json','work-profile-validation.json',
 'device-owner-qr.png','device-owner-provisioning.json','device-owner-provisioning-payload.txt','device-owner-provisioning-metadata.json','device-owner-validation.json',
 'android-runtime-smoke.json','build-environment.json'
]

with tempfile.TemporaryDirectory() as td:
    dist=Path(td)/'dist'; dist.mkdir()
    for i,name in enumerate(PRIMARY):
        p=dist/name
        if name.endswith('.json'):
            payload={'name':name,'i':i}
            if name.endswith('-validation.json') or name=='provisioning-validation.json': payload['ok']=True
            p.write_text(json.dumps(payload), 'utf-8')
        else:
            p.write_bytes((name+'\n').encode())
    proc=subprocess.run(['python3',str(SCRIPT),'--dist',str(dist),'--version','1.2.0','--apk-url','https://github.com/o/r/releases/download/v1.2.0/'+APK_NAME,'--apk-name',APK_NAME,'--qr-type','both'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    assert proc.returncode==0, proc.stdout
    bundle=dist/'DPC-AIO-1.2.0-QR-RELEASE-BUNDLE.zip'
    sidecar=dist/'DPC-AIO-1.2.0-QR-RELEASE-BUNDLE.zip.sha256'
    assert bundle.is_file() and sidecar.is_file()
    assert (dist/'QR-README.md').is_file()
    assert (dist/'RELEASE-INDEX.json').is_file()
    assert (dist/'SHA256SUMS.txt').is_file()
    observed=hashlib.sha256(bundle.read_bytes()).hexdigest()
    assert sidecar.read_text('utf-8').split()[0]==observed
    index=json.loads((dist/'RELEASE-INDEX.json').read_text('utf-8'))
    assert index['version']=='1.2.0'
    assert index['apkUrl'].endswith('/'+APK_NAME)
    assert index['apk']==APK_NAME
    assert index['qrType']=='both'
    assert index['bundle']['file']==bundle.name
    assert index['bundle']['sha256Sidecar']==sidecar.name
    with zipfile.ZipFile(bundle) as z:
        names=set(z.namelist())
        for required in PRIMARY+['QR-README.md','RELEASE-INDEX.json','SHA256SUMS.txt']:
            assert required in names, required
        assert bundle.name not in names
        assert sidecar.name not in names
    sums=(dist/'SHA256SUMS.txt').read_text('utf-8')
    assert bundle.name not in sums
    for required in PRIMARY+['QR-README.md','RELEASE-INDEX.json']:
        assert required in sums, required
print('test_qr_release_bundle_builder: PASS')
