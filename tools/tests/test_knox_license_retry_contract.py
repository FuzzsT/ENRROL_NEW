from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RETRY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseRetryCoordinator.kt'
BOOT = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxBootReceiver.kt'
CONTROLLER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseController.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'
CI = ROOT / '.github/workflows/pr-android-ci.yml'


def need(text: str, markers: list[str], label: str) -> None:
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'


def main() -> None:
    assert RETRY.is_file(), 'missing KnoxLicenseRetryCoordinator.kt'
    retry = RETRY.read_text(encoding='utf-8')
    boot = BOOT.read_text(encoding='utf-8')
    controller = CONTROLLER.read_text(encoding='utf-8')
    manifest = MANIFEST.read_text(encoding='utf-8')
    ci = CI.read_text(encoding='utf-8')

    need(retry, [
        'class KnoxLicenseRetryCoordinator',
        'KnoxLicenseActivationStatus.RETRYABLE',
        'JobScheduler',
        'JobInfo.Builder',
        'setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)',
        '.activateIfRequested(KnoxLicenseAttemptSource.RETRY_JOB)',
        'class KnoxLicenseRetryJobService',
        'jobFinished(params,',
    ], 'retry coordinator')

    need(boot, [
        'KnoxLicenseRetryCoordinator(context).onBootOrPackageReplace()',
    ], 'boot receiver')

    need(controller, [
        'KnoxLicenseRetryCoordinator(context).scheduleForRetry(',
    ], 'license controller')

    need(manifest, [
        'android:name=".KnoxLicenseRetryJobService"',
        'android.permission.BIND_JOB_SERVICE',
    ], 'manifest')

    assert 'knoxLicenseKey' not in retry, 'retry coordinator must not access or persist KPE secret'
    assert 'EnrollmentSecretStore' not in retry, 'retry coordinator must delegate secret handling to controller'
    assert 'test_knox_license_retry_contract.py' in ci, 'CI must run retry contract'

    print('KNOX_LICENSE_RETRY_CONTRACT: PASS')


if __name__ == '__main__':
    main()
