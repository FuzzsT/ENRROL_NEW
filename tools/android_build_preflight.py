#!/usr/bin/env python3
"""DPC-AIO Android build environment preflight.

Reports stable BUILD_READY / BUILD_BLOCKED state without downloading anything.
It verifies the exact toolchain encoded by the repository before Gradle is run.
"""
from __future__ import annotations
import argparse, json, os, re, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def first_existing(*paths: Path) -> Path | None:
    return next((p for p in paths if p and p.exists()), None)


def java_major() -> int | None:
    java = shutil.which("java")
    if not java:
        return None
    cp = subprocess.run([java, "-version"], text=True, capture_output=True)
    text = (cp.stderr or cp.stdout).splitlines()[0] if (cp.stderr or cp.stdout) else ""
    m = re.search(r'"(\d+)(?:\.|\")', text)
    return int(m.group(1)) if m else None


def catalog_value(name: str) -> str | None:
    text = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    m = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', text, re.M)
    return m.group(1) if m else None


def wrapper_version() -> str | None:
    p = ROOT / "gradle/wrapper/gradle-wrapper.properties"
    text = p.read_text(encoding="utf-8") if p.exists() else ""
    m = re.search(r'gradle-([0-9][0-9.]+)-bin\.zip', text)
    return m.group(1) if m else None


def latest_version_dir(parent: Path) -> Path | None:
    if not parent.is_dir():
        return None
    dirs = [p for p in parent.iterdir() if p.is_dir()]
    def key(p: Path):
        return tuple(int(x) if x.isdigit() else x for x in re.split(r'[.-]', p.name))
    try:
        return sorted(dirs, key=key)[-1] if dirs else None
    except TypeError:
        return sorted(dirs, key=lambda p: p.name)[-1] if dirs else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--require-signing", action="store_true")
    ap.add_argument("--json-out")
    ap.add_argument("--allow-blocked", action="store_true", help="emit diagnostics but return 0")
    args = ap.parse_args()

    compile_sdk = catalog_value("compileSdk")
    ndk_ver = catalog_value("ndk")
    cmake_ver = catalog_value("cmake")
    gradle_ver = wrapper_version()
    sdk_env = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    sdk = Path(sdk_env).expanduser() if sdk_env else None

    platform = sdk / "platforms" / f"android-{compile_sdk}" / "android.jar" if sdk and compile_sdk else None
    build_tools_dir = latest_version_dir(sdk / "build-tools") if sdk else None
    apksigner = build_tools_dir / ("apksigner.bat" if os.name == "nt" else "apksigner") if build_tools_dir else None
    ndk = sdk / "ndk" / str(ndk_ver) if sdk and ndk_ver else None
    cmake = sdk / "cmake" / str(cmake_ver) if sdk and cmake_ver else None

    signing_names = [
        "DPC_AIO_RELEASE_KEYSTORE_PATH",
        "DPC_AIO_RELEASE_STORE_PASSWORD",
        "DPC_AIO_RELEASE_KEY_ALIAS",
        "DPC_AIO_RELEASE_KEY_PASSWORD",
        "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
    ]
    signing_missing = [n for n in signing_names if not os.environ.get(n)] if args.require_signing else []

    checks = {
        "java21": java_major() == 21,
        "gradleWrapper": gradle_ver == "9.7.0",
        "androidSdk": bool(sdk and sdk.is_dir()),
        "compileSdk": bool(platform and platform.is_file()),
        "buildTools": bool(apksigner and apksigner.is_file()),
        "ndk": bool(ndk and ndk.is_dir()),
        "cmake": bool(cmake and cmake.is_dir()),
        "signing": not signing_missing,
    }
    blockers = [name for name, ok in checks.items() if not ok]
    state = "BUILD_READY" if not blockers else "BUILD_BLOCKED"
    report = {
        "schemaVersion": 1,
        "state": state,
        "checks": checks,
        "blockers": blockers,
        "expected": {
            "javaMajor": 21,
            "gradleWrapper": "9.7.0",
            "compileSdk": compile_sdk,
            "ndk": ndk_ver,
            "cmake": cmake_ver,
        },
        "observed": {
            "javaMajor": java_major(),
            "gradleWrapper": gradle_ver,
            "androidSdk": str(sdk) if sdk else None,
            "buildTools": build_tools_dir.name if build_tools_dir else None,
            "signingMissing": signing_missing,
        },
    }
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    if state == "BUILD_READY" or args.allow_blocked:
        return 0
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
