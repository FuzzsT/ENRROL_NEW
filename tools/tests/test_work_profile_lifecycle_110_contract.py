from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidWorkProfileLifecycleGateway.kt'
assert p.exists(), 'gateway missing'
t=p.read_text('utf-8')
for needle in ['setProfileName','userManager.userName','addCrossProfileIntentFilter','clearCrossProfileIntentFilters','FLAG_MANAGED_CAN_ACCESS_PARENT','FLAG_PARENT_CAN_ACCESS_MANAGED','DesiredCrossProfileInventory','isQuietModeEnabled']:
    assert needle in t, needle
for forbidden in ['Runtime.getRuntime().exec','ProcessBuilder','Settings.Secure','dpm.getProfileName','getProfileName(admin)']:
    assert forbidden not in t, forbidden
print('test_work_profile_lifecycle_110_contract: PASS')
