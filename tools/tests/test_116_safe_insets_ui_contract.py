#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
shell = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcUiShell.kt'
assert shell.exists(), 'DpcUiShell.kt missing'
text = shell.read_text(encoding='utf-8')
for token in [
    'object DpcUiShell',
    'setOnApplyWindowInsetsListener',
    'WindowInsets.Type.systemBars()',
    'WindowInsets.Type.ime()',
    'displayCutout',
    'setDecorFitsSystemWindows(false)',
    'isFillViewport = true',
]:
    assert token in text, token

app_dir = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
for name in ['AioDashboardActivity.kt', 'ActivityExplorerActivity.kt']:
    content = (app_dir / name).read_text(encoding='utf-8')
    assert 'DpcUiShell' in content, f'{name} must use DpcUiShell'


# Every app Activity that owns a content view must opt into the shared inset shell.
for path in app_dir.glob('*.kt'):
    content = path.read_text(encoding='utf-8')
    if 'setContentView(' in content and 'class ' in content and 'Activity' in content:
        assert 'DpcUiShell' in content, f'{path.name} owns content but does not use DpcUiShell'

# Primary long-form screens should be inset-safe, not raw ScrollView-only roots.
for path in app_dir.glob('*.kt'):
    content = path.read_text(encoding='utf-8')
    if 'setContentView(ScrollView(this).apply' in content:
        raise AssertionError(f'raw ScrollView setContentView remains: {path.name}')

print('PASS: safe insets UI shell contract')
