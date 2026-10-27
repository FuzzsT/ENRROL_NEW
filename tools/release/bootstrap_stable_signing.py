#!/usr/bin/env python3
"""
Bootstrap update-stable DPC-AIO signing for GitHub Actions.

Safety properties:
- never prints passwords, keystore bytes, or base64 to stdout;
- stores recovery material only in a local directory outside the repository by default;
- refuses to create a NEW signer unless --allow-new-key is supplied;
- configures GitHub Secrets through `gh secret set` stdin and the expected cert SHA as a variable.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile


SECRET_NAMES = (
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
)
FINGERPRINT_NAME = "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256"


def run(args: list[str], *, input_text: str | None = None, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        input=input_text,
        text=True,
        check=True,
        capture_output=capture,
    )


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"ERROR: required tool not found in PATH: {name}")
    return path


def chmod_private(path: Path) -> None:
    try:
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass


def make_password() -> str:
    return secrets.token_urlsafe(48)


def cert_sha256(keytool: str, keystore: Path, alias: str, password: str) -> str:
    with tempfile.NamedTemporaryFile(prefix="dpc-aio-cert-", suffix=".der", delete=False) as tf:
        cert_path = Path(tf.name)
    try:
        run([
            keytool,
            "-exportcert",
            "-keystore", str(keystore),
            "-storetype", "PKCS12",
            "-storepass", password,
            "-alias", alias,
            "-file", str(cert_path),
        ])
        return hashlib.sha256(cert_path.read_bytes()).hexdigest().upper()
    finally:
        try:
            cert_path.unlink()
        except FileNotFoundError:
            pass


def set_secret(gh: str, repo: str, name: str, value: str) -> None:
    run([gh, "secret", "set", name, "--repo", repo], input_text=value)


def secret_names(gh: str, repo: str) -> set[str]:
    cp = run([gh, "secret", "list", "--repo", repo, "--json", "name"], capture=True)
    data = json.loads(cp.stdout or "[]")
    return {str(row.get("name", "")) for row in data if row.get("name")}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--repo", default="FuzzsT/ENRROL_NEW")
    p.add_argument("--alias", default="dpc-aio-release")
    p.add_argument("--backup-dir", type=Path)
    p.add_argument(
        "--allow-new-key",
        action="store_true",
        help="Required acknowledgement that a new signer may break updates from APKs signed by an older key.",
    )
    p.add_argument(
        "--yes",
        action="store_true",
        help="Skip the interactive NEW SIGNER confirmation (still requires --allow-new-key).",
    )
    args = p.parse_args()

    if not args.allow_new_key:
        print(
            "REFUSED: creating a new release signer can break APK update continuity.\n"
            "Recover the existing stable keystore if deployed devices already use it.\n"
            "If a new signing lineage is intentional, rerun with --allow-new-key.",
            file=sys.stderr,
        )
        return 2

    if not args.yes:
        typed = input(
            f"Type NEW SIGNER to create a new update lineage for {args.repo}: "
        ).strip()
        if typed != "NEW SIGNER":
            print("Cancelled; no key or GitHub secret was changed.")
            return 3

    keytool = require_tool("keytool")
    gh = require_tool("gh")
    run([gh, "auth", "status", "--hostname", "github.com"])

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_dir = args.backup_dir or (
        Path.home() / "DPC-AIO-stable-signing-backup" / timestamp
    )
    backup_dir = backup_dir.expanduser().resolve()
    backup_dir.mkdir(parents=True, exist_ok=False)

    keystore = backup_dir / "dpc-aio-enterprise-release.p12"
    recovery = backup_dir / "RECOVERY-SECRETS.txt"
    metadata = backup_dir / "metadata.json"

    password = make_password()
    alias = args.alias

    run([
        keytool,
        "-genkeypair",
        "-noprompt",
        "-keystore", str(keystore),
        "-storetype", "PKCS12",
        "-storepass", password,
        "-keypass", password,
        "-alias", alias,
        "-keyalg", "RSA",
        "-keysize", "4096",
        "-sigalg", "SHA256withRSA",
        "-validity", "10000",
        "-dname", "CN=DPC-AIO Stable Release,O=DPC-AIO,C=PL",
    ])
    chmod_private(keystore)

    fingerprint = cert_sha256(keytool, keystore, alias, password)
    keystore_b64 = base64.b64encode(keystore.read_bytes()).decode("ascii")

    recovery.write_text(
        "\n".join([
            "# SENSITIVE. KEEP OFF GIT. Needed to recover/update the stable signing key.",
            f"DPC_AIO_RELEASE_STORE_PASSWORD={password}",
            f"DPC_AIO_RELEASE_KEY_ALIAS={alias}",
            f"DPC_AIO_RELEASE_KEY_PASSWORD={password}",
            f"{FINGERPRINT_NAME}={fingerprint}",
            "",
        ]),
        encoding="utf-8",
    )
    chmod_private(recovery)

    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "repository": args.repo,
                "createdAtUtc": timestamp,
                "keystore": keystore.name,
                "storeType": "PKCS12",
                "alias": alias,
                "certificateSha256": fingerprint,
                "note": "Passwords are intentionally not stored in metadata.json; see protected RECOVERY-SECRETS.txt.",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    # Send values through stdin so secret material does not appear in argv/process listings.
    set_secret(gh, args.repo, "DPC_AIO_RELEASE_KEYSTORE_B64", keystore_b64)
    set_secret(gh, args.repo, "DPC_AIO_RELEASE_STORE_PASSWORD", password)
    set_secret(gh, args.repo, "DPC_AIO_RELEASE_KEY_ALIAS", alias)
    set_secret(gh, args.repo, "DPC_AIO_RELEASE_KEY_PASSWORD", password)
    run([
        gh, "variable", "set", FINGERPRINT_NAME,
        "--repo", args.repo,
        "--body", fingerprint,
    ])

    configured = secret_names(gh, args.repo)
    missing = sorted(set(SECRET_NAMES) - configured)
    if missing:
        raise SystemExit(
            "ERROR: GitHub did not report all expected secret names after update: "
            + ", ".join(missing)
        )

    print("Stable signing bootstrap completed.")
    print(f"Repository: {args.repo}")
    print(f"Certificate SHA-256: {fingerprint}")
    print(f"Local recovery directory: {backup_dir}")
    print("Secret values were not printed.")
    print(
        "Next: run workflow 'Build AIO + enrollment QR'. "
        "Do not delete the local recovery directory."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
