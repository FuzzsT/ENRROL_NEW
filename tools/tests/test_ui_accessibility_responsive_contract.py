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

print('UI_ACCESSIBILITY_RESPONSIVE_CONTRACT: PASS')
