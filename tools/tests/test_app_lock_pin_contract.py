#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockStore.kt'
COORDINATOR = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockCoordinator.kt'
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockActivity.kt'
RECEIVER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockAlarmReceiver.kt'
LAUNCHER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    need(STORE, [
        'data class AppLockRecord',
        'packageName',
        'locked',
        'unlockedUntilMillis',
        'fun read(',
        'fun write(',
        'fun remove(',
        'fun all()',
    ])

    coordinator = need(COORDINATOR, [
        'AppPolicyCoordinator',
        'AndroidDevicePolicyGateway',
        'PinGuardVerifier',
        'fun lock(',
        'fun unlock(',
        'fun reconcileExpiredUnlocks(',
        'setSuspended(packageName, true)',
        'setSuspended(packageName, false)',
        'DEVICE_OWNER_REQUIRED',
    ])
    assert 'Accessibility' not in coordinator
    assert 'overlay' not in coordinator.lower()

    need(RECEIVER, [
        'class AppLockAlarmReceiver',
        'BroadcastReceiver',
        'reconcileExpiredUnlocks(',
    ])

    need(ACTIVITY, [
        'EXTRA_TARGET_PACKAGE',
        'PinGuard',
        'Lock app',
        'Unlock permanently',
        'Unlock 1 min',
        'Unlock 5 min',
        'Unlock 15 min',
        'AppLockCoordinator',
    ])

    need(LAUNCHER, [
        'App Lock',
        'AppLockActivity::class.java',
        'AppLockActivity.EXTRA_TARGET_PACKAGE',
        'reconcileExpiredUnlocks(',
    ])

    need(MANIFEST, [
        '<activity android:name=".AppLockActivity" android:exported="false" />',
        '<receiver android:name=".AppLockAlarmReceiver" android:exported="false" />',
    ])

    print('test_app_lock_pin_contract: PASS')


if __name__ == '__main__':
    main()
