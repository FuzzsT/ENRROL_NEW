#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
errors=[]

def read(path):
    p=ROOT/path
    if not p.is_file():
        errors.append(f'missing {path}')
        return ''
    return p.read_text('utf-8', errors='ignore')

app=read('apps/dpc/app/build.gradle.kts')
if 'versionName = "1.1.4"' not in app: errors.append('versionName 1.1.4 missing')
m=re.search(r'versionCode\s*=\s*(\d+)', app)
if not m or int(m.group(1)) < 25: errors.append('versionCode >=25 required')

workflow=read('.github/workflows/build-aio-enrollment.yml')
for token in [
    'Build QR release bundle',
    'tools/release/build_qr_release_bundle.py',
    'DPC_AIO_APP_VERSION',
    'DPC_AIO_QR_BUNDLE_NAME',
    '--version "$DPC_AIO_APP_VERSION"',
    'test -f "dist/$DPC_AIO_QR_BUNDLE_NAME"',
    'test -f "dist/$DPC_AIO_QR_BUNDLE_NAME.sha256"',
    'QR-README.md',
    'RELEASE-INDEX.json',
    'SHA256SUMS.txt',
    'work-profile-qr.png',
    'device-owner-qr.png',
    'provisioning-qr.png',
    'provisioning-validation.json',
    'work-profile-validation.json',
    'device-owner-validation.json',
]:
    if token not in workflow: errors.append(f'workflow missing {token}')

script=read('tools/release/build_qr_release_bundle.py')
for token in ['REQUIRED_PRIMARY_ASSETS','QR-README.md','RELEASE-INDEX.json','SHA256SUMS.txt','zipfile.ZipFile','bundleSha256']:
    if token not in script: errors.append(f'bundle builder missing {token}')

readme=read('README.md')
if 'DPC-AIO 1.1.4' not in readme or 'QR Release Bundle' not in readme:
    errors.append('README 1.1.4 QR release highlights missing')

report=read('RELEASE-VERIFICATION.json')
try:
    obj=json.loads(report)
    if obj.get('version')!='1.1.4': errors.append('release report version !=1.1.4')
    ev=obj.get('evidenceStates',{})
    if ev.get('QR_RELEASE_BUNDLE_VERIFIED') not in {'PASS','NOT_RUN','BLOCKED'}:
        errors.append('QR_RELEASE_BUNDLE_VERIFIED evidence missing')
except Exception as exc:
    errors.append(f'release report invalid JSON: {exc}')

verifier=read('tools/provisioning/verify_provisioning_qr.py')
if "VALID_MODES = ('auto', 'work-profile', 'fully-managed')" not in verifier:
    errors.append('QR verifier must accept compatibility mode auto')

runner=read('tools/run_host_tests.sh')
if 'test_114_qr_release_bundle_contract.py' not in runner:
    errors.append('1.1.4 contract not wired into host tests')

if errors:
    print('test_114_qr_release_bundle_contract: FAIL')
    for e in errors: print(' -', e)
    raise SystemExit(1)
print('test_114_qr_release_bundle_contract: PASS')
