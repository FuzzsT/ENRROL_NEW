#!/usr/bin/env python3
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HARNESS = ROOT / "tools/runtime/android_device_owner_smoke.py"

spec = spec_from_file_location("android_device_owner_smoke", HARNESS)
assert spec and spec.loader
module = module_from_spec(spec)
spec.loader.exec_module(module)

# Android's `am broadcast` wraps result data in quotes but does not JSON-escape
# embedded quotes. The parser must strip the shell wrapper, then parse the JSON.
sample = (
    "Broadcasting: Intent { act=io.dpcaio.action.VERIFY_DIAGNOSTICS }\n"
    'Broadcast completed: result=0, data="{"status":"VERIFIED","deviceOwner":true,"profileOwner":false}"\n'
)
code, data = module.parse_broadcast_result(sample)
assert code == 0, code
assert data["status"] == "VERIFIED", data
assert data["deviceOwner"] is True, data
assert data["profileOwner"] is False, data

for rel in (
    ".github/workflows/build-aio-enrollment.yml",
    ".github/workflows/build-emergency-enrollment.yml",
):
    text = (ROOT / rel).read_text("utf-8")
    marker = "- name: AOSP Device Owner runtime smoke"
    start = text.find(marker)
    assert start >= 0, f"{rel}: missing AOSP runtime smoke step"
    end = text.find("\n      - name:", start + len(marker))
    block = text[start : end if end >= 0 else len(text)]
    assert "target: default" in block, f"{rel}: AOSP smoke must use target: default"
    assert "target: google_apis" not in block, f"{rel}: google_apis image violates fresh AOSP smoke precondition"

print("ANDROID_RUNTIME_SMOKE_HARDENING_128: PASS")
