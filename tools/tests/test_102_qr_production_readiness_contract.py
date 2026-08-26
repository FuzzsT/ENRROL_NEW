#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def text(rel):
    return (ROOT / rel).read_text('utf-8')

def require(rel, *needles):
    body = text(rel)
    for needle in needles:
        assert needle in body, f"{rel}: missing {needle!r}"

def main():
    require('tools/provisioning/generate_provisioning.py',
            'ENROLLMENT_OFFLINE_MODE', '--offline-mode', '--offline-bundle-id',
            "('ONLINE', 'ONLINE_PREFERRED', 'FULL_OFFLINE', 'OFFLINE_THEN_SYNC')")
    require('apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentConfigParser.kt',
            'KEY_OFFLINE_MODE', 'FULL_OFFLINE', 'OFFLINE_THEN_SYNC')
    require('apps/dpc/modules/core/model/src/main/kotlin/io/dpcaio/core/model/EnrollmentModel.kt',
            'offlineMode:', 'offlineBundleId:')
    require('apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt',
            'EnrollmentExecutionRouter', 'EnrollmentExecutionOutcome.Complete')
    require('apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentExecutionRouter.kt',
            'FULL_OFFLINE', 'OFFLINE_THEN_SYNC', 'OfflineEnrollmentCoordinator')
    require('apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineEnrollmentCoordinator.kt',
            'OFFLINE_BUNDLE_REQUIRED', 'OFFLINE_VERIFIED', 'SYNC_PENDING')
    require('apps/dpc/app/build.gradle.kts',
            'DPC_AIO_RELEASE_KEYSTORE_B64', 'DPC_AIO_RELEASE_STORE_PASSWORD',
            'DPC_AIO_RELEASE_KEY_ALIAS', 'DPC_AIO_RELEASE_KEY_PASSWORD')

    workflow = text('.github/workflows/build-aio-enrollment.yml')
    for needle in ('assembleEnterpriseRelease', 'DPC-AIO-enterprise-release.apk',
                   'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256', 'cmp -s'):
        assert needle in workflow, f"workflow missing {needle!r}"
    assert 'assembleEnterpriseDebug' not in workflow, 'production workflow still builds debug APK'
    assert 'outputs/apk/enterprise/debug' not in workflow, 'production workflow still collects debug APK bytes'
    assert 'apps/dpc/app/build/outputs/apk/enterprise/release' in workflow, 'production workflow must collect enterpriseRelease APK from :app-dpc output directory'
    assert 'apps/dpc/app/build/outputs/provisioning/enterprise/release' in workflow, 'production workflow must collect provisioning files from :app-dpc output directory'
    assert 'apps/dpc/build/outputs/apk/enterprise/release' not in workflow, 'production workflow uses obsolete root-level APK output path'
    assert 'apps/dpc/build/outputs/provisioning/enterprise/release' not in workflow, 'production workflow uses obsolete root-level provisioning output path'

    emergency = text('.github/workflows/build-emergency-enrollment.yml')
    for needle in (
        'build-tools;36.0.0',
        '$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner',
        'verify --verbose --print-certs "$apk" 2>&1',
        'apps/dpc/app/build/outputs/apk/enterprise/release',
        'DPC-AIO-enterprise-release.apk',
    ):
        assert needle in emergency, f"emergency workflow missing {needle!r}"
    assert 'find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner' not in emergency, 'emergency workflow must not auto-select preview apksigner'

    print('test_102_qr_production_readiness_contract: PASS')

if __name__ == '__main__':
    main()
