#!/usr/bin/env python3
"""Verify pinned historical release assets before invoking signer recovery."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
RECOVERY = ROOT / "tools" / "release" / "recover_persisted_signing_keystore.py"
ASSET_URL = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/DPC-AIO-signing-keystore.enc"
)
HISTORICAL_APK_URL = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/DPC-AIO-enterprise-release.apk"
)
EXPECTED_ENCRYPTED_ASSET_SHA256 = "C00E8E06D30A762C88925594B403468A4FBE031B97095DBAD0DE5C9E2FACD6BD"
EXPECTED_HISTORICAL_APK_SHA256 = "02C0E15DFB86C499369FCD64ABA3D6163DF52112DE231CC4230C2470D5AC1A7C"
EXPECTED_CERT_SHA256 = "E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4"


def download(url: str, destination: Path) -> None:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "DPC-AIO-stable-signer-integrity/1"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        if getattr(response, "status", 200) != 200:
            raise SystemExit(f"DOWNLOAD_FAILED: HTTP {response.status}: {url}")
        destination.write_bytes(response.read())


def verify_file_sha256(path: Path, expected: str, marker: str) -> str:
    observed = hashlib.sha256(path.read_bytes()).hexdigest().upper()
    if observed != expected.upper():
        raise SystemExit(f"{marker}: expected={expected.upper()} observed={observed}")
    return observed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--configure-github", action="store_true")
    parser.add_argument("--dispatch", action="store_true")
    args = parser.parse_args()

    if args.dispatch and not args.configure_github:
        parser.error("--dispatch requires --configure-github")
    if not RECOVERY.is_file():
        raise SystemExit(f"RECOVERY_TOOL_MISSING: {RECOVERY}")

    with tempfile.TemporaryDirectory(prefix="dpc-aio-verified-recovery-") as raw_tmp:
        tmp = Path(raw_tmp)
        encrypted = tmp / "DPC-AIO-signing-keystore.enc"
        historical_apk = tmp / "DPC-AIO-enterprise-release.apk"

        print("Downloading pinned historical signer asset...")
        download(ASSET_URL, encrypted)
        encrypted_sha = verify_file_sha256(
            encrypted,
            EXPECTED_ENCRYPTED_ASSET_SHA256,
            "PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH",
        )

        print("Downloading pinned historical APK...")
        download(HISTORICAL_APK_URL, historical_apk)
        apk_sha = verify_file_sha256(
            historical_apk,
            EXPECTED_HISTORICAL_APK_SHA256,
            "HISTORICAL_APK_DIGEST_MISMATCH",
        )

        print(f"Historical signer asset SHA-256: {encrypted_sha}")
        print(f"Historical APK SHA-256: {apk_sha}")
        print("Pinned historical asset verification: PASS")

        command = [
            sys.executable,
            str(RECOVERY),
            "--encrypted-file",
            str(encrypted),
            "--historical-apk-file",
            str(historical_apk),
            "--expected-sha256",
            EXPECTED_CERT_SHA256,
        ]
        if args.configure_github:
            command.append("--configure-github")
        if args.dispatch:
            command.append("--dispatch")

        completed = subprocess.run(command, check=False)
        return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
