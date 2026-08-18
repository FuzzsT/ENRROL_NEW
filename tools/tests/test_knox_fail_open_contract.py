#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def main():
    app = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/DpcAioApplication.kt'
    manifest = (ROOT/'app-dpc/src/main/AndroidManifest.xml').read_text()
    assert app.exists(), 'DpcAioApplication missing'
    text = app.read_text()
    assert 'KnoxStartupController.evaluateAndPersist' in text
    assert 'android:name=".DpcAioApplication"' in manifest

    knox_files = [
        ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxStartupController.kt',
        ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxBootReceiver.kt',
        ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/AioDeviceAdminReceiver.kt',
    ]
    forbidden = ['reboot(', 'shutdown(', 'wipeData(', 'MASTER_CLEAR', 'ACTION_FACTORY_RESET']
    for p in knox_files:
        t=p.read_text()
        for token in forbidden:
            assert token not in t, f'{p.name} must not perform {token} on license state'
    print('test_knox_fail_open_contract: PASS')

if __name__ == '__main__': main()
