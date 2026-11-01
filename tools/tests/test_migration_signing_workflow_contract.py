from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN_WF = ROOT / ".github" / "workflows" / "build-aio-enrollment.yml"
MIGRATION_WF = ROOT / ".github" / "workflows" / "migration-signing.yml"
HELPER = ROOT / "tools" / "release" / "sign_migration_apk.py"
CI = ROOT / ".github" / "workflows" / "pr-android-ci.yml"


def main() -> None:
    main_text = MAIN_WF.read_text(encoding="utf-8")
    migration_text = MIGRATION_WF.read_text(encoding="utf-8")
    helper_text = HELPER.read_text(encoding="utf-8")
    ci = CI.read_text(encoding="utf-8")

    required_workflow = [
        "name: Migration signing validation",
        "workflow_dispatch:",
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
        "migration-signing-metadata.json",
        "MIGRATION_SIGNING_VERIFIED",
        "dpc-aio-migration",
        "inputs.publish_migration == true",
    ]
    missing = [item for item in required_workflow if item not in migration_text]
    assert not missing, f"missing migration workflow markers: {missing}"

    required_helper = [
        '"--next-signer"',
        '"--lineage"',
        '"--rotation-min-sdk-version"',
        'default=33',
        '"env:DPC_AIO_MIGRATION_OLD_STORE_PASSWORD"',
        '"env:DPC_AIO_MIGRATION_NEW_STORE_PASSWORD"',
        "MIGRATION_OLD_CERT_MISMATCH",
        "MIGRATION_NEW_CERT_MISMATCH",
        "MIGRATION_SIGNING_VERIFIED",
    ]
    helper_missing = [item for item in required_helper if item not in helper_text]
    assert not helper_missing, f"missing migration helper markers: {helper_missing}"

    assert "test_migration_signing_workflow_contract.py" in ci, (
        "migration signing workflow contract must run in Android CI"
    )

    # Normal production release stays isolated: auto remains stable and migration
    # is not a signing_mode option in build-aio-enrollment.yml.
    assert 'requested="${{ inputs.signing_mode || \'auto\' }}"' in main_text
    assert 'if [[ "$requested" == "auto" ]]; then\n            mode="stable"' in main_text
    signing_mode_section = main_text.split("signing_mode:", 1)[1].split("apk_url:", 1)[0]
    assert "migration" not in signing_mode_section

    print("MIGRATION_SIGNING_WORKFLOW_CONTRACT: PASS")


if __name__ == "__main__":
    main()
