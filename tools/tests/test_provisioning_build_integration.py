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

    workflow = must('.github/workflows/build-aio-enrollment.yml', 'Build signed enterpriseRelease')
    if 'releases/latest/download/DPC-AIO-enterprise-debug.apk' in workflow:
        raise AssertionError('manual provisioning must not depend on a pre-existing latest release asset')
    if 'actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09' not in workflow:
        raise AssertionError('workflow must use immutable checkout v5.1.0 pin on the Node 24 runner')
    for token in [
        'actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95',
        'android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd',
        'ANDROID_CLI="$(command -v android)"',
        '"$ANDROID_CLI" sdk list --all --all-versions --canary',
        '"$ANDROID_CLI" sdk install --canary',
        "awk '$1 ~ /^platforms\\/android-37\\.[0-9]+$/ { print $1 }'",
        'sort -V | tail -n1',
        'platforms/android-CinnamonBun',
        'build-tools/36.0.0',
        'ndk/28.2.13676358',
        'cmake/3.22.1',
        'test -e "$installed_path/android.jar"',
        'test -d "$ANDROID_SDK_ROOT/ndk/28.2.13676358"',
        'test -x "$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake"',
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
        'actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9',
        'Align cmdline-tools latest for emulator runner',
        'cmdline_tools_current="$ANDROID_SDK_ROOT/cmdline-tools/22.0"',
        'cmdline_tools_latest="$ANDROID_SDK_ROOT/cmdline-tools/latest"',
        'rm -rf "$cmdline_tools_latest"',
        'ln -s "$cmdline_tools_current" "$cmdline_tools_latest"',
        "grep -q '^Pkg.Revision=22.0$' \"$cmdline_tools_latest/source.properties\"",
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

    install_step = workflow[workflow.index('- name: Install Android 17 SDK + native toolchain'):workflow.index('- name: Set up Python QR dependencies')]
    if 'SDKMANAGER=' in install_step or '--channel=3' in install_step:
        raise AssertionError('release Android 17 install step must use Android SDK CLI instead of sdkmanager channel flags')
    if 'if has_package "platforms/android-37"' in install_step:
        raise AssertionError('Android CLI exposes versioned Android 17 platform paths; do not require nonexistent platforms/android-37 alias')

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

    refs = must('docs/upstream/TESTDPC-PROVISIONING.md', 'googlesamples/android-testdpc')
    for token in ['PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME', 'PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM']:
        if token not in refs:
            raise AssertionError(f'upstream doc missing {token}')

    print('test_provisioning_build_integration: PASS')


if __name__ == '__main__':
    main()
