#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "tools/runtime/android_device_owner_smoke.py"

spec = importlib.util.spec_from_file_location("android_device_owner_smoke", TARGET)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

# Matches the quoting shape observed from Android 35 `am broadcast` in Actions:
# data="{\\\"apiLevel\\\":35,...,\\\"status\\\":\\\"VERIFIED\\\"}"
real = (
    'Broadcast completed: result=0, data="{\\\\\\"apiLevel\\\\\\":35,'
    '\\\\"dpcVersion\\\\\":\\\\\"1.1.4\\\\\",'
    '\\\\"deviceOwner\\\\\":true,'
    '\\\\"profileOwner\\\\\":false,'
    '\\\\"moduleCounts\\\\\":{\\\\\"integrated\\\\\":41},'
    '\\\\"status\\\\\":\\\\\"VERIFIED\\\\\"}"\n'
)

code, payload = mod.parse_broadcast_result(real)
assert code == 0
assert payload["apiLevel"] == 35
assert payload["dpcVersion"] == "1.1.4"
assert payload["deviceOwner"] is True
assert payload["moduleCounts"]["integrated"] == 41
assert payload["status"] == "VERIFIED"

print("ANDROID_RUNTIME_SMOKE_REAL_ADB_114: PASS")
