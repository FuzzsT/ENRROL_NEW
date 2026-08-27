#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
ui = (ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DeviceLifecycleActivity.kt').read_text()
gw = (ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt').read_text()
for token in [
    'Disable status bar','Enable status bar','Disable keyguard','Enable keyguard','Lock device now',
    'Hide app','Unhide app','Suspend app','Unsuspend app','Enable system app',
    'Read delegated scopes','Apply delegated scopes',
]:
    assert token in ui, token
for token in [
    'setStatusBarDisabledPolicy','setKeyguardDisabledPolicy','lockDeviceNow','enableSystemAppPolicy',
    'setStatusBarDisabled(admin','setKeyguardDisabled(admin','lockNow()','enableSystemApp(admin',
]:
    assert token in gw, token
print('PASS: expanded official DevicePolicyManager controls')
