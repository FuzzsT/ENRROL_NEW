from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt'
RETRY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseRetryCoordinator.kt'


def need(path: Path, markers):
    text = path.read_text('utf-8')
    for marker in markers:
        assert marker in text, f'{path.name}: missing {marker}'
    return text


def main():
    activity = need(ACTIVITY, [
        'KnoxLicenseRuntimeStore(this).read()',
        'KnoxLicenseRetryCoordinator(this).isScheduled()',
        'KPE license diagnostics',
        'Status=',
        'Detail=',
        'ErrorCode=',
        'RetryScheduled=',
        'Retry now',
        'KnoxLicenseController(this@KnoxEnterpriseCenterActivity).activateIfRequested()',
        'render()',
    ])
    need(RETRY, [
        'fun isScheduled(): Boolean',
        'scheduler?.getPendingJob(JOB_ID) != null',
    ])
    forbidden = ['knoxLicenseKey', 'EnrollmentSecretStore(', 'KNOX_SECRET_REF']
    for marker in forbidden:
        assert marker not in activity, f'activity must not expose secret access: {marker}'
    print('KNOX_LICENSE_UI_DIAGNOSTICS_CONTRACT: PASS')


if __name__ == '__main__':
    main()
