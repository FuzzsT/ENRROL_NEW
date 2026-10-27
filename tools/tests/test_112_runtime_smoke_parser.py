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

# Android 35 / emulator 37 can render result data with an extra shell-escape
# layer around every JSON quote. This is the quoting shape observed in CI run #23.
real_adb_data = r'"{\\"apiLevel\\":35,\\"manufacturer\\":\\"Google\\",\\"model\\":\\"sdk_gphone64_x86_64\\",\\"dpcVersion\\":\\"1.1.4\\",\\"deviceOwner\\":true,\\"profileOwner\\":false,\\"moduleCounts\\":{\\"integrated\\":41},\\"status\\":\\"VERIFIED\\"}"'
sample_real = f'Broadcast completed: result=0, data={real_adb_data}\n'
code4,data4=mod.parse_broadcast_result(sample_real)
assert code4==0, code4
assert data4['apiLevel']==35
assert data4['dpcVersion']=='1.1.4'
assert data4['deviceOwner'] is True
assert data4['moduleCounts']['integrated']==41
assert data4['status']=='VERIFIED'
mod.validate_diagnostics(data4,'1.1.4')

# CI run #26 / emulator 37.2 emitted a partially shell-quoted data wrapper:
# the first object quote is literal while subsequent JSON quotes are escaped,
# and the wrapper itself ends as backslash+quote after the closing brace.
sample_ci26 = r'''Broadcast completed: result=0, data="{"apiLevel\":35,\"manufacturer\":\"Google\",\"model\":\"sdk_gphone64_x86_64\",\"dpcVersion\":\"1.1.4\",\"deviceOwner\":true,\"profileOwner\":false,\"moduleCounts\":{\"integrated\":41},\"status\":\"VERIFIED\"}\"'''
code5,data5=mod.parse_broadcast_result(sample_ci26)
assert code5==0, code5
assert data5['apiLevel']==35
assert data5['manufacturer']=='Google'
assert data5['dpcVersion']=='1.1.4'
assert data5['deviceOwner'] is True
assert data5['moduleCounts']['integrated']==41
assert data5['status']=='VERIFIED'
mod.validate_diagnostics(data5,'1.1.4')

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
# v14: keep CI26 shell-quoting regression tied to a real workflow run.
