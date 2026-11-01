#!/usr/bin/env python3
"""Audit a local signing-rotation bundle and optionally configure migration secrets.

Audit-only is the default. Secret values are sent to `gh secret set` on stdin and
are never printed by this tool.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

EXPECTED_OLD_CERT_SHA256 = "E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4"
EXPECTED_ROTATION_MIN_SDK = 33


def read_password(env_name: str, prompt: str) -> str:
    value = os.environ.get(env_name, "")
    if value:
        return value
    value = getpass.getpass(prompt)
    if not value:
        raise SystemExit(f"MIGRATION_SECRET_INPUT_REQUIRED: {env_name}")
    return value


def require_nonempty(path: Path, marker: str) -> Path:
    path = path.expanduser().resolve()
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"{marker}: {path}")
    return path


def normalize_fingerprint(value: object, marker: str) -> str:
    normalized = re.sub(r"[^0-9A-Fa-f]", "", str(value)).upper()
    if len(normalized) != 64:
        raise SystemExit(f"{marker}: expected 64 hex characters")
    return normalized


def b64_file(path: Path) -> str:
    return base64.b64encode(path.read_bytes()).decode("ascii")


def load_bundle(bundle_dir: Path, old_keystore: Path) -> tuple[dict[str, str], dict[str, object]]:
    bundle_dir = bundle_dir.expanduser().resolve()
    metadata_path = require_nonempty(bundle_dir / "rotation-metadata.json", "ROTATION_METADATA_MISSING")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))

    if metadata.get("schemaVersion") != 1:
        raise SystemExit("ROTATION_METADATA_INVALID: schemaVersion must be 1")
    if metadata.get("passwordsStored") is not False:
        raise SystemExit("ROTATION_METADATA_INVALID: passwordsStored must be false")
    if int(metadata.get("rotationMinSdkVersion", -1)) != EXPECTED_ROTATION_MIN_SDK:
        raise SystemExit("ROTATION_METADATA_INVALID: rotationMinSdkVersion must be 33")

    old_cert = normalize_fingerprint(metadata.get("oldCertificateSha256", ""), "OLD_CERT_INVALID")
    new_cert = normalize_fingerprint(metadata.get("newCertificateSha256", ""), "NEW_CERT_INVALID")
    if old_cert != EXPECTED_OLD_CERT_SHA256:
        raise SystemExit(
            f"HISTORICAL_SIGNER_MISMATCH: expected={EXPECTED_OLD_CERT_SHA256} observed={old_cert}"
        )
    if new_cert == old_cert:
        raise SystemExit("NEW_SIGNER_INVALID: new signer matches historical signer")

    old_alias = str(metadata.get("oldAlias", "")).strip()
    new_alias = str(metadata.get("newAlias", "")).strip()
    if not old_alias or not new_alias:
        raise SystemExit("ROTATION_METADATA_INVALID: signer aliases are required")

    old_keystore = require_nonempty(old_keystore, "OLD_KEYSTORE_MISSING")
    new_keystore_name = str(metadata.get("newKeystore", "")).strip()
    lineage_name = str(metadata.get("lineage", "")).strip()
    if not new_keystore_name or not lineage_name:
        raise SystemExit("ROTATION_METADATA_INVALID: newKeystore and lineage are required")

    new_keystore = require_nonempty(bundle_dir / new_keystore_name, "NEW_KEYSTORE_MISSING")
    lineage = require_nonempty(bundle_dir / lineage_name, "LINEAGE_MISSING")

    old_password = read_password("DPC_AIO_OLD_SIGNING_PASSWORD", "Historical signing password: ")
    new_password = read_password("DPC_AIO_NEW_SIGNING_PASSWORD", "New signing password: ")

    secrets = {
        "DPC_AIO_MIGRATION_OLD_KEYSTORE_B64": b64_file(old_keystore),
        "DPC_AIO_MIGRATION_OLD_STORE_PASSWORD": old_password,
        "DPC_AIO_MIGRATION_OLD_KEY_ALIAS": old_alias,
        "DPC_AIO_MIGRATION_OLD_KEY_PASSWORD": old_password,
        "DPC_AIO_MIGRATION_NEW_KEYSTORE_B64": b64_file(new_keystore),
        "DPC_AIO_MIGRATION_NEW_STORE_PASSWORD": new_password,
        "DPC_AIO_MIGRATION_NEW_KEY_ALIAS": new_alias,
        "DPC_AIO_MIGRATION_NEW_KEY_PASSWORD": new_password,
        "DPC_AIO_MIGRATION_LINEAGE_B64": b64_file(lineage),
        "DPC_AIO_MIGRATION_OLD_CERT_SHA256": old_cert,
        "DPC_AIO_MIGRATION_NEW_CERT_SHA256": new_cert,
    }
    return secrets, metadata


def set_github_secret(gh: str, repo: str, name: str, secret_value: str) -> None:
    cp = subprocess.run(
        [gh, "secret", "set", name, "--repo", repo],
        input=secret_value,
        text=True,
        capture_output=True,
    )
    if cp.returncode != 0:
        message = (cp.stderr or cp.stdout or "gh secret set failed").strip()
        raise SystemExit(f"GITHUB_SECRET_SET_FAILED: {name}: {message}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle-dir", type=Path, required=True)
    parser.add_argument("--old-keystore", type=Path, required=True)
    parser.add_argument("--repo", required=True, help="GitHub repository in owner/name form")
    parser.add_argument("--configure-github", action="store_true")
    args = parser.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo):
        parser.error("--repo must be in owner/name form")

    secrets, metadata = load_bundle(args.bundle_dir, args.old_keystore)
    print("MIGRATION_SECRET_AUDIT: PASS")
    print(f"Repository: {args.repo}")
    print(f"Historical certificate SHA-256: {metadata['oldCertificateSha256']}")
    print(f"New certificate SHA-256: {metadata['newCertificateSha256']}")
    print(f"Rotation minimum SDK: {metadata['rotationMinSdkVersion']}")
    print(f"Prepared secret names: {len(secrets)}")

    if not args.configure_github:
        print("Audit-only: GitHub Secrets were not changed.")
        return 0

    gh = shutil.which("gh")
    if not gh:
        raise SystemExit("MIGRATION_PREFLIGHT_MISSING: gh (GitHub CLI) not found")

    for name, secret_value in secrets.items():
        set_github_secret(gh, args.repo, name, secret_value)

    print("MIGRATION_SECRETS_CONFIGURED: PASS")
    print(f"Configured secret names: {len(secrets)}")
    print("No migration workflow was dispatched and no release was published.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
