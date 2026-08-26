#!/usr/bin/env python3
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2]
app=(ROOT/'apps/dpc/app/build.gradle.kts').read_text()
mcode=re.search(r'versionCode\s*=\s*(\d+)', app); mname=re.search(r'versionName\s*=\s*"([^"]+)"', app)
assert mcode and int(mcode.group(1)) >= 19, 'versionCode must preserve 1.0.1 or newer'
assert mname and tuple(map(int,mname.group(1).split('.'))) >= (1,0,1), 'versionName must preserve 1.0.1 or newer'
plugin=json.loads((ROOT/'plugins/chatgpt-companion/.codex-plugin/plugin.json').read_text())
plugin_version = tuple(int(x) for x in plugin['version'].split('.')[:3])
assert plugin_version >= (0, 1, 8), f"companion must preserve 0.1.8 or newer, got {plugin['version']}"
for script in ('tools/run_host_tests.sh','tools/release/verify-before-push.sh'):
    text=(ROOT/script).read_text()
    for test in (
        'test_101_build_resolver.py','test_101_build_cli.py',
        'test_101_offline_bundle_builder.py','test_101_offline_bundle_signing.py','test_101_offline_bundle_cli.py','test_101_apk_inspector.py',
        'test_101_device_harness.py','test_101_device_safe_guards.py','test_101_test_target_contract.py','test_101_verification_bridge_contract.py',
        'test_101_release_report.py','test_101_release_gate_contract.py',
    ):
        assert test in text, f'{script}: missing {test}'
print('test_101_release_gate_contract: PASS')
