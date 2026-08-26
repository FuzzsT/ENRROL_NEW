#!/usr/bin/env python3
from pathlib import Path
import importlib.util, json
ROOT=Path(__file__).resolve().parents[2]
SCRIPT=ROOT/'tools/runtime/android_device_owner_smoke.py'
if not SCRIPT.exists():
    raise SystemExit('RUNTIME_SMOKE_PARSER_112: FAIL missing harness')
spec=importlib.util.spec_from_file_location('runtime_smoke',SCRIPT)
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
sample='''Broadcasting: Intent { act=io.dpcaio.action.VERIFY_DIAGNOSTICS cmp=io.dpcaio.app/.VerificationCommandReceiver }\nBroadcast completed: result=0, data="{\\"status\\":\\"VERIFIED\\",\\"dpcVersion\\":\\"1.1.2\\",\\"deviceOwner\\":true,\\"profileOwner\\":false,\\"manufacturer\\":\\"Google\\",\\"model\\":\\"sdk_gphone64_x86_64\\",\\"moduleCounts\\":{\\"integrated\\":41}}"\n'''
code,data=mod.parse_broadcast_result(sample)
assert code==0, code
assert data['status']=='VERIFIED'
assert data['deviceOwner'] is True
assert data['dpcVersion']=='1.1.2'
mod.validate_diagnostics(data,'1.1.2')
try:
    mod.validate_diagnostics(data,'9.9.9')
except ValueError as e:
    assert 'version' in str(e).lower()
else:
    raise AssertionError('expected version mismatch')
print('RUNTIME_SMOKE_PARSER_112: PASS')
