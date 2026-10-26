#!/usr/bin/env python3
"""Extract the first signer certificate SHA-256 digest from apksigner output."""

from __future__ import annotations

import re
import sys

_SIGNER_SHA256 = re.compile(
    r"^Signer .+ certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)\s*$"
)


def extract_signer_sha256(text: str) -> str:
    for raw_line in text.splitlines():
        match = _SIGNER_SHA256.match(raw_line.strip())
        if match:
            return match.group(1).replace(":", "").upper()
    raise ValueError("apksigner output does not contain a signer certificate SHA-256 digest")


def main() -> int:
    text = sys.stdin.read()
    try:
        print(extract_signer_sha256(text))
    except ValueError as exc:
        print(f"APKSIGNER_CERT_PARSE_FAILED: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
