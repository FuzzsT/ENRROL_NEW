#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from project_layout import GRADLE_PROJECT_DIRS, NON_GRADLE_DIRS, project_dir, non_gradle_dir

errors = []
settings = ROOT / 'settings.gradle.kts'
if not settings.exists():
    errors.append('missing settings.gradle.kts')
    settings_text = ''
else:
    settings_text = settings.read_text(encoding='utf-8')

for name, rel in NON_GRADLE_DIRS.items():
    if not non_gradle_dir(name).exists():
        errors.append(f'missing project directory: {rel}')

for module, rel in GRADLE_PROJECT_DIRS.items():
    path = project_dir(module)
    if not path.exists():
        errors.append(f'missing module directory: {rel}')
    if not (path / 'build.gradle.kts').exists():
        errors.append(f'missing module build file: {rel}/build.gradle.kts')
    if f'":{module}"' not in settings_text:
        errors.append(f'module not included in settings: {module}')
    mapping = f'project(":{module}").projectDir = file("{rel}")'
    if mapping not in settings_text:
        errors.append(f'module projectDir mapping missing: {module} -> {rel}')

app_build = project_dir('app-dpc') / 'build.gradle.kts'
if not app_build.exists():
    errors.append('missing apps/dpc/app/build.gradle.kts')
else:
    txt = app_build.read_text(encoding='utf-8')
    for required in [
        'project(":core-model")', 'project(":core-execution")', 'project(":platform-compat")',
        'project(":policy-core")', 'project(":policy-android")', 'project(":permission-manager")', 'project(":offline-core")', 'project(":offline-android")', 'project(":samsung-settings")', 'project(":samsung-settings-android")', 'project(":permission-android")', 'project(":account-manager")', 'project(":account-android")', 'project(":app-manager")', 'project(":app-android")',
        'project(":activity-launcher")', 'project(":activity-android")', 'project(":installer-core")', 'project(":installer-android")', 'project(":delegation-core")', 'project(":knox-license-core")',
        'project(":knox-zt-core")', 'project(":knox-zt-android")', 'project(":scenario-core")', 'project(":scenario-android")', 'project(":nfc-lab-core")', 'project(":nfc-lab-android")'
    ]:
        if required not in txt:
            errors.append(f'app-dpc missing dependency {required}')

manifest = project_dir('app-dpc') / 'src' / 'main' / 'AndroidManifest.xml'
if not manifest.exists():
    errors.append('missing DPC AndroidManifest.xml')
else:
    manifest_text = manifest.read_text(encoding='utf-8')
    if manifest_text.count('android.app.device_admin') != 1:
        errors.append('expected exactly one device-admin receiver metadata entry')
    if '.AioDeviceAdminReceiver' not in manifest_text:
        errors.append('AioDeviceAdminReceiver missing from manifest')

versions = ROOT / 'gradle' / 'libs.versions.toml'
if not versions.exists():
    errors.append('missing gradle/libs.versions.toml')
else:
    version_text = versions.read_text(encoding='utf-8')
    for exact in ['compileSdk = "37"', 'minSdk = "29"', 'targetSdk = "37"', 'shizuku = "13.1.5"']:
        if exact not in version_text:
            errors.append(f'missing platform constraint: {exact}')

if errors:
    print('PROJECT_VERIFY: FAIL')
    for error in errors:
        print(f' - {error}')
    sys.exit(1)
print('PROJECT_VERIFY: PASS')
