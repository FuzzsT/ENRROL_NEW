from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/NetworkControlActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

doh_start = text.index('private fun doh()')
doh_end = text.index('private fun hex(', doh_start)
doh = text[doh_start:doh_end]

endpoint_snapshot = 'val endpointValue = endpoint.text.toString().trim()'
query_snapshot = 'val queryHexValue = queryHex.text.toString()'
operation_start = doh.index('runNetworkOperation(')
worker_input = doh[operation_start:]

assert endpoint_snapshot in doh, \
    'Network DoH endpoint must be snapshotted on the UI thread before worker start'
assert query_snapshot in doh, \
    'Network DoH query text must be snapshotted on the UI thread before worker start'
assert doh.index(endpoint_snapshot) < operation_start, \
    'Network DoH endpoint snapshot must happen before the async operation starts'
assert doh.index(query_snapshot) < operation_start, \
    'Network DoH query snapshot must happen before the async operation starts'
assert 'endpoint.text' not in worker_input, \
    'Network DoH worker must not read endpoint EditText'
assert 'queryHex.text' not in worker_input, \
    'Network DoH worker must not read queryHex EditText'
assert 'DohDiagnosticClient().query(endpointValue, hex(queryHexValue))' in worker_input, \
    'Network DoH worker must use the UI-thread input snapshots'

print('NETWORK_DOH_INPUT_SNAPSHOT_CONTRACT: PASS')
