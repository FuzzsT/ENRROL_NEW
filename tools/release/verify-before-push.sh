#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

printf '[1/10] Hidden UserHandle API refs...\n'
if grep -RInE --include='*.kt' 'UserHandle\.(myUserId|getUserId)\(' .; then
  echo 'FAIL: hidden UserHandle user-id API reference found.' >&2
  exit 1
fi
echo 'PASS: 0 hidden UserHandle user-id API refs.'

printf '[2/10] AndroidUserId helper...\n'
test -f apps/dpc/modules/platform/compat/src/main/kotlin/io/dpcaio/platform/AndroidUserId.kt
python3 tools/tests/test_public_user_id_contract.py

printf '[3/10] Android contracts...\n'
python3 tools/verify_android_contracts.py

printf '[4/10] Project verifier...\n'
python3 tools/verify_project.py

printf '[5/10] Workflow YAML...\n'
python3 - <<'PY'
from pathlib import Path
import yaml
p=Path('.github/workflows/build-aio-enrollment.yml')
with p.open(encoding='utf-8') as f:
    obj=yaml.safe_load(f)
assert isinstance(obj, dict)
text=p.read_text(encoding='utf-8')
required=['actions/setup-java@v5','android-actions/setup-android@v4','chmod +x gradlew','--list --channel=3']
for s in required:
    assert s in text, s
assert 'yes | sdkmanager --licenses' not in text
print('WORKFLOW_YAML: PASS')
PY

printf '[6/10] Explicit provisioning QR contracts...\n'
python3 tools/tests/test_dual_provisioning_qr.py
python3 tools/tests/test_work_profile_provisioning.py
python3 tools/tests/test_provisioning_build_integration.py
python3 tools/tests/test_enrollment_config_contract.py
python3 tools/tests/test_enrollment_session_store_contract.py
python3 tools/tests/test_enrollment_secret_store_contract.py
python3 tools/tests/test_enrollment_trust_contract.py
python3 tools/tests/test_enrollment_coordinator_contract.py
python3 tools/tests/test_enrollment_build_config_contract.py
python3 tools/tests/test_enrollment_offline_contract.py
python3 tools/tests/test_enrollment_ui_contract.py
python3 tools/tests/test_enrollment_recovery_contract.py
python3 tools/tests/test_enrollment_manual_ui_contract.py
python3 tools/tests/test_enrollment_server_runtime_contract.py
python3 tools/tests/test_offline_android_contract.py
python3 tools/tests/test_offline_bundle_android_verifier_contract.py
python3 tools/tests/test_offline_package_install_contract.py
python3 tools/tests/test_offline_policy_apply_contract.py
python3 tools/tests/test_offline_recovery_contract.py
python3 tools/tests/test_offline_ui_contract.py
python3 tools/tests/test_permission_manager_100_android_contract.py
python3 tools/tests/test_permission_manager_100_ui_contract.py
python3 tools/tests/test_component_manager_android_contract.py
python3 tools/tests/test_component_manager_shizuku_contract.py
python3 tools/tests/test_component_manager_ui_contract.py
python3 tools/tests/test_100_release_gate_contract.py
python3 tools/tests/test_101_build_resolver.py
python3 tools/tests/test_101_build_cli.py
python3 tools/tests/test_101_offline_bundle_builder.py
python3 tools/tests/test_101_offline_bundle_signing.py
python3 tools/tests/test_101_offline_bundle_cli.py
python3 tools/tests/test_101_apk_inspector.py
python3 tools/tests/test_101_device_harness.py
python3 tools/tests/test_101_device_safe_guards.py
python3 tools/tests/test_101_test_target_contract.py
python3 tools/tests/test_101_verification_bridge_contract.py
python3 tools/tests/test_101_release_report.py
python3 tools/tests/test_101_release_gate_contract.py


printf '[7/10] Capability and hidden-mode contracts...\n'
python3 tools/tests/test_management_context_contract.py
python3 tools/tests/test_module_visibility_contract.py
python3 tools/tests/test_enterprise_policy_hub_contract.py
python3 tools/tests/test_diagnostics_contract.py
python3 tools/tests/test_enterprise_operations_ui_contract.py
python3 tools/tests/test_credential_center_contract.py
python3 tools/tests/test_device_lifecycle_ui_contract.py
python3 tools/tests/test_cope_knox_ui_contract.py
python3 tools/tests/test_enterprise_operations_diagnostics_contract.py

printf '[8/10] Documented enterprise API contract...\n'
python3 tools/tests/test_enterprise_policy_android_contract.py
python3 tools/tests/test_enterprise_operations_android_contract.py
python3 tools/tests/test_enterprise_log_store_contract.py
python3 tools/tests/test_credential_android_contract.py
python3 tools/tests/test_device_lifecycle_android_contract.py
python3 tools/tests/test_cope_android_contract.py
python3 tools/tests/test_non_sdk_api_scan.py

printf '[9/10] Release secret scanner self-test...\n'
python3 tools/tests/test_release_secret_scan.py
python3 tools/tests/test_080_release_gate_contract.py
python3 tools/tests/test_090_release_gate_contract.py
python3 tools/tests/test_offline_android_contract.py
python3 tools/tests/test_offline_ui_contract.py
python3 tools/tests/test_offline_recovery_contract.py
python3 tools/tests/test_offline_bundle_android_verifier_contract.py
python3 tools/tests/test_offline_package_install_contract.py
python3 tools/tests/test_offline_policy_apply_contract.py
python3 tools/tests/test_offline_bundle_tool.py
python3 tools/tests/test_permission_manager_100_android_contract.py
python3 tools/tests/test_permission_manager_100_ui_contract.py
python3 tools/tests/test_shizuku_permission_mutation_contract.py
python3 tools/tests/test_component_manager_android_contract.py
python3 tools/tests/test_component_manager_shizuku_contract.py
python3 tools/tests/test_component_manager_ui_contract.py
python3 tools/tests/test_100_release_gate_contract.py

printf '[10/10] Gradle wrapper files...\n'
test -f gradlew
test -f gradle/wrapper/gradle-wrapper.jar
test -f gradle/wrapper/gradle-wrapper.properties
chmod +x gradlew

echo 'VERIFY-BEFORE-PUSH: PASS'
