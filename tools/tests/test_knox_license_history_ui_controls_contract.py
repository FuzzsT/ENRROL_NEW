from pathlib import Path

R = Path(__file__).resolve().parents[2]
APP = R / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
STORE = APP / 'KnoxLicenseHistoryStore.kt'
ACTIVITY = APP / 'KnoxEnterpriseCenterActivity.kt'
CI = R / '.github/workflows/pr-android-ci.yml'


def need(path: Path, markers: list[str], label: str) -> str:
    text = path.read_text('utf-8')
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'
    return text


def main() -> None:
    store = need(STORE, [
        'enum class KnoxLicenseHistoryFilter',
        'ALL', 'FAILED', 'RETRYABLE', 'MANUAL',
        'fun readRecent(limit: Int = 10, filter: KnoxLicenseHistoryFilter',
        'KnoxLicenseHistoryFilter.FAILED',
        'KnoxLicenseHistoryFilter.RETRYABLE',
        'KnoxLicenseHistoryFilter.MANUAL',
        'fun clear()',
        'remove(KEY_HISTORY)',
    ], 'history store')

    activity = need(ACTIVITY, [
        'private var historyFilter = KnoxLicenseHistoryFilter.ALL',
        'KPE activation history',
        'Filter: ALL',
        'Filter: FAILED',
        'Filter: RETRYABLE',
        'Filter: MANUAL',
        'Clear history',
        'KnoxLicenseHistoryStore(this@KnoxEnterpriseCenterActivity).clear()',
        'DateFormat.getDateTimeInstance(',
        '.format(Date(entry.timestampEpochMs))',
    ], 'history UI')

    for forbidden in ['KnoxLicenseRuntimeStore(this).write(', 'EnrollmentSecretStore', 'knoxLicenseKey']:
        assert forbidden not in activity, f'clear/filter UI must not mutate secret/runtime state: {forbidden}'
    for forbidden in ['EnrollmentSecretStore', 'knoxLicenseKey', 'KNOX_SECRET_REF']:
        assert forbidden not in store, f'history store must remain secret-free: {forbidden}'

    ci = CI.read_text('utf-8')
    assert 'test_knox_license_history_ui_controls_contract.py' in ci, 'CI must run KPE history UI controls contract'

    print('KNOX_LICENSE_HISTORY_UI_CONTROLS_CONTRACT: PASS')


if __name__ == '__main__':
    main()
