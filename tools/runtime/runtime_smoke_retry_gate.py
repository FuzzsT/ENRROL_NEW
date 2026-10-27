#!/usr/bin/env python3
"""Classify the one Device Owner runtime-smoke failure eligible for a fresh-AVD retry.

This gate is intentionally read-only. It never changes Android account state,
setup flags, Device Owner state, or the DPC. A retry is allowed only when the
bounded runtime evidence shows the known fresh-emulator condition where Android
rejects set-device-owner because accounts already exist on the device.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

RETRYABLE_ACCOUNT_MESSAGE = "already some accounts on the device"
EXPECTED_READBACK_ERROR = "Device Owner readback is not true"


def is_retryable_preexisting_accounts(report: dict[str, Any]) -> bool:
    if report.get("state") != "FAIL":
        return False

    owner = report.get("setDeviceOwner")
    if not isinstance(owner, dict):
        return False

    try:
        exit_code = int(owner.get("exitCode"))
    except (TypeError, ValueError):
        return False
    if exit_code == 0:
        return False

    output = str(owner.get("output") or "").lower()
    if RETRYABLE_ACCOUNT_MESSAGE not in output:
        return False

    error = str(report.get("error") or "")
    if EXPECTED_READBACK_ERROR not in error:
        return False

    return True


def load_report(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("runtime smoke report is not a JSON object")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True)
    args = parser.parse_args()

    path = Path(args.report)
    try:
        report = load_report(path)
    except Exception as exc:
        print(f"RUNTIME_SMOKE_RETRY=NOT_ALLOWED reason=invalid_report error={exc}", file=sys.stderr)
        return 2

    if is_retryable_preexisting_accounts(report):
        print("RUNTIME_SMOKE_RETRY=PREEXISTING_ACCOUNTS")
        return 0

    print("RUNTIME_SMOKE_RETRY=NOT_ALLOWED", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
