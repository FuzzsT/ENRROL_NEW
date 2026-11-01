#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WF = ROOT / ".github" / "workflows" / "migration-update-matrix.yml"


def need(text: str, markers: list[str]) -> None:
    for marker in markers:
        assert marker in text, f"missing marker: {marker}"


def main() -> None:
    assert WF.is_file(), "missing migration update matrix workflow"
    text = WF.read_text(encoding="utf-8")

    need(text, [
        "workflow_dispatch:",
        "report_le27_b64:",
        "report_28_32_b64:",
        "report_33_plus_b64:",
        "permissions:",
        "contents: read",
        "aggregate_migration_update_matrix.py",
        "MIGRATION_UPDATE_MATRIX_PASS",
        "actions/upload-artifact@",
        "migration-update-matrix-report",
    ])

    lowered = text.lower()
    forbidden = [
        "adb install",
        "install -r",
        "gh release",
        "softprops/action-gh-release",
        "create-release",
        "workflow_run:",
        "schedule:",
    ]
    for marker in forbidden:
        assert marker not in lowered, f"forbidden workflow behavior: {marker}"

    print("MIGRATION_UPDATE_MATRIX_WORKFLOW_CONTRACT: PASS")


if __name__ == "__main__":
    main()
