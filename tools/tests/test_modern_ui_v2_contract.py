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

explorer = (APP / 'ActivityExplorerActivity.kt').read_text(encoding='utf-8')
for token in [
    'Activity Manager 2.0', 'Batch Preview', 'Restore snapshot',
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

print('MODERN_UI_V2_CONTRACT: PASS')
