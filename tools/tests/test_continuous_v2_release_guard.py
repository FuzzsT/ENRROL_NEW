import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'release/check_continuous_v2_release.py'
TAG = 'dpc-aio-continuous-v2'
APK = 'DPC-AIO-enterprise-release.apk'
URL = 'https://github.com/FuzzsT/ENRROL_NEW/releases/download/' + TAG + '/' + APK
PIN = 'A' * 64


def sha(data):
    return hashlib.sha256(data).hexdigest()


class ReleaseGuardTest(unittest.TestCase):
    def run_case(self, *, first=False, version=101, data=b'new', old_pin=PIN,
                 tag=TAG, candidate_url=URL, bad_digest=False, old_digest_bad=False,
                 expected_pin=PIN, omit_asset=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            dist = root / 'dist'
            dist.mkdir()
            (dist / APK).write_bytes(data)
            (dist / 'signing-cert-sha256.txt').write_text(PIN + '\n')
            (dist / 'signing-mode.txt').write_text('stable\n')
            def manifest(code, payload, url=URL):
                return {'schemaVersion': 1, 'channel': 'continuous-v2', 'packageName': 'io.dpcaio.app',
                        'versionCode': code, 'apkSha256': sha(payload), 'apkSize': len(payload), 'apkUrl': url}
            candidate = manifest(version, b'wrong' if bad_digest else data, candidate_url)
            (dist / 'self-update.json').write_text(json.dumps(candidate))
            args = [sys.executable, str(SCRIPT), '--dist', str(dist), '--tag', tag, '--expected-pin', expected_pin]
            if first:
                args += ['--first-release']
            else:
                prior = root / 'prior'
                prior.mkdir()
                old = json.dumps(manifest(100, b'old')).encode()
                cert = (old_pin + '\n').encode()
                (prior / 'self-update.json').write_bytes(old)
                (prior / 'signing-cert-sha256.txt').write_bytes(cert)
                assets = [{'name': APK, 'digest': 'sha256:' + sha(b'bad' if old_digest_bad else b'old')},
                          {'name': 'self-update.json', 'digest': 'sha256:' + sha(old)},
                          {'name': 'signing-cert-sha256.txt', 'digest': 'sha256:' + sha(cert)}]
                if omit_asset:
                    assets.pop()
                (prior / 'release.json').write_text(json.dumps({'tag_name': TAG, 'assets': assets}))
                args += ['--prior', str(prior)]
            return subprocess.run(args, capture_output=True, text=True)

    def test_first_and_next(self):
        for kwargs in ({'first': True}, {}, {'version': 100, 'data': b'old'}):
            result = self.run_case(**kwargs)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('CONTINUOUS_V2_RELEASE_GUARD_OK', result.stdout)

    def test_rejections(self):
        cases = [({'version': 99}, 'ROLLBACK'), ({'version': 100}, 'VERSION_REUSE'),
                 ({'old_pin': 'B' * 64}, 'SIGNER_CHANGED'), ({'tag': 'dpc-aio-continuous'}, 'ROUTE_INVALID'),
                 ({'candidate_url': URL.replace(TAG, 'dpc-aio-continuous')}, 'MANIFEST_INVALID'),
                 ({'bad_digest': True}, 'CANDIDATE_DIGEST_MISMATCH'), ({'old_digest_bad': True}, 'PRIOR_DIGEST_MISMATCH'),
                 ({'expected_pin': 'B' * 64}, 'SIGNER_MISMATCH'), ({'omit_asset': True}, 'PRIOR_DIGEST_MISMATCH')]
        for kwargs, marker in cases:
            with self.subTest(marker=marker):
                result = self.run_case(**kwargs)
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn('CONTINUOUS_V2_' + marker, result.stderr)


if __name__ == '__main__':
    unittest.main()
