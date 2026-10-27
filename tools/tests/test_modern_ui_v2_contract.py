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

knoxzt = (APP / 'KnoxZtManagerActivity.kt').read_text(encoding='utf-8')
for token in [
    'KnoxZT Framework', 'Trusted HTTPS APK URL', 'Save trusted source',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in knoxzt, token

nfc = (APP / 'NfcLabActivity.kt').read_text(encoding='utf-8')
for token in [
    'NFC Lab', 'Enable reader', 'Install replay trace', 'Clear replay',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in nfc, token

policy = (APP / 'PolicyComplianceActivity.kt').read_text(encoding='utf-8')
for token in [
    'Checking enterprise enrollment', 'Enrollment diagnostics',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.action', 'ModernUi.scrollPage',
]:
    assert token in policy, token

settings = (APP / 'SamsungSettingsEditorActivity.kt').read_text(encoding='utf-8')
for token in [
    'Settings Editor', 'Write + verify stability',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.field', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in settings, token

scenario = (APP / 'ScenarioLabActivity.kt').read_text(encoding='utf-8')
for token in [
    'Scenario Recorder / Replay', 'Grant overlay access', 'Start REC overlay', 'Stop overlay',
    'ModernUi.hero', 'ModernUi.card', 'ModernUi.action', 'ModernUi.outputCard', 'ModernUi.scrollPage',
]:
    assert token in scenario, token

print('MODERN_UI_V2_CONTRACT: PASS')
