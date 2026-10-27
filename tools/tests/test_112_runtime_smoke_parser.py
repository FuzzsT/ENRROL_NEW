#!/usr/bin/env python3
from pathlib import Path
import importlib.util, json
ROOT=Path(__file__).resolve().parents[2]
SCRIPT=ROOT/'tools/runtime/android_device_owner_smoke.py'
if not SCRIPT.exists():
    raise SystemExit('RUNTIME_SMOKE_PARSER_112: FAIL missing harness')
spec=importlib.util.spec_from_file_location('runtime_smoke',SCRIPT)
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)

sample='''Broadcasting: Intent { act=io.dpcaio.action.VERIFY_DIAGNOSTICS cmp=io.dpcaio.app/.VerificationCommandReceiver }
Broadcast completed: result=0, data="{\"status\":\"VERIFIED\",\"dpcVersion\":\"1.1.2\",\"deviceOwner\":true,\"profileOwner\":false,\"manufacturer\":\"Google\",\"model\":\"sdk_gphone64_x86_64\",\"moduleCounts\":{\"integrated\":41}}"
'''
code,data=mod.parse_broadcast_result(sample)
assert code==0, code
assert data['status']=='VERIFIED'
assert data['deviceOwner'] is True
assert data['dpcVersion']=='1.1.2'
mod.validate_diagnostics(data,'1.1.2')

sample_extras='''Broadcast completed: result=0, data="{\"status\":\"VERIFIED\",\"dpcVersion\":\"1.1.4\",\"deviceOwner\":true,\"profileOwner\":false,\"moduleCounts\":{\"integrated\":41}}", extras: Bundle[{source=runtime-smoke}]
'''
code2,data2=mod.parse_broadcast_result(sample_extras)
assert code2==0, code2
assert data2['status']=='VERIFIED'
assert data2['dpcVersion']=='1.1.4'
mod.validate_diagnostics(data2,'1.1.4')

sample_unquoted='''Broadcast completed: result=0, data={"status":"VERIFIED","dpcVersion":"1.1.4","deviceOwner":true,"profileOwner":false,"moduleCounts":{"integrated":41}}, extras=Bundle[{source=runtime-smoke}]
'''
code3,data3=mod.parse_broadcast_result(sample_unquoted)
assert code3==0, code3
assert data3['deviceOwner'] is True

bad='''Broadcast completed: result=0, data={"status":"VERIFIED"} trailing-junk
'''
try:
    mod.parse_broadcast_result(bad)
except ValueError as e:
    assert 'unexpected trailing broadcast metadata' in str(e)
else:
    raise AssertionError('expected unknown trailing metadata rejection')

try:
    mod.validate_diagnostics(data,'9.9.9')
except ValueError as e:
    assert 'version' in str(e).lower()
else:
    raise AssertionError('expected version mismatch')
print('RUNTIME_SMOKE_PARSER_112: PASS')
