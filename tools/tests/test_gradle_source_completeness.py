#!/usr/bin/env python3
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "tools" / "gradle_source_audit.py"


def main() -> int:
    assert AUDIT.is_file(), f"missing audit tool: {AUDIT}"
    result = subprocess.run(
        [sys.executable, str(AUDIT), "--root", str(ROOT), "--json"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert result.returncode == 0, result.stderr or result.stdout
    data = json.loads(result.stdout)
    assert data["state"] == "SOURCE_COMPLETE", data
    assert data["blockers"] == [], data["blockers"]
    assert data["duplicateIncludes"] == [], data["duplicateIncludes"]
    assert data["moduleCount"] >= 35, data["moduleCount"]
    assert all(m["hasBuildFile"] for m in data["modules"])
    assert all(m["hasMainSource"] for m in data["modules"])
    # Kotlin-only JVM modules legitimately have compileJava NO-SOURCE.
    kotlin_only = [m for m in data["modules"] if m["classification"] == "kotlin-only"]
    assert kotlin_only, "expected Kotlin-only modules"
    assert all("compileJava" in m["expectedNoSourceTasks"] for m in kotlin_only)
    print("GRADLE_SOURCE_COMPLETENESS: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
