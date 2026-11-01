from pathlib import Path

R = Path(__file__).resolve().parents[2]
APP = R / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'


def need(path, markers):
    text = path.read_text('utf-8')
    for marker in markers:
        assert marker in text, f'{path.name}: missing {marker}'
    return text


def main():
    history = APP / 'KnoxLicenseHistoryStore.kt'
    assert history.exists(), 'missing KnoxLicenseHistoryStore.kt'
    text = need(history, [
        'enum class KnoxLicenseAttemptSource',
        'PROVISIONING', 'RETRY_JOB', 'MANUAL', 'BROADCAST',
        'data class KnoxLicenseHistoryEntry',
        'timestampEpochMs', 'previousStatus', 'newStatus', 'errorCode',
        'createDeviceProtectedStorageContext()',
        'MAX_ENTRIES = 50',
        'fun append(', 'fun readRecent(',
    ])
    for forbidden in ['knoxLicenseKey', 'EnrollmentSecretStore', 'KNOX_SECRET_REF']:
        assert forbidden not in text, f'history store must not access KPE secret: {forbidden}'

    controller = need(APP / 'KnoxLicenseController.kt', [
        'KnoxLicenseAttemptSource.PROVISIONING',
        'KnoxLicenseHistoryStore(context).append(',
    ])
    retry = need(APP / 'KnoxLicenseRetryCoordinator.kt', [
        'KnoxLicenseAttemptSource.RETRY_JOB',
    ])
    receiver = need(APP / 'KnoxLicenseStatusReceiver.kt', [
        'KnoxLicenseAttemptSource.BROADCAST',
        'KnoxLicenseHistoryStore(context).append(',
    ])
    activity = need(APP / 'KnoxEnterpriseCenterActivity.kt', [
        'KnoxLicenseHistoryStore(this).readRecent(',
        'KPE activation history',
        'KnoxLicenseAttemptSource.MANUAL',
        'Previous=', 'New=', 'Source=',
    ])
    for text_value, name in [(receiver, 'receiver'), (activity, 'activity')]:
        for forbidden in ['knoxLicenseKey', 'EnrollmentSecretStore', 'KNOX_SECRET_REF']:
            assert forbidden not in text_value, f'{name} must not access KPE secret: {forbidden}'

    print('KNOX_LICENSE_HISTORY_CONTRACT: PASS')


if __name__ == '__main__':
    main()
