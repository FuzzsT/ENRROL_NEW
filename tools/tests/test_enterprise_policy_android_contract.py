from pathlib import Path
root=Path(__file__).resolve().parents[2]
core=(root/'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt').read_text()
android=(root/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt').read_text()
for token in ['EnterpriseDevicePolicyGateway','TriStatePolicy','AppFunctionsPolicy','DeviceRestriction']:
    assert token in core, token
assert ('Build.VERSION.SDK_INT < 31' in android or 'Build.VERSION.SDK_INT >= 31' in android), 'API31 guard'
assert ('Build.VERSION.SDK_INT < 36' in android or 'Build.VERSION.SDK_INT >= 36' in android), 'API36 guard'
for token in [
    'canUsbDataSignalingBeDisabled()','setUsbDataSignalingEnabled','isUsbDataSignalingEnabled',
    'setAutoTimePolicy','getAutoTimePolicy','setAutoTimeZonePolicy','getAutoTimeZonePolicy',
    'setAppFunctionsPolicy','getAppFunctionsPolicy','UserManager.DISALLOW_THREAD_NETWORK',
    'UserManager.DISALLOW_NEAR_FIELD_COMMUNICATION_RADIO','UserManager.DISALLOW_CHANGE_NEAR_FIELD_COMMUNICATION_RADIO',
    'getParentProfileInstance',
]:
    assert token in android, token
assert 'PolicyStatus.UNSUPPORTED' in android
print('test_enterprise_policy_android_contract: PASS')
