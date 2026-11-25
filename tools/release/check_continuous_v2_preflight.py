#!/usr/bin/env python3
"""Fail-closed checks for the opt-in Continuous v2 release route."""
import os
import re
import sys

REPOSITORY = 'FuzzsT/ENRROL_NEW'
TAG = 'dpc-aio-continuous-v2'
LEGACY_CERT = 'E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4'
REQUIRED = (
    'DPC_AIO_V2_RELEASE_KEYSTORE_B64',
    'DPC_AIO_V2_RELEASE_STORE_PASSWORD',
    'DPC_AIO_V2_RELEASE_KEY_ALIAS',
    'DPC_AIO_V2_RELEASE_KEY_PASSWORD',
)


def normalize_pin(value):
    return re.sub(r'[:\s]', '', value).upper()


def validate(env):
    if (env.get('GITHUB_REPOSITORY'), env.get('GITHUB_REF'),
            env.get('GITHUB_EVENT_NAME'), env.get('DPC_AIO_RELEASE_TAG')) != (
            REPOSITORY, 'refs/heads/main', 'workflow_dispatch', TAG):
        raise ValueError('CONTINUOUS_V2_ROUTE_INVALID')
    missing = [name for name in REQUIRED if not env.get(name, '').strip()]
    if missing:
        raise ValueError('CONTINUOUS_V2_SIGNING_CONFIG_MISSING: ' + ', '.join(missing))
    pin = normalize_pin(env.get('DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256', ''))
    if not re.fullmatch(r'[0-9A-F]{64}', pin):
        raise ValueError('CONTINUOUS_V2_CERT_PIN_INVALID')
    if pin == LEGACY_CERT:
        raise ValueError('CONTINUOUS_V2_LEGACY_CERT_REJECTED')
    return pin


def main():
    try:
        validate(os.environ)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    print('CONTINUOUS_V2_PREFLIGHT_CONFIG: PASS (certificate verification still required)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
