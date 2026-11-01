from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt"

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

print("test_self_update_http_diagnostics: PASS")
