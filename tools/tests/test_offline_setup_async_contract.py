from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

helper_start = text.index('private fun importOfflineBundle')
helper_end = text.index('\n    private fun preview()', helper_start)
helper_body = text[helper_start:helper_end]

assert 'Thread {' in helper_body, \
    'Offline bundle copy/inspection/hash must run off the UI thread'
assert 'val sourceUri = uri' in helper_body, \
    'Offline bundle import must snapshot the Activity-result Uri before worker execution'
assert 'copyToPrivateStorage(sourceUri)' in helper_body, \
    'Offline bundle worker must use the captured Uri snapshot'
assert 'bundleReader.inspect(file, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)' in helper_body, \
    'Offline bundle verification must stay inside the import worker'
assert 'sha256(file)' in helper_body, \
    'Offline bundle hashing must stay inside the import worker'
assert 'runOnUiThread' in helper_body, \
    'Offline bundle worker must marshal final state back to the UI thread'
assert 'isFinishing || isDestroyed' in helper_body, \
    'Offline bundle import completion must ignore Activity teardown'
assert 'return@runOnUiThread' in helper_body, \
    'Offline bundle lifecycle guard must exit before touching Activity state/views'
assert 'generation != importGeneration' in helper_body, \
    'Offline bundle import must reject stale completion from an older selection'

result_start = text.index('override fun onActivityResult')
result_end = helper_start
result_body = text[result_start:result_end]
assert 'importOfflineBundle(sourceUri)' in result_body, \
    'Activity result must hand the captured Uri to the async import helper'
assert 'copyToPrivateStorage(' not in result_body, \
    'onActivityResult must not perform bundle copy on the UI thread'
assert 'bundleReader.inspect(' not in result_body, \
    'onActivityResult must not verify bundle contents on the UI thread'
assert 'sha256(' not in result_body, \
    'onActivityResult must not hash bundle contents on the UI thread'

assert 'file.inputStream().buffered().use' in text, \
    'Offline bundle SHA-256 must stream file contents instead of reading the entire bundle into memory'
assert 'file.readBytes()' not in text, \
    'Offline bundle SHA-256 must not allocate the entire bundle in memory'

print('OFFLINE_SETUP_ASYNC_CONTRACT: PASS')
