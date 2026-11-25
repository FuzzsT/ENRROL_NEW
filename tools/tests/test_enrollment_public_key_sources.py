#!/usr/bin/env python3
import base64
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/release/resolve_enrollment_public_key.py'
PREFIX = bytes.fromhex('302a300506032b6570032100')
A = base64.b64encode(PREFIX + bytes(range(32))).decode('ascii')
B = base64.b64encode(PREFIX + bytes(range(1, 33))).decode('ascii')


class EnrollmentPublicKeySources(unittest.TestCase):
    def invoke(self, variable='', secret='', manual='', endpoint=''):
        env = dict(os.environ, ENROLLMENT_PUBLIC_KEY_VARIABLE=variable,
                   ENROLLMENT_PUBLIC_KEY_SECRET=secret,
                   ENROLLMENT_PUBLIC_KEY_INPUT=manual,
                   DPC_AIO_ENROLLMENT_ENDPOINT=endpoint)
        return subprocess.run([sys.executable, str(SCRIPT)], env=env,
                              capture_output=True, text=True, timeout=10)

    def test_variable_only(self):
        r = self.invoke(variable=A)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, A))

    def test_secret_only(self):
        r = self.invoke(secret=B)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, B))

    def test_manual_only(self):
        r = self.invoke(manual=A)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, A))

    def test_pem_and_der_equivalent(self):
        pem = '-----BEGIN PUBLIC KEY-----\n' + A + '\n-----END PUBLIC KEY-----'
        r = self.invoke(variable=pem, secret=A, manual=A)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, A))

    def test_conflicting_input_is_rejected(self):
        r = self.invoke(variable=A, manual=B)
        self.assertEqual(r.returncode, 1)
        self.assertIn('ENROLLMENT_PUBLIC_KEY_SOURCE_CONFLICT', r.stderr)
        self.assertEqual(r.stdout, '')
        self.assertNotIn(A, r.stderr)
        self.assertNotIn(B, r.stderr)

    def test_wrong_oid_or_invalid_base64(self):
        wrong = base64.b64encode(bytes.fromhex('302a300506032b656e032100') + bytes(32)).decode()
        for value in (wrong, 'not base64', A + 'AAAA', '-----BEGIN PRIVATE KEY-----\n' + A + '\n-----END PRIVATE KEY-----'):
            with self.subTest(value=value):
                r = self.invoke(variable=value, secret=A)
                self.assertEqual(r.returncode, 1)
                self.assertIn('ENROLLMENT_PUBLIC_KEY_INVALID', r.stderr)
                self.assertEqual(r.stdout, '')

    def test_missing_online_key(self):
        r = self.invoke(endpoint='https://enroll.example.org')
        self.assertEqual(r.returncode, 1)
        self.assertIn('ENROLLMENT_PUBLIC_KEY_MISSING', r.stderr)

    def test_missing_offline_configuration(self):
        r = self.invoke()
        self.assertEqual((r.returncode, r.stdout.strip()), (0, ''))


if __name__ == '__main__':
    unittest.main()
