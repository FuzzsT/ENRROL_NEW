#!/usr/bin/env python3
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/release/resolve_signing_pin.py'
NAME = 'EXPECTED_V2_CERT'
A = 'AB' * 32
B = 'CD' * 32


class PinSources(unittest.TestCase):
    def run_pin(self, variable='', secret='', allow=False):
        env = dict(os.environ, **{NAME + '_VARIABLE': variable, NAME + '_SECRET': secret})
        args = [sys.executable, str(SCRIPT), NAME]
        if allow:
            args.append('--allow-missing')
        return subprocess.run(args, env=env, capture_output=True, text=True, timeout=10)

    def test_variable_only(self):
        r = self.run_pin(A)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, A))

    def test_secret_only(self):
        r = self.run_pin(secret=B)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, B))

    def test_equivalent_formats(self):
        r = self.run_pin(':'.join(['ab'] * 32), A)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, A))

    def test_conflict_without_disclosing_values(self):
        r = self.run_pin(A, B)
        self.assertEqual(r.returncode, 1)
        self.assertIn('SIGNING_CERT_SOURCE_CONFLICT', r.stderr)
        self.assertEqual(r.stdout, '')
        self.assertNotIn(A, r.stderr)
        self.assertNotIn(B, r.stderr)

    def test_invalid_secondary_source(self):
        r = self.run_pin(A, 'malformed-pin')
        self.assertEqual(r.returncode, 1)
        self.assertIn('SIGNING_CERT_PIN_INVALID', r.stderr)
        self.assertNotIn('malformed-pin', r.stderr)

    def test_required_missing(self):
        r = self.run_pin()
        self.assertEqual(r.returncode, 1)
        self.assertIn('SIGNING_CERT_PIN_MISSING', r.stderr)

    def test_optional_missing(self):
        r = self.run_pin(allow=True)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, ''))

    def test_workflow_wiring(self):
        w = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text()
        m = (ROOT / '.github/workflows/migration-signing.yml').read_text()
        for source, name in [(w, 'DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256'),
                             (w, 'DPC_AIO_CONFIGURED_SIGNING_CERT_SHA256'),
                             (w, 'EXPECTED_V2_CERT'),
                             (m, 'OLD_CERT_SHA256'), (m, 'NEW_CERT_SHA256')]:
            self.assertIn(name + '_VARIABLE:', source)
            self.assertIn(name + '_SECRET:', source)
            self.assertIn('resolve_signing_pin.py ' + name, source)


if __name__ == '__main__':
    unittest.main()
