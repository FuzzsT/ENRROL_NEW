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
assert "fetchManifestWithFallback" not in activity, "STABLE must not silently fall back to CONTINUOUS"
assert "Stable channel unavailable; using continuous manifest" not in activity, "STABLE UI must not advertise automatic CONTINUOUS fallback"
assert "manifestAllowedForSelection" in activity, "channel-selection guard must remain explicit"
assert "manifest.channel == selectedChannel" in activity, "only the explicitly selected channel may be used"
assert "selectedChannel == UpdateChannel.STABLE && manifest.channel == UpdateChannel.CONTINUOUS" not in activity, (
    "STABLE selection must reject CONTINUOUS manifests"
)

check_body = activity.split("private fun checkNow()", 1)[1].split("private fun downloadAndVerify()", 1)[0]
assert "client.fetchManifest(manifestUrl(channel))" in check_body, "checkNow must fetch only the explicitly selected channel"
assert "fetchManifestWithFallback(channel)" not in check_body, "automatic cross-channel fallback must not return"
assert "selectedChannel =" not in check_body, "checkNow must not silently change the user's selected channel"
assert "putString(KEY_CHANNEL" not in check_body, "checkNow must not silently persist a different channel"

render_body = activity.split("private fun render()", 1)[1].split("private fun checkNow()", 1)[0]
assert "planner.evaluate(packageName, installed.second, it.channel, it).accepted" in render_body, "render must evaluate the manifest's channel"
assert "planner.evaluate(packageName, installed.second, it.manifest.channel, it.manifest).accepted" in render_body, "cached candidate must use its manifest channel"

download_body = activity.split("private fun downloadAndVerify()", 1)[1].split("private fun submitInstall(", 1)[0]
assert "planner.evaluate(packageName, installedVersionCode, manifest.channel, manifest)" in download_body, "download must validate the selected manifest channel"

install_body = activity.split("private fun submitInstall(", 1)[1].split("private fun clearCandidate()", 1)[0]
assert "candidate.manifest.channel" in install_body, "install must validate against the candidate manifest channel"

print("test_self_update_http_diagnostics: PASS")
