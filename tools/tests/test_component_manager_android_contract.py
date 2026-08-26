from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidComponentStateGateway.kt'
i=ROOT/'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt'
assert p.is_file(),p
t=p.read_text(); inv=i.read_text()
for n in ['getComponentEnabledSetting','setComponentEnabledSetting','setComponentEnabledSettings','ComponentEnabledSetting','COMPONENT_STATE_MISMATCH']:
    assert n in t,n
for n in ['manifestEnabled','overrideState','effectiveEnabled']:
    assert n in inv,n
for forbidden in ['DevicePolicyManager','enableForeignActivity','disableForeignActivity']:
    assert forbidden not in t,forbidden
print('COMPONENT_MANAGER_ANDROID_CONTRACT: PASS')
