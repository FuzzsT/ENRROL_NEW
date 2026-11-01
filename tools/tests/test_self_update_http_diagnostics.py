from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt"
ACTIVITY = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt"

text = CLIENT.read_text(encoding="utf-8")

required = [
    "SelfUpdateHttpException",
    "statusCode: Int",
    "requestedUrl: String",
    "HTTP_NOT_FOUND",
    'requestedUrl.contains("/dpc-aio-stable/", ignoreCase = true)',
    'requestedUrl.contains("/dpc-aio-continuous/", ignoreCase = true)',
    "channel is not published yet (manifest HTTP 404)",
    "Self Update manifest was not found (HTTP 404)",
    "Self Update APK asset was not found (HTTP 404)",
]

for token in required:
    assert token in text, f"missing self-update HTTP diagnostic contract: {token}"

assert 'throw IOException("HTTP ${connection.responseCode}")' not in text, "raw HTTP-only diagnostic must not return"

activity = ACTIVITY.read_text(encoding="utf-8")
for token in [
    "fetchManifestWithFallback",
    "SelfUpdateClient.SelfUpdateHttpException",
    "error.statusCode == 404",
    "channel == UpdateChannel.STABLE",
    "client.fetchManifest(CONTINUOUS_MANIFEST_URL)",
    "Stable channel unavailable; using continuous manifest",
    "manifestAllowedForSelection",
]:
    assert token in activity, f"stable-to-continuous fallback contract missing: {token}"

check_body = activity.split("private fun checkNow()", 1)[1].split("private fun downloadAndVerify()", 1)[0]
assert "fetchManifestWithFallback(channel)" in check_body, "checkNow must use controlled manifest fallback"
assert "client.fetchManifest(manifestUrl(channel))" not in check_body, "direct manifest fetch bypasses fallback"
assert "selectedChannel = effectiveChannel" not in check_body, "fallback must not overwrite the user's selected STABLE channel"
assert "putString(KEY_CHANNEL, effectiveChannel.name)" not in check_body, "fallback must not persist CONTINUOUS as the user's channel"

render_body = activity.split("private fun render()", 1)[1].split("private fun checkNow()", 1)[0]
assert "planner.evaluate(packageName, installed.second, it.channel, it).accepted" in render_body, "render must evaluate the manifest's effective channel"
assert "planner.evaluate(packageName, installed.second, it.manifest.channel, it.manifest).accepted" in render_body, "cached candidate must use its manifest channel"

download_body = activity.split("private fun downloadAndVerify()", 1)[1].split("private fun submitInstall(", 1)[0]
assert "planner.evaluate(packageName, installedVersionCode, manifest.channel, manifest)" in download_body, "download must use fallback manifest channel"

install_body = activity.split("private fun submitInstall(", 1)[1].split("private fun clearCandidate()", 1)[0]
assert "candidate.manifest.channel" in install_body, "install must validate against the candidate manifest channel"

print("test_self_update_http_diagnostics: PASS")
