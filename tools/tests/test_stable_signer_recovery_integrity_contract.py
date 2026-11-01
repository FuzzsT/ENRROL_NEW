from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECOVERY = ROOT / "tools" / "release" / "recover_persisted_signing_keystore.py"


def main() -> None:
    text = RECOVERY.read_text(encoding="utf-8")

    required = [
        'EXPECTED_ENCRYPTED_ASSET_SHA256 = "C00E8E06D30A762C88925594B403468A4FBE031B97095DBAD0DE5C9E2FACD6BD"',
        'EXPECTED_HISTORICAL_APK_SHA256 = "02C0E15DFB86C499369FCD64ABA3D6163DF52112DE231CC4230C2470D5AC1A7C"',
        "def verify_file_sha256(",
        "PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH",
        "HISTORICAL_APK_DIGEST_MISMATCH",
        '"encryptedAssetSha256"',
        '"historicalApkSha256"',
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"missing stable signer recovery integrity contract markers: {missing}"

    encrypted_verify = text.find("PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH")
    decrypt = text.find("decrypt_keystore(openssl, encrypted, recovered, password)")
    assert 0 <= encrypted_verify < decrypt, "encrypted asset digest must be verified before decrypt"

    apk_verify = text.find("HISTORICAL_APK_DIGEST_MISMATCH")
    apk_signer = text.find("historical_apk_fingerprint = apk_signer_fingerprint")
    assert 0 <= apk_verify < apk_signer, "historical APK digest must be verified before signer inspection"

    print("STABLE_SIGNER_RECOVERY_INTEGRITY_CONTRACT: PASS")


if __name__ == "__main__":
    main()
