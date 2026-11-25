#!/usr/bin/env python3
"""Check v2 release metadata before publication; no network or signing operations."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

TAG = 'dpc-aio-continuous-v2'
APK = 'DPC-AIO-enterprise-release.apk'
URL = 'https://github.com/FuzzsT/ENRROL_NEW/releases/download/' + TAG + '/' + APK
LEGACY = 'E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4'


def require(condition, marker):
    if not condition:
        raise ValueError('CONTINUOUS_V2_' + marker)


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def pin(value):
    return re.sub(r'[:\s]', '', value).upper()


def valid_pin(value):
    return bool(re.fullmatch(r'[0-9A-F]{64}', value)) and value != LEGACY


def manifest_fields(obj):
    code, size, digest = obj.get('versionCode'), obj.get('apkSize'), obj.get('apkSha256')
    require(type(obj.get('schemaVersion')) is int and obj['schemaVersion'] == 1
            and obj.get('channel') == 'continuous-v2' and obj.get('packageName') == 'io.dpcaio.app'
            and obj.get('apkUrl') == URL and type(code) is int and 0 < code <= 2100000000
            and type(size) is int and size > 0 and isinstance(digest, str)
            and bool(re.fullmatch(r'[0-9a-fA-F]{64}', digest)), 'MANIFEST_INVALID')
    return code, size, digest.lower()


def guard(args):
    require(args.tag == TAG, 'ROUTE_INVALID')
    expected = pin(args.expected_pin)
    candidate_pin = pin((args.dist / 'signing-cert-sha256.txt').read_text())
    require(valid_pin(expected) and candidate_pin == expected
            and (args.dist / 'signing-mode.txt').read_text().strip() == 'stable', 'SIGNER_MISMATCH')
    require(not (args.dist / 'DPC-AIO-signing-keystore.enc').exists(), 'LEGACY_KEYSTORE_ASSET_FORBIDDEN')
    code, size, digest = manifest_fields(load(args.dist / 'self-update.json'))
    apk = args.dist / APK
    require(sha(apk) == digest and apk.stat().st_size == size, 'CANDIDATE_DIGEST_MISMATCH')
    if args.first_release:
        return
    prior = args.prior
    release = load(prior / 'release.json')
    require(release.get('tag_name') == TAG, 'ROUTE_INVALID')
    def remote_digest(name):
        assets = [a for a in release['assets'] if a.get('name') == name]
        require(len(assets) == 1, 'PRIOR_DIGEST_MISMATCH')
        return assets[0].get('digest')
    for name in ('self-update.json', 'signing-cert-sha256.txt'):
        require(remote_digest(name) == 'sha256:' + sha(prior / name), 'PRIOR_DIGEST_MISMATCH')
    require(pin((prior / 'signing-cert-sha256.txt').read_text()) == expected, 'SIGNER_CHANGED')
    old_code, _, old_digest = manifest_fields(load(prior / 'self-update.json'))
    require(remote_digest(APK) == 'sha256:' + old_digest, 'PRIOR_DIGEST_MISMATCH')
    require(code >= old_code, 'ROLLBACK')
    require(code != old_code or digest == old_digest, 'VERSION_REUSE')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dist', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--expected-pin', required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--first-release', action='store_true')
    mode.add_argument('--prior', type=Path)
    try:
        guard(parser.parse_args())
    except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
        message = str(error)
        print(message if message.startswith('CONTINUOUS_V2_') else 'CONTINUOUS_V2_METADATA_INVALID', file=sys.stderr)
        return 1
    print('CONTINUOUS_V2_RELEASE_GUARD_OK')
    return 0


if __name__ == '__main__':
    sys.exit(main())
