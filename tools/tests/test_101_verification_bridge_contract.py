#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
manifest=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
receiver=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/VerificationCommandReceiver.kt'
toggle=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/VerificationToggleActivity.kt'
assert receiver.is_file() and toggle.is_file(), 'verification bridge files missing'
text=receiver.read_text()
for token in ('android.permission.DUMP','ACTION_VERIFY_PERMISSION','ACTION_VERIFY_COMPONENT','AndroidPermissionManagerGateway','AndroidComponentStateGateway','setResultData'):
    assert token in text or token in manifest, token
assert '.VerificationCommandReceiver' in manifest
assert 'android:permission="android.permission.DUMP"' in manifest
assert 'android:exported="true"' in manifest
assert '.VerificationToggleActivity' in manifest
# Bridge is narrow: no wipe/reset/owner-removal verbs.
for forbidden in ('wipeData(', 'factoryReset', 'removeActiveAdmin', 'clearDeviceOwnerApp'):
    assert forbidden not in text, forbidden
print('test_101_verification_bridge_contract: PASS')
