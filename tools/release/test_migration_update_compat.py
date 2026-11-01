#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import Any


def run(args: list[str], *, check: bool = False) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(args, text=True, capture_output=True)
    if check and cp.returncode != 0:
        msg = (cp.stderr or cp.stdout or "command failed").strip()
        raise SystemExit(f"MIGRATION_ADB_COMMAND_FAILED: {msg}")
    return cp


def api_bucket(api: int) -> str:
    if api <= 27:
        return "<=27"
    if api <= 32:
        return "28-32"
    return "33+"


def adb_prefix(adb: str, serial: str | None) -> list[str]:
    return [adb, "-s", serial] if serial else [adb]


def parse_devices(output: str) -> list[str]:
    devices: list[str] = []
    for line in output.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def parse_package_state(output: str) -> dict[str, Any]:
    data: dict[str, Any] = {
        "present": bool(output.strip()),
        "versionCode": None,
        "versionName": None,
    }
    for line in output.splitlines():
        stripped = line.strip()
        if "versionCode=" in stripped:
            value = stripped.split("versionCode=", 1)[1].split()[0]
            data["versionCode"] = value
        if "versionName=" in stripped:
            data["versionName"] = stripped.split("versionName=", 1)[1].strip()
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="Plan or execute an ADB update compatibility check for a migration-signed APK.")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--execute", action="store_true", help="Actually run adb install -r. Default is dry-run/read-only.")
    args = parser.parse_args()

    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        raise SystemExit(f"MIGRATION_APK_MISSING: {args.apk}")

    serial = args.serial
    if not serial:
        devices = parse_devices(run([args.adb, "devices"], check=True).stdout)
        if len(devices) != 1:
            raise SystemExit(f"MIGRATION_ADB_DEVICE_COUNT_INVALID: {len(devices)}")
        serial = devices[0]

    prefix = adb_prefix(args.adb, serial)
    sdk_cp = run([*prefix, "shell", "getprop", "ro.build.version.sdk"], check=True)
    try:
        api_level = int(sdk_cp.stdout.strip())
    except ValueError as exc:
        raise SystemExit("MIGRATION_ADB_API_INVALID") from exc

    package_cp = run([*prefix, "shell", "dumpsys", "package", args.package])
    path_cp = run([*prefix, "shell", "pm", "path", args.package])
    installed = parse_package_state(package_cp.stdout if package_cp.returncode == 0 else "")
    installed["present"] = bool(path_cp.returncode == 0 and path_cp.stdout.strip())
    installed["pathOutput"] = path_cp.stdout.strip().splitlines() if path_cp.stdout.strip() else []

    install_cmd = [*prefix, "install", "-r", str(args.apk)]
    install_result: dict[str, Any] = {
        "executed": False,
        "command": install_cmd,
        "exitCode": None,
        "stdout": [],
        "stderr": [],
    }

    status = "MIGRATION_UPDATE_PLAN_READY"
    exit_code = 0

    if args.execute:
        cp = run(install_cmd)
        install_result.update(
            {
                "executed": True,
                "exitCode": cp.returncode,
                "stdout": cp.stdout.splitlines(),
                "stderr": cp.stderr.splitlines(),
            }
        )
        if cp.returncode == 0 and "Success" in cp.stdout:
            status = "MIGRATION_UPDATE_INSTALL_PASS"
        else:
            status = "MIGRATION_UPDATE_INSTALL_FAIL"
            exit_code = 1

    report = {
        "schemaVersion": 1,
        "status": status,
        "mode": "execute" if args.execute else "dry-run",
        "device": {
            "serial": serial,
            "apiLevel": api_level,
            "apiBucket": api_bucket(api_level),
        },
        "packageName": args.package,
        "apk": str(args.apk.resolve()),
        "installedPackage": installed,
        "install": install_result,
        "safety": {
            "uninstall": False,
            "clearData": False,
            "reboot": False,
            "executeRequiresExplicitFlag": True,
        },
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(status)
    print(f"serial={serial}")
    print(f"apiLevel={api_level}")
    print(f"apiBucket={api_bucket(api_level)}")
    print(f"report={args.report}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
