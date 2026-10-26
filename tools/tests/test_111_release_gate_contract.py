#!/usr/bin/env python3
from pathlib import Path
import json, re
ROOT=Path(__file__).resolve().parents[2]
errors=[]
read=lambda p:(ROOT/p).read_text('utf-8')
app=read('apps/dpc/app/build.gradle.kts')
vm=re.search(r'versionName\s*=\s*"1\.1\.(\d+)"', app)
if not vm or int(vm.group(1)) < 1: errors.append('versionName must be 1.1.1 or later 1.1.x')
m=re.search(r'versionCode\s*=\s*(\d+)',app)
if not m or int(m.group(1))<22: errors.append('versionCode >=22 required')
host=read('tools/run_host_tests.sh')
for t in ['test_110_release_gate_contract.py','test_111_build_runtime_readiness.py','test_111_build_preflight_runtime.py','test_111_release_gate_contract.py']:
    if t not in host: errors.append(f'host suite missing {t}')
workflow=read('.github/workflows/build-aio-enrollment.yml')
for marker in ['android_build_preflight.py --require-signing','build-environment.json','assembleEnterpriseRelease','DPC-AIO-enterprise-release.apk']:
    if marker not in workflow: errors.append(f'workflow missing {marker}')
report=json.loads(read('RELEASE-VERIFICATION.json'))
rv=re.fullmatch(r'1\.1\.(\d+)', str(report.get('version','')))
if not rv or int(rv.group(1)) < 1: errors.append('release report must be 1.1.1 or later 1.1.x')
if report.get('sourceEvidence',{}).get('buildRuntimeReadiness111')!='PASS': errors.append('build readiness evidence missing')
if report.get('buildReadiness',{}).get('status') not in {'BUILD_BLOCKED','BUILD_READY'}: errors.append('invalid build readiness state')
if errors:
    raise SystemExit('RELEASE_GATE_111: FAIL\n- '+'\n- '.join(errors))
print('RELEASE_GATE_111: PASS')
