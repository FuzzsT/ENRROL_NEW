#!/usr/bin/env python3
"""Verify an APK and print its signer certificate SHA-256 fingerprint."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess


CERT_PATTERN = re.compile(
    r"certificate\s+SHA-?256\s+digest\s*:\s*((?:[0-9a-fA-F]{2}:?){32})",
    re.IGNORECASE,
)


def read_fingerprint(apksigner: Path, apk: Path) -> str:
    result = subprocess.run(
        [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"apksigner rejected {apk} (exit {result.returncode}):\n{result.stdout.rstrip()}"
        )
    match = CERT_PATTERN.search(result.stdout)
    if not match:
        raise RuntimeError(
            "apksigner verified the APK but did not report a SHA-256 certificate digest:\n"
            + result.stdout.rstrip()
        )
    return match.group(1).replace(":", "").upper()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apksigner", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    if not args.apksigner.is_file():
        parser.error(f"apksigner not found: {args.apksigner}")
    if not args.apk.is_file():
        parser.error(f"APK not found: {args.apk}")
    try:
        print(read_fingerprint(args.apksigner, args.apk))
    except RuntimeError as error:
        parser.exit(2, f"{error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
