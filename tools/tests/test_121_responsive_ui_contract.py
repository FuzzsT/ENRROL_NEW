#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main'
KOTLIN = APP / 'kotlin/io/dpcaio/app'

shell = (KOTLIN / 'DpcUiShell.kt').read_text(encoding='utf-8')
assert 'fun View.setPaddingDp(' in shell, 'shared dp padding helper missing'
assert 'fun horizontalScrollRow(' in shell, 'responsive horizontal action/filter row helper missing'

manifest = (APP / 'AndroidManifest.xml').read_text(encoding='utf-8')
styles = APP / 'res/values/styles.xml'
assert styles.is_file(), 'explicit app theme missing'
styles_text = styles.read_text(encoding='utf-8')
assert 'name="AppTheme"' in styles_text
assert 'Theme.Material.Light.DarkActionBar' in styles_text
assert 'android:theme="@style/AppTheme"' in manifest

# Programmatic UI spacing must be density-independent. DpcUiShell itself works in px
# because WindowInsets are delivered in px; all Activity UI files should use setPaddingDp.
for path in KOTLIN.glob('*.kt'):
    if path.name == 'DpcUiShell.kt':
        continue
    text = path.read_text(encoding='utf-8')
    bad = re.findall(r'\bsetPadding\(\s*\d+', text)
    assert not bad, f'{path.name} still has raw-pixel padding: {bad[:3]}'

activity = (KOTLIN / 'ActivityExplorerActivity.kt').read_text(encoding='utf-8')
for token in [
    'horizontalScrollRow(scopeButton, enabledButton, exportedButton, launcherButton)',
    'horizontalScrollRow(permissionButton, favoritesButton, groupFilterButton, sortButton)',
    'horizontalScrollRow(',
]:
    assert token in activity, token
assert 'twoButtonRow(scopeButton, enabledButton)' not in activity
assert 'twoButtonRow(exportedButton, launcherButton)' not in activity

# The dashboard must keep a scroll root and density-independent spacing.
dash = (KOTLIN / 'AioDashboardActivity.kt').read_text(encoding='utf-8')
assert 'DpcUiShell.scroll(this, body)' in dash
assert 'setPaddingDp(' in dash

print('PASS: responsive density-independent UI contract')
