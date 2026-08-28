#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[2]
UPSTREAM = ROOT / "_upstream/google/extracted/android-testdpc-master/src/main/res/xml"


def keys_from(path: Path) -> list[str]:
    root = ET.parse(path).getroot()
    keys: list[str] = []
    for element in root.iter():
        key = element.attrib.get(ANDROID + "key")
        if key:
            keys.append(key)
    return keys


def direct_keys() -> list[str]:
    return keys_from(UPSTREAM / "device_policy_header.xml") + keys_from(UPSTREAM / "profile_policy_header.xml")


if __name__ == "__main__":
    keys = direct_keys()
    if len(keys) != 169:
        raise SystemExit(f"EXPECTED_169_KEYS_GOT_{len(keys)}")
    if len(set(keys)) != 169:
        raise SystemExit("DUPLICATE_TESTDPC_KEYS")
    print("\n".join(keys))
