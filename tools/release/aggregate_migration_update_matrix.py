#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

REQUIRED_BUCKETS = ["<=27", "28-32", "33+"]
PASS_STATUS = "MIGRATION_UPDATE_INSTALL_PASS"


def load_report(path: Path) -> dict[str, Any]:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"MIGRATION_UPDATE_REPORT_MISSING: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"MIGRATION_UPDATE_REPORT_INVALID: {path}") from exc
    if data.get("schemaVersion") != 1:
        raise SystemExit(f"MIGRATION_UPDATE_REPORT_SCHEMA_INVALID: {path}")
    bucket = data.get("device", {}).get("apiBucket")
    if bucket not in REQUIRED_BUCKETS:
        raise SystemExit(f"MIGRATION_UPDATE_REPORT_BUCKET_INVALID: {path}")
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="Aggregate migration update compatibility reports into one fail-closed matrix.")
    parser.add_argument("--report", nargs="+", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    bucket_results: dict[str, dict[str, Any]] = {}
    duplicate_buckets: list[str] = []

    for path in args.report:
        data = load_report(path)
        bucket = data["device"]["apiBucket"]
        if bucket in bucket_results:
            duplicate_buckets.append(bucket)
            continue
        bucket_results[bucket] = {
            "sourceReport": str(path.resolve()),
            "status": data.get("status"),
            "mode": data.get("mode"),
            "device": data.get("device"),
            "packageName": data.get("packageName"),
            "installedPackage": data.get("installedPackage"),
            "install": data.get("install"),
        }

    missing = [bucket for bucket in REQUIRED_BUCKETS if bucket not in bucket_results]
    failed = [bucket for bucket in REQUIRED_BUCKETS if bucket in bucket_results and bucket_results[bucket].get("status") != PASS_STATUS]
    duplicates = sorted(set(duplicate_buckets), key=REQUIRED_BUCKETS.index)

    passed = not missing and not failed and not duplicates
    status = "MIGRATION_UPDATE_MATRIX_PASS" if passed else "MIGRATION_UPDATE_MATRIX_FAIL"

    result = {
        "schemaVersion": 1,
        "status": status,
        "requiredBuckets": REQUIRED_BUCKETS,
        "missingBuckets": missing,
        "failedBuckets": failed,
        "duplicateBuckets": duplicates,
        "bucketResults": bucket_results,
        "safety": {
            "invokesAdb": False,
            "installsPackages": False,
            "mutatesDevice": False,
        },
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(status)
    if missing:
        print("missingBuckets=" + ",".join(missing))
    if failed:
        print("failedBuckets=" + ",".join(failed))
    if duplicates:
        print("duplicateBuckets=" + ",".join(duplicates))
    print(f"output={args.output}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
