from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WF = ROOT / ".github" / "workflows" / "build-aio-enrollment.yml"
CI = ROOT / ".github" / "workflows" / "pr-android-ci.yml"


def main() -> None:
    text = WF.read_text(encoding="utf-8")
    ci = CI.read_text(encoding="utf-8")

    required = [
        "- migration",
        "publish_migration:",
        "MIGRATION_SIGNING_CONFIGURED",
        "MIGRATION_SIGNING_KEY_REQUIRED",
        "DPC_AIO_MIGRATION_OLD_KEYSTORE_B64",
        "DPC_AIO_MIGRATION_NEW_KEYSTORE_B64",
        "DPC_AIO_MIGRATION_LINEAGE_B64",
        "DPC_AIO_MIGRATION_OLD_CERT_SHA256",
        "DPC_AIO_MIGRATION_NEW_CERT_SHA256",
        "Prepare migration signers",
        "Sign migration APK with lineage",
        "--next-signer",
        "--lineage",
        "--rotation-min-sdk-version",
        "33",
        "migration-signing-metadata.json",
        "MIGRATION_SIGNING_VERIFIED",
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"missing migration signing workflow markers: {missing}"

    assert "test_migration_signing_workflow_contract.py" in ci, (
        "migration signing workflow contract must run in Android CI"
    )

    # auto must remain stable; migration is explicit workflow_dispatch only.
    assert 'requested="${{ inputs.signing_mode || \'auto\' }}"' in text
    assert 'if [[ "$requested" == "auto" ]]; then\n            mode="stable"' in text
    assert "workflow_dispatch" in text

    # Publishing a migration build requires a second explicit opt-in.
    assert "inputs.publish_migration" in text

    print("MIGRATION_SIGNING_WORKFLOW_CONTRACT: PASS")


if __name__ == "__main__":
    main()
