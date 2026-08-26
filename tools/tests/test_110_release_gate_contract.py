#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_TESTS = [
    'test_102_qr_production_readiness_contract.py',
    'test_protected_targets_110_contract.py',
    'test_protected_automation_110_contract.py',
    'test_knox_official_110_contract.py',
    'test_sem_110_contract.py',
    'test_oem_internals_110_safety.py',
    'test_samsung_enterprise_center_110_ui.py',
    'test_package_trust_110_android_contract.py',
    'test_apk_plus_110_contract.py',
    'test_work_profile_lifecycle_110_contract.py',
    'test_credential_recovery_110_contract.py',
    'test_app_management_location_110_contract.py',
    'test_110_verification_report.py',
    'test_non_sdk_api_scan.py',
    'test_release_secret_scan.py',
]

REQUIRED_CORE = [
    'apps/dpc/modules/enterprise-protection/core/src/main/kotlin/io/dpcaio/protection/ProtectionPlanner.kt',
    'apps/dpc/modules/core/execution/src/main/kotlin/io/dpcaio/execution/EnterpriseTransactionEngine.kt',
    'apps/dpc/modules/core/execution/src/main/kotlin/io/dpcaio/execution/EnterpriseCapabilityRouter.kt',
    'apps/dpc/modules/knox/official/core/src/main/kotlin/io/dpcaio/knox/official/KnoxApiDescriptor.kt',
    'apps/dpc/modules/samsung/core/src/main/kotlin/io/dpcaio/samsung/sem/SemCapabilityCatalog.kt',
    'apps/dpc/modules/oem-internals/core/src/main/kotlin/io/dpcaio/oem/OemInternalModels.kt',
    'apps/dpc/modules/installer/core/src/main/kotlin/io/dpcaio/installer/PackageTrust.kt',
    'apps/dpc/modules/installer/core/src/main/kotlin/io/dpcaio/installer/ApkPlusArchivePlanner.kt',
    'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/WorkProfileLifecycle.kt',
    'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/CredentialRecovery.kt',
    'apps/dpc/modules/app-management/core/src/main/kotlin/io/dpcaio/appmanager/WholeAppState.kt',
]

def read(rel: str) -> str:
    return (ROOT / rel).read_text('utf-8', errors='strict')

def main() -> None:
    for rel in REQUIRED_CORE:
        if not (ROOT / rel).is_file():
            raise AssertionError(f'missing 1.1.0 source contract file: {rel}')

    host = read('tools/run_host_tests.sh')
    for test in REQUIRED_TESTS:
        if test not in host:
            raise AssertionError(f'host regression does not require {test}')

    gradle = read('apps/dpc/app/build.gradle.kts')
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
    if not version_name or not re.fullmatch(r'1\.1\.\d+', version_name.group(1)):
        raise AssertionError(f'1.1.0 compatibility gate requires versionName in 1.1.x, got {version_name.group(1) if version_name else None}')
    if not version_code or int(version_code.group(1)) < 21:
        raise AssertionError(f'1.1.0 release gate requires versionCode>=21, got {version_code.group(1) if version_code else None}')

    workflow = read('.github/workflows/build-aio-enrollment.yml')
    for marker in [
        'assembleEnterpriseRelease',
        'DPC-AIO-enterprise-release.apk',
        'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
        'cmp -s',
    ]:
        if marker not in workflow:
            raise AssertionError(f'1.0.2 production prerequisite lost: {marker}')

    print('test_110_release_gate_contract: PASS')

if __name__ == '__main__':
    main()
