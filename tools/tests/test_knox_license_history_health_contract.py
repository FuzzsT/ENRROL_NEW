from pathlib import Path

STORE = Path('apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxLicenseHistoryStore.kt')
UI = Path('apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt')


def need(path: Path, markers, label: str):
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        assert marker in text, f'{label}: missing {marker}'
    return text


def main():
    store = need(STORE, [
        'enum class KnoxLicenseHealthVerdict',
        'HEALTHY',
        'DEGRADED',
        'FAILING',
        'val successRate: Double',
        'val consecutiveProblemAttempts: Int',
        'val lastStableActiveEpochMs: Long?',
        'val healthVerdict: KnoxLicenseHealthVerdict',
        '.put("successRate", summary.successRate)',
        '.put("consecutiveProblemAttempts", summary.consecutiveProblemAttempts)',
        '.put("lastStableActiveEpochMs", summary.lastStableActiveEpochMs ?: JSONObject.NULL)',
        '.put("healthVerdict", summary.healthVerdict.name)',
    ], 'history health store')

    ui = need(UI, [
        'SuccessRate=',
        'ConsecutiveProblems=',
        'LastStableActive=',
        'Health=',
    ], 'history health ui')

    forbidden = ('EnrollmentSecretStore', 'knoxLicenseKey', 'KNOX_SECRET_REF')
    combined = (store + ui).lower()
    for marker in forbidden:
        assert marker.lower() not in combined, f'health diagnostics must not access secret material: {marker}'

    print('KNOX_LICENSE_HISTORY_HEALTH_CONTRACT: PASS')


if __name__ == '__main__':
    main()
