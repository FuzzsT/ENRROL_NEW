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

assert 'DPC_AIO_MANUAL_SIGNING_PASSWORD: ${{ secrets.DPC_AIO_LEGACY_SIGNING_PASSWORD }}' in workflow, (
    'stable signer must accept the persisted legacy password from Actions secrets for unattended main builds'
)
assert 'DPC_AIO_REQUIRE_PERSISTED_SIGNER: "true"' in workflow, (
    'manual and automated password-backed builds must never create/rotate an existing release signing key when persisted ciphertext is unavailable'
)
assert '::warning title=CONTINUOUS_BLOCKED::' in workflow
assert '::error title=CONTINUOUS_BLOCKED::' in workflow
assert workflow.count('>> "$GITHUB_STEP_SUMMARY"') >= 2
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

signer = (ROOT / 'tools/release/prepare_enterprise_signing.sh').read_text(encoding='utf-8')
for marker in [
    'PERSISTED_SIGNING_ASSET_FORMAT_INVALID',
    'PERSISTED_SIGNING_DECRYPT_TOOL_FAILED',
    'PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH',
    '-md sha256',
    '53616c7465645f5f',
]:
    assert marker in signer, f'persisted signer diagnostic missing: {marker}'

assert 'inputs.release_signing_password' not in workflow
assert '      release_signing_password:' not in workflow
assert 'Mask manual release password' not in workflow
assert 'SIGNING_CONFIG_CONFLICT: incomplete stable signer configuration' in workflow
assert 'HAS_MANUAL_SIGNING_PASSWORD' not in workflow

print('MAIN_STABLE_SIGNING_POLICY_CONTRACT: PASS')

# Execute the real resolver shell block with synthetic presence flags only.
# No production passwords, keystores, network calls or publication.
import os
import subprocess
import tempfile
import textwrap

def resolve_mode(*, requested="auto", event="workflow_dispatch", ref="refs/heads/main",
                 stable=False, legacy=True, v2=False, v2_partial=False, publish=False):
    block = workflow.split("      - name: Resolve pipeline mode", 1)[1].split(
        "      - name: Set up Java 21", 1)[0]
    script = textwrap.dedent(block.split("        run: |\n", 1)[1])
    script = script.replace("${{ inputs.signing_mode || 'auto' }}", requested)
    script = script.replace("${{ github.event_name }}", event)
    script = script.replace("${{ inputs.apk_url }}", "")
    assert "${{" not in script, "unresolved workflow expression"
    with tempfile.TemporaryDirectory() as directory:
        base = Path(directory)
        env = dict(os.environ)
        env.update({
            "GITHUB_EVENT_NAME": event, "GITHUB_REF": ref,
            "GITHUB_REF_TYPE": "tag" if ref.startswith("refs/tags/") else "branch",
            "GITHUB_REF_NAME": ref.rsplit("/", 1)[-1],
            "GITHUB_REPOSITORY": "FuzzsT/ENRROL_NEW",
            "RUNNER_TEMP": directory,
            "GITHUB_OUTPUT": str(base / "out"), "GITHUB_ENV": str(base / "env"),
            "GITHUB_STEP_SUMMARY": str(base / "summary"),
            "STABLE_SIGNING_CONFIGURED": str(stable).lower(),
            "HAS_LEGACY_SIGNING_PASSWORD": str(legacy).lower(),
            "V2_SIGNING_CONFIGURED": str(v2).lower(),
            "HAS_V2_SIGNING_CONFIG": str(v2 or v2_partial).lower(),
            "HAS_V2_CUSTOM_APK_URL": "false", "HAS_V2_REPAIR_REQUEST": "false",
            "V2_PUBLISH_REQUESTED": str(publish).lower(), "V2_FIRST_RELEASE_REQUESTED": "false",
        })
        for name in ("HAS_RELEASE_KEYSTORE", "HAS_RELEASE_STORE_PASSWORD",
                     "HAS_RELEASE_KEY_ALIAS", "HAS_RELEASE_KEY_PASSWORD", "HAS_EXPECTED_SIGNING_CERT"):
            env[name] = str(stable).lower()
        result = subprocess.run(["bash", "-c", script], env=env,
                                text=True, capture_output=True, timeout=10)
        outputs = dict(line.split("=", 1) for line in (base / "out").read_text().splitlines()) if (base / "out").exists() else {}
        return result, outputs

def expect_route(expected, tag, publish, **kwargs):
    result, out = resolve_mode(**kwargs)
    assert result.returncode == 0, result.stdout + result.stderr
    assert out["mode"] == expected, out
    assert out["release_tag"] == tag, out
    assert out["should_publish"] == str(publish).lower(), out

expect_route("continuous-v2", "dpc-aio-continuous-v2", False, v2=True)
expect_route("stable", "dpc-aio-continuous", True, stable=True, v2=True)
expect_route("stable", "dpc-aio-continuous", True, v2=False)
expect_route("continuous-v2", "dpc-aio-continuous-v2", False, event="push", v2=True)
expect_route("continuous-v2", "dpc-aio-continuous-v2", False, event="push", legacy=False, v2=True)
expect_route("emergency", "dpc-aio-emergency-enrollment", True, event="push", legacy=False)
expect_route("stable", "dpc-aio-continuous", True, event="push", stable=True, v2=True)
expect_route("stable", "dpc-aio-continuous", True, requested="stable", v2=True)
expect_route("continuous-v2", "dpc-aio-continuous-v2", False, requested="continuous-v2", v2=True)
expect_route("stable", "v1.4.0", True, event="push", ref="refs/tags/v1.4.0", v2=True)
result, _ = resolve_mode(v2_partial=True)
assert result.returncode != 0 and "AUTO_CONTINUOUS_V2_CONFIG_INCOMPLETE" in result.stderr
result, _ = resolve_mode(v2=True, publish=True)
assert result.returncode != 0 and "CONTINUOUS_V2_ROUTE_INVALID" in result.stderr
result, _ = resolve_mode(event="push", v2_partial=True)
assert result.returncode != 0 and "AUTO_CONTINUOUS_V2_CONFIG_INCOMPLETE" in result.stderr
print("AUTO_SIGNING_ROUTE_MATRIX: PASS")

# Exercise V2 alias selection using a synthetic PKCS12; never use release credentials.
import hashlib
with tempfile.TemporaryDirectory() as directory:
    fixture = Path(directory) / "fixture.p12"
    fixture_env = dict(os.environ, DPC_AIO_RELEASE_STORE_PASSWORD="fixture-only-password-123",
                       DPC_AIO_RELEASE_KEY_PASSWORD="fixture-only-password-123",
                       DPC_AIO_RELEASE_KEY_ALIAS="requested-windows-alias")
    subprocess.run(["keytool", "-genkeypair", "-alias", "actual-windows-alias", "-keyalg", "RSA",
                    "-keysize", "2048", "-storetype", "PKCS12", "-keystore", str(fixture),
                    "-storepass:env", "DPC_AIO_RELEASE_STORE_PASSWORD", "-dname", "CN=Fixture", "-validity", "1"],
                   env=fixture_env, check=True, capture_output=True, timeout=30)
    cert = subprocess.check_output(["keytool", "-exportcert", "-alias", "actual-windows-alias",
                                    "-keystore", str(fixture), "-storepass:env", "DPC_AIO_RELEASE_STORE_PASSWORD"],
                                   env=fixture_env, timeout=30)
    fixture_env["DPC_AIO_CONFIGURED_SIGNING_CERT_SHA256"] = hashlib.sha256(cert).hexdigest().upper()
    helper = ROOT / "tools/release/ResolveV2SigningAlias.java"
    for changes, expected in [
        ({}, "actual-windows-alias"),
        ({"DPC_AIO_RELEASE_KEY_ALIAS": "actual-windows-alias"}, "actual-windows-alias"),
        ({"DPC_AIO_CONFIGURED_SIGNING_CERT_SHA256": "0" * 64}, "V2_CERT_MATCH_NOT_UNIQUE"),
        ({"DPC_AIO_RELEASE_STORE_PASSWORD": "wrong"}, "V2_KEYSTORE_LOAD_FAILED"),
        ({"DPC_AIO_RELEASE_KEY_PASSWORD": "wrong"}, "V2_PRIVATE_KEY_UNAVAILABLE"),
    ]:
        result = subprocess.run(["java", str(helper), str(fixture)], env=dict(fixture_env, **changes),
                                text=True, capture_output=True, timeout=30)
        if expected == "actual-windows-alias":
            assert result.returncode == 0 and result.stdout.strip() == expected, result.stderr
        else:
            assert result.returncode != 0 and expected in result.stderr, result.stderr
    assert 'ResolveV2SigningAlias.java' in signer
    assert 'export DPC_AIO_RELEASE_KEY_ALIAS="$resolved_alias"' in signer
print("V2_PINNED_ALIAS_MATRIX: PASS")
