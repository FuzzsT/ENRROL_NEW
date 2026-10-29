#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/build-aio-enrollment.yml'
BUILD = ROOT / 'apps/dpc/app/build.gradle.kts'
GENERATOR = ROOT / 'tools/provisioning/generate_provisioning.py'
VERIFY = ROOT / 'tools/provisioning/verify_provisioning_qr.py'


def main():
    for path in [WORKFLOW, BUILD, GENERATOR, VERIFY]:
        if not path.is_file():
            raise AssertionError(f'missing {path.relative_to(ROOT)}')

    workflow = WORKFLOW.read_text(encoding='utf-8')
    build = BUILD.read_text(encoding='utf-8')

    for token in [
        ':app-dpc:assembleEnterpriseRelease',
        'generateEnterpriseReleaseProvisioningQr',
        'DPC_AIO_PROVISIONING_APK_URL',
        'DPC_AIO_GITHUB_REPOSITORY',
        'DPC_AIO_RELEASE_TAG',
        'DPC_AIO_ENROLLMENT_TOKEN',
        'DPC_AIO_ENROLLMENT_ENDPOINT',
        'DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY',
        'DPC_AIO_POLICY_PROFILE',
        'DPC_AIO_PROVISIONING_MODE',
        'DPC_AIO_ALLOW_OFFLINE',
        'DPC_AIO_ENROLLMENT_OFFLINE_MODE',
        'DPC_AIO_OFFLINE_BUNDLE_ID',
        'DPC_AIO_RELEASE_KEYSTORE_PATH',
        'DPC_AIO_RELEASE_STORE_PASSWORD',
        'DPC_AIO_RELEASE_KEY_ALIAS',
        'DPC_AIO_RELEASE_KEY_PASSWORD',
        'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
        'DPC_AIO_SIGNING_MODE',
        'stable',
        'emergency',
        'ephemeral',
        'android_build_preflight.py',
        '--require-signing',
        'extract_apksigner_sha256.py',
        'signing-mode.txt',
        'signing-cert-sha256.txt',
        'work-profile-qr.png',
        'work-profile-provisioning.json',
        'device-owner-qr.png',
        'device-owner-provisioning.json',
        'device-owner-provisioning-payload.txt',
        'device-owner-provisioning-metadata.json',
        'verify_provisioning_qr.py',
        '--expected-mode work-profile',
        '--expected-mode fully-managed',
        'work-profile-validation.json',
        'device-owner-validation.json',
        '--qr',
        '--apk',
        'actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',
        'dpc-aio-continuous',
        'gh release upload',
        '--clobber',
        'curl -fL',
        '--expected-apk-url',
        'Verify published APK byte identity',
        'Published APK asset(s) are public and byte-identical to the QR-bound build.',
    ]:
        if token not in workflow:
            raise AssertionError(f'workflow missing {token}')

    if not (
        'cmp -s' in workflow
        or (
            'sha256sum dist/DPC-AIO-enterprise-release.apk' in workflow
            and 'sha256sum public.apk' in workflow
            and '[[ "$observed" == "$expected" ]]' in workflow
        )
    ):
        raise AssertionError('workflow missing byte-identity verification (cmp or SHA-256)')

    publish_release = workflow.index('- name: Publish release assets')
    verify_release = workflow.index('- name: Verify published APK byte identity')
    if verify_release <= publish_release:
        raise AssertionError('published APK byte-identical verification must run after asset publication')

    for token in [
        'dpcAioProvisioningApkUrl',
        'DPC_AIO_PROVISIONING_APK_URL',
        'DPC_AIO_GITHUB_REPOSITORY',
        'DPC_AIO_CONTINUOUS_RELEASE_TAG',
        'dpc-aio-continuous',
        'DPC_AIO_ENROLLMENT_TOKEN',
        'DPC_AIO_ENROLLMENT_ENDPOINT',
        'DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY',
        'DPC_AIO_POLICY_PROFILE',
        'DPC_AIO_PROVISIONING_MODE',
        'DPC_AIO_ALLOW_OFFLINE',
        'DPC_AIO_ENROLLMENT_OFFLINE_MODE',
        'DPC_AIO_OFFLINE_BUNDLE_ID',
        'generateEnterpriseReleaseProvisioningQr',
        'work-profile',
        'fully-managed',
    ]:
        if token not in build:
            raise AssertionError(f'build.gradle.kts missing {token}')

    print('test_provisioning_build_integration: PASS')


if __name__ == '__main__':
    main()
