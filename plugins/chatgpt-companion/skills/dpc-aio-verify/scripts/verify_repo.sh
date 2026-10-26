#!/usr/bin/env bash
set -euo pipefail
repo=${1:?usage: verify_repo.sh <repo-root>}
cd "$repo" 2>/dev/null || { echo "repository root not found: $repo" >&2; exit 66; }
test -f gradlew || { echo "missing Gradle wrapper: $repo/gradlew" >&2; exit 66; }
test -f gradle/wrapper/gradle-wrapper.jar || { echo "missing Gradle wrapper JAR" >&2; exit 66; }
test -f gradle/wrapper/gradle-wrapper.properties || { echo "missing Gradle wrapper properties" >&2; exit 66; }
if grep -R -n -E 'UserHandle\.(myUserId|getUserId)\(' --include='*.kt' .; then
  echo "hidden UserHandle user-id API reference found" >&2
  exit 1
fi
[ -f tools/tests/test_project_layout.py ] && python3 tools/tests/test_project_layout.py
[ -f tools/tests/test_module_center_contract.py ] && python3 tools/tests/test_module_center_contract.py
[ -f tools/tests/test_110_release_gate_contract.py ] && python3 tools/tests/test_110_release_gate_contract.py
[ -f tools/tests/test_102_qr_production_readiness_contract.py ] && python3 tools/tests/test_102_qr_production_readiness_contract.py
[ -f tools/tests/test_101_release_gate_contract.py ] && python3 tools/tests/test_101_release_gate_contract.py
[ -f tools/tests/test_100_release_gate_contract.py ] && python3 tools/tests/test_100_release_gate_contract.py
[ -f tools/tests/test_090_release_gate_contract.py ] && python3 tools/tests/test_090_release_gate_contract.py
[ -f tools/tests/test_080_release_gate_contract.py ] && python3 tools/tests/test_080_release_gate_contract.py
[ -f tools/tests/test_non_sdk_api_scan.py ] && python3 tools/tests/test_non_sdk_api_scan.py
[ -f tools/tests/test_release_secret_scan.py ] && python3 tools/tests/test_release_secret_scan.py
[ -f tools/verify_project.py ] && python3 tools/verify_project.py
[ -f tools/verify_android_contracts.py ] && python3 tools/verify_android_contracts.py
[ -f tools/release_gate.py ] && python3 tools/release_gate.py
