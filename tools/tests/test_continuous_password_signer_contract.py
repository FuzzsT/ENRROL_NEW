from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"
SIGNER = ROOT / "tools/release/prepare_enterprise_signing.sh"

workflow = WORKFLOW.read_text(encoding="utf-8")

for token in [
    "secrets.DPC_AIO_LEGACY_SIGNING_PASSWORD",
    "DPC_AIO_MANUAL_SIGNING_PASSWORD",
    "DPC_AIO_PERSISTED_SIGNING_KEYSTORE_URL",
    "tools/release/prepare_enterprise_signing.sh",
    "password-release-keystore",
]:
    assert token in workflow, f"continuous password-backed signer contract missing: {token}"

assert SIGNER.exists(), "password-backed signer helper must exist"

signer = SIGNER.read_text(encoding="utf-8")
for token in [
    "PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH",
    "DPC-AIO-signing-keystore.enc",
    "openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000",
    "append_output mode \"password-release-keystore\"",
    "DPC_AIO_REQUIRE_PERSISTED_SIGNER",
    "PERSISTED_SIGNING_KEY_REQUIRED",
]:
    assert token in signer, f"signer persistence contract missing: {token}"

assert "github.event_name == 'workflow_dispatch'" in workflow
assert "needs.build.outputs.signing_mode" in workflow
assert "password-release-keystore" in workflow

assert "inputs.release_signing_password" not in workflow
assert "      release_signing_password:" not in workflow

print("CONTINUOUS_PASSWORD_SIGNER_CONTRACT: PASS")
