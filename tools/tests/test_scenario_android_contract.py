from pathlib import Path
root=Path(__file__).resolve().parents[2]
module=root/'scenario-android'
assert module.exists(), 'missing scenario-android'
texts='\n'.join(p.read_text(errors='ignore') for p in module.rglob('*') if p.is_file())
for required in ['ActivityLifecycleCallbacks','TYPE_APPLICATION_OVERLAY','Settings.canDrawOverlays','ScenarioArchiveCodec','ScenarioOverlayService','recordIntent','recordBroadcast']:
    assert required in texts, required
print('test_scenario_android_contract: PASS')
