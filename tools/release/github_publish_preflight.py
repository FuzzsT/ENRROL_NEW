#!/usr/bin/env python3
"""DPC-AIO GitHub publish preflight.

Checks repository/upload prerequisites without reading or printing secret values.
Exit code 0 means READY. Exit code 2 means BLOCKED.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

REQUIRED_SECRETS = (
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
)
CERT_NAME = "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256"
WORKFLOW = ".github/workflows/build-aio-enrollment.yml"

# Human-readable command names are intentionally kept here for audit tooling:
# gh auth status
# gh secret list
# gh variable list


def run(args: list[str], *, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=False)


def names_from_listing(text: str) -> set[str]:
    names: set[str] = set()
    stripped = text.strip()
    if not stripped:
        return names
    try:
        parsed = json.loads(stripped)
    except json.JSONDecodeError:
        parsed = None
    if isinstance(parsed, list):
        for item in parsed:
            if isinstance(item, dict) and isinstance(item.get("name"), str):
                names.add(item["name"].strip())
            elif isinstance(item, str):
                names.add(item.strip())
        return {n for n in names if n}
    for line in stripped.splitlines():
        value = line.strip().split()[0] if line.strip() else ""
        if value:
            names.add(value)
    return names


def normalize_remote(url: str) -> str:
    value = url.strip()
    value = re.sub(r"\.git$", "", value)
    value = re.sub(r"^git@github\.com:", "https://github.com/", value)
    return value.rstrip("/")


def add(checks: list[dict[str, object]], name: str, ok: bool, detail: str) -> None:
    checks.append({"name": name, "status": "PASS" if ok else "BLOCKED", "detail": detail})


def main() -> int:
    parser = argparse.ArgumentParser(description="Check whether DPC-AIO is ready to be published to GitHub.")
    parser.add_argument("--repo", required=True, help="GitHub repository in OWNER/REPO form")
    parser.add_argument("--root", default=".", help="Repository root (default: current directory)")
    parser.add_argument("--json-out", help="Write machine-readable status to this path")
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    checks: list[dict[str, object]] = []
    repo_ok = bool(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo))
    add(checks, "repo_name", repo_ok, args.repo if repo_ok else "OWNER/REPO required")

    add(checks, "root_exists", root.is_dir(), str(root))
    git_bin = shutil.which("git")
    gh_bin = shutil.which("gh")
    add(checks, "git_cli", bool(git_bin), "git available" if git_bin else "git not found in PATH")
    add(checks, "gh_cli", bool(gh_bin), "gh available" if gh_bin else "GitHub CLI (gh) not found in PATH")

    if root.is_dir() and git_bin:
        cp = run([git_bin, "rev-parse", "--is-inside-work-tree"], cwd=root)
        inside = cp.returncode == 0 and cp.stdout.strip().lower() == "true"
        add(checks, "git_worktree", inside, "Git work tree detected" if inside else "not a Git work tree")
        if inside:
            status = run([git_bin, "status", "--porcelain"], cwd=root)
            clean = status.returncode == 0 and not status.stdout.strip()
            add(checks, "git_clean", clean, "working tree clean" if clean else "commit/stash local changes before release")

            branch = run([git_bin, "branch", "--show-current"], cwd=root)
            branch_name = branch.stdout.strip() if branch.returncode == 0 else ""
            add(checks, "git_branch", bool(branch_name), branch_name or "detached HEAD / branch unresolved")

            remote = run([git_bin, "remote", "get-url", "origin"], cwd=root)
            remote_url = remote.stdout.strip() if remote.returncode == 0 else ""
            expected = f"https://github.com/{args.repo}"
            remote_ok = bool(remote_url) and normalize_remote(remote_url) == normalize_remote(expected)
            add(
                checks,
                "origin_remote",
                remote_ok,
                remote_url if remote_url else f"origin missing; expected {expected}.git",
            )

            workflow = root / WORKFLOW
            add(checks, "release_workflow", workflow.is_file(), WORKFLOW)

    if root.is_dir() and gh_bin and repo_ok:
        auth = run([gh_bin, "auth", "status"], cwd=root)
        auth_ok = auth.returncode == 0
        add(checks, "gh_auth", auth_ok, "authenticated" if auth_ok else "run: gh auth login")

        if auth_ok:
            view = run([gh_bin, "repo", "view", args.repo], cwd=root)
            repo_exists = view.returncode == 0
            add(checks, "github_repo", repo_exists, args.repo if repo_exists else "repository not accessible")

            if repo_exists:
                secrets_cp = run(
                    [gh_bin, "secret", "list", "--repo", args.repo, "--json", "name", "--jq", ".[ ].name".replace(" ", "")],
                    cwd=root,
                )
                secret_names = names_from_listing(secrets_cp.stdout) if secrets_cp.returncode == 0 else set()
                missing_secrets = [name for name in REQUIRED_SECRETS if name not in secret_names]
                add(
                    checks,
                    "release_signing_secrets",
                    secrets_cp.returncode == 0 and not missing_secrets,
                    "all required secret names configured" if not missing_secrets else "missing: " + ", ".join(missing_secrets),
                )

                vars_cp = run(
                    [gh_bin, "variable", "list", "--repo", args.repo, "--json", "name", "--jq", ".[ ].name".replace(" ", "")],
                    cwd=root,
                )
                variable_names = names_from_listing(vars_cp.stdout) if vars_cp.returncode == 0 else set()
                cert_present = CERT_NAME in variable_names or CERT_NAME in secret_names
                add(
                    checks,
                    "expected_signing_cert",
                    cert_present,
                    f"{CERT_NAME} configured as variable/secret" if cert_present else f"missing {CERT_NAME}",
                )

    blocked = [c for c in checks if c["status"] != "PASS"]
    result = {
        "schemaVersion": 1,
        "tool": "DPC-AIO GitHub Publish Preflight",
        "repository": args.repo,
        "root": str(root),
        "status": "READY" if not blocked else "BLOCKED",
        "checks": checks,
        "requiredSecretNames": list(REQUIRED_SECRETS),
        "requiredVariableOrSecretNames": [CERT_NAME],
        "note": "Secret values are never requested, stored, or printed by this preflight.",
    }

    rendered = json.dumps(result, indent=2, sort_keys=True)
    if args.json_out:
        out = Path(args.json_out).expanduser()
        if not out.is_absolute():
            out = root / out
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered + "\n", "utf-8")
    print(rendered)
    return 0 if result["status"] == "READY" else 2


if __name__ == "__main__":
    raise SystemExit(main())
