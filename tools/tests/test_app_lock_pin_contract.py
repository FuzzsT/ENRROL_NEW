#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockStore.kt'
COORDINATOR = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockCoordinator.kt'
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockActivity.kt'
RECEIVER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppLockAlarmReceiver.kt'
DASHBOARD = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    store = need(STORE, [
        'data class AppLockRecord',
        'packageName',
        'locked',
        'unlockedUntilMillis',
        'pendingUnlock',
        'fun read(',
        'fun write(',
        'fun remove(',
        'fun all()',
    ])
    assert 'putBoolean(key(record.packageName, "pendingUnlock"), record.pendingUnlock)' in store

    coordinator = need(COORDINATOR, [
        'AppPolicyCoordinator',
        'AndroidDevicePolicyGateway',
        'PinGuardVerifier',
        'fun lock(',
        'fun unlock(',
        'fun reconcileExpiredUnlocks(',
        'fun restorePendingRelocks(',
        'setSuspended(packageName, true)',
        'setSuspended(packageName, false)',
        'DEVICE_OWNER_REQUIRED',
        'NOT_MANAGED',
        'store.read(packageName)',
        'transitionLock',
        'synchronized(transitionLock)',
        'Uri.encode(packageName)',
        '.setData(',
        'rollbackSuspension(',
        'canScheduleExactAlarms()',
        'setExactAndAllowWhileIdle(',
        'setExact(',
        'pendingUnlock = true',
        'pendingUnlock = false',
        'it.pendingUnlock',
    ])
    assert coordinator.index('store.read(packageName)') < coordinator.index('setSuspended(packageName, false)'), 'unlock must verify App Lock ownership before unsuspending a package'
    assert coordinator.index('pendingUnlock = true') < coordinator.index('setSuspended(packageName, false)'), 'recoverable pending unlock must be persisted before unsuspending'
    assert coordinator.count('synchronized(transitionLock)') >= 4, 'lock/unlock/reconcile/restore transitions must share synchronization'
    assert 'setAndAllowWhileIdle(' not in coordinator, 'bounded App Lock unlocks must not rely on inexact alarms'
    assert 'Accessibility' not in coordinator
    assert 'overlay' not in coordinator.lower()

    receiver = need(RECEIVER, [
        'class AppLockAlarmReceiver',
        'BroadcastReceiver',
        'Intent.ACTION_BOOT_COMPLETED',
        'Intent.ACTION_MY_PACKAGE_REPLACED',
        'restorePendingRelocks(',
        'reconcileExpiredUnlocks(',
    ])
    assert 'goAsync()' in receiver

    activity = need(ACTIVITY, [
        'EXTRA_TARGET_PACKAGE',
        'PIN Guard',
        'Package name',
        'Lock app',
        'Unlock permanently',
        'Unlock 1 min',
        'Unlock 5 min',
        'Unlock 15 min',
        'AppLockCoordinator',
        'reconcileExpiredUnlocks(',
        'operationResult',
    ])
    assert activity.index('operationResult') < activity.index('refreshState('), 'operation result must be preserved when refreshing state'

    need(DASHBOARD, [
        'App Lock',
        'AppLockActivity::class.java',
    ])

    need(MANIFEST, [
        '<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />',
        '<activity android:name=".AppLockActivity" android:exported="false" />',
        'android:name=".AppLockAlarmReceiver"',
        '<action android:name="android.intent.action.BOOT_COMPLETED" />',
        '<action android:name="android.intent.action.MY_PACKAGE_REPLACED" />',
    ])

    print('test_app_lock_pin_contract: PASS')


if __name__ == '__main__':
    main()
