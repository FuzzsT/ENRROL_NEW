#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


def normalize_sha256(value: str) -> str:
    return "".join(ch for ch in value.upper() if ch in "0123456789ABCDEF")


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"MIGRATION_PREFLIGHT_MISSING: {name}")
    return path


def cert_sha256(keytool: str, keystore: Path, alias: str, password_env: str) -> str:
    cp = subprocess.run(
        [
            keytool,
            "-list",
            "-v",
            "-keystore",
            str(keystore),
            "-storepass:env",
            password_env,
            "-alias",
            alias,
        ],
        text=True,
        capture_output=True,
        env=os.environ.copy(),
    )
    if cp.returncode != 0:
        raise SystemExit(f"MIGRATION_SIGNER_READ_FAILED: alias={alias}")
    for line in cp.stdout.splitlines():
        stripped = line.strip()
        if stripped.startswith("SHA256:"):
            value = normalize_sha256(stripped.split(":", 1)[1])
            if len(value) == 64:
                return value
    raise SystemExit(f"MIGRATION_CERT_SHA256_MISSING: alias={alias}")


def run_checked(args: list[str]) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(args, text=True, capture_output=True, env=os.environ.copy())
    if cp.returncode != 0:
        message = (cp.stderr or cp.stdout or "command failed").strip()
        raise SystemExit(f"MIGRATION_COMMAND_FAILED: {message}")
    return cp


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-apk", type=Path, required=True)
    parser.add_argument("--output-apk", type=Path, required=True)
    parser.add_argument("--old-keystore", type=Path, required=True)
    parser.add_argument("--old-alias", required=True)
    parser.add_argument("--new-keystore", type=Path, required=True)
    parser.add_argument("--new-alias", required=True)
    parser.add_argument("--lineage", type=Path, required=True)
    parser.add_argument("--old-cert-sha256", required=True)
    parser.add_argument("--new-cert-sha256", required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--rotation-min-sdk-version", type=int, default=33)
    args = parser.parse_args()

    for path in (args.input_apk, args.old_keystore, args.new_keystore, args.lineage):
        if not path.is_file() or path.stat().st_size == 0:
            raise SystemExit(f"MIGRATION_INPUT_MISSING: {path}")

    if args.rotation_min_sdk_version < 28:
        raise SystemExit("MIGRATION_ROTATION_MIN_SDK_INVALID")

    keytool = require_tool("keytool")
    apksigner = require_tool("apksigner")

    expected_old = normalize_sha256(args.old_cert_sha256)
    expected_new = normalize_sha256(args.new_cert_sha256)
    observed_old = cert_sha256(keytool, args.old_keystore, args.old_alias, "DPC_AIO_MIGRATION_OLD_STORE_PASSWORD")
    observed_new = cert_sha256(keytool, args.new_keystore, args.new_alias, "DPC_AIO_MIGRATION_NEW_STORE_PASSWORD")

    if observed_old != expected_old:
        raise SystemExit(f"MIGRATION_OLD_CERT_MISMATCH expected={expected_old} observed={observed_old}")
    if observed_new != expected_new:
        raise SystemExit(f"MIGRATION_NEW_CERT_MISMATCH expected={expected_new} observed={observed_new}")
    if observed_old == observed_new:
        raise SystemExit("MIGRATION_SIGNERS_NOT_DISTINCT")

    args.output_apk.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.parent.mkdir(parents=True, exist_ok=True)

    run_checked(
        [
            apksigner,
            "sign",
            "--out",
            str(args.output_apk),
            "--rotation-min-sdk-version",
            str(args.rotation_min_sdk_version),
            "--ks",
            str(args.old_keystore),
            "--ks-key-alias",
            args.old_alias,
            "--ks-pass",
            "env:DPC_AIO_MIGRATION_OLD_STORE_PASSWORD",
            "--key-pass",
            "env:DPC_AIO_MIGRATION_OLD_KEY_PASSWORD",
            "--next-signer",
            "--ks",
            str(args.new_keystore),
            "--ks-key-alias",
            args.new_alias,
            "--ks-pass",
            "env:DPC_AIO_MIGRATION_NEW_STORE_PASSWORD",
            "--key-pass",
            "env:DPC_AIO_MIGRATION_NEW_KEY_PASSWORD",
            "--lineage",
            str(args.lineage),
            str(args.input_apk),
        ]
    )

    verify = run_checked([apksigner, "verify", "--verbose", "--print-certs", str(args.output_apk)])
    apk_sha = hashlib.sha256(args.output_apk.read_bytes()).hexdigest()

    args.metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "rotationMinSdkVersion": args.rotation_min_sdk_version,
                "oldCertificateSha256": observed_old,
                "newCertificateSha256": observed_new,
                "apkSha256": apk_sha,
                "apksignerVerify": verify.stdout.splitlines(),
                "status": "MIGRATION_SIGNING_VERIFIED",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    print("MIGRATION_SIGNING_VERIFIED")
    print(f"oldCertificateSha256={observed_old}")
    print(f"newCertificateSha256={observed_new}")
    print(f"apkSha256={apk_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
