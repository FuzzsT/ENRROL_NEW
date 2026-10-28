from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

system_start = text.index('private fun scanSystemConfigs()')
shizuku_start = text.index('private fun scanShizuku()', system_start)
system_body = text[system_start:shizuku_start]
shizuku_body = text[shizuku_start:]

assert 'Thread {' in system_body, 'System config Shizuku reads must run off the UI thread'
assert 'val permissions = shizuku.listPermissionConfigFiles()' in system_body
assert 'val sysconfig = shizuku.listSysconfigFiles()' in system_body
assert system_body.index('val permissions = shizuku.listPermissionConfigFiles()') < system_body.index('postUiIfAlive {'), \
    'System config Binder reads must complete before posting the result to UI'

assert 'Thread {' in shizuku_body, 'Permission-list Shizuku read must run off the UI thread'
assert 'val permissions = shizuku.listPermissions()' in shizuku_body
assert shizuku_body.index('val permissions = shizuku.listPermissions()') < shizuku_body.index('postUiIfAlive {'), \
    'Permission-list Binder read must complete before posting the result to UI'

print('PERMISSION_MANAGER_SHIZUKU_ASYNC_CONTRACT: PASS')
