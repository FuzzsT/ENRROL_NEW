from pathlib import Path
R=Path(__file__).resolve().parents[2]
m=(R/'apps/dpc/app/src/main/AndroidManifest.xml').read_text(); d=(R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
p=R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DeviceLifecycleActivity.kt'
assert p.exists(); t=p.read_text(); low=t.lower()
for n in ['Kiosk / Lock Task','Device Security','Application Control','Factory Reset Protection','Preview','Confirm','setLockTaskPolicySpec','setDeviceSecurityPolicySpec','clearManagedApplicationData','setFrpPolicySpec','LOCK_TASK_FEATURE_HOME','LOCK_TASK_FEATURE_NOTIFICATIONS','LOCK_TASK_FEATURE_OVERVIEW','LOCK_TASK_FEATURE_SYSTEM_INFO','LOCK_TASK_FEATURE_GLOBAL_ACTIONS','LOCK_TASK_FEATURE_KEYGUARD','setManagedApplicationRestrictions','setUserControlDisabledPackagesPolicy','Password complexity','Disable camera','Disable screen capture']:
    assert n in t
assert 'wipedata(' not in low and 'factoryreset(' not in low
assert '.DeviceLifecycleActivity' in m and 'Device Lifecycle Center' in d
print('test_device_lifecycle_ui_contract: PASS')
