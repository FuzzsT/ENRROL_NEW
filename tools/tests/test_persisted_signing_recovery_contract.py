#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "tools" / "release" / "recover_persisted_signing_keystore.py"
PASSWORD = "UnitTestRecoveryPassword-123!"
ALIAS = "dpc-aio-enterprise"


def require(name: str) -> str:
    path = shutil.which(name)
    if not path:
        print(f"SKIP: {name} not available")
        raise SystemExit(0)
    return path


def run(args, *, env=None, check=True):
    return subprocess.run(
        args,
        env=env,
        text=True,
        capture_output=True,
        check=check,
    )


source = HELPER.read_text(encoding="utf-8")
for marker in (
    '"-aes-256-cbc"',
    '"-pbkdf2"',
    '"600000"',
    '"env:DPC_AIO_EFFECTIVE_SIGNING_PASSWORD"',
    "DPC_AIO_RECOVERY_PASSWORD",
    "--configure-github",
    "SIGNER_MISMATCH",
    "DEFAULT_HISTORICAL_APK_URL",
    "--historical-apk-url",
    "--historical-apk-file",
    "HISTORICAL_APK_SIGNER_MISMATCH",
    "apksigner",
    '"signing_mode=stable"',
):
    assert marker in source, marker
assert 'add_argument("--password"' not in source
assert "print(password" not in source
assert "DPC_AIO_RELEASE_KEYSTORE_B64" in source

keytool = require("keytool")
openssl = require("openssl")
python = sys.executable

with tempfile.TemporaryDirectory(prefix="dpc-aio-recovery-contract-") as td_raw:
    td = Path(td_raw)
    original = td / "original.p12"
    encrypted = td / "signer.enc"
    historical_apk = td / "historical.apk"
    recovered_dir = td / "recovered"
    fake_bin = td / "bin"
    fake_bin.mkdir()
    fake_apksigner = fake_bin / "apksigner"

    env = os.environ.copy()
    env["DPC_AIO_TEST_PASSWORD"] = PASSWORD

    run([
        keytool,
        "-genkeypair",
        "-noprompt",
        "-keystore", str(original),
        "-storetype", "PKCS12",
        "-storepass:env", "DPC_AIO_TEST_PASSWORD",
        "-keypass:env", "DPC_AIO_TEST_PASSWORD",
        "-alias", ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-sigalg", "SHA256withRSA",
        "-validity", "30",
        "-dname", "CN=DPC-AIO Recovery Contract",
    ], env=env)

    run([
        openssl,
        "enc",
        "-aes-256-cbc",
        "-salt",
        "-pbkdf2",
        "-iter", "600000",
        "-in", str(original),
        "-out", str(encrypted),
        "-pass", "env:DPC_AIO_TEST_PASSWORD",
    ], env=env)

    cert = td / "original-cert.der"
    run([
        keytool,
        "-exportcert",
        "-keystore", str(original),
        "-storetype", "PKCS12",
        "-storepass:env", "DPC_AIO_TEST_PASSWORD",
        "-alias", ALIAS,
        "-file", str(cert),
    ], env=env)
    expected_fingerprint = hashlib.sha256(cert.read_bytes()).hexdigest().upper()

    historical_apk.write_bytes(b"contract historical apk placeholder\n")
    fake_apksigner.write_text(
        "#!/usr/bin/env python3\n"
        "import os\n"
        "print('Signer #1 certificate SHA-256 digest: ' + os.environ['DPC_AIO_TEST_APK_SHA'])\n",
        encoding="utf-8",
    )
    fake_apksigner.chmod(stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR)

    recover_env = os.environ.copy()
    recover_env["DPC_AIO_RECOVERY_PASSWORD"] = PASSWORD
    recover_env["DPC_AIO_TEST_APK_SHA"] = expected_fingerprint
    recover_env["PATH"] = str(fake_bin) + os.pathsep + recover_env.get("PATH", "")
    cp = run([
        python,
        str(HELPER),
        "--encrypted-file", str(encrypted),
        "--historical-apk-file", str(historical_apk),
        "--backup-dir", str(recovered_dir),
    ], env=recover_env)
    assert "Historical APK signer verification: PASS" in cp.stdout
    assert "Historical signing keystore recovery completed." in cp.stdout
    assert PASSWORD not in cp.stdout + cp.stderr
    assert (recovered_dir / "dpc-aio-enterprise-release.p12").is_file()
    metadata = (recovered_dir / "metadata.json").read_text(encoding="utf-8")
    assert '"iterations": 600000' in metadata
    assert '"historicalApkSignerVerified": true' in metadata
    assert f'"historicalApkCertificateSha256": "{expected_fingerprint}"' in metadata
    assert PASSWORD not in metadata

    wrong_dir = td / "wrong"
    wrong_env = recover_env.copy()
    wrong_env["DPC_AIO_RECOVERY_PASSWORD"] = "WrongRecoveryPassword-999!"
    cp = run([
        python,
        str(HELPER),
        "--encrypted-file", str(encrypted),
        "--historical-apk-file", str(historical_apk),
        "--backup-dir", str(wrong_dir),
    ], env=wrong_env, check=False)
    assert cp.returncode != 0
    assert "PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH" in cp.stderr
    assert wrong_env["DPC_AIO_RECOVERY_PASSWORD"] not in cp.stdout + cp.stderr

    mismatch_dir = td / "mismatch"
    mismatch_env = recover_env.copy()
    mismatch_env["DPC_AIO_TEST_APK_SHA"] = "0" * 64
    cp = run([
        python,
        str(HELPER),
        "--encrypted-file", str(encrypted),
        "--historical-apk-file", str(historical_apk),
        "--backup-dir", str(mismatch_dir),
    ], env=mismatch_env, check=False)
    assert cp.returncode != 0
    assert "HISTORICAL_APK_SIGNER_MISMATCH" in cp.stderr
    assert PASSWORD not in cp.stdout + cp.stderr

print("PERSISTED_SIGNING_RECOVERY_CONTRACT: PASS")
