#!/usr/bin/env python3
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[2]
UPSTREAM = ROOT / "_upstream/google/extracted/android-testdpc-master/src/main/res/xml"
PINNED = ROOT / "tools/parity/testdpc-direct-keys-169.json"


def keys_from(path: Path) -> list[str]:
    root = ET.parse(path).getroot()
    keys: list[str] = []
    for element in root.iter():
        key = element.attrib.get(ANDROID + "key")
        if key:
            keys.append(key)
    return keys


def pinned_keys() -> list[str]:
    payload = json.loads(PINNED.read_text("utf-8"))
    keys = payload.get("keys", [])
    if payload.get("count") != 169 or len(keys) != 169 or len(set(keys)) != 169:
        raise RuntimeError("INVALID_PINNED_TESTDPC_DENOMINATOR")
    return keys


def direct_keys() -> list[str]:
    device = UPSTREAM / "device_policy_header.xml"
    profile = UPSTREAM / "profile_policy_header.xml"
    if device.is_file() and profile.is_file():
        keys = keys_from(device) + keys_from(profile)
        if len(keys) != 169 or len(set(keys)) != 169:
            raise RuntimeError(f"INVALID_UPSTREAM_TESTDPC_DENOMINATOR:{len(keys)}")
        return keys
    return pinned_keys()


if __name__ == "__main__":
    keys = direct_keys()
    if len(keys) != 169:
        raise SystemExit(f"EXPECTED_169_KEYS_GOT_{len(keys)}")
    if len(set(keys)) != 169:
        raise SystemExit("DUPLICATE_TESTDPC_KEYS")
    print("\n".join(keys))
