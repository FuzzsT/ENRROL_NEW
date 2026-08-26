from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
whole=ROOT/'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidWholeAppStateGateway.kt'
restr=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidApplicationRestrictionsCoordinator.kt'
loc=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidGlobalLocationPolicyGateway.kt'
assert whole.exists() and restr.exists() and loc.exists(), '1.1.0 app-management gateways missing'
wt=whole.read_text('utf-8'); rt=restr.read_text('utf-8'); lt=loc.read_text('utf-8')
for needle in ['WholeAppStatePlanner','setApplicationEnabledSetting','setPackageEnabled','protectionDecision','readback']:
    assert needle in wt, needle
for needle in ['setApplicationRestrictions','getApplicationRestrictions','Executors.newSingleThreadExecutor','canonical']:
    assert needle in rt, needle
for needle in ['isDeviceOwnerApp','setLocationEnabled','LocationManager','isLocationEnabled']:
    assert needle in lt, needle
assert 'Settings.Secure' not in lt
print('test_app_management_location_110_contract: PASS')
