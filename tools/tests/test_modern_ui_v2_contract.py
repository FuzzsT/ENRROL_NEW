from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

for name in [
    'AioDashboardActivity.kt',
    'PermissionManagerActivity.kt',
    'EnterprisePolicyHubActivity.kt',
    'ModuleCenterActivity.kt',
    'NetworkControlActivity.kt',
    'DpcDiagnosticsActivity.kt',
    'EnrollmentManualActivity.kt',
    'EnrollmentStatusActivity.kt',
    'KnoxEnterpriseCenterActivity.kt',
    'ActivityExplorerActivity.kt',
    'OfflineSetupActivity.kt',
    'CredentialCenterActivity.kt',
    'DeviceLifecycleActivity.kt',
    'WorkProfileCopeActivity.kt',
    'EnterpriseOperationsActivity.kt',
    'GoogleAccountManagerActivity.kt',
    'KnoxZtManagerActivity.kt',
    'NfcLabActivity.kt',
    'PolicyComplianceActivity.kt',
    'SamsungSettingsEditorActivity.kt',
    'ScenarioLabActivity.kt',
]:
    text = (APP / name).read_text(encoding='utf-8')
    assert 'ModernUi.' in text, f'{name}: modern design system not applied'

design = (APP / 'ModernUi.kt').read_text(encoding='utf-8')
for token in [
    'enum class Tone',
    'fun page(activity: Activity): LinearLayout',
    'fun scrollPage(activity: Activity, content: LinearLayout): ScrollView',
    'fun chip(activity: Activity, text: String, tone: Tone)',
    'primary: Boolean',
    'fun outputCard(activity: Activity, text: String): TextView',
]:
    assert token in design, f'ModernUi compatibility API missing: {token}'

field_start = design.index('fun field(activity: Activity')
field_end = design.index('\n    fun action(', field_start)
field_body = design[field_start:field_end]
assert field_body.index('setSingleLine(true)') < field_body.index('this.inputType = inputType'), \
    'ModernUi.field must configure single-line before inputType to preserve password masking'

network = (APP / 'NetworkControlActivity.kt').read_text(encoding='utf-8')
for token in ['Private DNS', 'DNS over HTTPS', 'ModernUi.hero', 'ModernUi.outputCard', 'ModernUi.scrollPage']:
    assert token in network, token
assert 'isFinishing || isDestroyed' in network, \
    'Network DoH callback must ignore Activity teardown'
assert 'return@runOnUiThread' in network, \
    'Network lifecycle guard must exit before touching the result view'

diagnostics = (APP / 'DpcDiagnosticsActivity.kt').read_text(encoding='utf-8')
for token in ['System health', 'Management state', 'Module availability', 'ModernUi.Tone', 'ModernUi.chip', 'ModernUi.card']:
    assert token in diagnostics, token

enrollment = (APP / 'EnrollmentManualActivity.kt').read_text(encoding='utf-8')
for token in ['ModernUi.hero', 'ModernUi.field', 'ModernUi.card', 'ModernUi.action', 'ModernUi.outputCard']:
    assert token in enrollment, token

status = (APP / 'EnrollmentStatusActivity.kt').read_text(encoding='utf-8')
for token in ['ModernUi.hero', 'ModernUi.card', 'ModernUi.chip', 'ModernUi.action', 'ModernUi.scrollPage']:
    assert token in status, token

knox = (APP / 'KnoxEnterpriseCenterActivity.kt').read_text(encoding='utf-8')
for token in ['Capability Matrix', 'ModernUi.hero', 'ModernUi.card', 'ModernUi.chip', 'ModernUi.scrollPage']:
    assert token in knox, token
assert 'state.contains("ACTIVE")' not in knox, \
    'Knox tone mapper must not classify INACTIVE as ACTIVE by substring'
assert 'state.contains("AVAILABLE")' not in knox, \
    'Knox tone mapper must not classify UNAVAILABLE as AVAILABLE by substring'
assert 'state.contains("SUPPORTED")' not in knox, \
    'Knox tone mapper must not classify UNSUPPORTED as SUPPORTED by substring'
assert 'state == "ACTIVE" || state == "AVAILABLE" || state == "SUPPORTED"' in knox, \
    'Knox success tone must require exact positive states'
assert 'state.startsWith("UNSUPPORTED") -> ModernUi.Tone.DANGER' in knox, \
    'Knox unsupported states should remain danger even with suffixes'

explorer = (APP / 'ActivityExplorerActivity.kt').read_text(encoding='utf-8')
for token in [
    'Activity Launcher', 'Batch Preview', 'Restore snapshot',
    'ModernUi.hero', 'ModernUi.field', 'ModernUi.card', 'ModernUi.action',
    'ModernUi.actionRow', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in explorer, token

offline = (APP / 'OfflineSetupActivity.kt').read_text(encoding='utf-8')
for token in [
    'FULL OFFLINE', 'OFFLINE THEN SYNC', 'Preview deployment', 'Apply supported',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.chip', 'ModernUi.action',
    'ModernUi.actionRow', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in offline, token

credential = (APP / 'CredentialCenterActivity.kt').read_text(encoding='utf-8')
for token in [
    'Certificate & Credential Center', 'Private key export: NOT PERMITTED',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in credential, token

lifecycle = (APP / 'DeviceLifecycleActivity.kt').read_text(encoding='utf-8')
for token in [
    'Kiosk / Lock Task', 'Device Security', 'Application Control', 'Factory Reset Protection',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in lifecycle, token

cope = (APP / 'WorkProfileCopeActivity.kt').read_text(encoding='utf-8')
for token in [
    'Work Profile / COPE', 'Cross-profile packages', 'Contacts access', 'Caller ID access',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in cope, token

ops = (APP / 'EnterpriseOperationsActivity.kt').read_text(encoding='utf-8')
for token in [
    'Security Logging', 'Network Logging', 'System Update Policy', 'Stored log batches',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in ops, token
assert 'status.contains("AVAILABLE")' not in ops, \
    'Enterprise capability tone must not classify UNAVAILABLE as AVAILABLE by substring'
assert 'status == "AVAILABLE"' in ops, \
    'Enterprise capability success tone must require exact AVAILABLE status'

google = (APP / 'GoogleAccountManagerActivity.kt').read_text(encoding='utf-8')
for token in [
    'Google Account Priority', 'Wybierz konto dla DPC', 'Ustaw wybrane jako pierwsze',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in google, token
assert 'isFinishing || isDestroyed' in google, \
    'Google account removal callback must ignore Activity teardown'
assert 'return@runOnUiThread' in google, \
    'Google account lifecycle guard must exit before recursive UI workflow'
account_store = (ROOT / 'apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/AioAccountSelectionStore.kt').read_text(encoding='utf-8')
for token in [
    'fun saveReorderRecovery(',
    'fun pendingRemoval(): List<String>',
    'fun markRemoved(accountName: String)',
    'fun removalInFlight(): String?',
    'fun markRemovalInFlight(accountName: String)',
    'fun markRemovalFailed(accountName: String)',
    'fun manualRemovalRequired(): String?',
]:
    assert token in account_store, f'Google account recovery store missing phase API: {token}'
mark_readded_start = account_store.index('fun markReAdded(accountName: String)')
mark_readded_end = account_store.index('fun clearPendingReorder()', mark_readded_start)
assert 'clearPendingReorder()' not in account_store[mark_readded_start:mark_readded_end], \
    'markReAdded must not clear target/restore metadata before Activity restores policy'
execute_start = google.index('private fun executeRemovalPhase')
execute_end = google.index('private fun requestRemoval', execute_start)
execute_body = google[execute_start:execute_end]
persist_recovery = 'selectionStore.saveReorderRecovery(targetName, accountNames, emptyList(), restoreManagementDisabled)'
assert persist_recovery in execute_body, \
    'Google account recovery must persist the removal phase before automatic removal starts'
assert execute_body.index(persist_recovery) < execute_body.index('setGoogleAccountManagementDisabled(false)'), \
    'Google account recovery must be durable before management restrictions are relaxed'
request_start = google.index('private fun requestRemoval')
request_end = google.index('private fun resumePendingReorderIfPossible', request_start)
request_body = google[request_start:request_end]
assert 'selectionStore.markRemovalInFlight(name)' in request_body and request_body.index('selectionStore.markRemovalInFlight(name)') < request_body.index('requestRemoveGoogleAccount'), \
    'Google account removal must persist in-flight state before issuing the request'
mark_removed = 'if (removed) selectionStore.markRemoved(name)'
assert mark_removed in request_body and request_body.index(mark_removed) < request_body.index('runOnUiThread'), \
    'Confirmed removal must transition recovery state before lifecycle-gated UI work'
assert 'if (!removed) selectionStore.markRemovalFailed(name)' in request_body and request_body.index('if (!removed) selectionStore.markRemovalFailed(name)') < request_body.index('runOnUiThread'), \
    'Failed removal must persist manual fallback before lifecycle-gated UI work'
assert 'accountNames.drop(index)' not in google, \
    'Google account removal failure must not discard accounts removed earlier in the sequence'
assert 'val pendingRemoval = selectionStore.pendingRemoval()' in google, \
    'Google account resume must inspect the distinct removal phase'
assert 'pendingRemoval.filterNot { it in visibleNames }.forEach { selectionStore.markRemoved(it) }' in google, \
    'Google account resume must reconcile removals from observed account absence'
assert 'selectionStore.removalInFlight()' in google and 'scheduleRecoveryPoll()' in google, \
    'Google account resume must wait for in-flight removal and poll recovery state without duplicate requests'
assert 'selectionStore.manualRemovalRequired()' in google, \
    'Google account resume must persistently expose manual fallback after removal failure'
assert 'requestRemoval(selectionStore.pendingRemoval().first())' in google, \
    'Google account resume must continue the next automatic removal when no request is in flight'
assert 'private val recoveryPoll = Runnable' in google and 'override fun onPause()' in google, \
    'Google account recovery poll must be lifecycle-scoped'
assert 'reorderGateway.setGoogleAccountManagementDisabled(true)' in google, \
    'Google account recovery must restore the previous management restriction after recovery completes'

knoxzt = (APP / 'KnoxZtManagerActivity.kt').read_text(encoding='utf-8')
for token in [
    'KnoxZT Framework', 'Trusted HTTPS APK URL', 'Save trusted source',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in knoxzt, token
assert 'isFinishing || isDestroyed' in knoxzt, \
    'KnoxZT async callbacks must ignore Activity teardown'
assert 'return@runOnUiThread' in knoxzt, \
    'KnoxZT lifecycle guard must exit before touching output'

nfc = (APP / 'NfcLabActivity.kt').read_text(encoding='utf-8')
for token in [
    'NFC Lab', 'Enable reader', 'Install replay trace', 'Clear replay',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in nfc, token
assert 'isFinishing || isDestroyed' in nfc, \
    'NFC reader callback must ignore Activity teardown'
assert 'return@runOnUiThread' in nfc, \
    'NFC lifecycle guard must exit before touching output'

policy = (APP / 'PolicyComplianceActivity.kt').read_text(encoding='utf-8')
for token in [
    'Checking enterprise enrollment', 'Enrollment diagnostics',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in policy, token
assert 'isFinishing || isDestroyed' in policy, \
    'Policy compliance async completion must ignore Activity teardown'
assert 'return@runOnUiThread' in policy, \
    'Policy compliance lifecycle guard must exit before rendering enrollment outcome'

settings = (APP / 'SamsungSettingsEditorActivity.kt').read_text(encoding='utf-8')
for token in [
    'Settings Editor', 'Write + verify stability',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in settings, token
assert 'isFinishing || isDestroyed' in settings, \
    'Settings editor async callbacks must ignore Activity teardown'
assert 'return@runOnUiThread' in settings, \
    'Settings editor lifecycle guard must exit before touching status'
assert 'private var activeStabilityMonitor: AutoCloseable?' in settings, \
    'Settings editor must retain the active stability monitor for lifecycle cleanup'
assert 'override fun onDestroy()' in settings and 'activeStabilityMonitor?.close()' in settings, \
    'Settings editor must close the active stability monitor during Activity teardown'

scenario = (APP / 'ScenarioLabActivity.kt').read_text(encoding='utf-8')
for token in [
    'Scenario Recorder / Replay', 'Grant overlay access', 'Start REC overlay', 'Stop overlay',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in scenario, token

print('MODERN_UI_V2_CONTRACT: PASS')
