#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"

workflow = WORKFLOW.read_text(encoding="utf-8")

required = [
    "HAS_RELEASE_KEYSTORE",
    "HAS_STORE_PASSWORD",
    "HAS_KEY_ALIAS",
    "HAS_KEY_PASSWORD",
    "HAS_EXPECTED_CERT",
    "Stable signer not configured; automatic self-update publish disabled.",
    "Missing stable signing configuration:",
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
    "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
]

for token in required:
    assert token in workflow, f"stable signing diagnostic contract missing: {token}"

for forbidden in [
    'echo "$DPC_AIO_RELEASE_KEYSTORE_B64"',
    'echo "$DPC_AIO_RELEASE_STORE_PASSWORD"',
    'echo "$DPC_AIO_RELEASE_KEY_PASSWORD"',
]:
    assert forbidden not in workflow, f"stable signing diagnostics must never print secret values: {forbidden}"

print("STABLE_SIGNING_DIAGNOSTICS: PASS")
