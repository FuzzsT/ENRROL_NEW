#!/usr/bin/env python3
"""Recover the historical DPC-AIO stable signer without rotating it.

The legacy rolling release stored public ciphertext as
DPC-AIO-signing-keystore.enc on the dpc-aio-continuous release. The password
was intentionally kept out of the release and must be supplied locally.

This tool never generates or rotates a signing key. It decrypts the existing
ciphertext only into a TemporaryDirectory, verifies that its certificate matches
the historical public provisioning metadata, then delegates validation and
optional GitHub configuration to stable_signing_setup.py.
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlparse
from urllib.request import Request, urlopen

DEFAULT_REPO = "FuzzsT/ENRROL_NEW"
DEFAULT_ALIAS = "dpc-aio-enterprise"
DEFAULT_ENCRYPTED = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/DPC-AIO-signing-keystore.enc"
)
DEFAULT_PROVISIONING_METADATA = (
    "https://github.com/FuzzsT/ENRROL_NEW/releases/download/"
    "dpc-aio-continuous/provisioning-metadata.json"
)
PASSWORD_ENV = "DPC_AIO_LEGACY_SIGNING_PASSWORD"
MAX_CIPHERTEXT_BYTES = 16 * 1024 * 1024
MAX_METADATA_BYTES = 1024 * 1024
SETUP_TOOL = Path(__file__).with_name("stable_signing_setup.py")


def fail(message: str, code: int = 2) -> "NoReturn":
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(code)


def require_program(name: str) -> str:
    path = shutil.which(name)
    if not path:
        fail(f"required program not found: {name}")
    return path


def copy_limited(source, destination: Path) -> int:
    total = 0
    with destination.open("wb") as out:
        while True:
            chunk = source.read(64 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_CIPHERTEXT_BYTES:
                fail("encrypted keystore exceeds the safety size limit")
            out.write(chunk)
    if total == 0:
        fail("encrypted keystore is empty")
    return total


def materialize_encrypted(source: str, destination: Path) -> int:
    parsed = urlparse(source)
    if parsed.scheme in ("http", "https"):
        if parsed.scheme != "https":
            fail("remote encrypted keystore URL must use HTTPS")
        request = Request(source, headers={"User-Agent": "DPC-AIO-stable-signing-recovery/1"})
        try:
            with urlopen(request, timeout=60) as response:
                length = response.headers.get("Content-Length")
                if length and int(length) > MAX_CIPHERTEXT_BYTES:
                    fail("encrypted keystore exceeds the safety size limit")
                return copy_limited(response, destination)
        except SystemExit:
            raise
        except Exception as exc:
            fail(f"could not download encrypted keystore: {exc}")

    path = Path(source).expanduser().resolve()
    if not path.is_file():
        fail(f"encrypted keystore file not found: {path}")
    if path.stat().st_size > MAX_CIPHERTEXT_BYTES:
        fail("encrypted keystore exceeds the safety size limit")
    with path.open("rb") as src:
        return copy_limited(src, destination)


def read_limited_metadata(source: str) -> dict:
    parsed = urlparse(source)
    try:
        if parsed.scheme in ("http", "https"):
            if parsed.scheme != "https":
                fail("remote provisioning metadata URL must use HTTPS")
            request = Request(source, headers={"User-Agent": "DPC-AIO-stable-signing-recovery/1"})
            with urlopen(request, timeout=60) as response:
                length = response.headers.get("Content-Length")
                if length and int(length) > MAX_METADATA_BYTES:
                    fail("provisioning metadata exceeds the safety size limit")
                data = response.read(MAX_METADATA_BYTES + 1)
        else:
            path = Path(source).expanduser().resolve()
            if not path.is_file():
                fail(f"provisioning metadata file not found: {path}")
            if path.stat().st_size > MAX_METADATA_BYTES:
                fail("provisioning metadata exceeds the safety size limit")
            data = path.read_bytes()
    except SystemExit:
        raise
    except Exception as exc:
        fail(f"could not read historical provisioning metadata: {exc}")

    if len(data) > MAX_METADATA_BYTES:
        fail("provisioning metadata exceeds the safety size limit")
    if not data:
        fail("historical provisioning metadata is empty")
    try:
        parsed_json = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(f"historical provisioning metadata is not valid UTF-8 JSON: {exc}")
    if not isinstance(parsed_json, dict):
        fail("historical provisioning metadata must be a JSON object")
    return parsed_json


def normalize_sha256(value: object, label: str) -> str:
    if not isinstance(value, str):
        fail(f"{label} is missing")
    normalized = re.sub(r"[^0-9A-Fa-f]", "", value).upper()
    if len(normalized) != 64:
        fail(f"{label} must contain exactly one SHA-256 fingerprint")
    return normalized


def historical_certificate_fingerprint(metadata_source: str) -> str:
    metadata = read_limited_metadata(metadata_source)
    if metadata.get("checksumMode") != "signature":
        fail("historical provisioning metadata is not signature-bound")
    return normalize_sha256(metadata.get("certificateSha256Hex"), "certificateSha256Hex")


def recovered_certificate_fingerprint(
    keytool: str,
    keystore: Path,
    password: str,
    alias: str,
) -> str:
    env = os.environ.copy()
    env[PASSWORD_ENV] = password
    proc = subprocess.run(
        [
            keytool,
            "-list",
            "-v",
            "-keystore",
            str(keystore),
            "-storepass:env",
            PASSWORD_ENV,
            "-alias",
            alias,
        ],
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        fail("could not inspect recovered keystore certificate/alias")
    combined = f"{proc.stdout}\n{proc.stderr}"
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]{32,})", combined)
    if not match:
        fail("could not extract SHA-256 certificate fingerprint from recovered keystore")
    return normalize_sha256(match.group(1), "recovered certificate SHA-256")


def verify_historical_lineage(
    keytool: str,
    keystore: Path,
    password: str,
    alias: str,
    metadata_source: str,
) -> str:
    expected = historical_certificate_fingerprint(metadata_source)
    actual = recovered_certificate_fingerprint(keytool, keystore, password, alias)
    if actual != expected:
        fail(
            "recovered signing certificate does not match the historical dpc-aio-continuous "
            "provisioning certificate; refusing to configure stable signing"
        )
    print("Historical signing lineage: MATCH")
    print(f"Historical certificate SHA-256: {actual}")
    return actual


def obtain_password() -> str:
    password = os.environ.get(PASSWORD_ENV, "")
    if not password:
        password = getpass.getpass("Legacy Run workflow signing password: ")
    if not password:
        fail("legacy signing password is required")
    if "\n" in password or "\r" in password:
        fail("legacy signing password must not contain line breaks")
    return password


def decrypt_keystore(openssl: str, encrypted: Path, keystore: Path, password: str) -> None:
    env = os.environ.copy()
    env[PASSWORD_ENV] = password
    proc = subprocess.run(
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
            str(keystore),
            "-pass",
            f"env:{PASSWORD_ENV}",
        ],
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0 or not keystore.is_file() or keystore.stat().st_size == 0:
        keystore.unlink(missing_ok=True)
        fail(
            "could not decrypt the persisted signing keystore; verify the original "
            "Run workflow password and ciphertext"
        )
    try:
        os.chmod(keystore, 0o600)
    except OSError:
        pass


def delegate_to_setup(keystore: Path, alias: str, repo: str, password: str, apply: bool) -> int:
    if not SETUP_TOOL.is_file():
        fail(f"setup tool not found: {SETUP_TOOL}")

    env = os.environ.copy()
    env["DPC_AIO_RELEASE_STORE_PASSWORD"] = password
    env["DPC_AIO_RELEASE_KEY_PASSWORD"] = password
    env["DPC_AIO_RELEASE_KEY_ALIAS"] = alias

    command = [
        sys.executable,
        str(SETUP_TOOL),
        "--keystore",
        str(keystore),
        "--alias",
        alias,
        "--repo",
        repo,
    ]
    if apply:
        command.append("--apply")

    return subprocess.run(command, env=env).returncode


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Recover the existing DPC-AIO password-backed stable signer from the "
            "persisted encrypted rolling-release asset and verify historical certificate lineage. "
            "No signing key is generated or rotated."
        )
    )
    parser.add_argument(
        "--encrypted",
        default=DEFAULT_ENCRYPTED,
        help=(
            "HTTPS URL or local path to DPC-AIO-signing-keystore.enc "
            "(default: dpc-aio-continuous release asset)"
        ),
    )
    parser.add_argument(
        "--metadata",
        default=DEFAULT_PROVISIONING_METADATA,
        help=(
            "HTTPS URL or local path to historical provisioning-metadata.json used to verify "
            "the signing certificate lineage"
        ),
    )
    parser.add_argument("--alias", default=DEFAULT_ALIAS, help="Expected keystore alias")
    parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repository owner/name")
    parser.add_argument(
        "--apply",
        action="store_true",
        help=(
            "After local recovery, historical-lineage verification and private-key validation, "
            "configure stable signing through authenticated gh"
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    openssl = require_program("openssl")
    keytool = require_program("keytool")
    password = obtain_password()

    print("Recovery mode: existing persisted signer only; key generation/rotation is disabled.")
    print(f"Encrypted source: {args.encrypted}")
    print(f"Historical metadata: {args.metadata}")
    print(f"Expected alias: {args.alias}")

    with tempfile.TemporaryDirectory(prefix="dpc-aio-signing-recovery-") as temp_dir:
        temp = Path(temp_dir)
        encrypted = temp / "DPC-AIO-signing-keystore.enc"
        keystore = temp / "dpc-aio-recovered-release.p12"
        size = materialize_encrypted(args.encrypted, encrypted)
        print(f"Encrypted asset size: {size} bytes")
        decrypt_keystore(openssl, encrypted, keystore, password)
        verify_historical_lineage(keytool, keystore, password, args.alias, args.metadata)

        result = delegate_to_setup(keystore, args.alias, args.repo, password, args.apply)
        if result != 0:
            fail("recovered keystore did not pass stable signing setup validation", result)

    print("Recovered persisted stable signer: validated.")
    if args.apply:
        print("Stable signing configuration was submitted through stable_signing_setup.py.")
    else:
        print("Audit only. Re-run with --apply to configure GitHub after validation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
