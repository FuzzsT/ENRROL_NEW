from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
aidl=(ROOT/'apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl').read_text()
service=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt').read_text()
client=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt').read_text()
assert 'revokeRuntimePermission' in aidl
assert '"revoke", "--user"' in service
assert 'revokeRuntimePermission' in client
print('SHIZUKU_PERMISSION_MUTATION_CONTRACT: PASS')
