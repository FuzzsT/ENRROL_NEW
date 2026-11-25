#!/usr/bin/env python3
"""Resolve public certificate fingerprints without silently hiding conflicts."""
import argparse
import os
import re
import sys


def normalize(value):
    if not value:
        return ''
    value = re.sub(r'[: \t\r\n]', '', value).upper()
    if not re.fullmatch(r'[0-9A-F]{64}', value):
        raise ValueError('SIGNING_CERT_PIN_INVALID')
    return value


def resolve(variable, secret, allow_missing=False):
    variable, secret = normalize(variable), normalize(secret)
    if variable and secret and variable != secret:
        raise ValueError('SIGNING_CERT_SOURCE_CONFLICT')
    pin = variable or secret
    if not pin and not allow_missing:
        raise ValueError('SIGNING_CERT_PIN_MISSING')
    return pin


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('name', choices=[
        'DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256',
        'DPC_AIO_CONFIGURED_SIGNING_CERT_SHA256',
        'EXPECTED_V2_CERT', 'OLD_CERT_SHA256', 'NEW_CERT_SHA256',
    ])
    parser.add_argument('--allow-missing', action='store_true')
    args = parser.parse_args()
    try:
        pin = resolve(os.environ.get(args.name + '_VARIABLE', ''),
                      os.environ.get(args.name + '_SECRET', ''),
                      args.allow_missing)
    except ValueError as exc:
        print(f'{exc}: {args.name}', file=sys.stderr)
        return 1
    print(pin)
    return 0


if __name__ == '__main__':
    sys.exit(main())
