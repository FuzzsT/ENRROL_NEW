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
if 'local-localhost-app-system/dpc_android' not in readme:
    errors.append('README missing dpc_android target')

prod = read('.github/workflows/build-aio-enrollment.yml')
for needle in ['STABLE_SIGNING_KEY_REQUIRED', 'assembleEnterpriseRelease', 'device-owner-qr.png', 'work-profile-qr.png', 'cmp -s']:
    if needle not in prod:
        errors.append(f'production workflow missing {needle}')
if 'assembleEnterpriseDebug' in prod:
    errors.append('production workflow builds debug APK')

emergency = read('.github/workflows/build-emergency-enrollment.yml')
for needle in ['PKCS12', 'dpc-aio-emergency-enrollment', 'assembleEnterpriseRelease', 'device-owner-qr.png', 'work-profile-qr.png', 'signing-mode.txt', 'cmp -s']:
    if needle not in emergency:
        errors.append(f'emergency workflow missing {needle}')
if 'DPC_AIO_RELEASE_KEYSTORE_B64' in emergency:
    errors.append('emergency workflow unexpectedly depends on stable keystore secret')

if errors:
    raise SystemExit('DPC_ANDROID_MIGRATION_CONTRACT: FAIL\n- ' + '\n- '.join(errors))
print('DPC_ANDROID_MIGRATION_CONTRACT: PASS')
