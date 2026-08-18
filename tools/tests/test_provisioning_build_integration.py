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
    gradle = must('app-dpc/build.gradle.kts', 'generateProvisioningQr')
    if 'project.exec' in gradle:
        raise AssertionError('Gradle 9 removed Project.exec; use ProviderFactory.exec')
    for token in [
        'providers.exec',
        'DPC_AIO_PROVISIONING_APK_URL',
        'DPC_AIO_GITHUB_REPOSITORY',
        'generate_provisioning.py',
        'finalizedBy',
        'provisioning-qr.png',
    ]:
        if token not in gradle:
            raise AssertionError(f'app-dpc/build.gradle.kts missing {token}')

    workflow = must('.github/workflows/build-aio-enrollment.yml', 'generate provisioning QR')
    for token in [
        'actions/checkout@v4',
        'actions/setup-java@v4',
        'android-actions/setup-android@v3',
        'platforms;android-37',
        'build-tools;36.0.0',
        'ndk;28.2.13676358',
        'enterpriseDebug',
        'DPC_AIO_PROVISIONING_APK_URL',
        'provisioning-qr.png',
        'provisioning.json',
        'actions/upload-artifact@v4',
    ]:
        if token not in workflow:
            raise AssertionError(f'workflow missing {token}')

    refs = must('docs/upstream/TESTDPC-PROVISIONING.md', 'googlesamples/android-testdpc')
    for token in ['PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME', 'PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM']:
        if token not in refs:
            raise AssertionError(f'upstream doc missing {token}')

    print('test_provisioning_build_integration: PASS')


if __name__ == '__main__':
    main()
