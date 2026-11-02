#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/release/build_self_update_manifest.py"
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"
CLIENT = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt"
STORE = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateStore.kt"
COORDINATOR = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateOperationCoordinator.kt"
COORDINATOR_TEST = ROOT / "apps/dpc/modules/self-update/android/src/test/kotlin/io/dpcaio/selfupdate/android/SelfUpdateOperationCoordinatorTest.kt"
ACTIVITY = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt"


def invoke(apk: Path, out: Path, apk_url: str, channel: str = "CONTINUOUS"):
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--apk", str(apk),
            "--out", str(out),
            "--channel", channel,
            "--package", "io.dpcaio.app",
            "--version-name", "1.2.0",
            "--version-code", "26",
            "--apk-url", apk_url,
            "--published-at", "2026-10-30T00:00:00Z",
            "--release-notes-url", "https://github.com/FuzzsT/ENRROL_NEW/releases/tag/v1.2.0",
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )


def run_coordinator_behavior_test():
    with tempfile.TemporaryDirectory() as td:
        classes = Path(td) / "classes"
        classes.mkdir()
        compile_proc = subprocess.run(
            ["kotlinc", str(COORDINATOR), str(COORDINATOR_TEST), "-d", str(classes)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        assert compile_proc.returncode == 0, compile_proc.stdout
        run_proc = subprocess.run(
            ["kotlin", "-classpath", str(classes), "io.dpcaio.selfupdate.android.SelfUpdateOperationCoordinatorTestKt"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        assert run_proc.returncode == 0, run_proc.stdout
        assert "SelfUpdateOperationCoordinatorTest: PASS" in run_proc.stdout, run_proc.stdout


def main():
    with tempfile.TemporaryDirectory() as td:
        temp = Path(td)
        apk = temp / "DPC-AIO-enterprise-release.apk"
        apk.write_bytes(b"deterministic-self-update-apk\n")
        out = temp / "self-update.json"
        continuous_url = "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/DPC-AIO-enterprise-release.apk"

        proc = invoke(apk, out, continuous_url)
        assert proc.returncode == 0, proc.stdout
        payload = json.loads(out.read_text(encoding="utf-8"))
        assert payload["channel"] == "continuous"
        assert payload["apkUrl"] == continuous_url
        assert payload["apkSha256"] == hashlib.sha256(apk.read_bytes()).hexdigest()

        emergency_out = temp / "emergency.json"
        emergency_url = "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-emergency-enrollment/DPC-AIO-enterprise-release.apk"
        emergency = invoke(apk, emergency_out, emergency_url, channel="EMERGENCY")
        assert emergency.returncode == 0, emergency.stdout
        emergency_payload = json.loads(emergency_out.read_text(encoding="utf-8"))
        assert emergency_payload["channel"] == "emergency"
        assert emergency_payload["apkUrl"] == emergency_url

        bad = invoke(apk, temp / "bad.json", "http://example.invalid/update.apk")
        assert bad.returncode != 0, "non-HTTPS APK URL must be rejected"

    workflow = WORKFLOW.read_text(encoding="utf-8")
    for token in [
        "Build self-update manifest",
        "tools/release/build_self_update_manifest.py",
        "dist/self-update.json",
        'channel="continuous"',
        'channel="emergency"',
        'asset_tag="dpc-aio-emergency-enrollment"',
        "Verify published self-update manifest",
        "public-self-update.json",
        'expected_channel="emergency"',
        "PUBLIC_SELF_UPDATE_VERIFY_FAILED",
    ]:
        assert token in workflow, f"self-update release workflow missing: {token}"

    assert "steps.mode.outputs.should_publish == 'true'" in workflow, (
        "self-update manifest must be generated for every published channel, including emergency"
    )
    assert 'if [[ "$DPC_AIO_PIPELINE_MODE" == "emergency" ]]; then' in workflow, (
        "emergency builds must generate an emergency self-update manifest"
    )
    assert 'if [[ "$MODE" == "emergency" ]]; then' in workflow, (
        "published emergency self-update manifest must be verified"
    )
    assert 'if [[ "$mode" == "stable" && "$STABLE_SIGNING_CONFIGURED" != "true" ]]; then' in workflow
    assert 'Emergency signing is opt-in only and may only be selected explicitly from workflow_dispatch.' in workflow
    assert 'curl -fL --retry 3 --retry-delay 3 "$manifest_url" -o public-self-update.json' in workflow
    assert 'curl -fL --retry 3 --retry-delay 3 "$manifest_apk_url" -o public-self-update.apk' in workflow
    assert '[[ "$public_apk_sha" == "$manifest_apk_sha" && "$public_apk_sha" == "$local_apk_sha" ]]' in workflow

    client = CLIENT.read_text(encoding="utf-8")
    for token in ["MAX_MANIFEST_BYTES", "MAX_APK_BYTES", "readLimited", '"emergency" -> UpdateChannel.EMERGENCY']:
        assert token in client, f"self-update client contract missing: {token}"

    store = STORE.read_text(encoding="utf-8")
    for token in ["checked-manifest.json", "saveCheckedManifest", "loadCheckedManifest"]:
        assert token in store, f"checked Self Update manifest persistence missing: {token}"

    coordinator = COORDINATOR.read_text(encoding="utf-8")
    for token in ["AtomicReference", "Operation.CHECK", "Operation.DOWNLOAD", "Operation.INSTALL", "handoffDownloadToInstall"]:
        assert token in coordinator, f"Self Update unified operation gate missing: {token}"
    run_coordinator_behavior_test()

    activity = ACTIVITY.read_text(encoding="utf-8")
    assert "store.saveCheckedManifest(manifest)" in activity
    assert "UpdateChannel.EMERGENCY -> EMERGENCY_MANIFEST_URL" in activity
    assert "private var selectedChannel = UpdateChannel.CONTINUOUS" in activity

    print("SELF_UPDATE_RELEASE_MANIFEST: PASS")


if __name__ == "__main__":
    main()
