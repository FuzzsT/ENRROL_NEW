#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def must(path: str, token: str):
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f'missing {path}')
    text = p.read_text('utf-8', errors='ignore')
    if token not in text:
        raise AssertionError(f'{path} missing {token}')
    return text


def main():
    gradle = must('apps/dpc/app/build.gradle.kts', 'generateProvisioningQr')
    if 'project.exec' in gradle:
        raise AssertionError('Gradle 9 removed Project.exec; use ProviderFactory.exec')
    if 'releases/latest/download/' in gradle:
        raise AssertionError('Gradle provisioning fallback must not point at releases/latest')
    for token in [
        'DPC_AIO_CONTINUOUS_RELEASE_TAG',
        'dpc-aio-continuous',
        'flavor == "enterprise" && buildType == "debug"',
        'providers.exec',
        'DPC_AIO_PROVISIONING_APK_URL',
        'DPC_AIO_GITHUB_REPOSITORY',
        'generate_provisioning.py',
        'finalizedBy',
        'provisioning-qr.png',
        'DPC_AIO_PROVISIONING_MODE',
        'DPC_AIO_ALLOW_OFFLINE',
        'DPC_AIO_ENROLLMENT_OFFLINE_MODE',
        'DPC_AIO_OFFLINE_BUNDLE_ID',
        '--allow-offline',
        'work-profile-qr.png',
        'device-owner-qr.png',
        'publishExplicitMode("work-profile"',
        'publishExplicitMode("fully-managed"',
    ]:
        if token not in gradle:
            raise AssertionError(f'apps/dpc/app/build.gradle.kts missing {token}')

    workflow = must('.github/workflows/build-aio-enrollment.yml', 'generate provisioning QR')
    if 'releases/latest/download/DPC-AIO-enterprise-debug.apk' in workflow:
        raise AssertionError('manual provisioning must not depend on a pre-existing latest release asset')
    if 'actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09' not in workflow:
        raise AssertionError('workflow must use immutable checkout v5.1.0 pin on the Node 24 runner')
    for token in [
        'actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95',
        'android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699',
        '--list --channel=3',
        'platforms;android-37',
        'build-tools;36.0.0',
        'ndk;28.2.13676358',
        'enterpriseRelease',
        'DPC_AIO_PROVISIONING_APK_URL',
        'provisioning-qr.png',
        'DPC_AIO_PROVISIONING_MODE',
        'DPC_AIO_ALLOW_OFFLINE',
        'DPC_AIO_ENROLLMENT_OFFLINE_MODE',
        'DPC_AIO_OFFLINE_BUNDLE_ID',
        'allow_offline',
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
        'cmp -s',
        '--expected-apk-url',
        'Verify tag release APK is publicly downloadable',
        "if: github.ref_type == 'tag'",
        'Tag release APK URL is public and byte-identical to this build.',
    ]:
        if token not in workflow:
            raise AssertionError(f'workflow missing {token}')

    publish_tag = workflow.index('- name: Publish tag release assets')
    verify_tag = workflow.index('- name: Verify tag release APK is publicly downloadable')
    if verify_tag <= publish_tag:
        raise AssertionError('tag release byte-identical verification must run after tag asset publication')

    refs = must('docs/upstream/TESTDPC-PROVISIONING.md', 'googlesamples/android-testdpc')
    for token in ['PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME', 'PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM']:
        if token not in refs:
            raise AssertionError(f'upstream doc missing {token}')

    print('test_provisioning_build_integration: PASS')


if __name__ == '__main__':
    main()
