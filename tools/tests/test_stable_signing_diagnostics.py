#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"
SETUP_TOOL = ROOT / "tools/release/stable_signing_setup.py"

workflow = WORKFLOW.read_text(encoding="utf-8")

required = [
    "STABLE_SIGNING_CONFIGURED",
    "HAS_RELEASE_KEYSTORE",
    "HAS_RELEASE_STORE_PASSWORD",
    "HAS_RELEASE_KEY_ALIAS",
    "HAS_RELEASE_KEY_PASSWORD",
    "HAS_EXPECTED_SIGNING_CERT",
    "Stable signing unavailable; missing configuration:",
    "Prepare stable signer",
    "STABLE_SIGNING_KEY_REQUIRED: $name is not configured",
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
    "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
]

for token in required:
    assert token in workflow, f"stable signing diagnostic contract missing: {token}"

assert 'if [[ "$GITHUB_REF" == "refs/heads/main" && "$STABLE_SIGNING_CONFIGURED" == "true" ]]; then' in workflow, (
    "automatic stable publishing must remain gated by complete stable-signing readiness"
)

for forbidden in [
    'echo "$KEYSTORE_B64"',
    'echo "$STORE_PASSWORD"',
    'echo "$KEY_PASSWORD"',
    'echo "$DPC_AIO_RELEASE_KEYSTORE_B64"',
    'echo "$DPC_AIO_RELEASE_STORE_PASSWORD"',
    'echo "$DPC_AIO_RELEASE_KEY_PASSWORD"',
]:
    assert forbidden not in workflow, f"stable signing diagnostics must never print secret values: {forbidden}"

assert SETUP_TOOL.is_file(), "stable signing setup/audit tool is missing"
setup_source = SETUP_TOOL.read_text(encoding="utf-8")
for token in [
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
    "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
    "keytool",
    "gh",
    "secret",
    "variable",
    "getpass",
    "--apply",
]:
    assert token in setup_source, f"stable signing setup tool missing contract token: {token}"

for forbidden in [
    "print(store_password)",
    "print(key_password)",
    "print(keystore_b64)",
]:
    assert forbidden not in setup_source, f"stable signing setup tool must not print secret values: {forbidden}"

help_proc = subprocess.run(
    [sys.executable, str(SETUP_TOOL), "--help"],
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
assert help_proc.returncode == 0, help_proc.stdout
assert "--keystore" in help_proc.stdout
assert "--apply" in help_proc.stdout
assert "--repo" in help_proc.stdout

print("STABLE_SIGNING_DIAGNOSTICS: PASS")
