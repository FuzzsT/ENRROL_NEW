#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="${TMPDIR:-/tmp}/dpc-aio-tests"
rm -rf "$TMP" && mkdir -p "$TMP/classes"

mapfile -t SOURCES < <(
  find \
    "$ROOT/core-model/src/main/kotlin" \
    "$ROOT/core-execution/src/main/kotlin" "$ROOT/core-execution/src/test/kotlin" \
    "$ROOT/policy-core/src/main/kotlin" "$ROOT/policy-core/src/test/kotlin" \
    "$ROOT/permission-manager/src/main/kotlin" "$ROOT/permission-manager/src/test/kotlin" \
    "$ROOT/samsung-settings/src/main/kotlin" "$ROOT/samsung-settings/src/test/kotlin" \
    "$ROOT/account-manager/src/main/kotlin" "$ROOT/account-manager/src/test/kotlin" \
    "$ROOT/app-manager/src/main/kotlin" "$ROOT/app-manager/src/test/kotlin" \
    "$ROOT/platform-compat/src/main/kotlin" "$ROOT/platform-compat/src/test/kotlin" \
    "$ROOT/activity-launcher/src/main/kotlin" "$ROOT/activity-launcher/src/test/kotlin" \
    "$ROOT/delegation-core/src/main/kotlin" "$ROOT/delegation-core/src/test/kotlin" \
    "$ROOT/installer-core/src/main/kotlin" "$ROOT/installer-core/src/test/kotlin" \
    "$ROOT/knox-license-core/src/main/kotlin" "$ROOT/knox-license-core/src/test/kotlin" \
    "$ROOT/knox-license-lab/src/main/kotlin" "$ROOT/knox-license-lab/src/test/kotlin" \
    "$ROOT/knox-mock-core/src/main/kotlin" "$ROOT/knox-mock-core/src/test/kotlin" \
    "$ROOT/network-control/src/main/kotlin" "$ROOT/network-control/src/test/kotlin" \
    "$ROOT/knox-zt-core/src/main/kotlin" "$ROOT/knox-zt-core/src/test/kotlin" \
    "$ROOT/scenario-core/src/main/kotlin" "$ROOT/scenario-core/src/test/kotlin" \
    "$ROOT/nfc-lab-core/src/main/kotlin" "$ROOT/nfc-lab-core/src/test/kotlin" \
    -type f -name '*.kt' ! -path '*/android/*' | sort
)

kotlinc "${SOURCES[@]}" -d "$TMP/classes"

for MAIN in \
  io.dpcaio.execution.ExecutionPlannerTestKt \
  io.dpcaio.policy.PolicyResultTestKt \
  io.dpcaio.permission.EffectiveCapabilityResolverTestKt \
  io.dpcaio.permission.PermissionActionPlannerTestKt \
  io.dpcaio.permission.PermissionPolicyCoordinatorTestKt \
  io.dpcaio.permission.PermissionCatalogAndGrantPlannerTestKt \
  io.dpcaio.account.AccountPriorityPlannerTestKt \
  io.dpcaio.account.AccountReorderCoordinatorTestKt \
  io.dpcaio.appmanager.AppPolicyCoordinatorTestKt \
  io.dpcaio.appmanager.AppInventoryFilterTestKt \
  io.dpcaio.platform.CompatibilityGateTestKt \
  io.dpcaio.activity.ActivityAccessPlannerTestKt \
  io.dpcaio.activity.ActivityLaunchCoordinatorTestKt \
  io.dpcaio.activity.ActivityExecutorRouterTestKt \
  io.dpcaio.samsung.settings.SamsungSettingEditCoordinatorTestKt \
  io.dpcaio.samsung.settings.SamsungSettingRoutePlannerTestKt \
  io.dpcaio.delegation.DelegationAuthorizerTestKt \
  io.dpcaio.delegation.DelegationBrokerTestKt \
  io.dpcaio.installer.InstallSourceClassifierTestKt \
  io.dpcaio.installer.InstallPlannerTestKt \
  io.dpcaio.knox.license.lab.KnoxLabLicenseVerifierTestKt \
  io.dpcaio.knox.license.KnoxActivationPlannerTestKt \
  io.dpcaio.knox.license.KnoxStartupGateTestKt \
  io.dpcaio.knox.license.KnoxPackageControlPlannerTestKt \
  io.dpcaio.knox.license.KnoxRuntimeAccessPolicyTestKt \
  io.dpcaio.knox.mock.KnoxMockTestKt \
  io.dpcaio.network.DnsPolicyTestKt \
  io.dpcaio.knoxzt.KnoxZtRecoveryPlannerTestKt \
  io.dpcaio.scenario.ScenarioRecorderReplayTestKt \
  io.dpcaio.nfc.NfcReplayValidatorTestKt \
  io.dpcaio.nfc.NfcTraceCodecTestKt
 do
  kotlin -classpath "$TMP/classes" "$MAIN"
 done

python3 "$ROOT/tools/tests/test_android_contracts.py"
python3 "$ROOT/tools/tests/test_account_android_contract.py"
python3 "$ROOT/tools/tests/test_shizuku_permission_settings_contract.py"
python3 "$ROOT/tools/tests/test_samsung_settings_contract.py"
python3 "$ROOT/tools/tests/test_permission_catalog_android_contract.py"
python3 "$ROOT/tools/tests/test_lab_hook_activity_contract.py"
python3 "$ROOT/tools/tests/test_dhizuku_contract.py"
python3 "$ROOT/tools/tests/test_shizuku_contract.py"
python3 "$ROOT/tools/tests/test_provisioning_android_contract.py"
python3 "$ROOT/tools/tests/test_build_provisioning_qr.py"
python3 "$ROOT/tools/tests/test_provisioning_build_integration.py"
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
python3 "$ROOT/tools/tests/test_knox_lab_token_bundle.py"
node --test "$ROOT/provisioning-server/test"/*.test.mjs
python3 "$ROOT/tools/tests/test_release_gate.py"
python3 "$ROOT/tools/tests/test_no_policy_gates.py"
python3 "$ROOT/tools/verify_android_contracts.py"
python3 "$ROOT/tools/verify_project.py"
python3 "$ROOT/tools/release_gate.py"
echo "HOST_TEST_SUITE: PASS"
