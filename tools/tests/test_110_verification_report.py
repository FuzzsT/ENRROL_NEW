#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'RELEASE-VERIFICATION.json'
REQUIRED = {
    'SOURCE_VERIFIED',
    'APK_BUILD_VERIFIED',
    'ANDROID_ENTERPRISE_RUNTIME_VERIFIED',
    'KNOX_RUNTIME_VERIFIED',
    'SEM_RUNTIME_VERIFIED',
    'OEM_INTERNALS_RUNTIME_VERIFIED',
    'PACKAGE_TRUST_VERIFIED',
    'PROTECTED_OPERATION_VERIFIED',
    'DEVICE_OWNER_VERIFIED',
    'WORK_PROFILE_VERIFIED',
}
NON_PASS_RUNTIME = {
    'APK_BUILD_VERIFIED',
    'ANDROID_ENTERPRISE_RUNTIME_VERIFIED',
    'KNOX_RUNTIME_VERIFIED',
    'SEM_RUNTIME_VERIFIED',
    'OEM_INTERNALS_RUNTIME_VERIFIED',
    'DEVICE_OWNER_VERIFIED',
    'WORK_PROFILE_VERIFIED',
}
ALLOWED = {'PASS','FAIL','BLOCKED','NOT_RUN','UNAVAILABLE','STALE','PARTIAL','CONFLICT','ROLLED_BACK'}

def main() -> None:
    data = json.loads(REPORT.read_text('utf-8'))
    version = str(data.get('version', ''))
    parts = version.split('.')
    if len(parts) != 3 or not all(part.isdigit() for part in parts) or int(parts[0]) != 1 or tuple(map(int, parts)) < (1, 1, 0):
        raise AssertionError(f"report version must remain stable 1.x >=1.1.0, got {data.get('version')!r}")
    states = data.get('evidenceStates', {})
    missing = sorted(REQUIRED - set(states))
    if missing:
        raise AssertionError('missing evidence states: ' + ', '.join(missing))
    invalid = {k:v for k,v in states.items() if v not in ALLOWED}
    if invalid:
        raise AssertionError(f'invalid evidence states: {invalid}')
    if states['SOURCE_VERIFIED'] != 'PASS':
        raise AssertionError('source verification must be PASS after host/source/security gates')
    for key in NON_PASS_RUNTIME:
        if states[key] == 'PASS':
            raise AssertionError(f'{key} cannot be PASS without runtime/toolchain evidence')
    if states['PACKAGE_TRUST_VERIFIED'] not in {'PARTIAL','BLOCKED','NOT_RUN'}:
        raise AssertionError('package trust must retain partial/runtime semantics in source-only environment')
    if states['PROTECTED_OPERATION_VERIFIED'] not in {'PARTIAL','BLOCKED','NOT_RUN'}:
        raise AssertionError('protected operation must retain runtime semantics in source-only environment')
    if 'BUILD_BLOCKED' not in json.dumps(data):
        raise AssertionError('report must preserve build-readiness blocker')
    print('test_110_verification_report: PASS')

if __name__ == '__main__':
    main()
