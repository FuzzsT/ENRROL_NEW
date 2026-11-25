from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text(encoding='utf-8')
normal_ci = (ROOT / '.github/workflows/pr-android-ci.yml').read_text(encoding='utf-8')

for token in [
    'HAS_LEGACY_SIGNING_PASSWORD',
    'secrets.DPC_AIO_LEGACY_SIGNING_PASSWORD',
    'AUTO_EMERGENCY_FALLBACK',
    'mode="emergency"',
    'AUTO_EMERGENCY_FALLBACK: stable signing is unavailable; publishing a fresh recovery APK instead of failing the rolling build.',
]:
    assert token in workflow, f'rolling release fallback contract missing: {token}'

assert 'DPC_AIO_MANUAL_SIGNING_PASSWORD: ${{ inputs.release_signing_password || secrets.DPC_AIO_LEGACY_SIGNING_PASSWORD }}' in workflow, (
    'stable signer must accept the persisted legacy password from Actions secrets for unattended main builds'
)
assert 'DPC_AIO_REQUIRE_PERSISTED_SIGNER: ${{ github.event_name != \'workflow_dispatch\' }}' in workflow, (
    'automated password-backed builds must never create/rotate a signing key when persisted ciphertext is unavailable'
)
assert 'Prepare emergency ephemeral signer' in workflow, (
    'explicit emergency mode should remain available as a separate recovery path'
)

# Emergency has two distinct purposes. When stable signing credentials are
# available it may be advertised through Self Update, but it must use the
# stable signer so Android accepts it as an in-place update. If stable signing
# is unavailable, the explicit emergency build may remain a fresh-install /
# recovery artifact, but it must not publish a self-update manifest that the
# installed stable app will inevitably reject with SIGNER_MISMATCH.
for token in [
    'stable_signing_configured: ${{ steps.mode.outputs.stable_signing_configured }}',
    'echo "stable_signing_configured=$STABLE_SIGNING_CONFIGURED"',
    "if: env.DPC_AIO_PIPELINE_MODE == 'stable' || (env.DPC_AIO_PIPELINE_MODE == 'emergency' && steps.mode.outputs.stable_signing_configured == 'true')",
    "if: env.DPC_AIO_PIPELINE_MODE == 'emergency' && steps.mode.outputs.stable_signing_configured != 'true'",
    'id: self_update_manifest',
    'echo "available=false" >> "$GITHUB_OUTPUT"',
    'EMERGENCY_SELF_UPDATE_MANIFEST_SKIPPED_EPHEMERAL_SIGNER',
    "if: needs.build.outputs.should_publish == 'true' && needs.build.outputs.self_update_manifest == 'true'",
    'REPAIR_SELF_UPDATE_EMERGENCY_SKIPPED_EPHEMERAL_SIGNER',
]:
    assert token in workflow, f'emergency update-compatible signing contract missing: {token}'

assert 'self_update_manifest: ${{ steps.self_update_manifest.outputs.available }}' in workflow, (
    'publish job must know whether an update-compatible manifest was produced'
)
assert "if [[ \"$tag\" == \"dpc-aio-emergency-enrollment\" ]]; then" in workflow, (
    'repair mode must explicitly classify the historical emergency release'
)
assert "signing_mode=\"$(tr -d '[:space:]' < signing-mode.txt 2>/dev/null || true)\"" in workflow, (
    'repair mode must inspect the published emergency signing mode before backfilling a manifest'
)

assert 'push:\n    branches:\n      - main' in normal_ci, (
    'Android CI must also run on pushes to main so a CI/debug APK is produced independently of stable release signing'
)
assert ':app-dpc:assembleEnterpriseDebug' in normal_ci, 'main CI must build enterpriseDebug'
assert 'Package APK artifact' in normal_ci, 'main CI must package the CI/debug APK'
assert 'DPC-AIO-ci-debug-${{ github.run_number }}' in normal_ci, 'main CI must clearly label the versioned artifact as CI/debug'
assert 'CI-DEBUG-NOT-FOR-SELF-UPDATE.txt' in normal_ci, 'CI/debug artifact must carry an explicit self-update continuity warning'
assert 'DPC-AIO-enterprise-debug-${{ github.run_number }}' not in normal_ci, 'ephemeral CI/debug artifact must not look like the updateable normal release channel'
assert 'actions/upload-artifact@' in normal_ci, 'main CI must upload the APK artifact'
assert 'DPC_AIO_RELEASE_KEYSTORE_B64' not in normal_ci, 'CI/debug artifact must not require stable release signing secrets'

print('MAIN_STABLE_SIGNING_POLICY_CONTRACT: PASS')
