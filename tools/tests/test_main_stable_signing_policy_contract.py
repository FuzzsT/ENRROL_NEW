from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text(encoding='utf-8')

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

assert '\n  normal-apk:\n' in workflow, (
    'main workflow must expose an independent normal APK job even when stable release signing is unavailable'
)
normal = workflow.split('\n  normal-apk:\n', 1)[1].split('\n  build:\n', 1)[0]
assert 'assembleEnterpriseDebug' in normal, 'normal APK job must build enterprise debug'
assert 'DPC-AIO-enterprise-normal-${{ github.run_number }}' in normal, 'normal APK must be published as a named workflow artifact'
assert 'actions/upload-artifact@' in normal, 'normal APK job must upload its artifact'
assert 'DPC_AIO_RELEASE_KEYSTORE_B64' not in normal, 'normal APK job must not require stable release signing secrets'
assert '\n    needs:' not in normal, 'normal APK job must run independently of the stable release job'

print('MAIN_STABLE_SIGNING_POLICY_CONTRACT: PASS')
