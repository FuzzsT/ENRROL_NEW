from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUPPORT = ROOT / 'apps/dpc/modules/knox/zero-trust/android/src/main/kotlin/io/dpcaio/knoxzt/android/KnoxZtActivitySupport.kt'
text = SUPPORT.read_text(encoding='utf-8')

assert 'private val context: Context' not in text, \
    'KnoxZtActivitySupport must not retain the caller Context directly'
assert 'context.applicationContext' in text, \
    'KnoxZtActivitySupport must normalize the caller Context to applicationContext'
assert 'KnoxZtRecoveryManager(appContext' in text, \
    'KnoxZtActivitySupport recovery must use applicationContext'
assert 'ShizukuUserServiceClient(appContext)' in text, \
    'KnoxZtActivitySupport Shizuku client must use applicationContext'
assert 'AndroidActivityInventory(appContext)' in text, \
    'KnoxZtActivitySupport inventory must use applicationContext'

print('KNOXZT_ACTIVITY_SUPPORT_CONTEXT_CONTRACT: PASS')
