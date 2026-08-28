#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="${TMPDIR:-/tmp}/dpc-aio-tests"
rm -rf "$TMP" && mkdir -p "$TMP/classes"

mapfile -t SOURCES < <(
  find \
    "$ROOT/apps/dpc/modules/core/model/src/main/kotlin" "$ROOT/apps/dpc/modules/core/model/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/core/execution/src/main/kotlin" "$ROOT/apps/dpc/modules/core/execution/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/enterprise-protection/core/src/main/kotlin" "$ROOT/apps/dpc/modules/enterprise-protection/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/policy/core/src/main/kotlin" "$ROOT/apps/dpc/modules/policy/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/permissions/core/src/main/kotlin" "$ROOT/apps/dpc/modules/permissions/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/offline/core/src/main/kotlin" "$ROOT/apps/dpc/modules/offline/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/samsung/core/src/main/kotlin" "$ROOT/apps/dpc/modules/samsung/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/knox/official/core/src/main/kotlin" "$ROOT/apps/dpc/modules/knox/official/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/oem-internals/core/src/main/kotlin" "$ROOT/apps/dpc/modules/oem-internals/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/account/core/src/main/kotlin" "$ROOT/apps/dpc/modules/account/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/app-management/core/src/main/kotlin" "$ROOT/apps/dpc/modules/app-management/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/platform/compat/src/main/kotlin" "$ROOT/apps/dpc/modules/platform/compat/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/activity/core/src/main/kotlin" "$ROOT/apps/dpc/modules/activity/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/delegation/core/src/main/kotlin" "$ROOT/apps/dpc/modules/delegation/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/installer/core/src/main/kotlin" "$ROOT/apps/dpc/modules/installer/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/knox/license/core/src/main/kotlin" "$ROOT/apps/dpc/modules/knox/license/core/src/test/kotlin" \
    "$ROOT/apps/dpc/lab/knox-license/src/main/kotlin" "$ROOT/apps/dpc/lab/knox-license/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/knox/mock/core/src/main/kotlin" "$ROOT/apps/dpc/modules/knox/mock/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/network/core/src/main/kotlin" "$ROOT/apps/dpc/modules/network/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/knox/zero-trust/core/src/main/kotlin" "$ROOT/apps/dpc/modules/knox/zero-trust/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/scenario/core/src/main/kotlin" "$ROOT/apps/dpc/modules/scenario/core/src/test/kotlin" \
    "$ROOT/apps/dpc/modules/nfc-lab/core/src/main/kotlin" "$ROOT/apps/dpc/modules/nfc-lab/core/src/test/kotlin" \
    -type f -name '*.kt' ! -path '*/android/*' | sort
)

kotlinc "${SOURCES[@]}" -d "$TMP/classes"

KOTLIN_TEST_MAINS=(
  io.dpcaio.core.model.CapabilityResolverTestKt
  io.dpcaio.core.model.EnrollmentModelTestKt
  io.dpcaio.core.model.EnrollmentBootstrapTestKt
  io.dpcaio.model.EnterpriseCapabilityTestKt
  io.dpcaio.protection.ProtectionPlannerTestKt
  io.dpcaio.execution.EnterpriseTransactionTestKt
  io.dpcaio.execution.EnterpriseCapabilityRouterTestKt
  io.dpcaio.execution.ExecutionPlannerTestKt
  io.dpcaio.policy.PolicyResultTestKt
  io.dpcaio.policy.EnterprisePolicyModelTestKt
  io.dpcaio.policy.EnterpriseOperationsModelsTestKt
  io.dpcaio.policy.CredentialPolicyModelsTestKt
  io.dpcaio.policy.DeviceLifecycleModelsTestKt
  io.dpcaio.policy.CopePolicyModelsTestKt
  io.dpcaio.policy.WorkProfileLifecycleTestKt
  io.dpcaio.policy.CredentialRecoveryTestKt
  io.dpcaio.permission.EffectiveCapabilityResolverTestKt
  io.dpcaio.permission.PermissionActionPlannerTestKt
  io.dpcaio.permission.PermissionPolicyCoordinatorTestKt
  io.dpcaio.permission.PermissionManagerModelsTestKt
  io.dpcaio.permission.PermissionBatchTransactionTestKt
  io.dpcaio.offline.OfflineReadinessPlannerTestKt
  io.dpcaio.offline.OfflineBundleVerifierTestKt
  io.dpcaio.offline.OfflineSyncReceiptTestKt
  io.dpcaio.offline.OfflinePolicyModelsTestKt
  io.dpcaio.offline.OfflineSchemaCompatibilityTestKt
  io.dpcaio.permission.PermissionCatalogAndGrantPlannerTestKt
  io.dpcaio.account.AccountPriorityPlannerTestKt
  io.dpcaio.account.AccountReorderCoordinatorTestKt
  io.dpcaio.appmanager.AppPolicyCoordinatorTestKt
  io.dpcaio.appmanager.AppInventoryFilterTestKt
  io.dpcaio.appmanager.WholeAppStateTestKt
  io.dpcaio.platform.CompatibilityGateTestKt
  io.dpcaio.platform.AndroidUserIdTestKt
  io.dpcaio.activity.ActivityAccessPlannerTestKt
  io.dpcaio.activity.ActivityLaunchCoordinatorTestKt
  io.dpcaio.activity.ActivityExecutorRouterTestKt
  io.dpcaio.activity.ComponentControlTestKt
  io.dpcaio.activity.ActivityBrowserModelTestKt
  io.dpcaio.offline.OfflineAndroidCompatibilityTestKt
  io.dpcaio.samsung.settings.SamsungSettingEditCoordinatorTestKt
  io.dpcaio.samsung.settings.SamsungSettingRoutePlannerTestKt
  io.dpcaio.delegation.DelegationAuthorizerTestKt
  io.dpcaio.delegation.DelegationBrokerTestKt
  io.dpcaio.installer.InstallSourceClassifierTestKt
  io.dpcaio.installer.InstallPlannerTestKt
  io.dpcaio.installer.PackageTrustTestKt
  io.dpcaio.installer.ApkPlusArchivePlannerTestKt
  io.dpcaio.knox.license.lab.KnoxLabLicenseVerifierTestKt
  io.dpcaio.knox.license.KnoxActivationPlannerTestKt
  io.dpcaio.knox.license.KnoxStartupGateTestKt
  io.dpcaio.knox.license.KnoxPackageControlPlannerTestKt
  io.dpcaio.knox.license.KnoxRuntimeAccessPolicyTestKt
  io.dpcaio.knox.license.KnoxPublicCapabilityTestKt
  io.dpcaio.knox.official.KnoxCapabilityReducerTestKt
  io.dpcaio.samsung.sem.SemCapabilityCatalogTestKt
  io.dpcaio.oem.OemCircuitBreakerTestKt
  io.dpcaio.knox.mock.KnoxMockTestKt
  io.dpcaio.network.DnsPolicyTestKt
  io.dpcaio.knoxzt.KnoxZtRecoveryPlannerTestKt
  io.dpcaio.scenario.ScenarioRecorderReplayTestKt
  io.dpcaio.nfc.NfcReplayValidatorTestKt
  io.dpcaio.nfc.NfcTraceCodecTestKt
)

KOTLIN_EXPR="listOf("
for MAIN in "${KOTLIN_TEST_MAINS[@]}"; do
  KOTLIN_EXPR+="\"${MAIN}\","
done
KOTLIN_EXPR+=").forEach { Class.forName(it).getMethod(\"main\").invoke(null) }"
kotlin -classpath "$TMP/classes" -e "$KOTLIN_EXPR"

python3 "$ROOT/tools/tests/test_project_layout.py"
python3 "$ROOT/tools/tests/test_gradle_source_completeness.py"
python3 "$ROOT/tools/tests/test_android_contracts.py"
python3 "$ROOT/tools/tests/test_public_user_id_contract.py"
python3 "$ROOT/tools/tests/test_account_android_contract.py"
python3 "$ROOT/tools/tests/test_shizuku_permission_settings_contract.py"
python3 "$ROOT/tools/tests/test_samsung_settings_contract.py"
python3 "$ROOT/tools/tests/test_permission_catalog_android_contract.py"
python3 "$ROOT/tools/tests/test_lab_hook_activity_contract.py"
python3 "$ROOT/tools/tests/test_dhizuku_contract.py"
python3 "$ROOT/tools/tests/test_shizuku_contract.py"
python3 "$ROOT/tools/tests/test_provisioning_android_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_config_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_session_store_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_secret_store_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_trust_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_coordinator_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_build_config_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_offline_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_ui_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_recovery_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_manual_ui_contract.py"
python3 "$ROOT/tools/tests/test_offline_android_contract.py"
python3 "$ROOT/tools/tests/test_offline_bundle_android_verifier_contract.py"
python3 "$ROOT/tools/tests/test_offline_package_install_contract.py"
python3 "$ROOT/tools/tests/test_offline_policy_apply_contract.py"
python3 "$ROOT/tools/tests/test_offline_recovery_contract.py"
python3 "$ROOT/tools/tests/test_offline_ui_contract.py"
python3 "$ROOT/tools/tests/test_permission_manager_100_android_contract.py"
python3 "$ROOT/tools/tests/test_permission_manager_100_ui_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_android_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_shizuku_contract.py"
python3 "$ROOT/tools/tests/test_activity_explorer_concurrent_map_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_ui_contract.py"
python3 "$ROOT/tools/tests/test_100_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_101_build_resolver.py"
python3 "$ROOT/tools/tests/test_101_build_cli.py"
python3 "$ROOT/tools/tests/test_101_offline_bundle_builder.py"
python3 "$ROOT/tools/tests/test_101_offline_bundle_signing.py"
python3 "$ROOT/tools/tests/test_101_offline_bundle_cli.py"
python3 "$ROOT/tools/tests/test_101_apk_inspector.py"
python3 "$ROOT/tools/tests/test_101_device_harness.py"
python3 "$ROOT/tools/tests/test_101_device_safe_guards.py"
python3 "$ROOT/tools/tests/test_101_test_target_contract.py"
python3 "$ROOT/tools/tests/test_101_verification_bridge_contract.py"
python3 "$ROOT/tools/tests/test_101_release_report.py"
python3 "$ROOT/tools/tests/test_101_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_102_qr_production_readiness_contract.py"
python3 "$ROOT/tools/tests/test_enrollment_server_runtime_contract.py"
python3 "$ROOT/tools/tests/test_build_provisioning_qr.py"
python3 "$ROOT/tools/tests/test_dual_provisioning_qr.py"
python3 "$ROOT/tools/tests/test_work_profile_provisioning.py"
python3 "$ROOT/tools/tests/test_provisioning_build_integration.py"
python3 "$ROOT/tools/tests/test_123_testdpc_parity_catalog_169.py"
python3 "$ROOT/tools/tests/test_release_version_contract.py"
python3 "$ROOT/tools/tests/test_knox_lab_license_contract.py"
python3 "$ROOT/tools/tests/test_knox_startup_android_contract.py"
python3 "$ROOT/tools/tests/test_knox_fail_open_contract.py"
python3 "$ROOT/tools/tests/test_knoxzt_android_contract.py"
python3 "$ROOT/tools/tests/test_knoxzt_app_integration.py"
python3 "$ROOT/tools/tests/test_scenario_android_contract.py"
python3 "$ROOT/tools/tests/test_nfc_lab_android_contract.py"
python3 "$ROOT/tools/tests/test_native_trace_contract.py"
python3 "$ROOT/tools/tests/test_060_module_integration.py"
python3 "$ROOT/tools/tests/test_aio_dashboard_contract.py"
python3 "$ROOT/tools/tests/test_module_center_contract.py"
python3 "$ROOT/tools/tests/test_app_android_classpath_114_contract.py"
python3 "$ROOT/tools/tests/test_management_context_contract.py"
python3 "$ROOT/tools/tests/test_module_visibility_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_policy_android_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_policy_hub_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_operations_android_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_log_store_contract.py"
python3 "$ROOT/tools/tests/test_credential_android_contract.py"
python3 "$ROOT/tools/tests/test_device_lifecycle_android_contract.py"
python3 "$ROOT/tools/tests/test_cope_android_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_operations_ui_contract.py"
python3 "$ROOT/tools/tests/test_credential_center_contract.py"
python3 "$ROOT/tools/tests/test_device_lifecycle_ui_contract.py"
python3 "$ROOT/tools/tests/test_cope_knox_ui_contract.py"
python3 "$ROOT/tools/tests/test_enterprise_operations_diagnostics_contract.py"
python3 "$ROOT/tools/tests/test_080_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_090_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_offline_android_contract.py"
python3 "$ROOT/tools/tests/test_offline_ui_contract.py"
python3 "$ROOT/tools/tests/test_offline_recovery_contract.py"
python3 "$ROOT/tools/tests/test_offline_bundle_android_verifier_contract.py"
python3 "$ROOT/tools/tests/test_offline_package_install_contract.py"
python3 "$ROOT/tools/tests/test_offline_policy_apply_contract.py"
python3 "$ROOT/tools/tests/test_offline_bundle_tool.py"
python3 "$ROOT/tools/tests/test_permission_manager_100_android_contract.py"
python3 "$ROOT/tools/tests/test_permission_manager_100_ui_contract.py"
python3 "$ROOT/tools/tests/test_shizuku_permission_mutation_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_android_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_shizuku_contract.py"
python3 "$ROOT/tools/tests/test_activity_explorer_concurrent_map_contract.py"
python3 "$ROOT/tools/tests/test_component_manager_ui_contract.py"
python3 "$ROOT/tools/tests/test_100_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_protected_targets_110_contract.py"
python3 "$ROOT/tools/tests/test_protected_automation_110_contract.py"
python3 "$ROOT/tools/tests/test_knox_official_110_contract.py"
python3 "$ROOT/tools/tests/test_sem_110_contract.py"
python3 "$ROOT/tools/tests/test_oem_internals_110_safety.py"
python3 "$ROOT/tools/tests/test_samsung_enterprise_center_110_ui.py"
python3 "$ROOT/tools/tests/test_package_trust_110_android_contract.py"
python3 "$ROOT/tools/tests/test_apk_plus_110_contract.py"
python3 "$ROOT/tools/tests/test_work_profile_lifecycle_110_contract.py"
python3 "$ROOT/tools/tests/test_credential_recovery_110_contract.py"
python3 "$ROOT/tools/tests/test_app_management_location_110_contract.py"
python3 "$ROOT/tools/tests/test_110_verification_report.py"
python3 "$ROOT/tools/tests/test_110_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_diagnostics_contract.py"
python3 "$ROOT/tools/tests/test_non_sdk_api_scan.py"
python3 "$ROOT/tools/tests/test_release_secret_scan.py"
python3 "$ROOT/tools/tests/test_gradle_ci_output_filter.py"
python3 "$ROOT/tools/tests/test_knox_lab_token_bundle.py"
node --test "$ROOT/services/provisioning/test"/*.test.mjs
python3 "$ROOT/tools/tests/test_release_gate.py"
python3 "$ROOT/tools/tests/test_no_policy_gates.py"
python3 "$ROOT/tools/verify_android_contracts.py"
python3 "$ROOT/tools/verify_project.py"
python3 "$ROOT/tools/release_gate.py"
echo "HOST_TEST_SUITE: PASS"

python3 "$ROOT/tools/tests/test_111_build_runtime_readiness.py"

python3 "$ROOT/tools/tests/test_111_release_gate_contract.py"

python3 "$ROOT/tools/tests/test_111_build_preflight_runtime.py"
python3 "$ROOT/tools/tests/test_112_runtime_smoke_parser.py"
python3 "$ROOT/tools/tests/test_112_android_runtime_smoke_contract.py"
python3 "$ROOT/tools/tests/test_112_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_113_github_upload_ready_contract.py"
python3 "$ROOT/tools/tests/test_github_publish_kit_contract.py"
python3 "$ROOT/tools/tests/test_113_release_gate_contract.py"
python3 "$ROOT/tools/tests/test_qr_release_bundle_builder.py"
python3 "$ROOT/tools/tests/test_114_qr_release_bundle_contract.py"

python3 "$ROOT/tools/tests/test_115_app_pin_and_provisioning_modes_contract.py"
python3 "$ROOT/tools/tests/test_116_safe_insets_ui_contract.py"
python3 "$ROOT/tools/tests/test_117_activity_manager_persistence_contract.py"
python3 "$ROOT/tools/tests/test_118_activity_manager_3_contract.py"
python3 "$ROOT/tools/tests/test_119_dashboard_menu_contract.py"
python3 "$ROOT/tools/tests/test_120_device_lifecycle_expanded_controls.py"

python3 tools/tests/test_121_responsive_ui_contract.py

python3 tools/tests/test_122_workflow_qr_choice_contract.py

python3 tools/tests/test_122_qr_mode_matrix.py
python3 "$ROOT/tools/tests/test_126_android_parity_runtime_facts_contract.py"
python3 "$ROOT/tools/tests/test_127_tooling_hygiene_contract.py"
python3 "$ROOT/tools/tests/test_128_android_runtime_smoke_hardening.py"
