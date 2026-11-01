#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "release" / "test_migration_update_compat.py"


def write_fake_adb(path: Path, log: Path) -> None:
    path.write_text(
        "#!/bin/sh\n"
        f"echo \"$@\" >> '{log}'\n"
        "case \"$*\" in\n"
        "  *\"devices\"*) printf 'List of devices attached\\nSER123\\tdevice\\n' ;;\n"
        "  *\"getprop ro.build.version.sdk\"*) printf '33\\n' ;;\n"
        "  *\"dumpsys package io.dpcaio.app\"*) printf 'versionCode=123 minSdk=24 targetSdk=37\\nversionName=1.2.3\\n' ;;\n"
        "  *\"pm path io.dpcaio.app\"*) printf 'package:/data/app/io.dpcaio.app/base.apk\\n' ;;\n"
        "  *\"install -r\"*) printf 'Success\\n' ;;\n"
        "esac\n",
        encoding="utf-8",
    )
    path.chmod(path.stat().st_mode | stat.S_IXUSR)


def run_tool(args: list[str], env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(TOOL), *args],
        text=True,
        capture_output=True,
        env=env,
    )


def main() -> None:
    assert TOOL.is_file(), "missing migration ADB update compatibility tool"

    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        apk = tmp / "migration.apk"
        apk.write_bytes(b"fake-apk")
        adb_log = tmp / "adb.log"
        adb = tmp / "adb"
        write_fake_adb(adb, adb_log)

        env = os.environ.copy()

        dry_report = tmp / "dry.json"
        dry = run_tool(
            [
                "--apk", str(apk),
                "--package", "io.dpcaio.app",
                "--adb", str(adb),
                "--report", str(dry_report),
            ],
            env,
        )
        assert dry.returncode == 0, dry.stderr or dry.stdout
        dry_data = json.loads(dry_report.read_text(encoding="utf-8"))
        assert dry_data["status"] == "MIGRATION_UPDATE_PLAN_READY"
        assert dry_data["device"]["apiLevel"] == 33
        assert dry_data["device"]["apiBucket"] == "33+"
        assert dry_data["installedPackage"]["present"] is True
        assert dry_data["install"]["executed"] is False
        assert dry_data["install"]["command"][-3:] == ["install", "-r", str(apk)]
        assert "install -r" not in adb_log.read_text(encoding="utf-8")

        execute_report = tmp / "execute.json"
        executed = run_tool(
            [
                "--apk", str(apk),
                "--package", "io.dpcaio.app",
                "--adb", str(adb),
                "--report", str(execute_report),
                "--execute",
            ],
            env,
        )
        assert executed.returncode == 0, executed.stderr or executed.stdout
        execute_data = json.loads(execute_report.read_text(encoding="utf-8"))
        assert execute_data["status"] == "MIGRATION_UPDATE_INSTALL_PASS"
        assert execute_data["install"]["executed"] is True
        assert execute_data["install"]["exitCode"] == 0
        assert "install -r" in adb_log.read_text(encoding="utf-8")

    print("MIGRATION_ADB_UPDATE_MATRIX: PASS")


if __name__ == "__main__":
    main()
