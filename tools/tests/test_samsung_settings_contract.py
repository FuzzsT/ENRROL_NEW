#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def need(path, tokens):
    text=(ROOT/path).read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path} missing {token}'

def main():
    need('apps/dpc/modules/samsung/android/src/main/kotlin/io/dpcaio/samsung/settings/android/AndroidSamsungSettingGateway.kt', [
        'Settings.System.getString', 'Settings.Secure.getString', 'Settings.Global.getString',
        'Settings.System.canWrite', 'WRITE_SECURE_SETTINGS', 'SHIZUKU_SETTINGS'
    ])
    need('apps/dpc/modules/samsung/android/src/main/kotlin/io/dpcaio/samsung/settings/android/AndroidSettingStabilityMonitor.kt', [
        'ContentObserver', 'registerContentObserver', 'unregisterContentObserver'
    ])
    need('apps/dpc/app/src/main/kotlin/io/dpcaio/app/SamsungSettingsEditorActivity.kt', [
        'SamsungSettingEditCoordinator', 'AndroidSamsungSettingGateway', 'SettingNamespace'
    ])
    print('test_samsung_settings_contract: PASS')

if __name__ == '__main__': main()
