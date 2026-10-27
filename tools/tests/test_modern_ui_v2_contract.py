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
]:
    text = (APP / name).read_text(encoding='utf-8')
    assert 'ModernUi.' in text, f'{name}: modern design system not applied'

network = (APP / 'NetworkControlActivity.kt').read_text(encoding='utf-8')
for token in ['Private DNS', 'DNS over HTTPS', 'ModernUi.hero', 'ModernUi.outputCard', 'ModernUi.scrollPage']:
    assert token in network, token

diagnostics = (APP / 'DpcDiagnosticsActivity.kt').read_text(encoding='utf-8')
for token in ['System health', 'Management state', 'Module availability', 'ModernUi.chip', 'ModernUi.card']:
    assert token in diagnostics, token

print('MODERN_UI_V2_CONTRACT: PASS')
