#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
errors = []

def read(rel):
    return (ROOT / rel).read_text('utf-8')

for path in ROOT.rglob('*'):
    if not path.is_file() or '.git' in path.parts:
        continue
    try:
        text = path.read_text('utf-8')
    except UnicodeDecodeError:
        continue
    if path == Path(__file__):
        continue
    stale_a = 'local-localhost-app-system/' + 'test2'
    stale_b = 'FuzzsT/' + 'Temp'
    if stale_a in text or stale_b in text:
        errors.append(f'stale repository target: {path.relative_to(ROOT)}')

transient = [p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file() and (p.suffix in {'.pyc','.pyo'} or '__pycache__' in p.parts)]
if transient:
    errors.append('transient python bytecode present: ' + ', '.join(transient[:5]))

readme = read('README.md')
if 'FuzzsT/ENRROL_NEW' not in readme:
    errors.append('README missing dpc_android target')

workflow = read('.github/workflows/build-aio-enrollment.yml')
for needle in [
    'Prepare stable signer',
    'STABLE_SIGNING_KEY_REQUIRED',
    'Prepare emergency ephemeral signer',
    'PKCS12',
    'dpc-aio-emergency-enrollment',
    'assembleEnterpriseRelease',
    'device-owner-qr.png',
    'work-profile-qr.png',
    'signing-mode.txt',
    'signing_mode:',
]:
    if needle not in workflow:
        errors.append(f'unified enrollment workflow missing {needle}')

byte_identity_check = (
    'cmp -s' in workflow
    or (
        'sha256sum dist/DPC-AIO-enterprise-release.apk' in workflow
        and 'sha256sum public.apk' in workflow
        and 'observed' in workflow
        and 'expected' in workflow
    )
)
if not byte_identity_check:
    errors.append('unified enrollment workflow lacks published APK byte identity verification')

if 'assembleEnterpriseDebug' in workflow:
    errors.append('production workflow builds debug APK')
if '.github/workflows/build-emergency-enrollment.yml' in workflow:
    errors.append('unified workflow still references removed emergency workflow')

if errors:
    raise SystemExit('DPC_ANDROID_MIGRATION_CONTRACT: FAIL\n- ' + '\n- '.join(errors))
print('DPC_ANDROID_MIGRATION_CONTRACT: PASS')
