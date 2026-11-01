#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "release" / "aggregate_migration_update_matrix.py"


def write_report(path: Path, bucket: str, status: str = "MIGRATION_UPDATE_INSTALL_PASS") -> None:
    data = {
        "schemaVersion": 1,
        "status": status,
        "mode": "execute",
        "device": {"serial": f"device-{bucket}", "apiLevel": 33 if bucket == "33+" else (30 if bucket == "28-32" else 27), "apiBucket": bucket},
        "packageName": "io.dpcaio.app",
        "installedPackage": {"present": True, "versionCode": "1", "versionName": "1.0"},
        "install": {"executed": True, "exitCode": 0 if status.endswith("PASS") else 1, "stdout": ["Success"] if status.endswith("PASS") else [], "stderr": []},
    }
    path.write_text(json.dumps(data), encoding="utf-8")


def main() -> None:
    assert TOOL.is_file(), "missing migration update matrix aggregator"

    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        reports = []
        for bucket in ("<=27", "28-32", "33+"):
            p = tmp / f"{bucket.replace('+', 'plus').replace('<', 'lt').replace('=', 'eq')}.json"
            write_report(p, bucket)
            reports.append(p)

        out = tmp / "matrix.json"
        cp = subprocess.run(
            [sys.executable, str(TOOL), "--report", *(str(p) for p in reports), "--output", str(out)],
            text=True,
            capture_output=True,
        )
        assert cp.returncode == 0, cp.stderr or cp.stdout
        data = json.loads(out.read_text(encoding="utf-8"))
        assert data["status"] == "MIGRATION_UPDATE_MATRIX_PASS"
        assert data["requiredBuckets"] == ["<=27", "28-32", "33+"]
        assert data["missingBuckets"] == []
        assert data["failedBuckets"] == []
        assert set(data["bucketResults"]) == {"<=27", "28-32", "33+"}
        assert "adb" not in TOOL.read_text(encoding="utf-8")
        assert "install -r" not in TOOL.read_text(encoding="utf-8")

        incomplete = tmp / "incomplete.json"
        cp2 = subprocess.run(
            [sys.executable, str(TOOL), "--report", str(reports[0]), str(reports[1]), "--output", str(incomplete)],
            text=True,
            capture_output=True,
        )
        assert cp2.returncode != 0
        data2 = json.loads(incomplete.read_text(encoding="utf-8"))
        assert data2["status"] == "MIGRATION_UPDATE_MATRIX_FAIL"
        assert data2["missingBuckets"] == ["33+"]

    print("MIGRATION_UPDATE_MATRIX_AGGREGATE: PASS")


if __name__ == "__main__":
    main()
