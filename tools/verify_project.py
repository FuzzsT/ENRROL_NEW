#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_MODULES = [
    'core-model','core-execution','platform-compat','policy-core','policy-android','permission-manager','samsung-settings','samsung-settings-android',
    'app-manager','app-android','account-manager','account-android','activity-launcher','activity-android','permission-android','installer-core','installer-android','delegation-core','dhizuku-compat',
    'shizuku-adapter','knox-license-core','knox-license-lab','knox-mock-core','knox-mock-android','knox-zt-core','knox-zt-android',
    'network-control','network-android','scenario-core','scenario-android','nfc-lab-core','nfc-lab-android','native-diagnostics','app-dpc','lab-tools'
]
REQUIRED_NON_GRADLE_DIRS = ['provisioning-server']
errors = []
settings = ROOT / 'settings.gradle.kts'
if not settings.exists():
    errors.append('missing settings.gradle.kts')
    settings_text = ''
else:
    settings_text = settings.read_text(encoding='utf-8')

for directory in REQUIRED_NON_GRADLE_DIRS:
    if not (ROOT / directory).exists():
        errors.append(f'missing project directory: {directory}')

for module in REQUIRED_MODULES:
    if not (ROOT / module).exists():
        errors.append(f'missing module directory: {module}')
    if f'":{module}"' not in settings_text:
        errors.append(f'module not included in settings: {module}')

app_build = ROOT / 'app-dpc' / 'build.gradle.kts'
if not app_build.exists():
    errors.append('missing app-dpc/build.gradle.kts')
else:
    txt = app_build.read_text(encoding='utf-8')
    for required in [
        'project(":core-model")', 'project(":core-execution")', 'project(":platform-compat")',
        'project(":policy-core")', 'project(":policy-android")', 'project(":permission-manager")', 'project(":samsung-settings")', 'project(":samsung-settings-android")', 'project(":permission-android")', 'project(":account-manager")', 'project(":account-android")', 'project(":app-manager")', 'project(":app-android")',
        'project(":activity-launcher")', 'project(":activity-android")', 'project(":installer-core")', 'project(":installer-android")', 'project(":delegation-core")', 'project(":knox-license-core")',
        'project(":knox-zt-core")', 'project(":knox-zt-android")', 'project(":scenario-core")', 'project(":scenario-android")', 'project(":nfc-lab-core")', 'project(":nfc-lab-android")'
    ]:
        if required not in txt:
            errors.append(f'app-dpc missing dependency {required}')

manifest = ROOT / 'app-dpc' / 'src' / 'main' / 'AndroidManifest.xml'
if not manifest.exists():
    errors.append('missing app-dpc AndroidManifest.xml')
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
