#!/usr/bin/env python3
from pathlib import Path
import importlib.util
import json
import re
import sys
import types

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
pr_workflow=read('.github/workflows/pr-android-ci.yml')
for token in [
    'Build QR release bundle',
    'tools/release/build_qr_release_bundle.py',
    'DPC-AIO-1.1.4-QR-RELEASE-BUNDLE.zip',
    'DPC-AIO-1.1.4-QR-RELEASE-BUNDLE.zip.sha256',
    'QR-README.md',
    'RELEASE-INDEX.json',
    'SHA256SUMS.txt',
    'work-profile-qr.png',
    'device-owner-qr.png',
    'provisioning-qr.png',
    'provisioning-validation.json',
    'work-profile-validation.json',
    'device-owner-validation.json',
    'Prepare stable signer',
    'STABLE_SIGNING_KEY_REQUIRED',
    'DPC_AIO_RELEASE_KEYSTORE_B64',
    'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
    'Prepare emergency ephemeral signer',
    'PKCS12',
    'dpc-aio-emergency-enrollment',
    'apps/dpc/app/build/outputs/apk/enterprise/release',
    'apps/dpc/app/build/outputs/provisioning/enterprise/release',
    'tools/release/extract_apksigner_sha256.py',
    'target: aosp_atd',
]:
    if token not in workflow: errors.append(f'unified workflow missing {token}')

for forbidden in [
    'apps/dpc/build/outputs/',
    'target: google_apis',
    'device_provisioned',
    'user_setup_complete',
    'remove-account',
    'remove_account',
    'cmd account remove',
]:
    if forbidden in workflow: errors.append(f'unified workflow contains forbidden/stale marker {forbidden}')

# GitHub-hosted runners have moved artifact actions to the Node 24 generation.
# Keep immutable SHAs pinned so runtime deprecation fixes do not regress silently.
ARTIFACT_ACTION_PINS = {
    'actions/upload-artifact': 'cf430e030ddbb5b0abf93d22962f4752f3646cd9',  # v7.0.2
    'actions/download-artifact': '9000827ccba6bdab643e8b6fd33ac0654aef8333',  # v8.0.2
}
for action, sha in ARTIFACT_ACTION_PINS.items():
    marker=f'{action}@{sha}'
    if marker not in workflow:
        errors.append(f'unified workflow must pin {action} to current Node 24 release SHA {sha}')

SETUP_ANDROID_PIN = 'android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd'
for name, text in [
    ('pr-android-ci.yml', pr_workflow),
    ('build-aio-enrollment.yml', workflow),
]:
    if SETUP_ANDROID_PIN not in text:
        errors.append(f'{name} must pin setup-android v4.0.4')

# Both PR CI and release CI must keep Android minor releases at the canonical sdkmanager path.
# Aliasing android-37.2 as android-37 makes CMake/AGP emit CXX5304 package-location warnings.
def require_canonical_sdk_path(name, text):
    if 'ln -sfn "$installed_path" "$ANDROID_SDK_ROOT/platforms/android-37"' in text:
        errors.append(f'{name} must not alias a minor Android SDK package to platforms/android-37')
    if 'test -e "$ANDROID_SDK_ROOT/platforms/android-37/android.jar"' in text:
        errors.append(f'{name} must validate the canonical installed platform path instead of platforms/android-37')
    if 'installed_path="$ANDROID_SDK_ROOT/platforms/$installed_name"' not in text:
        errors.append(f'{name} must resolve the canonical installed platform path')
    if 'test -e "$installed_path/android.jar"' not in text:
        errors.append(f'{name} must validate android.jar at the canonical installed platform path')

require_canonical_sdk_path('pr-android-ci.yml', pr_workflow)
require_canonical_sdk_path('build-aio-enrollment.yml', workflow)

for token in [
    'id: runtime_smoke_primary',
    'continue-on-error: true',
    'tools/runtime/runtime_smoke_retry_gate.py',
    'DPC_AIO_RUNTIME_RETRY=1',
    "if: env.DPC_AIO_RUNTIME_RETRY == '1'",
    'AOSP Device Owner runtime smoke retry',
    'android-runtime-smoke-attempt1.json',
]:
    if token not in workflow:
        errors.append(f'unified workflow runtime retry missing {token}')
if workflow.count('ReactiveCircus/android-emulator-runner@') != 2:
    errors.append('unified workflow must contain exactly two emulator-runner attempts')

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
    errors.append('published APK byte identity verification missing')

# Rolling prereleases must not accumulate old versioned QR bundles. Stable tag releases are immutable history.
for token in [
    'DPC-AIO-*-QR-RELEASE-BUNDLE.zip',
    'gh release delete-asset "$RELEASE_TAG"',
    'if [[ "$GITHUB_REF_TYPE" != "tag" ]]',
]:
    if token not in workflow:
        errors.append(f'rolling release bundle cleanup missing {token}')

retry_gate_path=ROOT/'tools/runtime/runtime_smoke_retry_gate.py'
if not retry_gate_path.is_file():
    errors.append('runtime smoke retry gate missing')
else:
    try:
        spec=importlib.util.spec_from_file_location('runtime_smoke_retry_gate_contract', retry_gate_path)
        if not spec or not spec.loader:
            raise RuntimeError('could not import runtime smoke retry gate')
        retry_gate=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(retry_gate)
        retryable={
            'state':'FAIL',
            'setDeviceOwner':{
                'exitCode':255,
                'output':'Error: Not allowed to set the device owner because there are already some accounts on the device.'
            },
            'error':'ValueError: Device Owner readback is not true',
        }
        generic_failure={
            'state':'FAIL',
            'setDeviceOwner':{'exitCode':255,'output':'SecurityException: caller is not allowed'},
            'error':'ValueError: Device Owner readback is not true',
        }
        if retry_gate.is_retryable_preexisting_accounts(retryable) is not True:
            errors.append('pre-existing-account Device Owner failure must be retryable')
        if retry_gate.is_retryable_preexisting_accounts(generic_failure) is not False:
            errors.append('generic Device Owner failure must not be retryable')
    except Exception as exc:
        errors.append(f'runtime smoke retry gate behavior failed: {exc}')

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

verifier_path=ROOT/'tools/provisioning/verify_provisioning_qr.py'
verifier=read('tools/provisioning/verify_provisioning_qr.py')
if "VALID_MODES = ('auto', 'work-profile', 'fully-managed')" not in verifier:
    errors.append('QR verifier must accept compatibility mode auto')
for token in ['cv2.copyMakeBorder', 'QRCodeDetectorAruco', "('native', image)", "('padded'", 'QR image could not be decoded by OpenCV detectors']:
    if token not in verifier:
        errors.append(f'QR verifier resilience missing {token}')
if 'print(rendered, file=sys.stderr)' not in verifier:
    errors.append('QR verifier must echo failed validation report to stderr')

try:
    spec=importlib.util.spec_from_file_location('verify_provisioning_qr_contract', verifier_path)
    if not spec or not spec.loader:
        raise RuntimeError('could not import QR verifier')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    payload={'contract':'padded-fallback'}
    class FakeImage:
        shape=(840,840,3)
    image=FakeImage()
    class Detector:
        def detectAndDecode(self, candidate):
            if isinstance(candidate, tuple) and candidate and candidate[0]=='padded':
                return json.dumps(payload), object(), None
            return '', None, None
    fake_cv2=types.SimpleNamespace(
        BORDER_CONSTANT=0,
        imread=lambda _path:image,
        copyMakeBorder=lambda original,*_args,**_kwargs:('padded', original),
        QRCodeDetector=lambda:Detector(),
    )
    old_cv2=sys.modules.get('cv2')
    sys.modules['cv2']=fake_cv2
    try:
        observed=module.decode_qr(Path('contract.png'))
    finally:
        if old_cv2 is None:
            sys.modules.pop('cv2',None)
        else:
            sys.modules['cv2']=old_cv2
    if observed != payload:
        errors.append(f'QR padded fallback returned {observed!r}')
except Exception as exc:
    errors.append(f'QR padded fallback behavior failed: {exc}')

runner=read('tools/run_host_tests.sh')
if 'test_114_qr_release_bundle_contract.py' not in runner:
    errors.append('1.1.4 contract not wired into host tests')

if errors:
    print('test_114_qr_release_bundle_contract: FAIL')
    for e in errors: print(' -', e)
    raise SystemExit(1)
print('test_114_qr_release_bundle_contract: PASS')
