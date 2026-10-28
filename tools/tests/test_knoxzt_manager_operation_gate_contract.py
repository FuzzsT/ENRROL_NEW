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

helper_start = text.index('private fun postResultIfAlive')
helper_end = text.index('private fun ensureReady()', helper_start)
helper_body = text[helper_start:helper_end]
assert 'runOnUiThread {' in helper_body, 'KnoxZT results must return to the UI thread through one helper'
assert 'isFinishing || isDestroyed' in helper_body, 'KnoxZT result helper must ignore Activity teardown'
assert 'return@runOnUiThread' in helper_body, 'KnoxZT result helper must exit before touching views after teardown'

ensure_start = text.index('private fun ensureReady()')
ensure_end = text.index('private fun listActivities()', ensure_start)
ensure_body = text[ensure_start:ensure_end]
assert 'if (!beginOperation())' in ensure_body, 'ensureReady must reject overlapping manual operations'
assert 'catch (error: Exception)' in ensure_body, 'ensureReady worker exceptions must be converted into a safe UI result'
assert 'KnoxZT recovery error' in ensure_body, 'ensureReady must expose a clear recovery error message'
assert 'postResultIfAlive(' in ensure_body, 'ensureReady success/error results must use the lifecycle-safe helper'
assert 'finally {' in ensure_body and 'finishOperation()' in ensure_body, 'ensureReady must always release the operation gate'

list_start = text.index('private fun listActivities()')
list_body = text[list_start:]
assert 'if (!beginOperation())' in list_body, 'listActivities must share the manual operation gate'
assert 'catch (error: Exception)' in list_body, 'listActivities worker exceptions must be converted into a safe UI result'
assert 'KnoxZT activity inventory error' in list_body, 'listActivities must expose a clear inventory error message'
assert 'postResultIfAlive(' in list_body, 'listActivities success/error results must use the lifecycle-safe helper'
assert 'finally {' in list_body and 'finishOperation()' in list_body, 'listActivities must always release the operation gate'

print('KNOXZT_MANAGER_OPERATION_GATE_CONTRACT: PASS')
