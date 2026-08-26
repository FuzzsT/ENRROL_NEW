#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
base = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
paths = {name: base / name for name in [
    'EnrollmentCoordinator.kt', 'EnrollmentServerClient.kt', 'EnrollmentBootstrapApplier.kt',
    'PolicyComplianceActivity.kt', 'ProvisioningModeActivity.kt', 'EnrollmentExecutionRouter.kt'
]}
for name,p in paths.items(): assert p.is_file(), f'{name} missing'
coord = paths['EnrollmentCoordinator.kt'].read_text('utf-8')
server = paths['EnrollmentServerClient.kt'].read_text('utf-8')
applier = paths['EnrollmentBootstrapApplier.kt'].read_text('utf-8')
policy = paths['PolicyComplianceActivity.kt'].read_text('utf-8')
mode = paths['ProvisioningModeActivity.kt'].read_text('utf-8')
for marker in ['/v2/enrollments/reserve', '/v2/enrollments/validate', '/v2/enrollments/bootstrap', '/v2/enrollments/commit']:
    assert marker in server, f'server client missing {marker}'
for marker in ['EnrollmentSessionStore', 'EnrollmentSecretStore', 'EnrollmentTrustVerifier', 'EnrollmentBootstrapApplier', 'SERVER_REGISTRATION_PENDING']:
    assert marker in coord, f'coordinator missing {marker}'
assert 'AndroidDevicePolicyGateway' in applier
assert 'setAutoTimePolicy' in applier and 'isNetworkLoggingEnabled' in applier and 'isSecurityLoggingEnabled' in applier
assert policy.index('EnrollmentExecutionRouter') < policy.index('setResult(RESULT_OK)'), 'compliance must route before success'
router = paths['EnrollmentExecutionRouter.kt'].read_text('utf-8')
assert 'EnrollmentCoordinator' in router and 'OfflineEnrollmentCoordinator' in router
assert 'Thread {' in policy or 'Executors.' in policy, 'network enrollment must not run on main thread'
assert 'EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE' in mode
assert 'EnrollmentSessionStore' in mode
print('test_enrollment_coordinator_contract: PASS')
