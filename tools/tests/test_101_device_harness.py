#!/usr/bin/env python3
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from tools.verify_aio.device import run_device_preflight
from tools.verify_aio.report import VerificationReport, VerificationResult

class FakeRunner:
    def __init__(self, mapping): self.mapping=mapping
    def run(self, args):
        key=' '.join(args)
        out=self.mapping.get(key, ('',1))
        return type('R',(),{'stdout':out[0],'stderr':'','returncode':out[1]})()

none=run_device_preflight(FakeRunner({'adb devices':('List of devices attached\n\n',0)}))
assert none.status=='BLOCKED' and 'ADB_DEVICE_MISSING' in none.reasons, none

mapping={
'adb devices':('List of devices attached\nSER123\tdevice\n',0),
'adb -s SER123 shell getprop ro.build.version.sdk':('36\n',0),
'adb -s SER123 shell pm list users':('Users:\n\tUserInfo{0:Owner:13} running\n\tUserInfo{10:Work profile:30}\n',0),
'adb -s SER123 shell dumpsys device_policy':('Device Owner:\n  admin=ComponentInfo{io.dpcaio.app/.AioDeviceAdminReceiver}\nProfile Owner (User 10):\n  admin=ComponentInfo{io.dpcaio.app/.AioDeviceAdminReceiver}\n',0),
'adb -s SER123 shell pm path io.dpcaio.app':('package:/data/app/io.dpcaio/base.apk\n',0),
'adb -s SER123 shell dumpsys package io.dpcaio.app':('versionName=1.0.1\nversionCode=19\n',0),
}
ready=run_device_preflight(FakeRunner(mapping))
assert ready.status=='READY', ready
assert ready.api_level==36 and ready.device_owner=='io.dpcaio.app' and ready.profile_owner_user_ids==(10,), ready
assert ready.dpc_version=='1.0.1', ready

rep=VerificationReport(version='1.0.1')
rep.add(VerificationResult('sample','expected','observed','PASS',details={'token':'SECRET','password':'PW','safe':'yes'}))
data=json.loads(rep.to_json())
text=json.dumps(data)
assert 'SECRET' not in text and 'PW' not in text
assert data['results'][0]['details']['safe']=='yes'
print('test_101_device_harness: PASS')

import subprocess
cli=subprocess.run([sys.executable,'-m','tools.verify_aio.cli','device-preflight'],cwd=ROOT,text=True,capture_output=True)
assert cli.returncode in (0,2), cli.stderr
cli_data=json.loads(cli.stdout)
assert cli_data['status'] in ('READY','BLOCKED'), cli_data
plan=subprocess.run([sys.executable,'-m','tools.verify_aio.cli','safe-plan','--serial','SER123','--user','0'],cwd=ROOT,text=True,capture_output=True)
assert plan.returncode==0,plan.stderr
plan_data=json.loads(plan.stdout)
assert plan_data['mode']=='SAFE'
assert all('io.dpcaio.testtarget' in ' '.join(c) or 'FULL_OFFLINE_SMOKE' in ' '.join(c) or 'io.dpcaio.app' in ' '.join(c) or 'logcat' in c for c in plan_data['commands'])
print('test_101_device_cli: PASS')

from tools.verify_aio.device import execute_safe_verification
class DynamicRunner:
    def run(self,args):
        joined=' '.join(args)
        if 'am broadcast' in joined:
            return type('R',(),{'stdout':'Broadcast completed: result=0, data="{\\"status\\":\\"VERIFIED\\"}"\n','stderr':'','returncode':0})()
        return type('R',(),{'stdout':'','stderr':'','returncode':0})()
result=execute_safe_verification(DynamicRunner(),'SER123',user_id=0)
assert result['status']=='PASS', result
assert len(result['results'])==6, result
assert all(x['status']=='PASS' for x in result['results'])
print('test_101_device_execute_safe: PASS')

run_safe_cli=subprocess.run([sys.executable,'-m','tools.verify_aio.cli','run-safe','--serial','SER123','--user','0'],cwd=ROOT,text=True,capture_output=True)
assert run_safe_cli.returncode in (0,2),run_safe_cli.stderr
rs=json.loads(run_safe_cli.stdout)
assert rs['status'] in ('PASS','FAIL','BLOCKED'),rs
print('test_101_device_run_safe_cli: PASS')
