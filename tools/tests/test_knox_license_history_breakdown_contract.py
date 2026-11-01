from pathlib import Path

STORE = Path('apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseHistoryStore.kt')
ACTIVITY = Path('apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt')


def need(path: Path, markers: list[str], label: str) -> str:
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'
    return text


def main() -> None:
    store = need(STORE, [
        'statusCounts: Map<KnoxLicenseActivationStatus, Int>',
        'sourceCounts: Map<KnoxLicenseAttemptSource, Int>',
        'KnoxLicenseActivationStatus.entries.associateWith',
        'KnoxLicenseAttemptSource.entries.associateWith',
        '.put("statusCounts"',
        '.put("sourceCounts"',
    ], 'history breakdown store')

    activity = need(ACTIVITY, [
        'StatusCounts=',
        'SourceCounts=',
        'historySummary.statusCounts',
        'historySummary.sourceCounts',
    ], 'history breakdown UI')

    forbidden = ['EnrollmentSecretStore', 'knoxLicenseKey', 'KNOX_SECRET_REF']
    for marker in forbidden:
        assert marker not in store, f'history breakdown store leaks secret surface: {marker}'
        assert marker not in activity, f'history breakdown UI leaks secret surface: {marker}'

    print('KNOX_LICENSE_HISTORY_BREAKDOWN_CONTRACT: PASS')


if __name__ == '__main__':
    main()
