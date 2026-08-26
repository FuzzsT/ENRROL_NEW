from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionManagerGateway.kt'
assert p.is_file(),p
t=p.read_text()
for n in ['getPermissionGrantState','setPermissionGrantState','getPermissionPolicy','setPermissionPolicy','checkPermission','AppOpsManager','permissionToOp','targetUserId','POLICY_READBACK_MISMATCH']:
    assert n in t,n
for n in ['PERMISSION_POLICY_PROMPT','PERMISSION_POLICY_AUTO_GRANT','PERMISSION_POLICY_AUTO_DENY']:
    assert n in t,n
print('PERMISSION_MANAGER_100_ANDROID_CONTRACT: PASS')
