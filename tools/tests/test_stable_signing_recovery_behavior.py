#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
RECOVERY = ROOT / "tools/release/recover_stable_signing.py"
ALIAS = "dpc-aio-enterprise"
PASSWORD = "behavior-test-only-password"
PASSWORD_ENV = "DPC_AIO_LEGACY_SIGNING_PASSWORD"


def require(name: str) -> str:
    path = shutil.which(name)
    assert path, f"required test program missing: {name}"
    return path


def run(command: list[str], *, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(
        command,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert proc.returncode == 0, f"command failed: {' '.join(command)}\n{proc.stdout}\n{proc.stderr}"
    return proc


def generate_keystore(keytool: str, path: Path, cn: str) -> None:
    env = os.environ.copy()
    env[PASSWORD_ENV] = PASSWORD
    run(
        [
            keytool,
            "-genkeypair",
            "-alias",
            ALIAS,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "365",
            "-dname",
            f"CN={cn}, OU=CI, O=DPC-AIO, C=PL",
            "-storetype",
            "PKCS12",
            "-keystore",
            str(path),
            "-storepass:env",
            PASSWORD_ENV,
            "-keypass:env",
            PASSWORD_ENV,
        ],
        env=env,
    )


def certificate_sha256(keytool: str, path: Path) -> str:
    env = os.environ.copy()
    env[PASSWORD_ENV] = PASSWORD
    proc = run(
        [
            keytool,
            "-list",
            "-v",
            "-alias",
            ALIAS,
            "-keystore",
            str(path),
            "-storepass:env",
            PASSWORD_ENV,
        ],
        env=env,
    )
    combined = f"{proc.stdout}\n{proc.stderr}"
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]{32,})", combined)
    assert match, combined
    return re.sub(r"[^0-9A-Fa-f]", "", match.group(1)).upper()


def encrypt_legacy(openssl: str, source: Path, destination: Path) -> None:
    env = os.environ.copy()
    env[PASSWORD_ENV] = PASSWORD
    run(
        [
            openssl,
            "enc",
            "-aes-256-cbc",
            "-salt",
            "-pbkdf2",
            "-iter",
            "600000",
            "-in",
            str(source),
            "-out",
            str(destination),
            "-pass",
            f"env:{PASSWORD_ENV}",
        ],
        env=env,
    )


def load_recovery_module():
    spec = importlib.util.spec_from_file_location("recover_stable_signing_behavior", RECOVERY)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> None:
    assert RECOVERY.is_file(), RECOVERY
    openssl = require("openssl")
    keytool = require("keytool")
    recovery = load_recovery_module()

    with tempfile.TemporaryDirectory(prefix="dpc-aio-recovery-behavior-") as td:
        temp = Path(td)
        historical = temp / "historical.p12"
        unrelated = temp / "unrelated.p12"
        encrypted = temp / "DPC-AIO-signing-keystore.enc"
        materialized = temp / "materialized.enc"
        recovered = temp / "recovered.p12"
        metadata_match = temp / "provisioning-match.json"
        metadata_mismatch = temp / "provisioning-mismatch.json"

        generate_keystore(keytool, historical, "Historical Test Signer")
        generate_keystore(keytool, unrelated, "Unrelated Test Signer")
        expected = certificate_sha256(keytool, historical)
        wrong = certificate_sha256(keytool, unrelated)
        assert expected != wrong

        encrypt_legacy(openssl, historical, encrypted)
        metadata_match.write_text(
            json.dumps({"checksumMode": "signature", "certificateSha256Hex": expected}),
            encoding="utf-8",
        )
        metadata_mismatch.write_text(
            json.dumps({"checksumMode": "signature", "certificateSha256Hex": wrong}),
            encoding="utf-8",
        )

        size = recovery.materialize_encrypted(str(encrypted), materialized)
        assert size == encrypted.stat().st_size
        recovery.decrypt_keystore(openssl, materialized, recovered, PASSWORD)
        assert recovered.read_bytes() == historical.read_bytes(), "legacy decrypt must reproduce original keystore bytes"

        actual = recovery.verify_historical_lineage(
            keytool,
            recovered,
            PASSWORD,
            ALIAS,
            str(metadata_match),
        )
        assert actual == expected

        try:
            recovery.verify_historical_lineage(
                keytool,
                recovered,
                PASSWORD,
                ALIAS,
                str(metadata_mismatch),
            )
        except SystemExit as exc:
            assert exc.code == 2
        else:
            raise AssertionError("lineage mismatch must fail closed")

    print("STABLE_SIGNING_RECOVERY_BEHAVIOR: PASS")


if __name__ == "__main__":
    main()
