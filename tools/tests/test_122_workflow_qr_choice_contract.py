#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
main = (ROOT / ".github/workflows/build-aio-enrollment.yml").read_text("utf-8")
emergency = (ROOT / ".github/workflows/build-emergency-enrollment.yml").read_text("utf-8")
workflow_dir = ROOT / ".github/workflows"
gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text("utf-8")

for token in [
    "qr_type:",
    'description: "QR code: both = QR1 Work Profile + QR2 Fully Managed; work-profile = QR1 only; fully-managed = QR2 only"',
    'type: choice', 'default: "both"', '- both', '- work-profile', '- fully-managed',
    'DPC_AIO_QR_TYPE', 'release_signing_password:',
]:
    assert token in main, token

assert main.count("release_signing_password:") == 1
assert "qr_type:" in emergency
assert "release_signing_password:" not in emergency
assert "DPC_AIO_QR_TYPE" in emergency
assert sorted(p.name for p in workflow_dir.glob("*.yml")) == [
    "build-aio-enrollment.yml", "build-emergency-enrollment.yml",
]

for text in (main, emergency):
    assert text.count("    inputs:\n      qr_type:\n") == 1
    assert "id: qr" in text
    assert 'echo "qr_type=$qr_type" >> "$GITHUB_OUTPUT"' in text
    assert 'qr_type: ${{ steps.qr.outputs.qr_type }}' in text
    assert 'DPC_AIO_QR_TYPE: ${{ needs.build.outputs.qr_type }}' in text

for token in ['providers.environmentVariable("DPC_AIO_QR_TYPE")', '"both"', '"work-profile"', '"fully-managed"']:
    assert token in gradle, token
assert 'selectedQrModes' in gradle
assert 'work-profile-validation.json' in main
assert 'device-owner-validation.json' in main
expected_wp = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME"'
assert expected_wp in main
assert 'dist//work-profile-provisioning.json' not in main
print("PASS: workflow QR choice contract")
