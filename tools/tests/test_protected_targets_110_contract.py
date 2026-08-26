from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

component = (ROOT / 'apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ComponentControl.kt').read_text('utf-8')
permission = (ROOT / 'apps/dpc/modules/permissions/core/src/main/kotlin/io/dpcaio/permission/PermissionExecutionRouter.kt').read_text('utf-8')
app_policy = (ROOT / 'apps/dpc/modules/app-management/core/src/main/kotlin/io/dpcaio/appmanager/AppPolicyCoordinator.kt').read_text('utf-8')

for name, text in [('component', component), ('permission', permission), ('app_policy', app_policy)]:
    assert 'ProtectionPlanner' in text, f'{name} does not use common ProtectionPlanner'
    assert 'ProtectionRequest' in text, f'{name} does not construct a ProtectionRequest'
    assert 'protectionDecision' in text, f'{name} result does not expose protectionDecision'

assert 'Mutation.DISABLE' in component
assert 'Mutation.REVOKE_PERMISSION' in permission
assert 'Mutation.HIDE' in app_policy and 'Mutation.SUSPEND' in app_policy
print('test_protected_targets_110_contract: PASS')
