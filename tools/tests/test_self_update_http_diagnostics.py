from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt"
MODEL = ROOT / "apps/dpc/modules/self-update/core/src/main/kotlin/io/dpcaio/selfupdate/SelfUpdateModels.kt"
ACTIVITY = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt"

text = CLIENT.read_text(encoding="utf-8")
model = MODEL.read_text(encoding="utf-8")
activity = ACTIVITY.read_text(encoding="utf-8")

for token in [
    "SelfUpdateHttpException",
    "statusCode: Int",
    "requestedUrl: String",
    "HTTP_NOT_FOUND",
    'requestedUrl.contains("/dpc-aio-continuous/", ignoreCase = true)',
    'requestedUrl.contains("/dpc-aio-emergency-enrollment/", ignoreCase = true)',
    '"emergency" -> UpdateChannel.EMERGENCY',
    "channel is not published yet (manifest HTTP 404)",
    "Self Update manifest was not found (HTTP 404)",
    "Self Update APK asset was not found (HTTP 404)",
]:
    assert token in text, f"missing self-update HTTP/channel contract: {token}"

assert "EMERGENCY" in model, "Self Update model must expose EMERGENCY channel"
assert 'throw IOException("HTTP ${connection.responseCode}")' not in text, "raw HTTP-only diagnostic must not return"

for token in [
    "private var selectedChannel = UpdateChannel.CONTINUOUS",
    "listOf(UpdateChannel.CONTINUOUS, UpdateChannel.EMERGENCY)",
    "if (restored == UpdateChannel.STABLE) UpdateChannel.CONTINUOUS else restored",
    "CONTINUOUS_MANIFEST_URL",
    "EMERGENCY_MANIFEST_URL",
    '"https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/self-update.json"',
    '"https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-emergency-enrollment/self-update.json"',
    "UpdateChannel.EMERGENCY -> EMERGENCY_MANIFEST_URL",
    "manifest.channel == selectedChannel",
]:
    assert token in activity, f"Self Update UI/channel migration missing: {token}"

assert "UpdateChannel.entries.forEach" not in activity, "Self Update UI must expose only CONTINUOUS and EMERGENCY"
assert "fetchManifestWithFallback" not in activity, "Self Update channels must stay explicit/fail-closed"

check_body = activity.split("private fun checkNow()", 1)[1].split("private fun downloadAndVerify()", 1)[0]
assert "client.fetchManifest(manifestUrl(channel))" in check_body, "checkNow must fetch only the explicitly selected channel"
assert "selectedChannel =" not in check_body, "checkNow must not silently change the user's selected channel"

render_body = activity.split("private fun render()", 1)[1].split("private fun checkNow()", 1)[0]
assert "planner.evaluate(packageName, installed.second, it.channel, it).accepted" in render_body
assert "planner.evaluate(packageName, installed.second, it.manifest.channel, it.manifest).accepted" in render_body

print("test_self_update_http_diagnostics: PASS")
