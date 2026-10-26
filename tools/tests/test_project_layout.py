#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

EXPECTED = {
    ':app-dpc': 'apps/dpc/app',
    ':core-model': 'apps/dpc/modules/core/model',
    ':core-execution': 'apps/dpc/modules/core/execution',
    ':platform-compat': 'apps/dpc/modules/platform/compat',
    ':policy-core': 'apps/dpc/modules/policy/core',
    ':policy-android': 'apps/dpc/modules/policy/android',
    ':permission-manager': 'apps/dpc/modules/permissions/core',
    ':permission-android': 'apps/dpc/modules/permissions/android',
    ':samsung-settings': 'apps/dpc/modules/samsung/core',
    ':samsung-settings-android': 'apps/dpc/modules/samsung/android',
    ':account-manager': 'apps/dpc/modules/account/core',
    ':account-android': 'apps/dpc/modules/account/android',
    ':app-manager': 'apps/dpc/modules/app-management/core',
    ':app-android': 'apps/dpc/modules/app-management/android',
    ':activity-launcher': 'apps/dpc/modules/activity/core',
    ':activity-android': 'apps/dpc/modules/activity/android',
    ':installer-core': 'apps/dpc/modules/installer/core',
    ':installer-android': 'apps/dpc/modules/installer/android',
    ':delegation-core': 'apps/dpc/modules/delegation/core',
    ':dhizuku-compat': 'apps/dpc/integrations/dhizuku',
    ':shizuku-adapter': 'apps/dpc/integrations/shizuku',
    ':knox-license-core': 'apps/dpc/modules/knox/license/core',
    ':knox-license-lab': 'apps/dpc/lab/knox-license',
    ':knox-mock-core': 'apps/dpc/modules/knox/mock/core',
    ':knox-mock-android': 'apps/dpc/modules/knox/mock/android',
    ':knox-zt-core': 'apps/dpc/modules/knox/zero-trust/core',
    ':knox-zt-android': 'apps/dpc/modules/knox/zero-trust/android',
    ':native-diagnostics': 'apps/dpc/integrations/native-diagnostics',
    ':network-control': 'apps/dpc/modules/network/core',
    ':network-android': 'apps/dpc/modules/network/android',
    ':scenario-core': 'apps/dpc/modules/scenario/core',
    ':scenario-android': 'apps/dpc/modules/scenario/android',
    ':nfc-lab-core': 'apps/dpc/modules/nfc-lab/core',
    ':nfc-lab-android': 'apps/dpc/modules/nfc-lab/android',
    ':lab-tools': 'apps/dpc/lab/tools',
    ':aio-test-target': 'apps/aio-test-target',
}

ALLOWED_ROOT_DIRS = {
    '.github', 'apps', 'docs', 'gradle', 'lab', 'plugins', 'services', 'tools'
}

settings = (ROOT / 'settings.gradle.kts').read_text(encoding='utf-8')
for project, rel in EXPECTED.items():
    path = ROOT / rel
    assert path.is_dir(), f'{project} missing at {rel}'
    assert (path / 'build.gradle.kts').is_file(), f'{project} missing build.gradle.kts at {rel}'
    assert f'project("{project}").projectDir = file("{rel}")' in settings, f'{project} mapping missing'

assert (ROOT / 'services/provisioning').is_dir(), 'provisioning service must live under services/'
assert (ROOT / 'plugins/chatgpt-companion').is_dir(), 'companion plugin must live under plugins/'
assert (ROOT / 'lab/license').is_dir(), 'lab license material must live under lab/license/'
assert not (ROOT / 'knox-license-android').exists(), 'empty orphan knox-license-android must be removed'
assert not (ROOT / 'modules').exists(), 'application modules must live under apps/dpc/modules'
assert not (ROOT / 'integrations').exists(), 'application integrations must live under apps/dpc/integrations'

unexpected = sorted(
    p.name for p in ROOT.iterdir()
    if p.is_dir() and not p.name.startswith('.') and p.name not in ALLOWED_ROOT_DIRS
)
assert not unexpected, f'unexpected top-level directories: {unexpected}'

print('PROJECT_LAYOUT: PASS')
