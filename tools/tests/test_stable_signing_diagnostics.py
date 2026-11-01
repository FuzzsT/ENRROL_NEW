#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"
SETUP_TOOL = ROOT / "tools/release/stable_signing_setup.py"
RECOVERY_TOOL = ROOT / "tools/release/recover_stable_signing.py"

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
    "verify_private_key_password",
    "-storepass:env",
    "-srcstorepass:env",
    "-srckeypass:env",
    "TemporaryDirectory",
]:
    assert token in setup_source, f"stable signing setup tool missing contract token: {token}"

for forbidden in [
    "print(store_password)",
    "print(key_password)",
    "print(keystore_b64)",
    '"-storepass", store_password',
    '"-keypass", key_password',
    '"-srcstorepass", store_password',
    '"-srckeypass", key_password',
]:
    assert forbidden not in setup_source, f"stable signing setup tool must not expose secret values via output/argv: {forbidden}"

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

assert RECOVERY_TOOL.is_file(), "persisted stable signer recovery tool is missing"
recovery_source = RECOVERY_TOOL.read_text(encoding="utf-8")
for token in [
    "DPC-AIO-signing-keystore.enc",
    "dpc-aio-continuous",
    "aes-256-cbc",
    "pbkdf2",
    "600000",
    "DPC_AIO_LEGACY_SIGNING_PASSWORD",
    "getpass",
    "stable_signing_setup.py",
    "--apply",
    "TemporaryDirectory",
]:
    assert token in recovery_source, f"stable signer recovery tool missing contract token: {token}"

for forbidden in [
    "print(password)",
    "--pass", "password=" + "{password}",
]:
    assert forbidden not in recovery_source, f"stable signer recovery tool must not expose passwords: {forbidden}"

recovery_help = subprocess.run(
    [sys.executable, str(RECOVERY_TOOL), "--help"],
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
assert recovery_help.returncode == 0, recovery_help.stdout
for token in ["--encrypted", "--alias", "--repo", "--apply"]:
    assert token in recovery_help.stdout, f"recovery help missing: {token}"

print("STABLE_SIGNING_DIAGNOSTICS: PASS")
