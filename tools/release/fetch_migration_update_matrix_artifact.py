#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

WORKFLOW = "migration-update-matrix.yml"
WORKFLOW_NAME = "Migration Update Compatibility Matrix"
ARTIFACT = "migration-update-matrix-report"


def fail(message: str) -> None:
    raise SystemExit(message)


def run_gh(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, text=True, capture_output=True)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=(
            "Read-only verifier/downloader for one migration-update-matrix Actions execution. "
            "It never dispatches workflows or modifies devices/releases."
        )
    )
    p.add_argument("--repo", required=True, help="GitHub repository in owner/name form")
    p.add_argument("--run-id", required=True, type=int, help="Exact GitHub Actions database ID")
    p.add_argument("--output-dir", required=True, type=Path)
    p.add_argument(
        "--no-wait",
        action="store_true",
        help="Do not wait; fail unless the selected execution is already completed successfully",
    )
    p.add_argument("--interval", type=int, default=5, help="Polling interval used by gh run watch")
    return p.parse_args()


def inspect_run(gh: str, repo: str, run_id: int) -> dict:
    cp = run_gh(
        [
            gh,
            "run",
            "view",
            str(run_id),
            "--repo",
            repo,
            "--json",
            "databaseId,workflowName,status,conclusion,event,headBranch,headSha,url",
        ]
    )
    if cp.returncode != 0:
        detail = (cp.stderr or cp.stdout or "gh run view failed").strip()
        fail(f"MIGRATION_UPDATE_MATRIX_RUN_VIEW_FAILED: {detail}")
    try:
        data = json.loads(cp.stdout)
    except Exception as exc:
        fail(f"MIGRATION_UPDATE_MATRIX_RUN_VIEW_INVALID_JSON: {exc}")
    if int(data.get("databaseId") or 0) != run_id:
        fail("MIGRATION_UPDATE_MATRIX_RUN_ID_MISMATCH")
    if data.get("workflowName") not in (WORKFLOW_NAME, WORKFLOW):
        fail(
            "MIGRATION_UPDATE_MATRIX_WORKFLOW_MISMATCH: "
            f"expected={WORKFLOW_NAME} actual={data.get('workflowName')}"
        )
    return data


def main() -> int:
    args = parse_args()
    if args.repo.count("/") != 1 or any(not part for part in args.repo.split("/")):
        fail("MIGRATION_UPDATE_MATRIX_REPO_INVALID")
    if args.run_id <= 0:
        fail("MIGRATION_UPDATE_MATRIX_RUN_ID_INVALID")
    if args.interval < 1:
        fail("MIGRATION_UPDATE_MATRIX_INTERVAL_INVALID")

    gh = shutil.which("gh")
    if not gh:
        fail("MIGRATION_UPDATE_MATRIX_GH_NOT_FOUND")

    data = inspect_run(gh, args.repo, args.run_id)
    if data.get("status") != "completed":
        if args.no_wait:
            fail(f"MIGRATION_UPDATE_MATRIX_RUN_NOT_COMPLETED: {data.get('status')}")
        watch = run_gh(
            [
                gh,
                "run",
                "watch",
                str(args.run_id),
                "--repo",
                args.repo,
                "--exit-status",
                "--interval",
                str(args.interval),
            ]
        )
        if watch.returncode != 0:
            detail = (watch.stderr or watch.stdout or "gh run watch failed").strip()
            fail(f"MIGRATION_UPDATE_MATRIX_RUN_FAILED: {detail}")
        data = inspect_run(gh, args.repo, args.run_id)

    if data.get("status") != "completed" or data.get("conclusion") != "success":
        fail(
            "MIGRATION_UPDATE_MATRIX_RUN_NOT_SUCCESS: "
            f"status={data.get('status')} conclusion={data.get('conclusion')}"
        )

    args.output_dir.mkdir(parents=True, exist_ok=True)
    download = run_gh(
        [
            gh,
            "run",
            "download",
            str(args.run_id),
            "--repo",
            args.repo,
            "--name",
            ARTIFACT,
            "--dir",
            str(args.output_dir),
        ]
    )
    if download.returncode != 0:
        detail = (download.stderr or download.stdout or "gh run download failed").strip()
        fail(f"MIGRATION_UPDATE_MATRIX_ARTIFACT_DOWNLOAD_FAILED: {detail}")

    print("MIGRATION_UPDATE_MATRIX_ARTIFACT_READY: PASS")
    print(f"Workflow: {WORKFLOW}")
    print(f"Run ID: {args.run_id}")
    print(f"Artifact: {ARTIFACT}")
    print(f"Output: {args.output_dir}")
    if data.get("url"):
        print(f"Run URL: {data['url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
