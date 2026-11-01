from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidComponentStateGateway.kt'
i=ROOT/'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt'
assert p.is_file(),p
t=p.read_text(); inv=i.read_text()
for n in ['getComponentEnabledSetting','setComponentEnabledSetting','setComponentEnabledSettings','ComponentEnabledSetting','COMPONENT_STATE_MISMATCH']:
    assert n in t,n
for n in [
    'PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> ComponentOverrideState.DISABLED',
    'PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> ComponentOverrideState.DISABLED',
]:
    assert n in t, f'component disabled readback mapping missing {n}'
for n in ['manifestEnabled','overrideState','effectiveEnabled']:
    assert n in inv,n
for n in [
    'val currentUser = UserHandle.getUserHandleForUid(Process.myUid())',
    'val sameUid = user == currentUser && packageUid != null && packageUid == Process.myUid()',
    'sameUid = user == currentUser && launcherInfo.applicationInfo.uid == Process.myUid()',
]:
    assert n in inv, f'cross-user sameUid guard missing {n}'
for n in [
    'private fun targetPackageAvailable(packageName: String, user: UserHandle, currentUser: UserHandle): Boolean',
    'if (user == currentUser) return true',
    'if (user !in launcherApps.profiles) return false',
    'launcherApps.getApplicationInfo(packageName, 0, user)',
    'if (!targetPackageAvailable(packageName, user, currentUser)) return emptyList()',
]:
    assert n in inv, f'target-user package availability guard missing {n}'
for forbidden in ['DevicePolicyManager','enableForeignActivity','disableForeignActivity']:
    assert forbidden not in t,forbidden
print('COMPONENT_MANAGER_ANDROID_CONTRACT: PASS')