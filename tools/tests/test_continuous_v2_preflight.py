#!/usr/bin/env python3
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / 'tools/release/check_continuous_v2_preflight.py'
CONFIG = {
    'GITHUB_REPOSITORY': 'FuzzsT/ENRROL_NEW',
    'GITHUB_REF': 'refs/heads/main',
    'GITHUB_EVENT_NAME': 'workflow_dispatch',
    'DPC_AIO_RELEASE_TAG': 'dpc-aio-continuous-v2',
    'DPC_AIO_V2_RELEASE_KEYSTORE_B64': 'fixture-not-a-real-key',
    'DPC_AIO_V2_RELEASE_STORE_PASSWORD': 'fixture-store-password',
    'DPC_AIO_V2_RELEASE_KEY_ALIAS': 'fixture-alias',
    'DPC_AIO_V2_RELEASE_KEY_PASSWORD': 'fixture-key-password',
    'DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256': 'ab' * 32,
}


class V2PreflightTest(unittest.TestCase):
    def invoke(self, changes, expected=None):
        env = dict(CONFIG, **changes)
        result = subprocess.run([sys.executable, str(SCRIPT)], env=env, capture_output=True, text=True)
        if expected:
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn(expected, result.stderr)
        else:
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('CONTINUOUS_V2_PREFLIGHT_CONFIG: PASS', result.stdout)
        for value in CONFIG.values():
            if value.startswith('fixture-'):
                self.assertNotIn(value, result.stdout + result.stderr)

    def test_valid_route(self):
        self.invoke({})

    def test_wrong_route(self):
        for key, value in (
            ('DPC_AIO_RELEASE_TAG', 'dpc-aio-continuous'),
            ('DPC_AIO_RELEASE_TAG', 'dpc-aio-emergency-enrollment'),
            ('DPC_AIO_RELEASE_TAG', 'dpc-aio-stable'),
            ('GITHUB_REF', 'refs/tags/v1.4.0'),
            ('GITHUB_EVENT_NAME', 'push'),
            ('GITHUB_REPOSITORY', 'other/repo'),
        ):
            with self.subTest(key=key, value=value):
                self.invoke({key: value}, 'CONTINUOUS_V2_ROUTE_INVALID')

    def test_missing_credentials(self):
        for name in CONFIG:
            if name.startswith('DPC_AIO_V2_RELEASE_'):
                with self.subTest(name=name):
                    self.invoke({name: ''}, 'CONTINUOUS_V2_SIGNING_CONFIG_MISSING')

    def test_pin_validation(self):
        for pin in ('', 'bad', 'g' * 64):
            with self.subTest(pin=pin):
                self.invoke({'DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256': pin}, 'CONTINUOUS_V2_CERT_PIN_INVALID')

    def test_reject_legacy_pin(self):
        self.invoke({'DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256': 'E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4'}, 'CONTINUOUS_V2_LEGACY_CERT_REJECTED')



class V2BuildWiringTest(unittest.TestCase):
    def test_build_only_route(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / '.github/workflows/build-aio-enrollment.yml').read_text()
        block = workflow.split('if [[ "$mode" == "continuous-v2" ]]; then', 1)[1].split('elif', 1)[0]
        self.assertIn('release_tag="dpc-aio-continuous-v2"', block)
        self.assertIn('should_publish="false"', block)
        self.assertIn('DPC-AIO-continuous-v2-verified-build-only', block)
        self.assertIn('if [[ "$V2_PUBLISH_REQUESTED" == "true" ]]; then', block)
        self.assertLess(block.index('should_publish="false"'), block.index('should_publish="true"'))
        self.assertIn("inputs.signing_mode != 'continuous-v2'", workflow.split('  build:', 1)[0])
        self.assertIn('HAS_V2_REPAIR_REQUEST', workflow)
        signer = workflow.split('      - name: Prepare Continuous v2 signer', 1)[1].split('      - name: Prepare stable signer', 1)[0]
        self.assertIn("env.DPC_AIO_PIPELINE_MODE == 'continuous-v2'", signer)
        self.assertIn('check_continuous_v2_preflight.py', signer)
        self.assertIn('DPC_AIO_STRICT_EXPECTED_SIGNING_CERT: "true"', signer)
        self.assertIn('DPC_AIO_MANUAL_SIGNING_PASSWORD: ""', signer)
        self.assertIn('DPC_AIO_PERSISTED_SIGNING_KEYSTORE_URL: ""', signer)
        self.assertNotIn('secrets.DPC_AIO_RELEASE_', signer)
        self.assertIn('channel="continuous-v2"', workflow)
        self.assertIn('asset_tag="dpc-aio-continuous-v2"', workflow)
        self.assertIn("needs.build.outputs.should_publish == 'true'", workflow.split('  publish:', 1)[1])

    def test_publication_guard_wiring(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / '.github/workflows/build-aio-enrollment.yml').read_text()
        for name in ('publish_continuous_v2', 'first_continuous_v2_release'):
            self.assertIn('default: false', workflow.split('      ' + name + ':', 1)[1].split('        type: boolean', 1)[0])
        guard = workflow.split('      - name: Guard Continuous v2 release progression', 1)[1].split('      - name: Guard continuous release progression', 1)[0]
        for marker in ('check_continuous_v2_release.py', 'CONTINUOUS_V2_FIRST_RELEASE_CONFIRMATION_REQUIRED', 'CONTINUOUS_V2_RELEASE_LOOKUP_FAILED', 'CONTINUOUS_V2_ALREADY_EXISTS', '200)', '404)', '--prior "$guard_dir"', '--first-release'):
            self.assertIn(marker, guard)
        self.assertLess(workflow.index('      - name: Guard Continuous v2 release progression'), workflow.index('      - name: Publish release assets'))
        self.assertIn('expected_channel="continuous-v2"', workflow)
        self.assertIn('V2_RELEASE_EXISTS: ${{ steps.v2_guard.outputs.release_exists }}', workflow)
        self.assertIn("cancel-in-progress: ${{ inputs.signing_mode != 'continuous-v2' }}", workflow)

    def test_v2_default_is_build_specific(self):
        root = Path(__file__).resolve().parents[2]
        gradle = (root / 'apps/dpc/app/build.gradle.kts').read_text()
        activity = (root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt').read_text()
        self.assertIn('environmentVariable("DPC_AIO_CONTINUOUS_V2_BUILD")', gradle)
        self.assertIn('if (BuildConfig.CONTINUOUS_V2_BUILD) UpdateChannel.CONTINUOUS_V2 else UpdateChannel.CONTINUOUS', activity)
        self.assertIn('preferences.getString(KEY_CHANNEL, defaultChannel.name)', activity)


if __name__ == '__main__':
    unittest.main()
