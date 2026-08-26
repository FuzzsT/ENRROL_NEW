#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GRADLE_PROJECT_DIRS = {
    'app-dpc': 'apps/dpc/app',
    'core-model': 'apps/dpc/modules/core/model',
    'core-execution': 'apps/dpc/modules/core/execution',
    'platform-compat': 'apps/dpc/modules/platform/compat',
    'policy-core': 'apps/dpc/modules/policy/core',
    'policy-android': 'apps/dpc/modules/policy/android',
    'permission-manager': 'apps/dpc/modules/permissions/core',
    'offline-core': 'apps/dpc/modules/offline/core',
    'offline-android': 'apps/dpc/modules/offline/android',
    'permission-android': 'apps/dpc/modules/permissions/android',
    'samsung-settings': 'apps/dpc/modules/samsung/core',
    'samsung-settings-android': 'apps/dpc/modules/samsung/android',
    'account-manager': 'apps/dpc/modules/account/core',
    'account-android': 'apps/dpc/modules/account/android',
    'app-manager': 'apps/dpc/modules/app-management/core',
    'app-android': 'apps/dpc/modules/app-management/android',
    'activity-launcher': 'apps/dpc/modules/activity/core',
    'activity-android': 'apps/dpc/modules/activity/android',
    'installer-core': 'apps/dpc/modules/installer/core',
    'installer-android': 'apps/dpc/modules/installer/android',
    'delegation-core': 'apps/dpc/modules/delegation/core',
    'dhizuku-compat': 'apps/dpc/integrations/dhizuku',
    'shizuku-adapter': 'apps/dpc/integrations/shizuku',
    'knox-license-core': 'apps/dpc/modules/knox/license/core',
    'knox-license-lab': 'apps/dpc/lab/knox-license',
    'knox-mock-core': 'apps/dpc/modules/knox/mock/core',
    'knox-mock-android': 'apps/dpc/modules/knox/mock/android',
    'knox-zt-core': 'apps/dpc/modules/knox/zero-trust/core',
    'knox-zt-android': 'apps/dpc/modules/knox/zero-trust/android',
    'native-diagnostics': 'apps/dpc/integrations/native-diagnostics',
    'network-control': 'apps/dpc/modules/network/core',
    'network-android': 'apps/dpc/modules/network/android',
    'scenario-core': 'apps/dpc/modules/scenario/core',
    'scenario-android': 'apps/dpc/modules/scenario/android',
    'nfc-lab-core': 'apps/dpc/modules/nfc-lab/core',
    'nfc-lab-android': 'apps/dpc/modules/nfc-lab/android',
    'lab-tools': 'apps/dpc/lab/tools',
}

VERIFICATION_GRADLE_PROJECT_DIRS = {
    'aio-test-target': 'apps/aio-test-target',
}

NON_GRADLE_DIRS = {
    'provisioning-server': 'services/provisioning',
    'chatgpt-plugin': 'plugins/chatgpt-companion',
    'lab-license': 'lab/license',
}


def project_dir(name: str, root: Path = ROOT) -> Path:
    return root / GRADLE_PROJECT_DIRS[name]


def non_gradle_dir(name: str, root: Path = ROOT) -> Path:
    return root / NON_GRADLE_DIRS[name]
