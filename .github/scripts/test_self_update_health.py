import json
import unittest
from self_update_health import check, CHANNELS, REPO


def manifest(channel, tag):
    return dict(schemaVersion=1, channel=channel, packageName='io.dpcaio.app',
                versionCode=114, versionName='1.1.4', apkSize=42,
                apkSha256='ab'*32,
                apkUrl=f'https://github.com/{REPO}/releases/download/{tag}/DPC-AIO-enterprise-release.apk')


class HealthTests(unittest.TestCase):
    def fake(self, overrides=None):
        overrides = overrides or {}
        def fetch(tag):
            channel = next(c for c, t, _ in CHANNELS if t == tag)
            return overrides.get(channel, (200, json.dumps(manifest(channel, tag)).encode()))
        return fetch

    def test_all_valid(self):
        report, code = check(self.fake())
        self.assertEqual(code, 0)
        self.assertEqual([r['status'] for r in report], ['MANIFEST_VALID']*4)

    def test_required_missing_and_optional_missing(self):
        report, code = check(self.fake({'stable': (404, b''), 'continuous-v2': (404, b'')}))
        self.assertEqual(code, 1)
        self.assertEqual(report[0]['status'], 'REQUIRED_MANIFEST_MISSING')
        self.assertEqual(report[3]['status'], 'OPTIONAL_MANIFEST_MISSING')

    def test_optional_missing_does_not_fail_required_healthy(self):
        report, code = check(self.fake({'emergency': (404, b''), 'continuous-v2': (404, b'')}))
        self.assertEqual(code, 0)
        self.assertEqual(report[2]['status'], 'OPTIONAL_MANIFEST_MISSING')

    def test_invalid_manifest_continues(self):
        report, code = check(self.fake({'stable': (200, b'{')}))
        self.assertEqual(code, 1)
        self.assertEqual(report[0]['status'], 'MANIFEST_INVALID')
        self.assertEqual(report[1]['status'], 'MANIFEST_VALID')
        self.assertEqual(len(report), 4)

    def test_wrong_url_digest_and_boolean_version(self):
        for field, value in [('apkUrl', 'https://example.org/a.apk'), ('apkSha256', 'bad'), ('versionCode', True)]:
            with self.subTest(field=field):
                data = manifest('continuous', 'dpc-aio-continuous')
                data[field] = value
                report, code = check(self.fake({'continuous': (200, json.dumps(data).encode())}))
                self.assertEqual(code, 1)
                self.assertEqual(report[1]['status'], 'MANIFEST_INVALID')

    def test_optional_server_failure_is_not_missing(self):
        report, code = check(self.fake({'continuous-v2': (503, b'')}))
        self.assertEqual(code, 1)
        self.assertEqual(report[3]['status'], 'HTTP_ERROR')

    def test_transfer_failure_is_not_missing(self):
        report, code = check(self.fake({'emergency': (0, b'')}))
        self.assertEqual(code, 1)
        self.assertEqual(report[2]['status'], 'TRANSFER_FAILED')

    def test_duplicate_json_fields(self):
        data = json.dumps(manifest('stable', 'dpc-aio-stable'))[:-1] + ', "channel": "stable"}'
        report, code = check(self.fake({'stable': (200, data.encode())}))
        self.assertEqual(code, 1)
        self.assertEqual(report[0]['status'], 'MANIFEST_INVALID')
