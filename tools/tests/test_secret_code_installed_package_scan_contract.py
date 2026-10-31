from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SETTINGS = ROOT / 'settings.gradle.kts'
APP_GRADLE = ROOT / 'apps/dpc/app/build.gradle.kts'
MODULE_GRADLE = ROOT / 'apps/dpc/modules/engineering/android/build.gradle.kts'
SCANNER = ROOT / 'apps/dpc/modules/engineering/android/src/main/kotlin/io/dpcaio/engineering/android/AndroidSecretCodeInstalledPackageScanner.kt'

settings = SETTINGS.read_text(encoding='utf-8')
app_gradle = APP_GRADLE.read_text(encoding='utf-8')

assert 'project(":engineering-android").projectDir = file("apps/dpc/modules/engineering/android")' in settings, 'engineering-android module missing'
assert 'implementation(project(":engineering-android"))' in app_gradle, 'app must directly own engineering-android'
assert MODULE_GRADLE.is_file(), 'engineering-android build.gradle.kts missing'
assert SCANNER.is_file(), 'AndroidSecretCodeInstalledPackageScanner.kt missing'

module_gradle = MODULE_GRADLE.read_text(encoding='utf-8')
scanner = SCANNER.read_text(encoding='utf-8')

for token in ['com.android.library', 'implementation(project(":engineering-core"))']:
    assert token in module_gradle, f'engineering-android build missing {token}'

for token in [
    'class AndroidSecretCodeInstalledPackageScanner',
    'PackageManager',
    'getInstalledPackages',
    'sourceDir',
    'splitSourceDirs',
    'SecretCodeManifestExtractor.extract',
    'SecretCodeSourceKind.BASE',
    'SecretCodeSourceKind.SPLIT',
    'SecretCodeInventoryReducer.reduce',
]:
    assert token in scanner, f'installed package scanner missing {token}'

for forbidden in [
    'sendBroadcast(',
    'startActivity(',
    'Runtime.getRuntime().exec',
    'ProcessBuilder(',
    'su ',
    'adb shell',
]:
    assert forbidden not in scanner, f'installed package scanner must stay read-only: {forbidden}'

print('test_secret_code_installed_package_scan_contract: PASS')
