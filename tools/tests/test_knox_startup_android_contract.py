#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def main():
    manifest = (ROOT/'app-dpc/src/main/AndroidManifest.xml').read_text()
    receiver = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxBootReceiver.kt'
    controller = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxStartupController.kt'
    store = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxRuntimeStateStore.kt'
    admin = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/AioDeviceAdminReceiver.kt'
    package_control = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxAwarePackageController.kt'
    gate = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxRuntimeGate.kt'
    bridge = ROOT/'app-dpc/src/main/kotlin/io/dpcaio/app/KnoxRealLicenseStateBridge.kt'

    for p in [receiver, controller, store, package_control, gate, bridge]:
        assert p.exists(), f'missing {p.name}'

    assert 'android.permission.RECEIVE_BOOT_COMPLETED' in manifest
    assert 'android.permission.ACCESS_NETWORK_STATE' in manifest
    assert '.KnoxBootReceiver' in manifest
    assert 'android.intent.action.LOCKED_BOOT_COMPLETED' in manifest
    assert 'android.intent.action.MY_PACKAGE_REPLACED' in manifest

    ctxt = controller.read_text()
    for required in ['KnoxStartupGate', 'isDeviceOwnerApp', 'Build.MANUFACTURER', 'ALLOW_LAB_ACTIVE_WITH_DPM_FALLBACK', 'KnoxFlavorLicenseProvider.isLabSimulatedActive']:
        assert required in ctxt, f'controller missing {required}'

    atxt = admin.read_text()
    assert 'onEnabled' in atxt and 'onProfileProvisioningComplete' in atxt
    assert 'KnoxStartupController.evaluateAndPersist' in atxt

    ptxt = package_control.read_text()
    for required in ['setHidden', 'setSuspended', 'REAL_KNOX_REQUIRED']:
        assert required in ptxt, f'package fallback missing {required}'

    gtxt=gate.read_text()
    for required in ['isMdmGateActive', 'canManagePackagesWithDpm', 'canUseKnoxOnlyApis']:
        assert required in gtxt, f'gate missing {required}'
    assert 'KnoxLicenseResultInterpreter' in bridge.read_text()

    print('test_knox_startup_android_contract: PASS')

if __name__ == '__main__': main()
