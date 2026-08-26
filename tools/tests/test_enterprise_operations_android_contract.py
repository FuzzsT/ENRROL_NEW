from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = ROOT / 'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt'
CORE = ROOT / 'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt'
text = GATEWAY.read_text()
core = CORE.read_text()

required_gateway = [
    'setSecurityLoggingEnabled', 'isSecurityLoggingEnabled', 'retrieveSecurityLogs',
    'retrievePreRebootSecurityLogs', 'setNetworkLoggingEnabled', 'isNetworkLoggingEnabled',
    'retrieveNetworkLogs', 'getSystemUpdatePolicy', 'setSystemUpdatePolicy',
    'SystemUpdatePolicy.createAutomaticInstallPolicy', 'SystemUpdatePolicy.createWindowedInstallPolicy',
    'SystemUpdatePolicy.createPostponeInstallPolicy', 'FreezePeriod(',
]
for needle in required_gateway:
    assert needle in text, f'missing Android enterprise operation: {needle}'

for needle in [
    'setSecurityLoggingEnabled', 'retrieveSecurityLogs', 'setNetworkLoggingEnabled',
    'retrieveNetworkLogs', 'getSystemUpdatePolicySpec', 'setSystemUpdatePolicySpec'
]:
    assert needle in core, f'missing core gateway contract: {needle}'

assert 'Class.forName("android.app.admin' not in text
assert 'getDeclaredMethod(' not in text
print('test_enterprise_operations_android_contract: PASS')
