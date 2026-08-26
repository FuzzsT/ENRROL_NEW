#!/usr/bin/env python3
from pathlib import Path
import sys

FORBIDDEN_NAMES = {'dpc-aio-lab-private.pem'}
PRIVATE_KEY_MARKERS = (
    '-----BEGIN PRIVATE KEY-----',
    '-----BEGIN RSA PRIVATE KEY-----',
    '-----BEGIN EC PRIVATE KEY-----',
    '-----BEGIN OPENSSH PRIVATE KEY-----',
)

def scan(root: Path):
    findings=[]
    for path in root.rglob('*'):
        if not path.is_file():
            continue
        rel=path.relative_to(root).as_posix()
        if path.name in FORBIDDEN_NAMES:
            findings.append(f'forbidden filename: {rel}')
            continue
        if path.stat().st_size > 2_000_000:
            continue
        try:
            text=path.read_text('utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        lines={line.strip() for line in text.splitlines()}
        if any(marker in lines for marker in PRIVATE_KEY_MARKERS):
            findings.append(f'private key material: {rel}')
    return findings

def main(argv=None):
    args=sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print('usage: scan_release_tree.py <staged-tree>', file=sys.stderr)
        return 2
    root=Path(args[0]).resolve()
    findings=scan(root)
    if findings:
        for finding in findings:
            print(f'FAIL: {finding}', file=sys.stderr)
        return 1
    print('RELEASE_SECRET_SCAN: PASS')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
