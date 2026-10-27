#!/usr/bin/env python3
"""Configure GitHub Actions stable signing from an existing keystore."""

from __future__ import annotations

import argparse
import base64
import getpass
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


SECRET_NAMES = (
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
)
FINGERPRINT_NAME = "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256"


def run(args, *, input_text=None, capture=False):
    return subprocess.run(
        args,
        input=input_text,
        text=True,
        check=True,
        capture_output=capture,
    )


def require(name):
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"ERROR: required tool not found in PATH: {name}")
    return path


def fingerprint(keytool, keystore, storetype, storepass, alias):
    with tempfile.NamedTemporaryFile(prefix="dpc-aio-cert-", suffix=".der", delete=False) as tf:
        cert = Path(tf.name)
    try:
        run([
            keytool,
            "-exportcert",
            "-keystore", str(keystore),
            "-storetype", storetype,
            "-storepass", storepass,
            "-alias", alias,
            "-file", str(cert),
        ])
        return hashlib.sha256(cert.read_bytes()).hexdigest().upper()
    finally:
        cert.unlink(missing_ok=True)


def normalize_sha(value):
    return "".join(ch for ch in value.upper() if ch in "0123456789ABCDEF")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("keystore", type=Path)
    p.add_argument("--repo", default="FuzzsT/ENRROL_NEW")
    p.add_argument("--storetype", default="PKCS12", choices=["PKCS12", "JKS"])
    p.add_argument("--alias")
    p.add_argument(
        "--expected-fingerprint",
        help="Optional historical signer SHA-256. Refuses configuration if it differs.",
    )
    args = p.parse_args()

    keytool = require("keytool")
    gh = require("gh")
    run([gh, "auth", "status", "--hostname", "github.com"])

    ks = args.keystore.expanduser().resolve()
    if not ks.is_file():
        raise SystemExit(f"ERROR: keystore not found: {ks}")

    storepass = getpass.getpass("Keystore password: ")
    alias = args.alias or input("Key alias: ").strip()
    if not alias:
        raise SystemExit("ERROR: alias is required")
    keypass = getpass.getpass("Key password (press Enter if same as keystore): ") or storepass

    observed = fingerprint(keytool, ks, args.storetype, storepass, alias)
    if args.expected_fingerprint:
        expected = normalize_sha(args.expected_fingerprint)
        if observed != expected:
            raise SystemExit(
                f"ERROR: signer mismatch. Expected historical SHA-256 {expected}, observed {observed}. "
                "GitHub secrets were not changed."
            )

    b64 = base64.b64encode(ks.read_bytes()).decode("ascii")
    values = {
        "DPC_AIO_RELEASE_KEYSTORE_B64": b64,
        "DPC_AIO_RELEASE_STORE_PASSWORD": storepass,
        "DPC_AIO_RELEASE_KEY_ALIAS": alias,
        "DPC_AIO_RELEASE_KEY_PASSWORD": keypass,
    }
    for name, value in values.items():
        run([gh, "secret", "set", name, "--repo", args.repo], input_text=value)
    run([gh, "variable", "set", FINGERPRINT_NAME, "--repo", args.repo, "--body", observed])

    listed = run([gh, "secret", "list", "--repo", args.repo, "--json", "name"], capture=True)
    configured = {row["name"] for row in json.loads(listed.stdout or "[]")}
    missing = sorted(set(SECRET_NAMES) - configured)
    if missing:
        raise SystemExit("ERROR: secret-name verification failed: " + ", ".join(missing))

    print("Existing stable signer configured.")
    print(f"Repository: {args.repo}")
    print(f"Certificate SHA-256: {observed}")
    print("Secret values were not printed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
