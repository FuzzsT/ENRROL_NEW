#!/usr/bin/env python3
from pathlib import Path
import json, re
ROOT=Path(__file__).resolve().parents[2]
errors=[]
read=lambda p:(ROOT/p).read_text('utf-8')
app=read('apps/dpc/app/build.gradle.kts')
vm=re.search(r'versionName\s*=\s*"([0-9.]+)"',app)
if not vm or tuple(map(int,vm.group(1).split('.'))) < (1,1,2): errors.append('versionName >=1.1.2 required')
m=re.search(r'versionCode\s*=\s*(\d+)',app)
if not m or int(m.group(1))<23: errors.append('versionCode >=23 required')
for rel in [
  'tools/runtime/android_device_owner_smoke.py',
  'tools/tests/test_112_runtime_smoke_parser.py',
  'tools/tests/test_112_android_runtime_smoke_contract.py',
]:
    if not (ROOT/rel).is_file(): errors.append(f'missing {rel}')
host=read('tools/run_host_tests.sh')
for t in [
  'test_111_release_gate_contract.py',
  'test_112_runtime_smoke_parser.py',
  'test_112_android_runtime_smoke_contract.py',
  'test_112_release_gate_contract.py',
]:
    if t not in host: errors.append(f'host suite missing {t}')
workflow=read('.github/workflows/build-aio-enrollment.yml')
for marker in [
  'ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d',
  'tools/runtime/android_device_owner_smoke.py',
  'dist/android-runtime-smoke.json',
  'Publish continuous enrollment assets',
  'Publish tag release assets',
]:
    if marker not in workflow: errors.append(f'workflow missing {marker}')
report=json.loads(read('RELEASE-VERIFICATION.json'))
rv=tuple(map(int,report.get('version','0.0.0').split('.')))
if rv < (1,1,2): errors.append('release report version <1.1.2')
if report.get('sourceEvidence',{}).get('androidRuntimeSmokeHarness112')!='PASS':
    errors.append('runtime smoke harness source evidence missing')
ci=report.get('ciRuntimeEvidence',{})
if ci.get('status') not in {'NOT_RUN','PASS','FAIL'}: errors.append('invalid ciRuntimeEvidence status')
# Static/source checks may not falsely claim runtime PASS in this local environment.
if report.get('evidenceStates',{}).get('ANDROID_ENTERPRISE_RUNTIME_VERIFIED') == 'PASS' and ci.get('status') != 'PASS':
    errors.append('runtime evidence promoted without CI runtime PASS')
if errors:
    raise SystemExit('RELEASE_GATE_112: FAIL\n- '+'\n- '.join(errors))
print('RELEASE_GATE_112: PASS')
