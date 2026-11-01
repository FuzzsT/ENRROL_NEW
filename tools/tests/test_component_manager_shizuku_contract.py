from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
aidl=(ROOT/'apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl').read_text()
service=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt').read_text()
client=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt').read_text()
execp=ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuComponentStateExecutor.kt'
assert execp.is_file(), execp
t=execp.read_text()
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
print('COMPONENT_MANAGER_SHIZUKU_CONTRACT: PASS')
