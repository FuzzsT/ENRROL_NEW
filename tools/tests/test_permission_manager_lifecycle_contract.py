from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'private fun postUiIfAlive' in text, 'Permission Manager must centralize lifecycle-safe UI callbacks'
assert 'isFinishing || isDestroyed' in text, 'Permission Manager UI callback helper must ignore teardown'
assert 'return@runOnUiThread' in text, 'Permission Manager lifecycle helper must exit before touching views'

for token in [
    'postUiIfAlive { output.text = text }',
    'postUiIfAlive { output.text = "Global policy requested=',
    'postUiIfAlive { output.text = "Apply supported',
    'postUiIfAlive { output.text = "Restore previous DPC states',
]:
    assert token in text, f'Permission Manager async callback missing lifecycle-safe helper: {token}'

print('PERMISSION_MANAGER_LIFECYCLE_CONTRACT: PASS')
