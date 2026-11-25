#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / 'tools/release/build_self_update_manifest.py'
BASE = 'https://github.com/FuzzsT/ENRROL_NEW/releases/download/'
NAME = 'DPC-AIO-enterprise-release.apk'

class ContinuousV2ManifestTest(unittest.TestCase):
    def generate(self, channel, tag, base=BASE):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / NAME
            apk.write_bytes(b'test-only APK fixture')
            out = Path(td) / 'self-update.json'
            url = base + tag + '/' + NAME
            result = subprocess.run([sys.executable, str(SCRIPT), '--apk', str(apk), '--out', str(out), '--channel', channel, '--package', 'io.dpcaio.app', '--version-name', '1.4.0', '--version-code', '100', '--apk-url', url, '--published-at', '2026-11-25T00:00:00Z', '--release-notes-url', 'https://github.com/FuzzsT/ENRROL_NEW/releases/tag/' + tag], capture_output=True, text=True)
            return result, json.loads(out.read_text()) if out.exists() else None

    def test_v2_uses_separate_tag(self):
        result, manifest = self.generate('CONTINUOUS-V2', 'dpc-aio-continuous-v2')
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(manifest['channel'], 'continuous-v2')
        self.assertEqual(manifest['apkUrl'], BASE + 'dpc-aio-continuous-v2/' + NAME)
        self.assertEqual(manifest['apkSha256'], hashlib.sha256(b'test-only APK fixture').hexdigest())

    def test_v2_rejects_legacy_tags(self):
        for tag in ('dpc-aio-continuous', 'dpc-aio-stable', 'dpc-aio-emergency-enrollment'):
            with self.subTest(tag=tag):
                result, manifest = self.generate('continuous-v2', tag)
                self.assertEqual(result.returncode, 2)
                self.assertIsNone(manifest)
                self.assertIn('CONTINUOUS_V2_APK_URL_MISMATCH', result.stdout)

    def test_legacy_channels_unchanged(self):
        for channel, tag in (('continuous', 'dpc-aio-continuous'), ('stable', 'dpc-aio-stable'), ('emergency', 'dpc-aio-emergency-enrollment')):
            with self.subTest(channel=channel):
                result, manifest = self.generate(channel, tag)
                self.assertEqual(result.returncode, 0, result.stdout)
                self.assertEqual(manifest['channel'], channel)

    def test_v2_rejects_other_repository(self):
        result, manifest = self.generate('continuous-v2', 'dpc-aio-continuous-v2', 'https://github.com/other/repo/releases/download/')
        self.assertEqual(result.returncode, 2)
        self.assertIsNone(manifest)
        self.assertIn('CONTINUOUS_V2_APK_URL_MISMATCH', result.stdout)

if __name__ == '__main__':
    unittest.main()
