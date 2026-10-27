#!/usr/bin/env python3
"""
Recover the historical DPC-AIO password-backed signing keystore from the
dpc-aio-continuous release asset and verify it against the historical APK.

The historical workflow encrypted the PKCS12 keystore with:
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 600000

Safety:
- recovery password is read from DPC_AIO_RECOVERY_PASSWORD or an interactive prompt;
- no password command-line option is provided;
- password/keystore bytes are never printed;
- the recovered certificate must match the signer of the historical APK;
- GitHub Secrets are modified only with --configure-github;
- recovered material is stored outside the repository by default.
"""

from __future__ import annotations

import argparse
import base64
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
import urllib.request


DEFAULT_REPO = "FuzzsT/ENRROL_NEW"
DEFAULT_ALIAS = "dpc-aio-enterprise"
DEFAULT_ASSET_URL = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/DPC-AIO-signing-keystore.enc"
)
DEFAULT_HISTORICAL_APK_URL = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/DPC-AIO-enterprise-release.apk"
)
SECRET_NAMES = (
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
)
FINGERPRINT_NAME = "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256"


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"ERROR: required tool not found in PATH: {name}")
    return path


def find_apksigner() -> str:
    direct = shutil.which("apksigner")
    if direct:
        return direct
    roots = [
        os.environ.get("ANDROID_SDK_ROOT", ""),
        os.environ.get("ANDROID_HOME", ""),
    ]
    for raw_root in roots:
        if not raw_root:
            continue
        build_tools = Path(raw_root) / "build-tools"
        if not build_tools.is_dir():
            continue
        candidates = sorted(build_tools.glob("*/apksigner"), reverse=True)
        for candidate in candidates:
            if candidate.is_file() and os.access(candidate, os.X_OK):
                return str(candidate)
    raise SystemExit(
        "ERROR: required tool not found: apksigner. Install Android SDK build-tools "
        "or place apksigner in PATH."
    )


def run(
    args: list[str],
    *,
    env: dict[str, str] | None = None,
    input_text: str | None = None,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        env=env,
        input=input_text,
        text=True,
        check=True,
        capture_output=capture,
    )


def chmod_private(path: Path) -> None:
    try:
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass


def normalized_sha256(value: str) -> str:
    return "".join(value.split()).replace(":", "").upper()


def read_password() -> str:
    password = os.environ.get("DPC_AIO_RECOVERY_PASSWORD", "")
    if password:
        return password
    return getpass.getpass("Historical DPC-AIO release signing password: ")


def download_asset(url: str, destination: Path) -> None:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "DPC-AIO-stable-signing-recovery/2"},
    )
    with urllib.request.urlopen(req, timeout=60) as response:
        if getattr(response, "status", 200) != 200:
            raise RuntimeError(f"download failed with HTTP {response.status}")
        with destination.open("wb") as out:
            shutil.copyfileobj(response, out)


def decrypt_keystore(openssl: str, encrypted: Path, output: Path, password: str) -> None:
    env = os.environ.copy()
    env["DPC_AIO_EFFECTIVE_SIGNING_PASSWORD"] = password
    cp = subprocess.run(
        [
            openssl,
            "enc",
            "-d",
            "-aes-256-cbc",
            "-pbkdf2",
            "-iter",
            "600000",
            "-in",
            str(encrypted),
            "-out",
            str(output),
            "-pass",
            "env:DPC_AIO_EFFECTIVE_SIGNING_PASSWORD",
        ],
        env=env,
        text=True,
        capture_output=True,
    )
    if cp.returncode != 0 or not output.exists() or output.stat().st_size == 0:
        output.unlink(missing_ok=True)
        raise SystemExit(
            "PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH: asset could not be decrypted "
            "with the supplied recovery password."
        )
    chmod_private(output)


def validate_and_fingerprint(
    keytool: str,
    keystore: Path,
    alias: str,
    password: str,
) -> str:
    env = os.environ.copy()
    env["DPC_AIO_EFFECTIVE_STORE_PASSWORD"] = password

    cp = subprocess.run(
        [
            keytool,
            "-list",
            "-keystore",
            str(keystore),
            "-storetype",
            "PKCS12",
            "-alias",
            alias,
            "-storepass:env",
            "DPC_AIO_EFFECTIVE_STORE_PASSWORD",
        ],
        env=env,
        text=True,
        capture_output=True,
    )
    if cp.returncode != 0:
        raise SystemExit(
            "PERSISTED_SIGNING_KEY_INVALID: decrypted keystore does not contain "
            "the expected alias or password."
        )

    with tempfile.NamedTemporaryFile(
        prefix="dpc-aio-recovered-cert-", suffix=".der", delete=False
    ) as tf:
        cert_path = Path(tf.name)
    try:
        run(
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
                "DPC_AIO_EFFECTIVE_STORE_PASSWORD",
                "-file",
                str(cert_path),
            ],
            env=env,
            capture=True,
        )
        return hashlib.sha256(cert_path.read_bytes()).hexdigest().upper()
    finally:
        cert_path.unlink(missing_ok=True)


def apk_signer_fingerprint(apksigner: str, apk: Path) -> str:
    cp = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk)],
        text=True,
        capture_output=True,
    )
    if cp.returncode != 0:
        raise SystemExit(
            "HISTORICAL_APK_INVALID: apksigner could not verify the historical APK."
        )
    output = cp.stdout + "\n" + cp.stderr
    for line in output.splitlines():
        marker = "certificate SHA-256 digest:"
        if marker not in line:
            continue
        value = normalized_sha256(line.split(marker, 1)[1])
        if len(value) == 64 and all(ch in "0123456789ABCDEF" for ch in value):
            return value
    raise SystemExit(
        "HISTORICAL_APK_INVALID: certificate SHA-256 digest not found in apksigner output."
    )


def set_secret(gh: str, repo: str, name: str, value: str) -> None:
    run([gh, "secret", "set", name, "--repo", repo], input_text=value)


def configure_github(
    gh: str,
    repo: str,
    keystore: Path,
    alias: str,
    password: str,
    fingerprint: str,
) -> None:
    run([gh, "auth", "status", "--hostname", "github.com"], capture=True)
    keystore_b64 = base64.b64encode(keystore.read_bytes()).decode("ascii")
    set_secret(gh, repo, "DPC_AIO_RELEASE_KEYSTORE_B64", keystore_b64)
    set_secret(gh, repo, "DPC_AIO_RELEASE_STORE_PASSWORD", password)
    set_secret(gh, repo, "DPC_AIO_RELEASE_KEY_ALIAS", alias)
    set_secret(gh, repo, "DPC_AIO_RELEASE_KEY_PASSWORD", password)
    run(
        [
            gh,
            "variable",
            "set",
            FINGERPRINT_NAME,
            "--repo",
            repo,
            "--body",
            fingerprint,
        ]
    )

    cp = run(
        [gh, "secret", "list", "--repo", repo, "--json", "name"],
        capture=True,
    )
    configured = {
        str(row.get("name", ""))
        for row in json.loads(cp.stdout or "[]")
        if row.get("name")
    }
    missing = sorted(set(SECRET_NAMES) - configured)
    if missing:
        raise SystemExit(
            "ERROR: GitHub did not report all expected signing secret names: "
            + ", ".join(missing)
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--alias", default=DEFAULT_ALIAS)
    parser.add_argument("--asset-url", default=DEFAULT_ASSET_URL)
    parser.add_argument("--encrypted-file", type=Path)
    parser.add_argument("--historical-apk-url", default=DEFAULT_HISTORICAL_APK_URL)
    parser.add_argument("--historical-apk-file", type=Path)
    parser.add_argument("--backup-dir", type=Path)
    parser.add_argument("--expected-sha256")
    parser.add_argument("--configure-github", action="store_true")
    parser.add_argument("--dispatch", action="store_true")
    args = parser.parse_args()

    if args.dispatch and not args.configure_github:
        parser.error("--dispatch requires --configure-github")

    openssl = require_tool("openssl")
    keytool = require_tool("keytool")
    apksigner = find_apksigner()
    gh = require_tool("gh") if args.configure_github else None

    password = read_password()
    if len(password) < 12:
        raise SystemExit(
            "RECOVERY_PASSWORD_INVALID: expected the original 12+ character "
            "manual release signing password."
        )

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_dir = args.backup_dir or (
        Path.home() / "DPC-AIO-stable-signing-recovery" / timestamp
    )
    backup_dir = backup_dir.expanduser().resolve()
    backup_dir.mkdir(parents=True, exist_ok=False)

    encrypted = backup_dir / "DPC-AIO-signing-keystore.enc"
    recovered = backup_dir / "dpc-aio-enterprise-release.p12"
    historical_apk = backup_dir / "DPC-AIO-historical-enterprise-release.apk"
    metadata = backup_dir / "metadata.json"

    if args.encrypted_file:
        source = args.encrypted_file.expanduser().resolve()
        if not source.is_file() or source.stat().st_size == 0:
            raise SystemExit(f"ERROR: encrypted keystore asset not found: {source}")
        shutil.copyfile(source, encrypted)
    else:
        print(f"Downloading historical encrypted signer asset from: {args.asset_url}")
        download_asset(args.asset_url, encrypted)

    if args.historical_apk_file:
        apk_source = args.historical_apk_file.expanduser().resolve()
        if not apk_source.is_file() or apk_source.stat().st_size == 0:
            raise SystemExit(f"ERROR: historical APK not found: {apk_source}")
        shutil.copyfile(apk_source, historical_apk)
        historical_apk_source = str(apk_source)
    else:
        print(f"Downloading historical APK from: {args.historical_apk_url}")
        download_asset(args.historical_apk_url, historical_apk)
        historical_apk_source = args.historical_apk_url

    chmod_private(encrypted)
    decrypt_keystore(openssl, encrypted, recovered, password)
    fingerprint = validate_and_fingerprint(keytool, recovered, args.alias, password)
    historical_apk_fingerprint = apk_signer_fingerprint(apksigner, historical_apk)

    if fingerprint != historical_apk_fingerprint:
        raise SystemExit(
            "HISTORICAL_APK_SIGNER_MISMATCH: "
            f"historical_apk={historical_apk_fingerprint} recovered={fingerprint}"
        )

    expected = normalized_sha256(args.expected_sha256 or "")
    if expected and fingerprint != expected:
        raise SystemExit(
            f"SIGNER_MISMATCH: expected={expected} recovered={fingerprint}"
        )

    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 2,
                "repository": args.repo,
                "recoveredAtUtc": timestamp,
                "sourceAsset": args.asset_url if not args.encrypted_file else str(args.encrypted_file),
                "historicalApkSource": historical_apk_source,
                "historicalApkSignerVerified": True,
                "historicalApkCertificateSha256": historical_apk_fingerprint,
                "keystore": recovered.name,
                "storeType": "PKCS12",
                "alias": args.alias,
                "certificateSha256": fingerprint,
                "historicalEncryption": {
                    "cipher": "AES-256-CBC",
                    "pbkdf2": True,
                    "iterations": 600000,
                },
                "note": "Recovery password is intentionally not stored.",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    chmod_private(metadata)

    if args.configure_github:
        assert gh is not None
        configure_github(
            gh,
            args.repo,
            recovered,
            args.alias,
            password,
            fingerprint,
        )
        print("GitHub stable-signing Secrets/Variable configured.")

    if args.dispatch:
        assert gh is not None
        run(
            [
                gh,
                "workflow",
                "run",
                "build-aio-enrollment.yml",
                "--repo",
                args.repo,
                "--ref",
                "main",
            ]
        )
        print("Production enrollment workflow dispatched on main.")

    print("Historical APK signer verification: PASS")
    print("Historical signing keystore recovery completed.")
    print(f"Certificate SHA-256: {fingerprint}")
    print(f"Local recovery directory: {backup_dir}")
    print("Recovery password and keystore bytes were not printed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
