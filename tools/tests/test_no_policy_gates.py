#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def require(cond, msg):
    if not cond:
        raise AssertionError(msg)


def main():
    contracts = (ROOT / 'tools/verify_android_contracts.py').read_text(encoding='utf-8')
    project = (ROOT / 'tools/verify_project.py').read_text(encoding='utf-8')
    app = (ROOT / 'apps/dpc/app/build.gradle.kts').read_text(encoding='utf-8')

    for token in [
        'GLOBAL_FORBIDDEN',
        'FORBIDDEN_HIDDEN_API',
        'FORBIDDEN_XPOSED',
        'FORBIDDEN_LSPOSED',
        'FORBIDDEN_REFLECTION_BYPASS',
        'FORBIDDEN_APPOP_MUTATION',
    ]:
        require(token not in contracts, f'policy gate token still present: {token}')

    require('production app depends on lab-tools' not in project,
            'verify_project must not reject lab-tools dependency')

    for dep in [':knox-license-lab', ':knox-mock-android', ':lab-tools']:
        require(f'implementation(project("{dep}"))' in app,
                f'{dep} must be globally available through implementation')

    for scoped in ['labImplementation(', 'tstImplementation(', 'engImplementation(']:
        require(scoped not in app, f'flavor-scoped policy dependency remains: {scoped}')

    print('test_no_policy_gates: PASS')


if __name__ == '__main__':
    main()
