from pathlib import Path
root=Path(__file__).resolve().parents[2]
snapshot_path=root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsSnapshot.kt'
activity_path=root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsActivity.kt'
manifest=(root/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
assert snapshot_path.is_file(), 'snapshot missing'
assert activity_path.is_file(), 'activity missing'
s=snapshot_path.read_text(); a=activity_path.read_text()
for token in ['apiLevel','deviceOwner','profileOwner','organizationOwnedProfile','moduleCounts','shizukuBinderAlive','knoxLicenseActive']:
    assert token in s, token
for token in ['ACTION_CREATE_DOCUMENT','application/json','dpc-diagnostics.json','openOutputStream']:
    assert token in a, token
for forbidden in ['dpc-aio-lab-private.pem','dpc-aio-lab-klm.token','enrollment_token','getSharedPreferences("com.','/data/data/']:
    assert forbidden not in s+a, f'forbidden diagnostics source marker: {forbidden}'
assert '.DpcDiagnosticsActivity' in manifest
print('test_diagnostics_contract: PASS')
