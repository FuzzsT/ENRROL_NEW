from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

surface = (APP / 'UiSurfaceController.kt').read_text(encoding='utf-8')
design = (APP / 'ModernUi.kt').read_text(encoding='utf-8')

assert 'ValueAnimator.areAnimatorsEnabled()' in surface, \
    'UiSurfaceController must respect the system animator setting before entrance motion'
assert 'content.alpha = 1f' in surface and 'content.translationY = 0f' in surface, \
    'reduced-motion path must leave content fully visible and unshifted'

row_start = design.index('fun actionRow(activity: Activity')
row_end = design.index('\n    fun outputCard(', row_start)
row = design[row_start:row_end]
for token in [
    'screenWidthDp',
    'buttons.size >= 3',
    'LinearLayout.VERTICAL',
    'LinearLayout.HORIZONTAL',
    'ViewGroup.LayoutParams.MATCH_PARENT',
]:
    assert token in row, f'ModernUi.actionRow responsive contract missing: {token}'

# Button press motion must also respect the system animator setting. When animations
# are disabled the action should run immediately rather than waiting on ViewPropertyAnimator.
action_start = design.index('private fun buildAction(')
action_end = design.index('\n    fun actionRow(', action_start)
action = design[action_start:action_end]
assert 'ValueAnimator.areAnimatorsEnabled()' in action, \
    'ModernUi actions must respect the system animator setting'
assert 'if (!ValueAnimator.areAnimatorsEnabled())' in action, \
    'ModernUi actions need an explicit reduced-motion path'
assert 'onClick()' in action, \
    'reduced-motion action path must execute the click immediately'

# Interactive filter/tab pills need a full 48dp minimum touch target.
pill_start = design.index('fun pill(')
pill_end = design.index('\n    fun monogramTile(', pill_start)
pill = design[pill_start:pill_end]
assert 'minHeight = dp(activity, 48)' in pill, \
    'ModernUi.pill must expose at least a 48dp touch target'
assert 'minimumHeight = dp(activity, 48)' in pill, \
    'ModernUi.pill minimumHeight must stay at 48dp'

print('UI_ACCESSIBILITY_RESPONSIVE_CONTRACT: PASS')
