from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/NetworkControlActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

doh_start = text.index('private fun doh()')
doh_end = text.index('private fun hex(', doh_start)
doh = text[doh_start:doh_end]
thread = doh[doh.index('Thread {'):]

assert 'val endpointValue = endpoint.text.toString().trim()' in doh, \
    'Network DoH endpoint must be snapshotted on the UI thread before worker start'
assert 'val queryHexValue = queryHex.text.toString()' in doh, \
    'Network DoH query text must be snapshotted on the UI thread before worker start'
assert 'endpoint.text' not in thread, \
    'Network DoH worker must not read endpoint EditText'
assert 'queryHex.text' not in thread, \
    'Network DoH worker must not read queryHex EditText'

print('NETWORK_DOH_INPUT_SNAPSHOT_CONTRACT: PASS')
