from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
explorer = (APP / 'ActivityExplorerActivity.kt').read_text(encoding='utf-8')
modern = (APP / 'ModernUi.kt').read_text(encoding='utf-8')

# Visual hierarchy: the launcher should read like an app/component browser, not a settings form.
for token in [
    'renderStatsStrip',
    'renderActivityIdentity',
    'ActivityQuickFilter.entries',
    'VISIBLE',
    'EXPORTED',
    'DISABLED',
    'Advanced controls',
]:
    assert token in explorer, f'Activity Explorer visual hierarchy missing: {token}'

# Dedicated compact primitives keep visual polish consistent and reusable.
for token in [
    'fun compactCard(',
    'fun pill(',
    'fun monogramTile(',
    'fun metricTile(',
    'fun divider(',
]:
    assert token in modern, f'ModernUi visual primitive missing: {token}'

# Filter controls should use pills rather than full-size action buttons.
assert 'ModernUi.pill(' in explorer, 'Quick filters must use compact pills'
assert 'ModernUi.monogramTile(' in explorer, 'Activity rows must include a visual identity tile'
assert 'ModernUi.compactCard(' in explorer, 'Activity rows must use compact cards'
assert 'ModernUi.metricTile(' in explorer, 'Launcher summary must expose compact metrics'

# Launch stays the dominant action; component mutation controls remain secondary.
launch_idx = explorer.index('"Launch", primary = true')
advanced_idx = explorer.index('"Advanced controls"')
assert launch_idx < advanced_idx, 'Launch CTA must remain above advanced component controls'

print('ACTIVITY_EXPLORER_VISUAL_POLISH_CONTRACT: PASS')
