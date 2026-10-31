#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUTH = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallAuthorizationCoordinator.kt'
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt'
WORKFLOW = ROOT / '.github/workflows/pr-android-ci.yml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    auth = need(AUTH, [
        'class InstallAuthorizationCoordinator',
        'PinGuardVerifier',
        'fun isEnabled(',
        'fun setEnabled(',
        'fun authorize(',
        'fun consumeAuthorization(',
        'PinGuardResult.SUCCESS',
    ])
    assert 'SharedPreferences' in auth or 'getSharedPreferences' in auth
    assert 'plaintext' not in auth.lower()
    assert 'authorizationGranted' in auth or 'authorizedItemId' in auth

    activity = need(ACTIVITY, [
        'InstallAuthorizationCoordinator',
        'Require PIN for installs',
        'requestInstallAuthorization(',
        'consumeAuthorization(',
        'continueQueueIfReady(',
        'queueCoordinator.startOrResume()',
        'PackageInstaller',
        'verifyTargetAndSigner(',
        'createSession(',
    ])

    # Authorization must happen before a queued item is moved into PREFLIGHT.
    continue_start = activity.index('private fun continueQueueIfReady(')
    process_start = activity.index('private fun processQueueItem(', continue_start)
    continue_body = activity[continue_start:process_start]
    assert continue_body.index('requestInstallAuthorization(') < continue_body.index('queueCoordinator.startOrResume()'), 'PIN authorization must be requested before QUEUED -> PREFLIGHT'
    assert continue_body.index('consumeAuthorization(') < continue_body.index('queueCoordinator.startOrResume()'), 'one-shot authorization must be consumed before starting preflight'

    # Existing package/signer validation must remain before PackageInstaller session creation.
    single_start = activity.index('private fun installSingleApk(')
    apks_start = activity.index('private fun installApksArchive(', single_start)
    single_body = activity[single_start:apks_start]
    assert single_body.index('verifyTargetAndSigner(') < single_body.index('createSession('), 'single APK trust checks must run before PackageInstaller session creation'

    assert 'Accessibility' not in activity
    assert 'overlay' not in activity.lower()
    assert 'su ' not in activity

    workflow = need(WORKFLOW, [
        'Run app security contracts',
        'python3 tools/tests/test_install_pin_guard_contract.py',
    ])
    assert workflow.count('test_install_pin_guard_contract.py') == 1

    print('test_install_pin_guard_contract: PASS')


if __name__ == '__main__':
    main()
