#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt").read_text("utf-8")

assert "app.packageName !in loadedActivities" not in source
assert "app.packageName in loadedActivities" not in source
assert "!loadedActivities.containsKey(app.packageName)" in source
assert "loadedActivities.containsKey(app.packageName)" in source
print("ACTIVITY_EXPLORER_CONCURRENT_MAP_CONTRACT: PASS")
