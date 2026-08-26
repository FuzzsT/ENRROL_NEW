from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
g=(ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt').read_text()
c=(ROOT/'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt').read_text()
comp=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt').read_text()
manifest=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
for n in ['setCrossProfilePackages','getCrossProfilePackages','setManagedProfileMaximumTimeOff','getManagedProfileMaximumTimeOff','setPersonalAppsSuspended','setOrganizationId','setOrganizationName','setAffiliationIds','getAffiliationIds']:
    assert n in g, f'missing COPE API {n}'
for n in ['getCopePolicySnapshot','setCrossProfilePackagesPolicy','setManagedProfileMaximumTimeOffPolicy','setPersonalAppsSuspendedPolicy','setOrganizationIdentity','setAffiliationIdsPolicy']:
    assert n in c, f'missing COPE gateway {n}'
assert 'android.app.action.CHECK_POLICY_COMPLIANCE' in manifest
assert 'android.app.action.ADMIN_POLICY_COMPLIANCE' in manifest
assert 'RESULT_OK' in comp
for marker in ['PackagePolicy', 'setManagedProfileContactsAccessPolicy', 'setManagedProfileCallerIdAccessPolicy', 'PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM']:
    assert marker in g, marker
print('test_cope_android_contract: PASS')
