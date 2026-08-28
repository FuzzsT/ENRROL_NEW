#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROVIDER = ROOT / "apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/parity/AndroidParityRuntimeFactsProvider.kt"


def main() -> None:
    if not PROVIDER.is_file():
        raise AssertionError("AndroidParityRuntimeFactsProvider.kt is missing")
    text = PROVIDER.read_text("utf-8")
    required = [
        "class AndroidParityRuntimeFactsProvider",
        "Build.VERSION.SDK_INT",
        "isDeviceOwnerApp(packageName)",
        "isProfileOwnerApp(packageName)",
        "PackageManager.FEATURE_WIFI",
        "PackageManager.FEATURE_TELEPHONY",
        "PackageManager.FEATURE_TELEPHONY_EUICC",
        "PackageManager.FEATURE_CAMERA_ANY",
        "PackageManager.FEATURE_NFC",
        "PackageManager.FEATURE_MANAGED_USERS",
        "getDelegatedScopes",
        "ParityRuntimeFacts(",
        "PlatformFeature.WIFI",
        "PlatformFeature.TELEPHONY",
        "PlatformFeature.EUICC",
        "PlatformFeature.CAMERA",
        "PlatformFeature.NFC",
        "PlatformFeature.MANAGED_USERS",
    ]
    missing = [token for token in required if token not in text]
    if missing:
        raise AssertionError("missing runtime-facts tokens: " + ", ".join(missing))
    forbidden = ["java.lang.reflect", "getDeclaredMethod(", "setAccessible(true)"]
    present_forbidden = [token for token in forbidden if token in text]
    if present_forbidden:
        raise AssertionError("runtime facts provider must not use reflection/private APIs: " + ", ".join(present_forbidden))
    print("ANDROID_PARITY_RUNTIME_FACTS_126: PASS")


if __name__ == "__main__":
    main()
