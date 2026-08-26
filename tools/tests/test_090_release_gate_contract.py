#!/usr/bin/env python3
from pathlib import Path
import json
ROOT = Path(__file__).resolve().parents[2]
HOST = (ROOT / 'tools/run_host_tests.sh').read_text('utf-8')
PRE = (ROOT / 'tools/release/verify-before-push.sh').read_text('utf-8')
GRADLE = (ROOT / 'apps/dpc/app/build.gradle.kts').read_text('utf-8')
PLUGIN = json.loads((ROOT / 'plugins/chatgpt-companion/.codex-plugin/plugin.json').read_text('utf-8'))
SERVER_PACKAGE = json.loads((ROOT / 'services/provisioning/package.json').read_text('utf-8'))
ENROLLMENT_SKILL = (ROOT / 'plugins/chatgpt-companion/skills/dpc-aio-enrollment/SKILL.md').read_text('utf-8')
import re
version_code = int(re.search(r'versionCode\s*=\s*(\d+)', GRADLE).group(1))
version_name = tuple(int(x) for x in re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', GRADLE).groups())
plugin_version = tuple(int(x) for x in PLUGIN['version'].split('.'))
assert version_code >= 17
assert version_name >= (0, 9, 0)
assert plugin_version >= (0, 1, 6)
assert SERVER_PACKAGE['version'] == '0.4.0'
for marker in ['reserve', 'signed bootstrap', 'KME', 'zero-touch']:
    assert marker.lower() in ENROLLMENT_SKILL.lower(), f'enrollment skill missing {marker}'
PY_TESTS = [
 'test_enrollment_config_contract.py', 'test_enrollment_session_store_contract.py',
 'test_enrollment_secret_store_contract.py', 'test_enrollment_trust_contract.py',
 'test_enrollment_coordinator_contract.py', 'test_enrollment_build_config_contract.py',
 'test_enrollment_offline_contract.py', 'test_enrollment_ui_contract.py',
 'test_enrollment_recovery_contract.py', 'test_enrollment_manual_ui_contract.py',
 'test_enrollment_server_runtime_contract.py',
]
for name in PY_TESTS:
    assert name in HOST, f'host suite missing {name}'
    assert name in PRE, f'pre-push missing {name}'
for main in ['io.dpcaio.core.model.EnrollmentModelTestKt', 'io.dpcaio.core.model.EnrollmentBootstrapTestKt']:
    assert main in HOST, f'host suite missing {main}'
assert 'test_090_release_gate_contract.py' in HOST
assert 'test_090_release_gate_contract.py' in PRE
print('test_090_release_gate_contract: PASS')
