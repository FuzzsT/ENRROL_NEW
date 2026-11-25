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


if __name__ == '__main__':
    unittest.main()
