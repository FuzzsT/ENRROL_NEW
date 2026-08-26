from pathlib import Path
root=Path(__file__).resolve().parents[2]
manifest=(root/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
texts='\n'.join(p.read_text(errors='ignore') for p in (root/'apps/dpc/app/src/main/kotlin').rglob('*.kt'))
for cls in ['AioDashboardActivity','ActivityExplorerActivity','NetworkControlActivity','ScenarioLabActivity','NfcLabActivity','KnoxZtManagerActivity']:
    assert cls in texts, cls
    assert f'.{cls}' in manifest, cls
assert manifest.count('android.intent.action.MAIN') == 1
for required in ['AndroidActivityInventory','ActivityLaunchCoordinator','ShizukuActivityRouteExecutor','ScenarioOverlayService','NfcTagInspector','DeviceOwnerPrivateDnsController']:
    assert required in texts, required
print('test_aio_dashboard_contract: PASS')

dashboard=(root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
for token in ['EnterprisePolicyHubActivity', 'DpcDiagnosticsActivity', 'DpcUiPreferences.read', 'developerMode', 'Advanced / Lab']:
    assert token in dashboard, token
scenario_pos=dashboard.index('Scenario Recorder / Replay')
nfc_pos=dashboard.index('NFC Lab')
guard_pos=dashboard.index('developerMode')
assert guard_pos < scenario_pos < nfc_pos, 'lab surfaces must be after developerMode guard'
