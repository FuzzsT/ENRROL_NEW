from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
aidl=(ROOT/'apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl').read_text()
service=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt').read_text()
client=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt').read_text()
execp=ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuComponentStateExecutor.kt'
autostart=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt'
assert execp.is_file(), execp
assert autostart.is_file(), autostart
t=execp.read_text()
aut=autostart.read_text(encoding='utf-8')
assert 'setComponentEnabledState' in aidl
assert 'String setComponentEnabledStateVerified(String packageName, String className, String state, int userId);' in aidl
assert '"enable", "--user"' in service
assert '"disable", "--user"' in service
assert '"default-state", "--user"' in service
for token in [
    'override fun setComponentEnabledStateVerified(',
    'runTypedCaptureChecked(',
]:
    assert token in service, token
for token in [
    '.version(5)',
    'fun setComponentEnabledStateVerified(',
    'connectedRemote()?.setComponentEnabledStateVerified(',
]:
    assert token in client, token
for token in [
    'val observedState: ComponentOverrideState?',
    'val verified: Boolean',
    'client.setComponentEnabledStateVerified(',
    'new state:',
    'ComponentOverrideState.ENABLED',
    'ComponentOverrideState.DISABLED',
    'ComponentOverrideState.DEFAULT',
    'VERIFIED:',
]:
    assert token in t, token
assert 'client.setComponentEnabledState(packageName, className, state.name, userId)' not in t, 'executor must use verified target-user mutation output'
for forbidden in ['force', 'su -c', 'sh -c']:
    assert forbidden not in service.lower(), forbidden

for token in [
    'queryCurrentUserBootReceivers(selectedUser)',
    'private fun queryCurrentUserBootReceivers(userId: Int): ReceiverInventory',
    'queryCrossUserBootReceivers(userId)',
    'CURRENT_USER_SHIZUKU_AUGMENTED',
    'local.associateBy { it.component.flattenToString() }',
    'privileged.rows.filterNot { it.component.flattenToString() in localByComponent }',
    'sortRows(local + shizukuOnly)',
]:
    assert token in aut, f'current-user Shizuku inventory augmentation missing {token}'

for token in [
    'if (readback != null)',
    'CURRENT_USER_SHIZUKU_STATE user=$selectedUser',
    'lastVerified=${submitted?.name ?: "none"}',
    'Manifest=unknown',
    'liveReadback=mutation-only',
]:
    assert token in aut, f'current-user Shizuku verified state display missing {token}'

assert 'else queryLocalBootReceivers()' not in aut, 'cross-user scan must not silently fall back to current-user inventory'
print('COMPONENT_MANAGER_SHIZUKU_CONTRACT: PASS')
