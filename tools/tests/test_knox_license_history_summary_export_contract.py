from pathlib import Path

R = Path(__file__).resolve().parents[2]
APP = R / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
STORE = APP / 'KnoxLicenseHistoryStore.kt'
ACTIVITY = APP / 'KnoxEnterpriseCenterActivity.kt'


def need(path: Path, markers, label: str):
    text = path.read_text('utf-8')
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'
    return text


def main():
    store = need(STORE, [
        'data class KnoxLicenseHistorySummary',
        'totalAttempts',
        'lastSuccessEpochMs',
        'lastFailureEpochMs',
        'fun summary(): KnoxLicenseHistorySummary',
        'fun exportJson(): String',
        '"schema"',
        '"dpc-aio.knox-kpe-history.v1"',
        '"entries"',
    ], 'history store')

    activity = need(ACTIVITY, [
        'KnoxLicenseHistoryStore(this).summary()',
        'Attempts=',
        'LastSuccess=',
        'LastFailure=',
        'Export history JSON',
        'Intent.ACTION_SEND',
        'type = "application/json"',
        'Intent.EXTRA_TEXT',
        'Intent.createChooser',
        'KnoxLicenseHistoryStore(this@KnoxEnterpriseCenterActivity).exportJson()',
    ], 'activity')

    forbidden = ['knoxLicenseKey', 'EnrollmentSecretStore', 'KNOX_SECRET_REF']
    for text, label in [(store, 'history store'), (activity, 'activity')]:
        for marker in forbidden:
            assert marker not in text, f'{label} must not access KPE secret: {marker}'

    print('KNOX_LICENSE_HISTORY_SUMMARY_EXPORT_CONTRACT: PASS')


if __name__ == '__main__':
    main()
