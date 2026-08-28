#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text(encoding='utf-8')
gradle = (ROOT / 'apps/dpc/app/build.gradle.kts').read_text(encoding='utf-8')
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
dashboard = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text(encoding='utf-8')
pin_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcPinManager.kt').read_text(encoding='utf-8')
pin_settings = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcPinSettingsActivity.kt').read_text(encoding='utf-8')

# Explicit Android Enterprise modes are selected by DPC_AIO_QR_TYPE; both remains the default compatibility behavior.
for token in [
    'providers.environmentVariable("DPC_AIO_QR_TYPE")',
    '"both" -> linkedSetOf("work-profile", "fully-managed")',
    'publishExplicitMode("work-profile"',
    'publishExplicitMode("fully-managed"',
    'work-profile-qr.png',
    'device-owner-qr.png',
]:
    assert token in gradle, token
for token in [
    '--expected-mode work-profile',
    '--expected-mode fully-managed',
    'work-profile-validation.json',
    'device-owner-validation.json',
]:
    assert token in workflow, token

# App PIN exists only on the human-facing launcher UI; system provisioning callbacks stay ungated.
assert 'App PIN / Security' in dashboard
assert 'DpcPinManager.isEnabled' in dashboard
assert 'PBKDF2WithHmacSHA256' in pin_manager
assert 'MessageDigest.isEqual' in pin_manager
assert 'MAX_FAILURES_BEFORE_DELAY = 5' in pin_manager
assert 'DpcPinSettingsActivity' in manifest
assert 'ProvisioningModeActivity' in manifest
assert 'PolicyComplianceActivity' in manifest
assert 'DpcPinManager' not in (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeActivity.kt').read_text(encoding='utf-8')
assert 'DpcPinManager' not in (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt').read_text(encoding='utf-8')
assert 'Set PIN' in pin_settings and 'Disable PIN' in pin_settings and 'Remove PIN' in pin_settings

print('PASS: selectable work-profile + fully-managed QR generation and app PIN UI contract')
