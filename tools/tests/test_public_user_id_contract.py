#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
patterns = [
    re.compile(r"\bUserHandle\.myUserId\s*\("),
    re.compile(r"\bUserHandle\.getUserId\s*\("),
    re.compile(r"\bandroid\.os\.UserHandle\.myUserId\s*\("),
    re.compile(r"\bandroid\.os\.UserHandle\.getUserId\s*\("),
]
violations = []
for path in ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for lineno, line in enumerate(text.splitlines(), 1):
        if any(p.search(line) for p in patterns):
            violations.append(f"{path.relative_to(ROOT)}:{lineno}:{line.strip()}")
if violations:
    raise SystemExit("Hidden UserHandle user-id APIs remain:\n" + "\n".join(violations))
print("PUBLIC_USER_ID_CONTRACT: PASS")
