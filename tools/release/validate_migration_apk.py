#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def fail(marker: str, detail: str = "") -> None:
    suffix = f": {detail}" if detail else ""
    raise SystemExit(f"{marker}{suffix}")


def require_file(path: Path, marker: str) -> Path:
    path = path.expanduser().resolve()
    if not path.is_file() or path.stat().st_size == 0:
        fail(marker, str(path))
    return path


def normalize_sha256(value: object) -> str:
    return re.sub(r"[^0-9A-Fa-f]", "", str(value)).upper()


def run_checked(args: list[str]) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(args, text=True, capture_output=True)
    if cp.returncode != 0:
        message = (cp.stderr or cp.stdout or "command failed").strip()
        fail("MIGRATION_VALIDATION_COMMAND_FAILED", message)
    return cp


def package_info(apk: Path) -> dict[str, object] | None:
    aapt = shutil.which("aapt2") or shutil.which("aapt")
    if not aapt:
        return None
    cp = subprocess.run([aapt, "dump", "badging", str(apk)], text=True, capture_output=True)
    if cp.returncode != 0:
        return None
    first = next((line for line in cp.stdout.splitlines() if line.startswith("package:")), "")
    if not first:
        return None
    def field(name: str) -> str | None:
        m = re.search(rf"\b{name}='([^']*)'", first)
        return m.group(1) if m else None
    return {
        "packageName": field("name"),
        "versionCode": field("versionCode"),
        "versionName": field("versionName"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--signing-metadata", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    apk = require_file(args.apk, "MIGRATION_APK_MISSING")
    metadata_path = require_file(args.signing_metadata, "MIGRATION_METADATA_MISSING")

    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail("MIGRATION_METADATA_INVALID", str(exc))

    required = (
        "schemaVersion",
        "rotationMinSdkVersion",
        "oldCertificateSha256",
        "newCertificateSha256",
        "apkSha256",
        "status",
    )
    missing = [name for name in required if name not in metadata]
    if missing:
        fail("MIGRATION_METADATA_INVALID", f"missing={','.join(missing)}")
    if metadata.get("schemaVersion") != 1:
        fail("MIGRATION_METADATA_INVALID", "schemaVersion must be 1")
    if metadata.get("status") != "MIGRATION_SIGNING_VERIFIED":
        fail("MIGRATION_METADATA_INVALID", "unexpected signing status")

    rotation_min_sdk = metadata.get("rotationMinSdkVersion")
    if not isinstance(rotation_min_sdk, int) or rotation_min_sdk < 28:
        fail("MIGRATION_METADATA_INVALID", "rotationMinSdkVersion invalid")

    old_cert = normalize_sha256(metadata.get("oldCertificateSha256"))
    new_cert = normalize_sha256(metadata.get("newCertificateSha256"))
    if len(old_cert) != 64 or len(new_cert) != 64 or old_cert == new_cert:
        fail("MIGRATION_METADATA_INVALID", "certificate fingerprints invalid")

    expected_apk_sha = normalize_sha256(metadata.get("apkSha256"))
    observed_apk_sha = hashlib.sha256(apk.read_bytes()).hexdigest().upper()
    if expected_apk_sha != observed_apk_sha:
        fail(
            "MIGRATION_APK_SHA256_MISMATCH",
            f"expected={expected_apk_sha} observed={observed_apk_sha}",
        )

    apksigner = shutil.which("apksigner")
    if not apksigner:
        fail("MIGRATION_PREFLIGHT_MISSING", "apksigner")
    verify = run_checked([apksigner, "verify", "--verbose", "--print-certs", str(apk)])

    report = {
        "schemaVersion": 1,
        "status": "MIGRATION_VALIDATION_PASS",
        "apk": str(apk),
        "apkSha256": observed_apk_sha,
        "rotationMinSdkVersion": rotation_min_sdk,
        "oldCertificateSha256": old_cert,
        "newCertificateSha256": new_cert,
        "package": package_info(apk),
        "apksignerVerify": verify.stdout.splitlines(),
        "sourceSigningMetadata": str(metadata_path),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print("MIGRATION_VALIDATION_PASS")
    print(f"apkSha256={observed_apk_sha}")
    print(f"rotationMinSdkVersion={rotation_min_sdk}")
    print(f"report={args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
