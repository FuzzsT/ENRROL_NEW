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
    'android-runtime-smoke.json', 'Upload verified enrollment artifacts'
]:
    if marker not in workflow:
        errors.append(f'workflow runtime smoke missing {marker}')

# aosp_atd images do not need an explicit hardware profile. Passing one makes
# avdmanager attempt to load a system-image devices.xml that the ATD package
# does not provide, producing a deterministic "Could not load devices" error.
if re.search(r'target:\s*aosp_atd\s*\n\s*arch:\s*x86_64\s*\n\s*profile:', workflow):
    errors.append('aosp_atd runtime smoke must not force an AVD hardware profile')

# Runtime smoke must happen before integrity packaging and before the verified
# artifact set is uploaded/published. The unified workflow publishes both stable
# and emergency modes through one publish job, so contract the behavior rather
# than legacy step names.
smoke_pos=workflow.find('tools/runtime/android_device_owner_smoke.py')
integrity_markers=['Refresh artifact hashes after runtime smoke', 'Build QR release bundle']
integrity_positions=[workflow.find(x) for x in integrity_markers if workflow.find(x) >= 0]
if not integrity_positions:
    errors.append('workflow missing post-runtime integrity packaging step')
else:
    integrity_pos=min(integrity_positions)
    if smoke_pos < 0 or smoke_pos > integrity_pos:
        errors.append('runtime smoke must run before post-runtime integrity packaging')

upload_pos=workflow.find('Upload verified enrollment artifacts')
publish_pos=workflow.find('Publish release assets')
if smoke_pos < 0 or upload_pos < 0 or smoke_pos > upload_pos:
    errors.append('runtime smoke must run before verified artifact upload')
if smoke_pos < 0 or publish_pos < 0 or smoke_pos > publish_pos:
    errors.append('runtime smoke must run before unified release publication')

explicit_runtime_refs=workflow.count('dist/android-runtime-smoke.json')
legacy_hashing='find dist -maxdepth 1 -type f ! -name SHA256SUMS.txt' in workflow
bundle_hashing=('build_qr_release_bundle.py' in workflow and 'android-runtime-smoke.json' in bundle_builder and 'SHA256SUMS.txt' in bundle_builder)
wildcard_release_upload=('gh release upload "$RELEASE_TAG" dist/*' in workflow or 'gh release upload "$tag" dist/*' in workflow)
upload_dist=(
    'uses: actions/upload-artifact@' in workflow
    and re.search(r'^\s*path:\s*dist/?\s*$', workflow, re.MULTILINE) is not None
)
if explicit_runtime_refs < 1 or not (legacy_hashing or bundle_hashing) or not wildcard_release_upload or not upload_dist:
    errors.append('runtime smoke evidence must be generated, integrity-hashed, uploaded as artifact, and published with release assets')

if errors:
    raise SystemExit('ANDROID_RUNTIME_SMOKE_112_CONTRACT: FAIL\n- ' + '\n- '.join(errors))
print('ANDROID_RUNTIME_SMOKE_112_CONTRACT: PASS')
