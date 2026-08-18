from pathlib import Path
root=Path(__file__).resolve().parents[2]
settings=(root/'settings.gradle.kts').read_text()
app=(root/'app-dpc/build.gradle.kts').read_text()
runner=(root/'tools/run_host_tests.sh').read_text()
for module in ['knox-zt-core','knox-zt-android','scenario-core','scenario-android','nfc-lab-core','nfc-lab-android']:
    assert f'":{module}"' in settings, f'settings {module}'
    assert f'project(":{module}")' in app, f'app {module}'
for marker in ['KnoxMockTestKt','DnsPolicyTestKt','KnoxZtRecoveryPlannerTestKt','ScenarioRecorderReplayTestKt','NfcReplayValidatorTestKt','NfcTraceCodecTestKt','test_knoxzt_android_contract.py','test_scenario_android_contract.py','test_nfc_lab_android_contract.py','test_native_trace_contract.py','test_aio_dashboard_contract.py']:
    assert marker in runner, marker
print('test_060_module_integration: PASS')
