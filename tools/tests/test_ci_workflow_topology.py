#!/usr/bin/env python3
"""Guard the intentionally small, unambiguous GitHub Actions surface."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = ROOT / ".github" / "workflows"
EXPECTED_WORKFLOWS = {
    "build-aio-enrollment.yml": "Build AIO + enrollment QR",
    "build-emergency-enrollment.yml": "Emergency enrollment (ephemeral signing)",
}


def workflow_name(path: Path) -> str:
    match = re.search(r"(?m)^name:\s*(.+?)\s*$", path.read_text("utf-8"))
    assert match, f"workflow has no top-level name: {path.relative_to(ROOT)}"
    return match.group(1).strip('"\'')


def main() -> None:
    workflows = {
        path.name: path
        for path in WORKFLOW_DIR.iterdir()
        if path.is_file() and path.suffix in {".yml", ".yaml"}
    }

    assert workflows.keys() == EXPECTED_WORKFLOWS.keys(), (
        "unexpected GitHub Actions workflow topology; keep the documented production "
        f"and emergency entry points only: {sorted(workflows)}"
    )

    names = {filename: workflow_name(path) for filename, path in workflows.items()}
    assert names == EXPECTED_WORKFLOWS, f"workflow names changed unexpectedly: {names}"
    assert len(set(names.values())) == len(names), f"duplicate workflow names: {names}"

    for filename, path in workflows.items():
        workflow = path.read_text("utf-8")
        assert "${{ github.run_id }}-${{ github.run_attempt }}" in workflow, (
            f"artifact names must be unique per run and retry: {filename}"
        )
        assert "GH_TOKEN: ${{ github.token }}" in workflow, (
            f"publish job must use the automatic GitHub Actions token: {filename}"
        )

    print("PASS: workflow topology, automatic token and dynamic artifacts are guarded")


if __name__ == "__main__":
    main()
