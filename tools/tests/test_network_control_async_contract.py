from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/NetworkControlActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'private var operationGeneration = 0' in text, \
    'Network Control must track operation ordering across Private DNS and DoH'
assert 'private fun runNetworkOperation' in text, \
    'Network Control must centralize worker-thread operations'
assert 'Thread {' in text[text.index('private fun runNetworkOperation'):], \
    'Network Control operations must execute off the UI thread'
assert 'isFinishing || isDestroyed' in text, \
    'Network Control worker completion must ignore Activity teardown'
assert 'generation != operationGeneration' in text, \
    'Network Control must discard stale completions from older operations'
assert 'val hostValue = host.text.toString().trim()' in text, \
    'Specified Private DNS must snapshot editable input before worker execution'
assert 'controller().applySpecifiedHost(hostValue)' in text, \
    'Specified Private DNS must use the snapshotted host'
assert 'controller().applyOpportunistic()' in text, \
    'Opportunistic Private DNS must remain supported through the async path'
assert 'DohDiagnosticClient().query(endpointValue, hex(queryHexValue))' in text, \
    'DoH query semantics must remain unchanged'

print('NETWORK_CONTROL_ASYNC_CONTRACT: PASS')
