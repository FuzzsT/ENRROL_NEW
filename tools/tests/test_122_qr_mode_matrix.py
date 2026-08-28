#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text("utf-8")
main = (ROOT / ".github/workflows/build-aio-enrollment.yml").read_text("utf-8")
bundle = (ROOT / "tools/release/build_qr_release_bundle.py").read_text("utf-8")

for mode in ("both", "work-profile", "fully-managed"):
    assert mode in gradle
    assert mode in main
    assert mode in bundle
assert 'selectedQrModes' in gradle
assert '--expected-mode work-profile' in main
assert '--expected-mode fully-managed' in main
assert '"qrType"' in bundle
print("PASS: three-mode QR selection matrix")
