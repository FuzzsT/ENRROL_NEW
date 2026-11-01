#!/usr/bin/env python3
"""Audit and optionally configure DPC-AIO stable signing for GitHub Actions.

This tool intentionally never generates or rotates a signing key. It validates an
existing keystore, derives its certificate SHA-256 fingerprint, verifies private
key access before applying configuration, and can configure the repository
Actions secrets/variable through an authenticated GitHub CLI.
Secret values are passed via environment/stdin and are never printed or placed
in keytool argv.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEFAULT_REPO = "FuzzsT/ENRROL_NEW"
SECRET_NAMES = (
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
)
CERT_NAME = "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256"
ALL_CONFIG_NAMES = (*SECRET_NAMES, CERT_NAME)
STORE_PASSWORD_ENV = "DPC_AIO_SETUP_STORE_PASSWORD"
KEY_PASSWORD_ENV = "DPC_AIO_SETUP_KEY_PASSWORD"
VERIFY_PASSWORD_ENV = "DPC_AIO_SETUP_VERIFY_PASSWORD"


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"ERROR: {message}")


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        fail(f"required tool not found in PATH: {name}")
    return path


def prompt_secret(env_name: str, prompt: str) -> str:
    value = os.environ.get(env_name, "")
    if not value:
        value = getpass.getpass(prompt)
    if not value:
        fail(f"{env_name} cannot be empty")
    if "\n" in value or "\r" in value:
        fail(f"{env_name} must not contain line breaks")
    return value


def run_checked(
    command: list[str],
    *,
    input_text: str | None = None,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            input=input_text,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or exc.stdout or "command failed").strip()
        fail(f"command failed: {command[0]} ({detail})")


def password_env(store_password: str, key_password: str | None = None) -> dict[str, str]:
    env = os.environ.copy()
    env[STORE_PASSWORD_ENV] = store_password
    if key_password is not None:
        env[KEY_PASSWORD_ENV] = key_password
    return env


def inspect_keystore(
    keytool: str,
    keystore: Path,
    store_password: str,
    alias: str,
) -> str:
    proc = run_checked(
        [
            keytool,
            "-list",
            "-v",
            "-keystore",
            str(keystore),
            "-storepass:env",
            STORE_PASSWORD_ENV,
            "-alias",
            alias,
        ],
        env=password_env(store_password),
    )
    combined = f"{proc.stdout}\n{proc.stderr}"
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]{32,})", combined)
    if not match:
        fail("could not extract SHA-256 certificate fingerprint from keytool output")
    fingerprint = re.sub(r"[^0-9A-Fa-f]", "", match.group(1)).upper()
    if len(fingerprint) != 64:
        fail(f"unexpected SHA-256 fingerprint length: {len(fingerprint)}")
    return fingerprint


def verify_private_key_password(
    keytool: str,
    keystore: Path,
    store_password: str,
    alias: str,
    key_password: str,
) -> None:
    """Prove private-key access without modifying the source keystore.

    keytool importkeystore must decrypt the selected private-key entry to copy it
    into a temporary PKCS12 keystore. A wrong key password therefore fails before
    any GitHub secret is written. The temporary copy is deleted automatically.
    """
    with tempfile.TemporaryDirectory(prefix="dpc-aio-key-verify-") as temp_dir:
        destination = Path(temp_dir) / "private-key-check.p12"
        verify_password = secrets.token_urlsafe(32)
        env = password_env(store_password, key_password)
        env[VERIFY_PASSWORD_ENV] = verify_password

        run_checked(
            [
                keytool,
                "-importkeystore",
                "-noprompt",
                "-srckeystore",
                str(keystore),
                "-srcalias",
                alias,
                "-srcstorepass:env",
                STORE_PASSWORD_ENV,
                "-srckeypass:env",
                KEY_PASSWORD_ENV,
                "-destkeystore",
                str(destination),
                "-deststoretype",
                "PKCS12",
                "-deststorepass:env",
                VERIFY_PASSWORD_ENV,
                "-destkeypass:env",
                VERIFY_PASSWORD_ENV,
            ],
            env=env,
        )
        if not destination.is_file() or destination.stat().st_size <= 0:
            fail("private key validation did not produce a temporary keystore")

        run_checked(
            [
                keytool,
                "-list",
                "-keystore",
                str(destination),
                "-alias",
                alias,
                "-storepass:env",
                VERIFY_PASSWORD_ENV,
            ],
            env=env,
        )


def set_secret(gh: str, repo: str, name: str, value: str) -> None:
    # `gh secret set` reads the secret from stdin when --body is omitted. This
    # keeps the value out of the process argv and normal console output.
    run_checked([gh, "secret", "set", name, "--repo", repo], input_text=value)


def set_variable(gh: str, repo: str, name: str, value: str) -> None:
    # A certificate fingerprint is public metadata, not a secret. It is stored
    # as the repository variable expected by the release workflow.
    run_checked([gh, "variable", "set", name, "--repo", repo, "--body", value])


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Audit an existing DPC-AIO release keystore and optionally configure "
            "the GitHub Actions stable-signing secrets/variable. No key is generated."
        )
    )
    parser.add_argument("--keystore", type=Path, help="Path to the existing stable release keystore")
    parser.add_argument("--alias", help="Key alias inside the keystore; prompts if omitted")
    parser.add_argument("--repo", default=DEFAULT_REPO, help=f"GitHub repository (default: {DEFAULT_REPO})")
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Validate private-key access, then configure GitHub Actions using authenticated gh; default is audit-only",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.keystore is None:
        if "--help" not in sys.argv and "-h" not in sys.argv:
            fail("--keystore is required for audit/apply")
        return 0

    keystore = args.keystore.expanduser().resolve()
    if not keystore.is_file():
        fail(f"keystore does not exist: {keystore}")
    if keystore.stat().st_size <= 0:
        fail(f"keystore is empty: {keystore}")

    keytool = require_tool("keytool")
    alias = args.alias or os.environ.get("DPC_AIO_RELEASE_KEY_ALIAS", "")
    if not alias:
        alias = input("Release key alias: ").strip()
    if not alias:
        fail("DPC_AIO_RELEASE_KEY_ALIAS cannot be empty")

    store_password = prompt_secret(
        "DPC_AIO_RELEASE_STORE_PASSWORD",
        "Keystore password: ",
    )
    fingerprint = inspect_keystore(keytool, keystore, store_password, alias)

    print("Stable signing audit: OK")
    print(f"Repository: {args.repo}")
    print(f"Keystore: {keystore}")
    print(f"Keystore bytes: {keystore.stat().st_size}")
    print(f"Key alias: {alias}")
    print(f"Certificate SHA-256: {fingerprint}")
    print("Required GitHub Actions entries:")
    for name in ALL_CONFIG_NAMES:
        print(f"  - {name}")

    if not args.apply:
        print("Mode: audit-only (use --apply to validate private-key access and configure GitHub Actions)")
        return 0

    key_password = prompt_secret(
        "DPC_AIO_RELEASE_KEY_PASSWORD",
        "Private key password: ",
    )
    verify_private_key_password(keytool, keystore, store_password, alias, key_password)
    print("Private key access validation: OK")

    gh = require_tool("gh")
    run_checked([gh, "auth", "status"])

    keystore_b64 = base64.b64encode(keystore.read_bytes()).decode("ascii")
    values = {
        "DPC_AIO_RELEASE_KEYSTORE_B64": keystore_b64,
        "DPC_AIO_RELEASE_STORE_PASSWORD": store_password,
        "DPC_AIO_RELEASE_KEY_ALIAS": alias,
        "DPC_AIO_RELEASE_KEY_PASSWORD": key_password,
    }
    for name in SECRET_NAMES:
        set_secret(gh, args.repo, name, values[name])
    set_variable(gh, args.repo, CERT_NAME, fingerprint)

    print("GitHub Actions stable-signing configuration: APPLIED")
    print("Secret values were not printed. The existing release key was not modified or replaced.")
    print(f"Configured repository: {args.repo}")
    print(f"Expected certificate SHA-256: {fingerprint}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
