#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "apps/dpc/modules/app-management/android/build.gradle.kts"


def main() -> int:
    text = BUILD.read_text(encoding="utf-8")
    required = [
        'implementation(project(":app-manager"))',
        'implementation(project(":enterprise-protection"))',
        'implementation(project(":policy-core"))',
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f"app-android classpath is incomplete: {missing}"
    print("APP_ANDROID_CLASSPATH_114_CONTRACT: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
