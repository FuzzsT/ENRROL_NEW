#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "release" / "configure_migration_signing.py"
EXPECTED_OLD = "E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4"
EXPECTED_SECRET_NAMES = {
    "DPC_AIO_MIGRATION_OLD_KEYSTORE_B64",
    "DPC_AIO_MIGRATION_OLD_STORE_PASSWORD",
    "DPC_AIO_MIGRATION_OLD_KEY_ALIAS",
    "DPC_AIO_MIGRATION_OLD_KEY_PASSWORD",
    "DPC_AIO_MIGRATION_NEW_KEYSTORE_B64",
    "DPC_AIO_MIGRATION_NEW_STORE_PASSWORD",
    "DPC_AIO_MIGRATION_NEW_KEY_ALIAS",
    "DPC_AIO_MIGRATION_NEW_KEY_PASSWORD",
    "DPC_AIO_MIGRATION_LINEAGE_B64",
    "DPC_AIO_MIGRATION_OLD_CERT_SHA256",
    "DPC_AIO_MIGRATION_NEW_CERT_SHA256",
}


def main() -> None:
    assert TOOL.is_file(), "missing migration secrets configurator"
    source = TOOL.read_text(encoding="utf-8")
    assert "--body" not in source, "secret values must not be passed in process arguments"
    assert "input=value" in source or "input=secret_value" in source, "gh secret set must receive secret via stdin"

    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        bundle = tmp / "bundle"
        bundle.mkdir()
        old_keystore = tmp / "historical.p12"
        old_keystore.write_bytes(b"old-keystore")
        (bundle / "dpc-aio-enterprise-rotated.p12").write_bytes(b"new-keystore")
        (bundle / "dpc-aio-signing-lineage.bin").write_bytes(b"lineage")
        (bundle / "rotation-metadata.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "oldAlias": "dpc-aio-enterprise",
                    "newAlias": "dpc-aio-enterprise-rotated",
                    "oldCertificateSha256": EXPECTED_OLD,
                    "newCertificateSha256": "A" * 64,
                    "rotationMinSdkVersion": 33,
                    "newKeystore": "dpc-aio-enterprise-rotated.p12",
                    "lineage": "dpc-aio-signing-lineage.bin",
                    "passwordsStored": False,
                }
            ),
            encoding="utf-8",
        )

        gh_log = tmp / "gh.log"
        fake_gh = tmp / ("gh.cmd" if os.name == "nt" else "gh")
        if os.name == "nt":
            fake_gh.write_text("@echo off\r\necho %*>>\"%GH_TEST_LOG%\"\r\nmore >nul\r\n", encoding="utf-8")
        else:
            fake_gh.write_text("#!/usr/bin/env bash\necho \"$*\" >> \"$GH_TEST_LOG\"\ncat >/dev/null\n", encoding="utf-8")
            fake_gh.chmod(0o755)

        env = os.environ.copy()
        env["PATH"] = str(tmp) + os.pathsep + env.get("PATH", "")
        env["GH_TEST_LOG"] = str(gh_log)
        env["DPC_AIO_OLD_SIGNING_PASSWORD"] = "old-password-example"
        env["DPC_AIO_NEW_SIGNING_PASSWORD"] = "new-password-example-1234"

        audit = subprocess.run(
            [sys.executable, str(TOOL), "--bundle-dir", str(bundle), "--old-keystore", str(old_keystore), "--repo", "FuzzsT/ENRROL_NEW"],
            cwd=ROOT,
            env=env,
            text=True,
            capture_output=True,
        )
        assert audit.returncode == 0, audit.stderr or audit.stdout
        assert "MIGRATION_SECRET_AUDIT: PASS" in audit.stdout
        assert not gh_log.exists() or not gh_log.read_text(encoding="utf-8").strip(), "audit-only must not invoke gh"

        configured = subprocess.run(
            [sys.executable, str(TOOL), "--bundle-dir", str(bundle), "--old-keystore", str(old_keystore), "--repo", "FuzzsT/ENRROL_NEW", "--configure-github"],
            cwd=ROOT,
            env=env,
            text=True,
            capture_output=True,
        )
        assert configured.returncode == 0, configured.stderr or configured.stdout
        assert "MIGRATION_SECRETS_CONFIGURED: PASS" in configured.stdout
        lines = gh_log.read_text(encoding="utf-8").splitlines()
        names = {line.split()[2] for line in lines if line.startswith("secret set ") and len(line.split()) >= 3}
        assert names == EXPECTED_SECRET_NAMES, (names, EXPECTED_SECRET_NAMES)
        joined = "\n".join(lines)
        assert "old-password-example" not in joined
        assert "new-password-example-1234" not in joined
        assert "--body" not in joined

    print("MIGRATION_SECRETS_CONFIGURATOR: PASS")


if __name__ == "__main__":
    main()
