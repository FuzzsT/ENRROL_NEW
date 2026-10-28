from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'private fun importOfflineBundle' in text, \
    'Offline bundle import must be centralized in an async helper'
assert 'Thread {' in text[text.index('private fun importOfflineBundle'):], \
    'Offline bundle copy/inspection/hash must run off the UI thread'
assert 'val sourceUri = uri' in text, \
    'Offline bundle import must snapshot the Activity-result Uri before worker execution'
assert 'copyToPrivateStorage(sourceUri)' in text, \
    'Offline bundle worker must use the captured Uri snapshot'
assert 'bundleReader.inspect(file, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)' in text, \
    'Offline bundle verification must stay inside the import worker'
assert 'sha256(file)' in text, \
    'Offline bundle hashing must stay inside the import worker'
assert 'runOnUiThread' in text, \
    'Offline bundle worker must marshal final state back to the UI thread'
assert 'isFinishing || isDestroyed' in text, \
    'Offline bundle import completion must ignore Activity teardown'
assert 'return@runOnUiThread' in text, \
    'Offline bundle lifecycle guard must exit before touching Activity state/views'

result_start = text.index('override fun onActivityResult')
result_end = text.index('\n    private fun preview()', result_start)
result_body = text[result_start:result_end]
assert 'copyToPrivateStorage(' not in result_body, \
    'onActivityResult must not perform bundle copy on the UI thread'
assert 'bundleReader.inspect(' not in result_body, \
    'onActivityResult must not verify bundle contents on the UI thread'
assert 'sha256(' not in result_body, \
    'onActivityResult must not hash bundle contents on the UI thread'

print('OFFLINE_SETUP_ASYNC_CONTRACT: PASS')
