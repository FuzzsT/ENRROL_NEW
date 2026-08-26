#!/usr/bin/env python3
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[2]
errors=[]

def read(rel):
    p=ROOT/rel
    if not p.is_file():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text('utf-8')

app=read('apps/dpc/app/build.gradle.kts')
receiver=read('apps/dpc/app/src/main/kotlin/io/dpcaio/app/VerificationCommandReceiver.kt')
workflow=read('.github/workflows/build-aio-enrollment.yml')
harness=read('tools/runtime/android_device_owner_smoke.py')
bundle_builder=read('tools/release/build_qr_release_bundle.py')

vm=re.search(r'versionName\s*=\s*"([0-9.]+)"', app)
if not vm or tuple(map(int,vm.group(1).split('.'))) < (1,1,2):
    errors.append('versionName must be >=1.1.2')
m=re.search(r'versionCode\s*=\s*(\d+)', app)
if not m or int(m.group(1)) < 23:
    errors.append('versionCode must be >=23')
for marker in ['ACTION_VERIFY_DIAGNOSTICS', 'DpcDiagnosticsSnapshot.capture(context)', 'status", "VERIFIED"']:
    if marker not in receiver:
        errors.append(f'diagnostics verification bridge missing {marker}')
for marker in [
    'adb', 'dpm', 'set-device-owner', 'AioDeviceAdminReceiver',
    'VERIFY_DIAGNOSTICS', 'deviceOwner', 'dpcVersion', 'android-runtime-smoke.json'
]:
    if marker not in harness:
        errors.append(f'runtime harness missing {marker}')
for marker in [
    'Enable KVM group perms', 'ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d',
    'api-level: 35', 'tools/runtime/android_device_owner_smoke.py',
    'android-runtime-smoke.json', 'Upload Android runtime smoke evidence'
]:
    if marker not in workflow:
        errors.append(f'workflow runtime smoke missing {marker}')
# Runtime smoke must gate integrity packaging and both publication paths.
smoke_pos=workflow.find('tools/runtime/android_device_owner_smoke.py')
integrity_markers=['Refresh artifact hashes after runtime smoke', 'Build QR release bundle']
integrity_positions=[workflow.find(x) for x in integrity_markers if workflow.find(x) >= 0]
if not integrity_positions:
    errors.append('workflow missing post-runtime integrity packaging step')
else:
    integrity_pos=min(integrity_positions)
    if smoke_pos < 0 or smoke_pos > integrity_pos:
        errors.append('runtime smoke must run before post-runtime integrity packaging')
for marker in ['Publish continuous enrollment assets', 'Publish tag release assets']:
    pos=workflow.find(marker)
    if smoke_pos < 0 or pos < 0 or smoke_pos > pos:
        errors.append(f'runtime smoke must run before {marker}')

explicit_runtime_refs=workflow.count('dist/android-runtime-smoke.json')
legacy_hashing='find dist -maxdepth 1 -type f ! -name SHA256SUMS.txt' in workflow
bundle_hashing=('build_qr_release_bundle.py' in workflow and 'android-runtime-smoke.json' in bundle_builder and 'SHA256SUMS.txt' in bundle_builder)
wildcard_release_upload='gh release upload "$tag" dist/*' in workflow
if explicit_runtime_refs < 2 or not (legacy_hashing or bundle_hashing) or not wildcard_release_upload:
    errors.append('runtime smoke evidence must be generated, integrity-hashed, uploaded as artifact, and published with release assets')

if errors:
    raise SystemExit('ANDROID_RUNTIME_SMOKE_112_CONTRACT: FAIL\n- ' + '\n- '.join(errors))
print('ANDROID_RUNTIME_SMOKE_112_CONTRACT: PASS')
