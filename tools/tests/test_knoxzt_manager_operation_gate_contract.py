from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxZtManagerActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

for token in [
    'private val operationLock = Any()',
    'private var operationRunning = false',
    'private fun beginOperation(): Boolean',
    'private fun finishOperation()',
    'KnoxZT operation already running',
]:
    assert token in text, f'KnoxZT manager missing serialized manual operation gate: {token}'

assert 'companion object {' in text, 'KnoxZT manual-operation gate must be process-wide across Activity recreation'
companion_start = text.index('companion object {')
companion_end = text.index('\n    override fun onCreate', companion_start)
companion = text[companion_start:companion_end]
assert 'private val operationLock = Any()' in companion, \
    'KnoxZT operation lock must survive Activity recreation'
assert 'private var operationRunning = false' in companion, \
    'KnoxZT running state must survive Activity recreation'

ensure_start = text.index('private fun ensureReady()')
ensure_end = text.index('private fun listActivities()', ensure_start)
ensure_body = text[ensure_start:ensure_end]
assert 'if (!beginOperation())' in ensure_body, 'ensureReady must reject overlapping manual operations'
assert 'finally {' in ensure_body and 'finishOperation()' in ensure_body, 'ensureReady must always release the operation gate'

list_start = text.index('private fun listActivities()')
list_body = text[list_start:]
assert 'if (!beginOperation())' in list_body, 'listActivities must share the manual operation gate'
assert 'finally {' in list_body and 'finishOperation()' in list_body, 'listActivities must always release the operation gate'

print('KNOXZT_MANAGER_OPERATION_GATE_CONTRACT: PASS')
