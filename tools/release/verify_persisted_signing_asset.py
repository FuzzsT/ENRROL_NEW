#!/usr/bin/env python3
"""Compare downloaded encrypted signer bytes to GitHub asset SHA-256 metadata."""
import hashlib
from pathlib import Path
import re
import sys


def main():
    if len(sys.argv) != 3 or not re.fullmatch(r'sha256:[0-9a-fA-F]{64}', sys.argv[2]):
        print('PERSISTED_SIGNING_ASSET_DIGEST_INVALID', file=sys.stderr)
        return 1
    try:
        digest = hashlib.sha256()
        with Path(sys.argv[1]).open('rb') as fh:
            for chunk in iter(lambda: fh.read(65536), b''):
                digest.update(chunk)
    except OSError:
        print('PERSISTED_SIGNING_ASSET_READ_FAILED', file=sys.stderr)
        return 1
    if digest.hexdigest() != sys.argv[2][7:].lower():
        print('PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH', file=sys.stderr)
        return 1
    print('PERSISTED_SIGNING_ASSET_DIGEST_MATCH')
    return 0


if __name__ == '__main__':
    sys.exit(main())
