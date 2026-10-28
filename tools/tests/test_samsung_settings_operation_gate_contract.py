from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/SamsungSettingsEditorActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'companion object' in text, 'Samsung settings operation gate must survive Activity recreation'
companion = text[text.index('companion object'):text.index('override fun onCreate')]
assert 'private val operationLock = Any()' in companion, 'Samsung settings operation gate needs a process-wide lock'
assert 'private var operationRunning = false' in companion, 'Samsung settings operation gate needs process-wide running state'

write_start = text.index('private fun writeValue()')
write_body = text[write_start:text.index('override fun onDestroy()', write_start)]
for token in [
    'synchronized(operationLock)',
    'if (operationRunning)',
    'operationRunning = true',
    'finally {',
    'operationRunning = false',
]:
    assert token in write_body, f'Samsung settings write operation gate missing: {token}'

assert write_body.index('operationRunning = true') < write_body.index('Thread {'), \
    'Samsung settings write gate must be acquired before starting the worker'
assert write_body.index('finally {') < write_body.rindex('operationRunning = false'), \
    'Samsung settings write gate must be released from finally'

print('SAMSUNG_SETTINGS_OPERATION_GATE_CONTRACT: PASS')
