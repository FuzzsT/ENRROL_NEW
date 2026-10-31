#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUTH = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallAuthorizationCoordinator.kt'
QUEUE = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueCoordinator.kt'
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
        'fun requestInstallAuthorization(',
        'PinGuardResult.SUCCESS',
        'authorizationGranted',
        'requestGeneration',
        'invalidatePendingRequest(',
        'verifyPin(',
    ])
    assert 'SharedPreferences' in auth or 'getSharedPreferences' in auth
    assert 'plaintext' not in auth.lower()
    assert auth.count('invalidatePendingRequest()') >= 2, 'cancel/dismiss paths must invalidate an in-flight PIN request'
    assert 'requestGeneration.get() != requestId' in auth or 'requestGeneration.get() == requestId' in auth, 'async verification must confirm the request is still current before granting authorization'

    # Dialog verification must not call authorize(), because authorize grants immediately.
    executor_start = auth.index('executor.execute')
    callback_start = auth.index('activity.runOnUiThread', executor_start)
    executor_body = auth[executor_start:callback_start]
    assert 'verifyPin(' in executor_body, 'dialog worker must perform verification without granting authorization yet'
    assert 'authorize(' not in executor_body, 'cancellable dialog verification must not grant authorization before request validity is rechecked'

    queue = need(QUEUE, [
        'InstallAuthorizationCoordinator',
        'fun startOrResume(',
        'authorization.consumeAuthorization()',
        'authorization.requestInstallAuthorization(',
        'InstallQueueState.PREFLIGHT',
        'store.updateItem(updated)',
    ])

    # Authorization must happen before a queued item is moved into PREFLIGHT.
    start = queue.index('fun startOrResume(')
    mark_installing = queue.index('fun markInstalling(', start)
    start_body = queue[start:mark_installing]
    assert start_body.index('authorization.consumeAuthorization()') < start_body.index('InstallQueueState.PREFLIGHT'), 'one-shot authorization must be consumed before QUEUED -> PREFLIGHT'
    assert start_body.index('authorization.requestInstallAuthorization(') < start_body.index('InstallQueueState.PREFLIGHT'), 'PIN prompt must be requested before QUEUED -> PREFLIGHT'
    assert start_body.index('InstallQueueState.PREFLIGHT') < start_body.index('store.updateItem(updated)'), 'PREFLIGHT state must only be persisted after authorization'

    activity = need(ACTIVITY, [
        'continueQueueIfReady(',
        'queueCoordinator.startOrResume()',
        'PackageInstaller',
        'verifyTargetAndSigner(',
        'createSession(',
    ])

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
