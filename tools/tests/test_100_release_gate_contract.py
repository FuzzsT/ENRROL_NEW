from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
settings=(ROOT/'settings.gradle.kts').read_text()
app=(ROOT/'apps/dpc/app/build.gradle.kts').read_text()
host=(ROOT/'tools/run_host_tests.sh').read_text()
pre=(ROOT/'tools/release/verify-before-push.sh').read_text()

for needle in [':offline-core', ':offline-android', 'apps/dpc/modules/offline/core', 'apps/dpc/modules/offline/android']:
    assert needle in settings, f'settings missing {needle}'
for needle in ['implementation(project(":offline-core"))','implementation(project(":offline-android"))']:
    assert needle in app, f'app dependency missing {needle}'

kotlin_mains=[
 'io.dpcaio.offline.OfflineReadinessPlannerTestKt',
 'io.dpcaio.offline.OfflineBundleVerifierTestKt',
 'io.dpcaio.offline.OfflineSchemaCompatibilityTestKt',
 'io.dpcaio.offline.OfflineAndroidCompatibilityTestKt',
 'io.dpcaio.offline.OfflinePolicyModelsTestKt',
 'io.dpcaio.offline.OfflineSyncReceiptTestKt',
 'io.dpcaio.permission.PermissionManagerModelsTestKt',
 'io.dpcaio.permission.PermissionBatchTransactionTestKt',
 'io.dpcaio.activity.ComponentControlTestKt',
]
for main in kotlin_mains:
    assert main in host, f'host missing {main}'

contracts=[
 'test_offline_android_contract.py',
 'test_offline_bundle_android_verifier_contract.py',
 'test_offline_package_install_contract.py',
 'test_offline_policy_apply_contract.py',
 'test_offline_recovery_contract.py',
 'test_offline_ui_contract.py',
 'test_permission_manager_100_android_contract.py',
 'test_permission_manager_100_ui_contract.py',
 'test_component_manager_android_contract.py',
 'test_component_manager_shizuku_contract.py',
 'test_component_manager_ui_contract.py',
 'test_100_release_gate_contract.py',
]
for script in (host, pre):
    for name in contracts:
        assert name in script, f'release script missing {name}'
print('DPC_100_RELEASE_GATE_CONTRACT: PASS')
