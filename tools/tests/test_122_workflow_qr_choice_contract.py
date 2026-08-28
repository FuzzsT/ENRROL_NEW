#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
main = (ROOT / ".github/workflows/build-aio-enrollment.yml").read_text("utf-8")
emergency = (ROOT / ".github/workflows/build-emergency-enrollment.yml").read_text("utf-8")
gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text("utf-8")

for token in [
    "qr_type:",
    "type: choice",
    'default: "both"',
    "- both",
    "- work-profile",
    "- fully-managed",
    "DPC_AIO_QR_TYPE",
    "release_signing_password:",
]:
    assert token in main, token

assert main.count("release_signing_password:") == 1
assert "qr_type:" in emergency
assert "release_signing_password:" not in emergency
assert "DPC_AIO_QR_TYPE" in emergency

for token in [
    'providers.environmentVariable("DPC_AIO_QR_TYPE")',
    '"both"',
    '"work-profile"',
    '"fully-managed"',
]:
    assert token in gradle, token

# Selection must control generation and validation, not only naming.
assert "selectedQrModes" in gradle
assert "DPC_AIO_QR_TYPE" in main
assert "DPC_AIO_QR_TYPE" in emergency
assert "work-profile-validation.json" in main
assert "device-owner-validation.json" in main

print("PASS: workflow QR choice contract")
