#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path
import shutil
import subprocess
import sys

WORKFLOW = "migration-update-matrix.yml"
DEFAULT_REF = "main"
EXPECTED = (
    ("report_le27_b64", "<=27"),
    ("report_28_32_b64", "28-32"),
    ("report_33_plus_b64", "33+"),
)


def fail(message: str) -> None:
    raise SystemExit(message)


def load_report(path: Path, expected_bucket: str) -> bytes:
    if not path.is_file():
        fail(f"MIGRATION_UPDATE_MATRIX_REPORT_MISSING: {path}")
    raw = path.read_bytes()
    if not raw:
        fail(f"MIGRATION_UPDATE_MATRIX_REPORT_EMPTY: {path}")
    try:
        data = json.loads(raw.decode("utf-8"))
    except Exception as exc:
        fail(f"MIGRATION_UPDATE_MATRIX_REPORT_INVALID_JSON: {path}: {exc}")
    if data.get("schemaVersion") != 1:
        fail(f"MIGRATION_UPDATE_MATRIX_REPORT_SCHEMA_INVALID: {path}")
    if data.get("status") != "MIGRATION_UPDATE_INSTALL_PASS":
        fail(f"MIGRATION_UPDATE_MATRIX_REPORT_NOT_PASS: {path}")
    bucket = (data.get("device") or {}).get("apiBucket")
    if bucket != expected_bucket:
        fail(
            f"MIGRATION_UPDATE_MATRIX_REPORT_BUCKET_MISMATCH: {path}: "
            f"expected={expected_bucket} actual={bucket}"
        )
    return raw


def build_payload(paths: tuple[Path, Path, Path]) -> dict[str, str]:
    payload: dict[str, str] = {}
    for (input_name, bucket), path in zip(EXPECTED, paths):
        raw = load_report(path, bucket)
        payload[input_name] = base64.b64encode(raw).decode("ascii")
    return payload


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Audit or explicitly dispatch the migration update compatibility matrix workflow."
    )
    p.add_argument("--report-le27", required=True, type=Path)
    p.add_argument("--report-28-32", required=True, dest="report_28_32", type=Path)
    p.add_argument("--report-33-plus", required=True, dest="report_33_plus", type=Path)
    p.add_argument("--repo", required=True, help="GitHub repository in owner/name form")
    p.add_argument("--ref", default=DEFAULT_REF)
    p.add_argument("--dispatch", action="store_true", help="Explicitly dispatch the workflow via GitHub CLI")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    if args.repo.count("/") != 1 or any(not part for part in args.repo.split("/")):
        fail("MIGRATION_UPDATE_MATRIX_REPO_INVALID")
    if not args.ref.strip():
        fail("MIGRATION_UPDATE_MATRIX_REF_INVALID")

    paths = (args.report_le27, args.report_28_32, args.report_33_plus)
    payload = build_payload(paths)

    print("MIGRATION_UPDATE_MATRIX_DISPATCH_AUDIT: PASS")
    print(f"Repository: {args.repo}")
    print(f"Workflow: {WORKFLOW}")
    print(f"Ref: {args.ref}")
    print("Validated buckets: <=27, 28-32, 33+")
    print("Prepared workflow inputs: 3")

    if not args.dispatch:
        print("Dispatch: NOT EXECUTED (use --dispatch to opt in)")
        return 0

    gh = shutil.which("gh")
    if not gh:
        fail("MIGRATION_UPDATE_MATRIX_GH_NOT_FOUND")

    command = [
        gh,
        "workflow",
        "run",
        WORKFLOW,
        "--repo",
        args.repo,
        "--ref",
        args.ref,
        "--json",
    ]
    cp = subprocess.run(
        command,
        input=json.dumps(payload, separators=(",", ":")),
        text=True,
        capture_output=True,
    )
    if cp.returncode != 0:
        detail = (cp.stderr or cp.stdout or "gh workflow run failed").strip()
        fail(f"MIGRATION_UPDATE_MATRIX_DISPATCH_FAILED: {detail}")

    print("MIGRATION_UPDATE_MATRIX_DISPATCHED: PASS")
    if cp.stdout.strip():
        print(cp.stdout.strip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
