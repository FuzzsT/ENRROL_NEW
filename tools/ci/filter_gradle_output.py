#!/usr/bin/env python3
"""Keep Gradle CI output useful without changing the Gradle task graph.

Only cosmetic terminal task-state lines are hidden. Warnings, errors, task names,
QR generator output and BUILD SUCCESSFUL/FAILED remain visible.
"""
from __future__ import annotations

import re
import sys

COSMETIC_TASK_STATE = re.compile(
    r"^> Task .+ (?:NO-SOURCE|SKIPPED|UP-TO-DATE|FROM-CACHE)\s*$"
)
CONFIG_CACHE_HINT = re.compile(
    r"^Consider enabling configuration cache to speed up this build:"
)


def should_hide(line: str) -> bool:
    text = line.rstrip("\r\n")
    return bool(COSMETIC_TASK_STATE.match(text) or CONFIG_CACHE_HINT.match(text))


def main() -> int:
    for line in sys.stdin:
        if not should_hide(line):
            sys.stdout.write(line)
            sys.stdout.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
