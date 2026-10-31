#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CORE = ROOT / 'apps/dpc/modules/app-management/core/src/main/kotlin/io/dpcaio/appmanager/PinGuardPolicy.kt'
STORE = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidPinGuardStore.kt'
VERIFIER = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/PinGuardVerifier.kt'
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PinGuardActivity.kt'
DASHBOARD = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    core = need(CORE, [
        'enum class PinGuardResult',
        'NOT_CONFIGURED',
        'INVALID',
        'LOCKED_OUT',
        'SUCCESS',
    ])
    store = need(STORE, [
        'algorithm',
        'salt',
        'iterations',
        'verifier',
        'failedAttempts',
        'lockoutUntilMillis',
    ])
    verifier = need(VERIFIER, [
        'PBKDF2WithHmacSHA256',
        'SecureRandom',
        'MessageDigest.isEqual',
        'CharArray',
        '.fill(',
    ])

    forbidden = ['putString("pin"', "putString('pin'", 'plainPin', 'rawPin']
    combined = store + verifier
    for token in forbidden:
        assert token not in combined, f'PIN guard must not persist plaintext PIN marker: {token}'

    assert 'lockoutUntilMillis' in core or 'lockoutUntilMillis' in verifier, 'lockout decision must be represented in PIN guard logic'

    activity = need(ACTIVITY, [
        'PinGuardVerifier',
        'Set / Change PIN',
        'Verify PIN',
        'Disable PIN',
        'currentPin',
        'newPin',
        'confirmPin',
        'verifier.verify(',
        'verifier.configure(',
        'verifier.clear()',
        'Executors.newSingleThreadExecutor()',
        'runOnUiThread',
        'setBusy(',
        'isEnabled = !busy',
    ])
    assert activity.index('verifier.verify(') < activity.index('verifier.clear()'), 'Disable PIN must verify current PIN before clearing verifier state'
    need(DASHBOARD, ['PIN Guard', 'PinGuardActivity::class.java'])
    need(MANIFEST, ['<activity android:name=".PinGuardActivity" android:exported="false" />'])

    print('test_pin_guard_contract: PASS')


if __name__ == '__main__':
    main()
