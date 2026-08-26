#!/usr/bin/env python3
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2]
errors=[]
read=lambda p:(ROOT/p).read_text('utf-8')
app=read('apps/dpc/app/build.gradle.kts')
vm=re.search(r'versionName\s*=\s*"([0-9.]+)"', app)
if not vm or tuple(map(int,vm.group(1).split('.'))) < (1,1,3): errors.append('versionName >=1.1.3 required')
m=re.search(r'versionCode\s*=\s*(\d+)',app)
if not m or int(m.group(1))<24: errors.append('versionCode >=24 required')
for rel in [
 'tools/tests/test_113_github_upload_ready_contract.py',
 'tools/tests/test_github_publish_kit_contract.py',
 'tools/release/github_publish_preflight.py',
 'tools/release/publish_to_github.sh',
 'docs/releases/GITHUB-PUBLISH.md',
 'docs/releases/GITHUB-SECRETS.example',
 'docs/GITHUB-PUBLISHING.md',
 '.github/workflows/build-aio-enrollment.yml',
]:
 if not (ROOT/rel).is_file(): errors.append(f'missing {rel}')
host=read('tools/run_host_tests.sh')
for marker in ['test_112_release_gate_contract.py','test_113_github_upload_ready_contract.py','test_github_publish_kit_contract.py','test_113_release_gate_contract.py']:
 if marker not in host: errors.append(f'host suite missing {marker}')
report=json.loads(read('RELEASE-VERIFICATION.json'))
rv=report.get('version','0.0.0')
if tuple(map(int,rv.split('.'))) < (1,1,3): errors.append('release report version <1.1.3')
se=report.get('sourceEvidence',{})
for marker in ['githubUploadReadiness113','githubActionsSecretScope113','githubActionsPermissionSplit113','githubActionsImmutablePins113']:
 if se.get(marker) is None: errors.append(f'release evidence missing {marker}')
if errors:
 raise SystemExit('RELEASE_GATE_113: FAIL\n- '+'\n- '.join(errors))
print('RELEASE_GATE_113: PASS')
