#!/usr/bin/env python3
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/release/verify_persisted_signing_asset.py'


class PersistedAssetIntegrity(unittest.TestCase):
    def invoke(self, payload, digest):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / 'fixture.enc'
            path.write_bytes(payload)
            return subprocess.run([sys.executable, str(SCRIPT), str(path), digest],
                                  capture_output=True, text=True, timeout=10)

    def test_exact_bytes(self):
        data = b'test-only ciphertext fixture'
        r = self.invoke(data, 'sha256:' + hashlib.sha256(data).hexdigest())
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn('PERSISTED_SIGNING_ASSET_DIGEST_MATCH', r.stdout)

    def test_changed_bytes(self):
        r = self.invoke(b'changed', 'sha256:' + hashlib.sha256(b'original').hexdigest())
        self.assertEqual(r.returncode, 1)
        self.assertIn('PERSISTED_SIGNING_ASSET_DIGEST_MISMATCH', r.stderr)

    def test_invalid_or_missing_metadata(self):
        for value in ('', 'null', 'sha256:bad'):
            with self.subTest(value=value):
                r = self.invoke(b'fixture', value)
                self.assertEqual(r.returncode, 1)
                self.assertIn('PERSISTED_SIGNING_ASSET_DIGEST_INVALID', r.stderr)


if __name__ == '__main__':
    unittest.main()
