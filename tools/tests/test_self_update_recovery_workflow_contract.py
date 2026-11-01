#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"
RECOVERY = ROOT / "tools/release/recover_persisted_signing_keystore.py"

workflow = WORKFLOW.read_text(encoding="utf-8")
recovery = RECOVERY.read_text(encoding="utf-8")

for token in [
    "RECOVERY_SIGNING_CONFIGURED",
    "DPC_AIO_RECOVERY_PASSWORD",
    "Prepare recovered stable signer",
    "tools/release/recover_persisted_signing_keystore.py",
    "dpc-aio-recovered-stable-signer",
    "historicalApkSignerVerified",
    "DPC_AIO_SIGNING_MODE=recovered-stable",
]:
    assert token in workflow, f"persisted signer recovery workflow missing: {token}"

assert '"$STABLE_SIGNING_CONFIGURED" == "true" || "$RECOVERY_SIGNING_CONFIGURED" == "true"' in workflow, (
    "main auto mode must allow verified persisted-signer recovery when full stable secrets are absent"
)
assert "if: env.DPC_AIO_PIPELINE_MODE == 'stable' && env.STABLE_SIGNING_CONFIGURED == 'true'" in workflow, (
    "configured stable signer step must not run for the recovery-only path"
)
assert "if: env.DPC_AIO_PIPELINE_MODE == 'stable' && env.STABLE_SIGNING_CONFIGURED != 'true' && env.RECOVERY_SIGNING_CONFIGURED == 'true'" in workflow, (
    "recovered signer step must be isolated to stable mode with recovery password available"
)

for token in [
    "HISTORICAL_APK_SIGNER_MISMATCH",
    "PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH",
    '"historicalApkSignerVerified": True',
    "DPC_AIO_RECOVERY_PASSWORD",
]:
    assert token in recovery, f"recovery helper safety contract missing: {token}"

print("SELF_UPDATE_RECOVERY_WORKFLOW_CONTRACT: PASS")
