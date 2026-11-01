from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECEIVER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseStatusReceiver.kt'
CI = ROOT / '.github/workflows/pr-android-ci.yml'


def need(text: str, markers: list[str], label: str) -> None:
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'


def main() -> None:
    receiver = RECEIVER.read_text(encoding='utf-8')
    ci = CI.read_text(encoding='utf-8')

    need(receiver, [
        'val stored = KnoxLicenseRuntimeStore(context).write(',
        'val retry = KnoxLicenseRetryCoordinator(context)',
        'KnoxLicenseActivationStatus.ACTIVE -> retry.cancel()',
        'KnoxLicenseActivationStatus.RETRYABLE -> retry.scheduleForRetry(stored)',
    ], 'status receiver retry routing')

    assert 'knoxLicenseKey' not in receiver, 'status receiver must not access KPE secret'
    assert 'EnrollmentSecretStore' not in receiver, 'status receiver must not read provisioning secret store'
    assert 'test_knox_license_status_retry_contract.py' in ci, 'CI must run status retry contract'

    print('KNOX_LICENSE_STATUS_RETRY_CONTRACT: PASS')


if __name__ == '__main__':
    main()
