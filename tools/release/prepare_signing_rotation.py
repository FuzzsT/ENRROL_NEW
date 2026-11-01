#!/usr/bin/env python3
"""Prepare a local Android signing-key rotation bundle without publishing anything.

This tool verifies the recovered historical signer, creates a fresh PKCS12 signer,
and generates an apksigner signing-certificate lineage. It never configures GitHub
Secrets and never dispatches a workflow.
"""

from __future__ import annotations

import argparse
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile


EXPECTED_OLD_CERT_SHA256 = "E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4"
DEFAULT_OLD_ALIAS = "dpc-aio-enterprise"
DEFAULT_NEW_ALIAS = "dpc-aio-enterprise-rotated"
DEFAULT_ROTATION_MIN_SDK = 33


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"ROTATION_PREFLIGHT_MISSING: required tool not found: {name}")
    return path


def find_apksigner() -> str:
    direct = shutil.which("apksigner") or shutil.which("apksigner.bat")
    if direct:
        return direct
    roots = [os.environ.get("ANDROID_SDK_ROOT", ""), os.environ.get("ANDROID_HOME", "")]
    candidates: list[Path] = []
    for raw_root in roots:
        if not raw_root:
            continue
        build_tools = Path(raw_root) / "build-tools"
        if not build_tools.is_dir():
            continue
        candidates.extend(build_tools.glob("*/apksigner"))
        candidates.extend(build_tools.glob("*/apksigner.bat"))
    for candidate in sorted(candidates, reverse=True):
        if candidate.is_file():
            return str(candidate)
    raise SystemExit(
        "ROTATION_PREFLIGHT_MISSING: apksigner not found in PATH or Android SDK build-tools"
    )


def chmod_private(path: Path) -> None:
    try:
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass


def read_secret(env_name: str, prompt: str, *, confirm: bool = False) -> str:
    value = os.environ.get(env_name, "")
    if value:
        return value
    value = getpass.getpass(prompt)
    if confirm:
        repeated = getpass.getpass("Repeat new signing password: ")
        if value != repeated:
            raise SystemExit("NEW_SIGNING_PASSWORD_MISMATCH: entered passwords differ")
    return value


def cert_fingerprint(keytool: str, keystore: Path, alias: str, env_name: str) -> str:
    env = os.environ.copy()
    with tempfile.NamedTemporaryFile(prefix="dpc-aio-cert-", suffix=".der", delete=False) as tf:
        cert_path = Path(tf.name)
    try:
        cp = subprocess.run(
            [
                keytool,
                "-exportcert",
                "-keystore",
                str(keystore),
                "-storetype",
                "PKCS12",
                "-alias",
                alias,
                "-storepass:env",
                env_name,
                "-file",
                str(cert_path),
            ],
            env=env,
            text=True,
            capture_output=True,
        )
        if cp.returncode != 0 or not cert_path.exists() or cert_path.stat().st_size == 0:
            raise SystemExit(
                f"SIGNER_READ_FAILED: could not read alias {alias!r} from {keystore}"
            )
        return hashlib.sha256(cert_path.read_bytes()).hexdigest().upper()
    finally:
        cert_path.unlink(missing_ok=True)


def run_checked(args: list[str], *, env: dict[str, str]) -> None:
    cp = subprocess.run(args, env=env, text=True, capture_output=True)
    if cp.returncode != 0:
        message = (cp.stderr or cp.stdout or "command failed").strip()
        raise SystemExit(f"ROTATION_COMMAND_FAILED: {message}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--old-keystore", type=Path, required=True)
    parser.add_argument("--old-alias", default=DEFAULT_OLD_ALIAS)
    parser.add_argument("--new-alias", default=DEFAULT_NEW_ALIAS)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--rotation-min-sdk-version", type=int, default=DEFAULT_ROTATION_MIN_SDK)
    args = parser.parse_args()

    if args.rotation_min_sdk_version < 28:
        parser.error("--rotation-min-sdk-version must be >= 28")

    old_keystore = args.old_keystore.expanduser().resolve()
    if not old_keystore.is_file() or old_keystore.stat().st_size == 0:
        raise SystemExit(f"OLD_SIGNER_MISSING: {old_keystore}")

    keytool = require_tool("keytool")
    apksigner = find_apksigner()

    old_password = read_secret(
        "DPC_AIO_OLD_SIGNING_PASSWORD",
        "Historical DPC-AIO signing password: ",
    )
    new_password = read_secret(
        "DPC_AIO_NEW_SIGNING_PASSWORD",
        "New DPC-AIO stable signing password: ",
        confirm=True,
    )
    if len(new_password) < 16:
        raise SystemExit("NEW_SIGNING_PASSWORD_INVALID: use at least 16 characters")

    env = os.environ.copy()
    env["DPC_AIO_OLD_SIGNING_PASSWORD"] = old_password
    env["DPC_AIO_NEW_SIGNING_PASSWORD"] = new_password

    old_fingerprint = cert_fingerprint(
        keytool, old_keystore, args.old_alias, "DPC_AIO_OLD_SIGNING_PASSWORD"
    )
    if old_fingerprint != EXPECTED_OLD_CERT_SHA256:
        raise SystemExit(
            "HISTORICAL_SIGNER_MISMATCH: "
            f"expected={EXPECTED_OLD_CERT_SHA256} observed={old_fingerprint}"
        )

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_dir = args.output_dir or (Path.home() / "DPC-AIO-signing-rotation" / timestamp)
    output_dir = output_dir.expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=False)

    new_keystore = output_dir / "dpc-aio-enterprise-rotated.p12"
    lineage = output_dir / "dpc-aio-signing-lineage.bin"
    metadata = output_dir / "rotation-metadata.json"

    run_checked(
        [
            keytool,
            "-genkeypair",
            "-storetype",
            "PKCS12",
            "-keystore",
            str(new_keystore),
            "-storepass:env",
            "DPC_AIO_NEW_SIGNING_PASSWORD",
            "-keypass:env",
            "DPC_AIO_NEW_SIGNING_PASSWORD",
            "-alias",
            args.new_alias,
            "-keyalg",
            "RSA",
            "-keysize",
            "4096",
            "-sigalg",
            "SHA256withRSA",
            "-validity",
            "3650",
            "-dname",
            "CN=DPC-AIO Stable Rotated, OU=Release, O=DPC-AIO, C=PL",
            "-noprompt",
        ],
        env=env,
    )
    if not new_keystore.is_file() or new_keystore.stat().st_size == 0:
        raise SystemExit("NEW_SIGNER_GENERATION_FAILED: keytool did not create a keystore")
    chmod_private(new_keystore)

    new_fingerprint = cert_fingerprint(
        keytool, new_keystore, args.new_alias, "DPC_AIO_NEW_SIGNING_PASSWORD"
    )
    if new_fingerprint == old_fingerprint:
        raise SystemExit("NEW_SIGNER_INVALID: new signer matches the historical signer")

    run_checked(
        [
            apksigner,
            "rotate",
            "--out",
            str(lineage),
            "--old-signer",
            "--ks",
            str(old_keystore),
            "--ks-key-alias",
            args.old_alias,
            "--ks-pass",
            "env:DPC_AIO_OLD_SIGNING_PASSWORD",
            "--key-pass",
            "env:DPC_AIO_OLD_SIGNING_PASSWORD",
            "--new-signer",
            "--ks",
            str(new_keystore),
            "--ks-key-alias",
            args.new_alias,
            "--ks-pass",
            "env:DPC_AIO_NEW_SIGNING_PASSWORD",
            "--key-pass",
            "env:DPC_AIO_NEW_SIGNING_PASSWORD",
        ],
        env=env,
    )
    if not lineage.is_file() or lineage.stat().st_size == 0:
        raise SystemExit("LINEAGE_GENERATION_FAILED: apksigner did not create the lineage")
    chmod_private(lineage)

    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "preparedAtUtc": timestamp,
                "oldAlias": args.old_alias,
                "newAlias": args.new_alias,
                "oldCertificateSha256": old_fingerprint,
                "newCertificateSha256": new_fingerprint,
                "rotationMinSdkVersion": args.rotation_min_sdk_version,
                "newKeystore": new_keystore.name,
                "lineage": lineage.name,
                "securityStatus": "historical signer treated as potentially compromised; use only to authorize migration",
                "passwordsStored": False,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    chmod_private(metadata)

    print("ROTATION_PREPARED: PASS")
    print(f"Historical certificate SHA-256: {old_fingerprint}")
    print(f"New certificate SHA-256: {new_fingerprint}")
    print(f"Rotation minimum SDK: {args.rotation_min_sdk_version}")
    print(f"Local rotation directory: {output_dir}")
    print("No GitHub Secrets were changed and no workflow was dispatched.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
