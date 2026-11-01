#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTROLLER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseController.kt'
RECEIVER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseStatusReceiver.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'


def require(text: str, *needles: str) -> None:
    for needle in needles:
        assert needle in text, f'missing Knox license controller contract marker: {needle}'


def main() -> None:
    assert CONTROLLER.is_file(), 'missing KnoxLicenseController.kt'
    assert RECEIVER.is_file(), 'missing KnoxLicenseStatusReceiver.kt'

    controller = CONTROLLER.read_text('utf-8')
    receiver = RECEIVER.read_text('utf-8')
    manifest = MANIFEST.read_text('utf-8')

    require(
        controller,
        'enum class KnoxLicenseActivationStatus',
        'ACTIVE',
        'RETRYABLE',
        'FAILED',
        'UNSUPPORTED',
        'KnoxProvisioningSecretCapture.isAutoActivateRequested(context)',
        'EnrollmentSecretStore(context).get(KnoxProvisioningSecretCapture.KNOX_SECRET_REF)',
        '.knoxLicenseKey',
        'DevicePolicyManager',
        'isDeviceOwnerApp(context.packageName)',
        'Build.MANUFACTURER.equals("samsung", ignoreCase = true)',
        'com.samsung.android.knox.license.KnoxEnterpriseLicenseManager',
        'getInstance',
        'activateLicense',
        'KnoxLicenseResultInterpreter',
    )
    require(
        receiver,
        'com.samsung.android.knox.intent.action.KNOX_LICENSE_STATUS',
        'KnoxEnterpriseLicenseManager',
        'EXTRA_LICENSE_STATUS',
        'EXTRA_LICENSE_ERROR_CODE',
        'KnoxLicenseRuntimeStore',
        'KnoxLicenseResultInterpreter',
    )
    require(
        manifest,
        'android:name=".KnoxLicenseStatusReceiver"',
        'com.samsung.android.knox.intent.action.KNOX_LICENSE_STATUS',
    )

    combined = controller + '\n' + receiver
    for forbidden in ['Log.d(', 'Log.i(', 'Log.v(', 'println(', 'printStackTrace(']:
        assert forbidden not in combined, f'license flow must not log secret-bearing runtime data: {forbidden}'

    print('KNOX_LICENSE_CONTROLLER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
