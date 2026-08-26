from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
g=(ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt').read_text()
c=(ROOT/'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt').read_text()
for n in [
 'setLockTaskPackages','getLockTaskPackages','setLockTaskFeatures','getLockTaskFeatures',
 'setRequiredPasswordComplexity','setMaximumFailedPasswordsForWipe','setKeyguardDisabledFeatures',
 'setCameraDisabled','setScreenCaptureDisabled','setUninstallBlocked','setApplicationRestrictions',
 'clearApplicationUserData','setUserControlDisabledPackages','setFactoryResetProtectionPolicy','getFactoryResetProtectionPolicy'
]: assert n in g, f'missing lifecycle Android API {n}'
for n in ['getLockTaskPolicySpec','setLockTaskPolicySpec','setDeviceSecurityPolicySpec','setUninstallBlockedPolicy','clearManagedApplicationData','setFrpPolicySpec','getFrpPolicySpec']:
    assert n in c, f'missing lifecycle gateway {n}'
low=(g+'\n'+c).lower()
assert '.wipedata(' not in low
assert '.factoryreset(' not in low
print('test_device_lifecycle_android_contract: PASS')
