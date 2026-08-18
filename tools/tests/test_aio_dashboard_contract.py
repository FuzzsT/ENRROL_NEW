from pathlib import Path
root=Path(__file__).resolve().parents[2]
manifest=(root/'app-dpc/src/main/AndroidManifest.xml').read_text()
texts='\n'.join(p.read_text(errors='ignore') for p in (root/'app-dpc/src/main/kotlin').rglob('*.kt'))
for cls in ['AioDashboardActivity','ActivityExplorerActivity','NetworkControlActivity','ScenarioLabActivity','NfcLabActivity','KnoxZtManagerActivity']:
    assert cls in texts, cls
    assert f'.{cls}' in manifest, cls
assert manifest.count('android.intent.action.MAIN') == 1
for required in ['AndroidActivityInventory','ActivityLaunchCoordinator','ShizukuActivityRouteExecutor','ScenarioOverlayService','NfcTagInspector','DeviceOwnerPrivateDnsController']:
    assert required in texts, required
print('test_aio_dashboard_contract: PASS')
