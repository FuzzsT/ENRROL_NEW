from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
ACTIVITY = APP / 'OfflineSetupActivity.kt'
STORE = APP / 'OfflineDeploymentStore.kt'
INSTALLER = ROOT / 'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflinePackageInstaller.kt'
text = ACTIVITY.read_text(encoding='utf-8')
store = STORE.read_text(encoding='utf-8')
installer = INSTALLER.read_text(encoding='utf-8')

assert 'private fun stageAndCommitBundle' in text, \
    'Offline package staging must be centralized in an off-main helper'
helper_start = text.index('private fun stageAndCommitBundle')
helper_end = text.index('\n    private fun shizukuAvailable()', helper_start)
helper_body = text[helper_start:helper_end]

for token in [
    'Thread {',
    'packageInstaller.stageBundle(file, manifest)',
    'packageInstaller.commit(plan, callback.intentSender)',
    'runOnUiThread',
    'isFinishing || isDestroyed',
    'bundleIoInProgress.set(false)',
]:
    assert token in helper_body, f'Offline apply worker missing required behavior: {token}'

apply_start = text.index('private fun applySupported()')
apply_end = helper_start
apply_body = text[apply_start:apply_end]
assert 'stageAndCommitBundle(' in apply_body, \
    'Apply must delegate package staging to the worker helper'
assert 'packageInstaller.stageBundle(' not in apply_body, \
    'Apply must not copy APK payloads on the UI thread'
assert 'packageInstaller.commit(' not in apply_body, \
    'Apply must not commit PackageInstaller sessions on the UI thread'

assert text.count('bundleIoInProgress.compareAndSet(false, true)') >= 2, \
    'Import and apply must share one process-wide bundle I/O gate'
assert 'private val bundleIoInProgress = java.util.concurrent.atomic.AtomicBoolean(false)' in text, \
    'Shared bundle I/O gate must survive Activity recreation without retaining an Activity'
assert 'private fun deploymentOwnsBundle()' in text, \
    'Offline setup must block bundle replacement/reapply while recovery still owns the bundle path'
for stage in [
    'OfflineStage.PACKAGES_STAGED',
    'OfflineStage.PACKAGES_INSTALLED',
    'OfflineStage.PERMISSIONS_APPLIED',
    'OfflineStage.COMPONENTS_APPLIED',
    'OfflineStage.POLICIES_APPLIED',
    'OfflineStage.READBACK_VERIFIED',
]:
    assert stage in text, f'Active deployment stage missing from bundle-path guard: {stage}'

import_start = text.index('private fun importOfflineBundle')
import_end = text.index('\n    private fun preview()', import_start)
import_body = text[import_start:import_end]
assert 'deploymentOwnsBundle()' in import_body, \
    'Import must not overwrite a bundle still owned by active recovery/deployment'
assert 'deploymentOwnsBundle()' in apply_body, \
    'Apply must not start a second deployment while the current bundle is active'

# Ownership must be rechecked only after the process-wide gate is actually acquired.
import_gate = import_body.index('bundleIoInProgress.compareAndSet(false, true)')
import_recheck = import_body.find('deploymentOwnsBundle()', import_gate)
assert import_recheck > import_gate, \
    'Import must recheck deployment ownership after acquiring the shared bundle I/O gate'
helper_gate = helper_body.index('bundleIoInProgress.compareAndSet(false, true)')
helper_recheck = helper_body.find('deploymentOwnsBundle()', helper_gate)
assert helper_recheck > helper_gate, \
    'Package staging must recheck deployment ownership after acquiring the shared bundle I/O gate'
assert 'bundleIoInProgress.set(false)' in import_body[import_recheck:import_recheck + 500], \
    'Import ownership rejection must release the acquired shared bundle I/O gate'
assert 'bundleIoInProgress.set(false)' in helper_body[helper_recheck:helper_recheck + 500], \
    'Package staging ownership rejection must release the acquired shared bundle I/O gate'

# PACKAGES_STAGED must persist the exact session that commit() uses, including single-package plans.
assert 'val commitSessionId = plan.parentSessionId ?: plan.childSessionIds.single()' in helper_body, \
    'Staging must compute the exact PackageInstaller session used for commit'
assert 'commitSessionId = commitSessionId' in helper_body, \
    'PACKAGES_STAGED must persist the exact commit session for crash recovery'
assert 'val commitSessionId: Int? = null' in store, \
    'Offline deployment state must persist a resumable PackageInstaller commit session id'
assert '.putInt("commitSessionId", state.commitSessionId ?: -1)' in store, \
    'Offline deployment store must save commitSessionId'
assert 'prefs.getInt("commitSessionId", -1).takeIf { it >= 0 }' in store, \
    'Offline deployment store must restore commitSessionId'
assert 'fun commitSession(sessionId: Int, statusReceiver: IntentSender)' in installer, \
    'Offline installer must support resuming commit of an existing staged session'
assert 'fun isCommitted(sessionId: Int): Boolean?' in installer and '.isCommitted' in installer, \
    'Offline installer must distinguish staged-uncommitted sessions from committed sessions'

print('OFFLINE_APPLY_ASYNC_CONTRACT: PASS')
