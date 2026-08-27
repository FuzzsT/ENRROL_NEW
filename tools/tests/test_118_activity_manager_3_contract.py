#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
activity = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt').read_text(encoding='utf-8')
for token in [
    'Activity Manager 3.0',
    'listApps(',
    'expandedPackages',
    'loadedActivities',
    'ActivityBrowserFilter',
    'AppScope',
    'EnabledStateFilter',
    'ExportedStateFilter',
    'LauncherStateFilter',
    'PermissionStateFilter',
    'favoritesOnly',
    'favoriteGroup',
    'toggleAppFavorite',
    'toggleActivityFavorite',
    'createGroup',
    'renameGroup',
    'deleteGroup',
    'setMembership',
    'ComponentControlRouter',
    'changeState(',
    'launch(activity',
]:
    assert token in activity, token
# Must be all-app first, not require manually entering a package to start.
assert 'hint = "package name"' not in activity
assert 'Scan activities' not in activity
print('PASS: Activity Manager 3.0 UI/behavior contract')
