from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WRAPPER = ROOT / "tools" / "release" / "recover_stable_signer_verified.py"
LAUNCHER = ROOT / "RECOVER-STABLE-SIGNER.ps1"


def main() -> None:
    assert WRAPPER.is_file(), "verified recovery wrapper is missing"
    text = WRAPPER.read_text(encoding="utf-8")
    launcher = LAUNCHER.read_text(encoding="utf-8")

    required = [
        'EXPECTED_ENCRYPTED_ASSET_SHA256 = "C00E8E06D30A762C88925594B403468A4FBE031B97095DBAD0DE5C9E2FACD6BD"',
        'EXPECTED_HISTORICAL_APK_SHA256 = "02C0E15DFB86C499369FCD64ABA3D6163DF52112DE231CC4230C2470D5AC1A7C"',
        "def verify_file_sha256(",
        "PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH",
        "HISTORICAL_APK_DIGEST_MISMATCH",
        "recover_persisted_signing_keystore.py",
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"missing stable signer recovery integrity markers: {missing}"

    assert "recover_stable_signer_verified.py" in launcher, "launcher must use verified wrapper"
    assert "recover_persisted_signing_keystore.py" not in launcher, "launcher must not bypass verified wrapper"

    print("STABLE_SIGNER_RECOVERY_INTEGRITY_CONTRACT: PASS")


if __name__ == "__main__":
    main()
