#!/usr/bin/env python3
"""CI runtime smoke for the DPC-AIO enterprise release on a fresh AOSP emulator.

The harness installs the already-built APK, asks Android's shell DevicePolicyManager
CLI to set the DPC as Device Owner, launches the dashboard, then reads a shell-only
DUMP-protected diagnostics broadcast from the app. It writes a bounded JSON evidence
file and never fabricates runtime PASS from source/static checks.
"""
from __future__ import annotations
import argparse
import json
import re
import subprocess
import time
from pathlib import Path
from typing import Any

PACKAGE = "io.dpcaio.app"
ADMIN = f"{PACKAGE}/.AioDeviceAdminReceiver"
DASHBOARD = f"{PACKAGE}/.AioDashboardActivity"
VERIFY_RECEIVER = f"{PACKAGE}/.VerificationCommandReceiver"
VERIFY_ACTION = "io.dpcaio.action.VERIFY_DIAGNOSTICS"
DEFAULT_OUT = "dist/android-runtime-smoke.json"


def run(cmd: list[str], *, check: bool = True, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout)
    if check and cp.returncode != 0:
        raise RuntimeError(
            f"command failed ({cp.returncode}): {' '.join(cmd)}\n"
            f"stdout={cp.stdout.strip()}\nstderr={cp.stderr.strip()}"
        )
    return cp


def adb(*args: str, check: bool = True, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return run(["adb", *args], check=check, timeout=timeout)


def wait_for_boot(timeout_seconds: int = 240) -> None:
    adb("wait-for-device", timeout=timeout_seconds)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        cp = adb("shell", "getprop", "sys.boot_completed", check=False, timeout=30)
        if cp.returncode == 0 and cp.stdout.strip() == "1":
            return
        time.sleep(2)
    raise RuntimeError("Android emulator did not reach sys.boot_completed=1")


def parse_broadcast_result(text: str) -> tuple[int, dict[str, Any]]:
    lines = [line.strip() for line in text.splitlines() if "Broadcast completed:" in line]
    if not lines:
        raise ValueError(f"broadcast completion line missing: {text!r}")
    line = lines[-1]
    m = re.search(r"Broadcast completed:\s*result=(-?\d+)(?:,\s*data=(.*))?$", line)
    if not m:
        raise ValueError(f"cannot parse broadcast completion: {line!r}")
    code = int(m.group(1))
    raw = (m.group(2) or "").strip()
    if not raw:
        raise ValueError("diagnostics broadcast returned no result data")

    # ActivityManager output is not stable across platform/tool versions. Some
    # variants print JSON result-data as a JSON-escaped quoted string, while AOSP
    # ActivityManagerShellCommand can wrap the raw result string in display quotes
    # without escaping quotes already present inside it. Normalize optional extras,
    # then accept both representations before decoding the actual JSON object.
    candidate = raw
    if candidate.startswith('"'):
        extras_marker = '", extras: '
        if extras_marker in candidate:
            candidate = candidate.rsplit(extras_marker, 1)[0] + '"'
        if not candidate.endswith('"'):
            raise ValueError(f"unterminated quoted broadcast result data: {candidate!r}")
    else:
        candidate = candidate.split(", extras: ", 1)[0].strip()

    payload: Any
    try:
        decoded = json.loads(candidate)
    except json.JSONDecodeError:
        # Raw AOSP display form: data="{"status":"VERIFIED",...}"
        payload = candidate[1:-1] if candidate.startswith('"') and candidate.endswith('"') else candidate
    else:
        # Escaped wrapper form: data="{\"status\":\"VERIFIED\",...}"
        payload = decoded

    if isinstance(payload, dict):
        data = payload
    elif isinstance(payload, str):
        data = json.loads(payload)
    else:
        raise ValueError("diagnostics result data is neither JSON object nor JSON string")
    if not isinstance(data, dict):
        raise ValueError("diagnostics payload is not a JSON object")
    return code, data


def validate_diagnostics(data: dict[str, Any], expected_version: str) -> None:
    if data.get("status") != "VERIFIED":
        raise ValueError(f"diagnostics status is not VERIFIED: {data.get('status')!r}")
    if data.get("dpcVersion") != expected_version:
        raise ValueError(
            f"DPC version mismatch: expected {expected_version}, observed {data.get('dpcVersion')!r}"
        )
    if data.get("deviceOwner") is not True:
        raise ValueError("Device Owner readback is not true")
    if data.get("profileOwner") is True:
        raise ValueError("fresh Device Owner smoke unexpectedly reports Profile Owner")
    module_counts = data.get("moduleCounts")
    if not isinstance(module_counts, dict) or int(module_counts.get("integrated") or 0) <= 0:
        raise ValueError("module inventory readback is missing/empty")


def sanitize_command_output(text: str, limit: int = 1200) -> str:
    return text.strip().replace("\x00", "")[:limit]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--expected-version", required=True)
    ap.add_argument("--boot-timeout", type=int, default=240)
    args = ap.parse_args()

    apk = Path(args.apk).resolve()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    report: dict[str, Any] = {
        "schemaVersion": 1,
        "state": "FAIL",
        "apk": apk.name,
        "expectedVersion": args.expected_version,
        "package": PACKAGE,
        "deviceAdmin": ADMIN,
    }

    try:
        if not apk.is_file():
            raise FileNotFoundError(apk)
        wait_for_boot(args.boot_timeout)

        install = adb("install", "-r", str(apk), timeout=180)
        report["install"] = sanitize_command_output(install.stdout or install.stderr)

        owner = adb("shell", "dpm", "set-device-owner", ADMIN, check=False, timeout=90)
        report["setDeviceOwner"] = {
            "exitCode": owner.returncode,
            "output": sanitize_command_output((owner.stdout or "") + "\n" + (owner.stderr or "")),
        }

        launch = adb("shell", "am", "start", "-W", "-n", DASHBOARD, check=False, timeout=60)
        report["dashboardLaunch"] = {
            "exitCode": launch.returncode,
            "output": sanitize_command_output((launch.stdout or "") + "\n" + (launch.stderr or "")),
        }

        broadcast = adb(
            "shell", "am", "broadcast", "--receiver-foreground",
            "-a", VERIFY_ACTION, "-n", VERIFY_RECEIVER,
            timeout=90,
        )
        result_code, diagnostics = parse_broadcast_result(broadcast.stdout)
        report["broadcastResultCode"] = result_code
        report["diagnostics"] = diagnostics
        validate_diagnostics(diagnostics, args.expected_version)

        # Independent shell-side evidence that the installed package is resolvable.
        package_path = adb("shell", "pm", "path", PACKAGE, timeout=30)
        if "package:" not in package_path.stdout:
            raise RuntimeError("pm path did not resolve the DPC package")
        report["packageResolved"] = True
        report["state"] = "PASS"
    except Exception as exc:  # evidence is still written on failure
        report["error"] = f"{type(exc).__name__}: {exc}"
        out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2, sort_keys=True))
        return 2

    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
