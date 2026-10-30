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
    ]:
        assert token in workflow, f"self-update release workflow missing: {token}"
    assert 'channel="stable"' in workflow, "tag release must produce stable self-update channel"
    assert 'channel="continuous"' in workflow, "rolling release must produce continuous self-update channel"
    assert 'stable_alias="dpc-aio-stable"' in workflow, "stable self-update must use deterministic alias"

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

    print("SELF_UPDATE_RELEASE_MANIFEST: PASS")


if __name__ == "__main__":
    main()
