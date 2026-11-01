from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "release" / "prepare_signing_rotation.py"
LAUNCHER = ROOT / "PREPARE-STABLE-SIGNER-ROTATION.ps1"
CI = ROOT / ".github" / "workflows" / "pr-android-ci.yml"


def main() -> None:
    assert TOOL.is_file(), "missing signing rotation preparation tool"
    text = TOOL.read_text(encoding="utf-8")
    launcher = LAUNCHER.read_text(encoding="utf-8") if LAUNCHER.is_file() else ""
    ci = CI.read_text(encoding="utf-8")

    required = [
        'EXPECTED_OLD_CERT_SHA256 = "E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4"',
        "DEFAULT_ROTATION_MIN_SDK = 33",
        "apksigner",
        '"rotate"',
        '"--old-signer"',
        '"--new-signer"',
        '"--ks-pass"',
        '"env:DPC_AIO_OLD_SIGNING_PASSWORD"',
        '"env:DPC_AIO_NEW_SIGNING_PASSWORD"',
        '"rotation-min-sdk-version"',
        '"oldCertificateSha256"',
        '"newCertificateSha256"',
        '"lineage"',
        "HISTORICAL_SIGNER_MISMATCH",
        "ROTATION_PREPARED: PASS",
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"missing stable signer rotation contract markers: {missing}"

    assert "gh secret" not in text.lower(), "rotation preparation must not configure GitHub secrets"
    assert "workflow run" not in text.lower(), "rotation preparation must not dispatch workflows"
    assert "--password" not in text, "passwords must not be accepted on the command line"

    assert "prepare_signing_rotation.py" in launcher
    assert "test_stable_signer_rotation_contract.py" in ci

    print("STABLE_SIGNER_ROTATION_CONTRACT: PASS")


if __name__ == "__main__":
    main()
