from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
aidl=(ROOT/'apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl').read_text()
service=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt').read_text()
client=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt').read_text()
execp=ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuComponentStateExecutor.kt'
assert execp.is_file(), execp
t=execp.read_text()
assert 'setComponentEnabledState' in aidl
assert '"enable", "--user"' in service
assert '"disable", "--user"' in service
assert '"default-state", "--user"' in service
assert 'setComponentEnabledState' in client and 'setComponentEnabledState' in t
for forbidden in ['force', 'su -c', 'sh -c']:
    assert forbidden not in service.lower(), forbidden
print('COMPONENT_MANAGER_SHIZUKU_CONTRACT: PASS')
