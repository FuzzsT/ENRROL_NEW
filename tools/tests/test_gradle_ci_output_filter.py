#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "tools" / "ci" / "filter_gradle_output.py"
spec = importlib.util.spec_from_file_location("filter_gradle_output", MODULE)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

hidden = [
    "> Task :core:processResources NO-SOURCE\n",
    "> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED\n",
    "> Task :app:preBuild UP-TO-DATE\n",
    "> Task :lib:compileKotlin FROM-CACHE\n",
    "Consider enabling configuration cache to speed up this build: https://docs.gradle.org/x\n",
]
visible = [
    "> Task :app:compileKotlin\n",
    "w: file.kt:1: deprecated API\n",
    "e: file.kt:2: error\n",
    "BUILD SUCCESSFUL in 1m\n",
    "BUILD FAILED in 10s\n",
    '{"provisioningMode":"work-profile"}\n',
]
for line in hidden:
    assert mod.should_hide(line), line
for line in visible:
    assert not mod.should_hide(line), line
print("PASS: Gradle CI output filter hides only cosmetic task-state lines")
