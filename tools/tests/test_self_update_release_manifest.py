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


def invoke(apk: Path, out: Path, apk_url: str):
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--apk", str(apk),
            "--out", str(out),
            "--channel", "STABLE",
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
            [
                "kotlin",
                "-classpath",
                str(classes),
                "io.dpcaio.selfupdate.android.SelfUpdateOperationCoordinatorTestKt",
            ],
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

        proc = invoke(
            apk,
            out,
            "https://github.com/FuzzsT/ENRROL_NEW/releases/download/v1.2.0/DPC-AIO-enterprise-release.apk",
        )
        assert proc.returncode == 0, proc.stdout
        assert out.is_file(), "self-update.json was not created"

        first_bytes = out.read_bytes()
        payload = json.loads(first_bytes.decode("utf-8"))
        assert payload == {
            "schemaVersion": 1,
            "channel": "stable",
            "packageName": "io.dpcaio.app",
            "versionName": "1.2.0",
            "versionCode": 26,
            "apkUrl": "https://github.com/FuzzsT/ENRROL_NEW/releases/download/v1.2.0/DPC-AIO-enterprise-release.apk",
            "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "apkSize": apk.stat().st_size,
            "publishedAt": "2026-10-30T00:00:00Z",
            "releaseNotesUrl": "https://github.com/FuzzsT/ENRROL_NEW/releases/tag/v1.2.0",
        }
        assert payload["apkSha256"] == payload["apkSha256"].lower(), "SHA-256 must be lowercase"

        proc2 = invoke(
            apk,
            out,
            "https://github.com/FuzzsT/ENRROL_NEW/releases/download/v1.2.0/DPC-AIO-enterprise-release.apk",
        )
        assert proc2.returncode == 0, proc2.stdout
        assert out.read_bytes() == first_bytes, "generator output must be deterministic for identical inputs"

        bad = invoke(apk, temp / "bad.json", "http://example.invalid/update.apk")
        assert bad.returncode != 0, "non-HTTPS APK URL must be rejected"

    workflow = WORKFLOW.read_text(encoding="utf-8")
    for token in [
        "Build self-update manifest",
        "tools/release/build_self_update_manifest.py",
        "dist/self-update.json",
        "dpc-aio-stable",
        "DPC-AIO-enterprise-release.apk",
        "gh release upload",
        "Verify published self-update manifest",
        "public-self-update.json",
        "manifest_apk_sha",
        "manifest_apk_url",
        "manifest_channel",
        "PUBLIC_SELF_UPDATE_VERIFY_FAILED",
    ]:
        assert token in workflow, f"self-update release workflow missing: {token}"
    assert 'channel="stable"' in workflow, "tag release must produce stable self-update channel"
    assert 'channel="continuous"' in workflow, "rolling release must produce continuous self-update channel"
    assert 'stable_alias="dpc-aio-stable"' in workflow, "stable self-update must use deterministic alias"
    assert 'if [[ "${{ github.event_name }}" == "workflow_dispatch" ]]; then' in workflow, (
        "manual auto runs must be detected before the main-branch emergency fallback"
    )
    assert 'mode="stable" # manual auto repair/publish path' in workflow, (
        "workflow_dispatch with signing_mode=auto must select stable signing for continuous self-update publishing"
    )
    assert "STABLE_SIGNING_CONFIGURED" in workflow, (
        "automatic main self-update publishing must be gated by a stable-signing readiness flag"
    )
    assert 'if [[ "$GITHUB_REF" == "refs/heads/main" && "$STABLE_SIGNING_CONFIGURED" == "true" ]]; then' in workflow, (
        "main auto mode must prefer stable signing only when the full stable signer is configured"
    )
    assert 'mode="stable" # automatic main self-update publish when stable signer configured' in workflow, (
        "configured main pushes must publish the continuous self-update channel"
    )
    assert 'elif [[ "$GITHUB_REF" == "refs/heads/main" ]]; then' in workflow, (
        "main must retain an emergency fallback when stable signing is not configured"
    )
    assert 'if [[ "$mode" == "stable" && "$GITHUB_REF" == "refs/heads/main" && -z "${{ inputs.apk_url }}" ]]; then' in workflow, (
        "stable main builds must publish dpc-aio-continuous when they build their own APK"
    )
    assert 'curl -fL --retry 3 --retry-delay 3 "$manifest_url" -o public-self-update.json' in workflow, (
        "published self-update verification must fetch the public manifest"
    )
    assert 'curl -fL --retry 3 --retry-delay 3 "$manifest_apk_url" -o public-self-update.apk' in workflow, (
        "published self-update verification must fetch the APK declared by the manifest"
    )
    assert '[[ "$public_apk_sha" == "$manifest_apk_sha" && "$public_apk_sha" == "$local_apk_sha" ]]' in workflow, (
        "published self-update verification must require public, manifest, and local APK SHA-256 identity"
    )

    client = CLIENT.read_text(encoding="utf-8")
    for token in [
        "MAX_MANIFEST_BYTES",
        "MAX_APK_BYTES",
        "readLimited",
        "maxBytes: Long = MAX_APK_BYTES",
        "require(maxBytes in 1..MAX_APK_BYTES)",
        "expectedLength > maxBytes",
        "downloaded + count > maxBytes",
    ]:
        assert token in client, f"self-update network payload limit missing: {token}"

    store = STORE.read_text(encoding="utf-8")
    for token in [
        "checked-manifest.json",
        "saveCheckedManifest",
        "loadCheckedManifest",
    ]:
        assert token in store, f"checked Self Update manifest persistence missing: {token}"

    coordinator = COORDINATOR.read_text(encoding="utf-8")
    for token in [
        "AtomicReference",
        "enum class Operation",
        "Operation.CHECK",
        "Operation.DOWNLOAD",
        "Operation.INSTALL",
        "tryStartCheck",
        "finishCheck",
        "handoffDownloadToInstall",
    ]:
        assert token in coordinator, f"Self Update unified operation gate missing: {token}"
    assert "AtomicBoolean" not in coordinator, "Self Update operations must share one atomic slot"
    run_coordinator_behavior_test()

    activity = ACTIVITY.read_text(encoding="utf-8")
    assert "store.saveCheckedManifest(manifest)" in activity, "successful update check must persist the checked manifest"
    assert activity.count("store.loadCheckedManifest()") >= 2, (
        "Self Update must restore the checked manifest across Activity recreation/resume"
    )
    check_body = activity.split("private fun checkNow()", 1)[1].split("private fun downloadAndVerify()", 1)[0]
    assert "operations.tryStartCheck()" in check_body, "Self Update check must use the shared operation gate"
    assert "operations.finishCheck()" in check_body, "Self Update check gate must be released in all outcomes"

    download_body = activity.split("private fun downloadAndVerify()", 1)[1].split("private fun submitInstall", 1)[0]
    for token in [
        "var autoSilentAfterDownload = false",
        "val silentAvailable = autoSilentEnabled && silentInstallLikelyAvailable()",
        "autoSilentAfterDownload = silentAvailable",
        "operations.handoffDownloadToInstall()",
        "SelfUpdateInstallPolicy.AUTO_SILENT",
        "installSlotAlreadyHeld = true",
    ]:
        assert token in download_body, f"Self Update atomic auto-silent handoff missing: {token}"
    assert download_body.rfind("operations.handoffDownloadToInstall()") < download_body.rfind("installSlotAlreadyHeld = true"), (
        "Self Update must acquire the INSTALL slot atomically before staging auto-silent install"
    )

    submit_body = activity.split("private fun submitInstall", 1)[1].split("private fun clearCandidate", 1)[0]
    assert "installSlotAlreadyHeld: Boolean = false" in submit_body, (
        "Self Update install must support an already-held INSTALL slot from atomic auto-silent handoff"
    )

    print("SELF_UPDATE_RELEASE_MANIFEST: PASS")


if __name__ == "__main__":
    main()
