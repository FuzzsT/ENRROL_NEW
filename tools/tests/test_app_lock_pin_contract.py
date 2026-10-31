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
        'pendingLock',
        'fun read(',
        'fun write(',
        'fun remove(',
        'fun all()',
    ])
    assert 'putBoolean(key(record.packageName, "pendingUnlock"), record.pendingUnlock)' in store
    assert 'putBoolean(key(record.packageName, "pendingLock"), record.pendingLock)' in store

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
        'pendingLock = true',
        'pendingLock = false',
        'it.pendingUnlock',
        'it.pendingLock',
        'private fun rollbackSuspension(packageName: String): Boolean',
        'rollbackVerified',
        'keepPendingRecovery(',
    ])
    assert coordinator.index('store.read(packageName)') < coordinator.index('setSuspended(packageName, false)'), 'unlock must verify App Lock ownership before unsuspending a package'
    assert coordinator.index('pendingUnlock = true') < coordinator.index('setSuspended(packageName, false)'), 'recoverable pending unlock must be persisted before unsuspending'
    lock_start = coordinator.index('fun lock(')
    lock_suspend = coordinator.index('policy.setSuspended(packageName, true)', lock_start)
    pending_lock_write = coordinator.index('pendingLock = true', lock_start)
    assert pending_lock_write < lock_suspend, 'recoverable lock ownership must be persisted before suspending a new package'
    assert coordinator.count('synchronized(transitionLock)') >= 4, 'lock/unlock/reconcile/restore transitions must share synchronization'
    assert 'setAndAllowWhileIdle(' not in coordinator, 'bounded App Lock unlocks must not rely on inexact alarms'
    assert 'Accessibility' not in coordinator
    assert 'overlay' not in coordinator.lower()

    unsuspend_index = coordinator.index('val outcome = policy.setSuspended(packageName, false)')
    non_verified_index = coordinator.index('if (outcome.verification != AppPolicyVerification.VERIFIED)', unsuspend_index)
    recovery_index = coordinator.index('keepPendingRecovery(', non_verified_index)
    assert recovery_index > non_verified_index, 'failed unsuspend verification must preserve fail-closed recovery state'

    assert coordinator.count('rollbackVerified') >= 3, 'rollback result must be checked on every post-unsuspend failure path'

    reconcile_start = coordinator.index('fun reconcileExpiredUnlocks(')
    restore_start = coordinator.index('fun restorePendingRelocks(', reconcile_start)
    reconcile_body = coordinator[reconcile_start:restore_start]
    assert 'keepPendingRecovery(record.copy(' in reconcile_body and 'pendingUnlock = true' in reconcile_body and 'pendingLock = false' in reconcile_body, 'failed expiry relock must persist recovery state and schedule a retry'

    restore_body = coordinator[restore_start:coordinator.index('fun state(', restore_start)]
    assert 'record.pendingLock' in restore_body, 'boot/update recovery must process incomplete lock ownership transitions'

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
        'Choose package',
        'RuntimeTargetPicker.showPackagePicker',
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
