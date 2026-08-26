#!/usr/bin/env python3
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from tools.verify_aio.device import plan_permission_tests, plan_component_tests, plan_full_offline_smoke

plans = plan_permission_tests('SER123', user_id=0) + plan_component_tests('SER123', user_id=0) + plan_full_offline_smoke('SER123')
text='\n'.join(' '.join(step) for step in plans)
for forbidden in ('wipe-data','wipe_data','factory_reset','factory-reset','remove-active-admin','remove-user','account remove','AioDeviceAdminReceiver','PolicyComplianceActivity','ProvisioningModeActivity'):
    assert forbidden not in text, (forbidden,text)
# Functional mutations must go through the DPC shell-only verification bridge, not raw pm mutation commands.
assert 'io.dpcaio.app/.VerificationCommandReceiver' in text, text
assert 'io.dpcaio.action.VERIFY_PERMISSION' in text, text
assert 'io.dpcaio.action.VERIFY_COMPONENT' in text, text
for raw in (' pm grant ', ' pm revoke ', ' pm enable ', ' pm disable ', ' pm default-state '):
    assert raw not in (' '+text+' '), raw
assert 'io.dpcaio.testtarget' in text
assert 'svc wifi disable' not in text and 'svc data disable' not in text, 'harness must not globally break user connectivity'
print('test_101_device_safe_guards: PASS')
