#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
aio = (ROOT / ".github/workflows/build-aio-enrollment.yml").read_text("utf-8")
emergency = (ROOT / ".github/workflows/build-emergency-enrollment.yml").read_text("utf-8")
gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text("utf-8")

for name, text in (("aio", aio), ("emergency", emergency)):
    assert "Verify signing keystore file and path before build" in text, name
    assert "Resolve and verify built APK path" in text, name
    assert 'realpath -e "$raw"' in text, name
    assert 'test -s "$raw"' in text, name
    assert 'DPC_AIO_BUILT_APK_PATH=$apk' in text, name
    assert ': "${DPC_AIO_BUILT_APK_PATH:?DPC_AIO_BUILT_APK_PATH is required}"' in text, name
    assert "find apps/dpc/app/build/outputs/apk/enterprise/release -type f -name '*.apk' | head -n1" not in text, name

assert "require(keystoreFile.isFile && keystoreFile.length() > 0L)" in gradle
print("PASS: signing keystore and built APK paths are verified before use")
