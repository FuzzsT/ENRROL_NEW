from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text(encoding='utf-8')
normal_ci = (ROOT / '.github/workflows/pr-android-ci.yml').read_text(encoding='utf-8')

assert 'if [[ "$requested" == "auto" ]]; then\n            mode="stable"' in workflow, (
    'auto mode must resolve directly to stable; normal builds may not silently fall back to emergency'
)
assert 'mode="emergency"' not in workflow.split('if [[ "$requested" == "auto" ]]; then', 1)[1].split('else\n            mode="$requested"', 1)[0], (
    'auto mode must not contain an emergency fallback'
)
assert 'Emergency signing is opt-in only and may only be selected explicitly from workflow_dispatch.' in workflow, (
    'workflow must reject non-dispatch emergency signing'
)
assert 'STABLE_SIGNING_KEY_REQUIRED: normal/stable build will not fall back to emergency signing.' in workflow, (
    'missing stable signing configuration must fail closed with a clear diagnostic'
)
assert 'Prepare emergency ephemeral signer' in workflow, (
    'explicit emergency mode should remain available as a separate recovery path'
)

assert 'push:\n    branches:\n      - main' in normal_ci, (
    'normal Android CI must also run on pushes to main so a usable APK is produced independently of stable release signing'
)
assert ':app-dpc:assembleEnterpriseDebug' in normal_ci, 'normal main CI must build enterpriseDebug'
assert 'Package APK artifact' in normal_ci, 'normal main CI must package the APK'
assert 'DPC-AIO-enterprise-debug-${{ github.run_number }}' in normal_ci, 'normal main CI must upload a versioned APK artifact'
assert 'actions/upload-artifact@' in normal_ci, 'normal main CI must upload the APK artifact'
assert 'DPC_AIO_RELEASE_KEYSTORE_B64' not in normal_ci, 'normal main CI must not require stable release signing secrets'

print('MAIN_STABLE_SIGNING_POLICY_CONTRACT: PASS')
