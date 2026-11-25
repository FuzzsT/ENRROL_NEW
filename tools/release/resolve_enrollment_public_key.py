#!/usr/bin/env python3
"""Resolve an explicitly trusted Ed25519 public key; never generate trust anchors."""
import base64
import binascii
import os
import sys

PREFIX = bytes.fromhex('302a300506032b6570032100')
SOURCES = ('ENROLLMENT_PUBLIC_KEY_VARIABLE', 'ENROLLMENT_PUBLIC_KEY_SECRET', 'ENROLLMENT_PUBLIC_KEY_INPUT')


def normalize(value):
    value = value.strip()
    if value.startswith('-----BEGIN PUBLIC KEY-----') and value.endswith('-----END PUBLIC KEY-----'):
        value = value[len('-----BEGIN PUBLIC KEY-----'):-len('-----END PUBLIC KEY-----')]
    try:
        raw = base64.b64decode(''.join(value.split()), validate=True)
    except (ValueError, binascii.Error):
        raise ValueError('ENROLLMENT_PUBLIC_KEY_INVALID') from None
    if len(raw) != 44 or not raw.startswith(PREFIX):
        raise ValueError('ENROLLMENT_PUBLIC_KEY_INVALID')
    return base64.b64encode(raw).decode('ascii')


def main():
    try:
        keys = {normalize(os.environ[name]) for name in SOURCES if os.environ.get(name, '').strip()}
        if len(keys) > 1:
            raise ValueError('ENROLLMENT_PUBLIC_KEY_SOURCE_CONFLICT')
        if not keys and os.environ.get('DPC_AIO_ENROLLMENT_ENDPOINT', '').strip():
            raise ValueError('ENROLLMENT_PUBLIC_KEY_MISSING')
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(next(iter(keys), ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
