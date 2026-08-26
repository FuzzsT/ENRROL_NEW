#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main():
    bridge=(ROOT/'apps/dpc/lab/tools/src/main/kotlin/io/dpcaio/lab/LabHookActivityBridge.kt').read_text()
    route=(ROOT/'apps/dpc/lab/tools/src/main/kotlin/io/dpcaio/lab/LabHookActivityRouteExecutor.kt').read_text()
    planner=(ROOT/'apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ActivityAccessPlanner.kt').read_text()
    assert 'OWN_PACKAGE_ONLY' in bridge
    assert 'DEBUGGABLE_REQUIRED' in bridge
    assert 'LAB_JAVA_HOOK' in route and 'LAB_ART_HOOK' in route
    assert 'input.labBuild && input.targetOwnedDebuggable' in planner
    app=(ROOT/'apps/dpc/app/build.gradle.kts').read_text()
    assert 'implementation(project(":lab-tools"))' in app
    print('test_lab_hook_activity_contract: PASS')
if __name__=='__main__': main()
