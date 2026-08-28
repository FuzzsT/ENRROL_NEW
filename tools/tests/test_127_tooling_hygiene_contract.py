#!/usr/bin/env python3
from pathlib import Path
import stat

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools/run_host_tests.sh"
VERIFY_AIO = ROOT / "tools/verify-aio"


def main() -> None:
    if not RUNNER.is_file():
        raise AssertionError("tools/run_host_tests.sh is missing")
    if not VERIFY_AIO.is_file():
        raise AssertionError("tools/verify-aio is missing")

    runner = RUNNER.read_text("utf-8")
    required_runner_lines = [
        'python3 "$ROOT/tools/tests/test_126_android_parity_runtime_facts_contract.py"',
        'python3 "$ROOT/tools/tests/test_127_tooling_hygiene_contract.py"',
    ]
    missing = [line for line in required_runner_lines if line not in runner]
    if missing:
        raise AssertionError("canonical host runner is missing: " + ", ".join(missing))

    mode = VERIFY_AIO.stat().st_mode
    if not mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH):
        raise AssertionError("tools/verify-aio must be executable in the Git tree")

    print("TOOLING_HYGIENE_127: PASS")


if __name__ == "__main__":
    main()
