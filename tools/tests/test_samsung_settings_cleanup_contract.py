from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/SamsungSettingsEditorActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'private var stabilityCleanup: Runnable?' in text, \
    'Settings editor must retain delayed stability cleanup so it can be cancelled'
assert 'private fun cancelStabilityCleanup()' in text, \
    'Settings editor must centralize delayed cleanup cancellation'
assert 'stabilityCleanup?.let(status::removeCallbacks)' in text, \
    'Settings editor must remove delayed cleanup callbacks during Activity teardown'
assert text.count('cancelStabilityCleanup()') >= 3, \
    'Settings editor must cancel cleanup before replacement and again during teardown'
assert 'stabilityCleanup = null' in text, \
    'Settings editor must release its delayed cleanup reference'

print('SAMSUNG_SETTINGS_CLEANUP_CONTRACT: PASS')
