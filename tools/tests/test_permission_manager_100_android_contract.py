from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionManagerGateway.kt'
auto=ROOT/'apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionGrantCoordinator.kt'
client=ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt'
assert p.is_file(),p
assert auto.is_file(),auto
assert client.is_file(),client
t=p.read_text(); at=auto.read_text(); ct=client.read_text()
for n in ['getPermissionGrantState','setPermissionGrantState','getPermissionPolicy','setPermissionPolicy','checkPermission','AppOpsManager','permissionToOp','targetUserId','POLICY_READBACK_MISMATCH']:
    assert n in t,n
for n in ['PERMISSION_POLICY_PROMPT','PERMISSION_POLICY_AUTO_GRANT','PERMISSION_POLICY_AUTO_DENY']:
    assert n in t,n
for n in ['fun awaitConnected(timeoutMs: Long','SystemClock.elapsedRealtime()','Thread.sleep(','bind()','isConnected()']:
    assert n in ct,f'Shizuku readiness wait missing {n}'
for n in ['val shizukuReady = shizuku?.awaitConnected(','shizukuAvailable = shizukuReady']:
    assert n in at,f'AUTO permission readiness missing {n}'
for n in [
    'private fun isAlreadyGranted(',
    'AndroidUserId.fromUid(Process.myUid())',
    'shizuku?.getRuntimePermissionState(packageName, entry.name, userId)',
    'shizuku?.getAppOp(packageName, pair.commandName, userId)',
    'AppOpReadbackParser.parse(',
    'observed=GRANTED',
    'effectiveMode',
    'user=$userId',
]:
    assert n in at,f'target-user AUTO permission readback missing {n}'
assert 'val already = pm.checkPermission(entry.name, packageName) == PackageManager.PERMISSION_GRANTED' not in at,'AUTO planner must not use current-user PackageManager as target-user alreadyGranted state'
assert 'exit == 0 && isGranted(packageName, entry.name)' not in at,'Shizuku runtime grant must not verify through current-user PackageManager'
assert 'exit == 0 && verifyAppOp(packageName, pair.opStr)' not in at,'Shizuku AppOp grant must not verify through current-user uid/AppOpsManager'
assert 'shizuku?.identity() != null' not in at,'AUTO permissions must not race on immediate identity() readback'
print('PERMISSION_MANAGER_100_ANDROID_CONTRACT: PASS')
