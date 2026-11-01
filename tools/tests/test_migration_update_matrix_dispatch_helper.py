#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "release" / "dispatch_migration_update_matrix.py"


def write_report(path: Path, bucket: str) -> None:
    data = {
        "schemaVersion": 1,
        "status": "MIGRATION_UPDATE_INSTALL_PASS",
        "mode": "execute",
        "device": {"serial": f"device-{bucket}", "apiLevel": 33 if bucket == "33+" else (30 if bucket == "28-32" else 27), "apiBucket": bucket},
        "packageName": "io.dpcaio.app",
        "installedPackage": {"present": True, "versionCode": "1", "versionName": "1.0"},
        "install": {"executed": True, "exitCode": 0, "stdout": ["Success"], "stderr": []},
    }
    path.write_text(json.dumps(data), encoding="utf-8")


def main() -> None:
    assert TOOL.is_file(), "missing migration update matrix dispatch helper"

    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        reports = []
        for name, bucket in (("le27.json", "<=27"), ("28-32.json", "28-32"), ("33-plus.json", "33+")):
            p = tmp / name
            write_report(p, bucket)
            reports.append(p)

        log = tmp / "gh-argv.json"
        fake_gh = tmp / "gh"
        fake_gh.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, sys\n"
            "open(os.environ['FAKE_GH_LOG'], 'w', encoding='utf-8').write(json.dumps(sys.argv[1:]))\n"
            "payload = sys.stdin.read()\n"
            "open(os.environ['FAKE_GH_STDIN'], 'w', encoding='utf-8').write(payload)\n"
            "print('https://github.com/example/run/1')\n",
            encoding="utf-8",
        )
        fake_gh.chmod(0o755)
        stdin_log = tmp / "gh-stdin.json"
        env = os.environ.copy()
        env["PATH"] = f"{tmp}{os.pathsep}{env.get('PATH', '')}"
        env["FAKE_GH_LOG"] = str(log)
        env["FAKE_GH_STDIN"] = str(stdin_log)

        base = [sys.executable, str(TOOL), "--report-le27", str(reports[0]), "--report-28-32", str(reports[1]), "--report-33-plus", str(reports[2]), "--repo", "FuzzsT/ENRROL_NEW"]
        dry = subprocess.run(base, text=True, capture_output=True, env=env)
        assert dry.returncode == 0, dry.stderr or dry.stdout
        assert "MIGRATION_UPDATE_MATRIX_DISPATCH_AUDIT: PASS" in dry.stdout
        assert not log.exists(), "dry-run must not invoke gh"

        dispatch = subprocess.run(base + ["--dispatch"], text=True, capture_output=True, env=env)
        assert dispatch.returncode == 0, dispatch.stderr or dispatch.stdout
        assert "MIGRATION_UPDATE_MATRIX_DISPATCHED: PASS" in dispatch.stdout
        argv = json.loads(log.read_text(encoding="utf-8"))
        assert argv == ["workflow", "run", "migration-update-matrix.yml", "--repo", "FuzzsT/ENRROL_NEW", "--ref", "main", "--json"], argv
        payload = json.loads(stdin_log.read_text(encoding="utf-8"))
        assert set(payload) == {"report_le27_b64", "report_28_32_b64", "report_33_plus_b64"}
        assert all(isinstance(v, str) and v for v in payload.values())
        assert all(v not in " ".join(argv) for v in payload.values()), "report payload must not be exposed in argv"

    print("MIGRATION_UPDATE_MATRIX_DISPATCH_HELPER: PASS")


if __name__ == "__main__":
    main()
