#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOST = (ROOT / 'tools/run_host_tests.sh').read_text('utf-8')
PRE = (ROOT / 'tools/release/verify-before-push.sh').read_text('utf-8')

PY_TESTS = [
    'test_enterprise_operations_android_contract.py',
    'test_enterprise_log_store_contract.py',
    'test_credential_android_contract.py',
    'test_device_lifecycle_android_contract.py',
    'test_cope_android_contract.py',
    'test_enterprise_operations_ui_contract.py',
    'test_credential_center_contract.py',
    'test_device_lifecycle_ui_contract.py',
    'test_cope_knox_ui_contract.py',
    'test_enterprise_operations_diagnostics_contract.py',
]
KOTLIN_MAINS = [
    'io.dpcaio.policy.EnterpriseOperationsModelsTestKt',
    'io.dpcaio.policy.CredentialPolicyModelsTestKt',
    'io.dpcaio.policy.DeviceLifecycleModelsTestKt',
    'io.dpcaio.policy.CopePolicyModelsTestKt',
    'io.dpcaio.knox.license.KnoxPublicCapabilityTestKt',
]

for name in PY_TESTS:
    assert name in HOST, f'host suite missing {name}'
    assert name in PRE, f'pre-push missing {name}'
for main in KOTLIN_MAINS:
    assert main in HOST, f'host suite missing Kotlin main {main}'
assert 'test_080_release_gate_contract.py' in HOST
assert 'test_080_release_gate_contract.py' in PRE
print('test_080_release_gate_contract: PASS')
