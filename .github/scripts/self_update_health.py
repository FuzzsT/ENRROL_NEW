"""Read-only public Self Update manifest checks; not an APK signature audit."""
import json
import re
import subprocess
import tempfile
from pathlib import Path

REPO = 'FuzzsT/ENRROL_NEW'
CHANNELS = [('stable', 'dpc-aio-stable', True), ('continuous', 'dpc-aio-continuous', True), ('emergency', 'dpc-aio-emergency-enrollment', False), ('continuous-v2', 'dpc-aio-continuous-v2', False)]
MAX_BYTES = 65536
BASE = f'https://github.com/{REPO}/releases/download/'


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate field')
        result[key] = value
    return result


def validate(raw, channel, tag):
    if len(raw) > MAX_BYTES:
        raise ValueError('oversized manifest')
    data = json.loads(raw.decode('utf-8'), object_pairs_hook=unique)
    if not isinstance(data, dict):
        raise ValueError('not an object')
    url = data.get('apkUrl')
    valid_url = url == BASE + tag + '/DPC-AIO-enterprise-release.apk'
    if channel == 'stable' and isinstance(url, str):
        valid_url = valid_url or bool(re.fullmatch(re.escape(BASE) + r'v[0-9][A-Za-z0-9._-]*/DPC-AIO-enterprise-release\.apk', url))
    if (type(data.get('schemaVersion')) is not int or data['schemaVersion'] != 1
            or data.get('channel') != channel or data.get('packageName') != 'io.dpcaio.app'
            or type(data.get('versionCode')) is not int or data['versionCode'] <= 0
            or not isinstance(data.get('versionName'), str) or not data['versionName'].strip()
            or type(data.get('apkSize')) is not int or data['apkSize'] <= 0
            or not isinstance(data.get('apkSha256'), str) or not re.fullmatch(r'[0-9a-fA-F]{64}', data['apkSha256'])
            or not valid_url):
        raise ValueError('manifest contract')
    return data['versionCode']


def fetch_public(tag):
    try:
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'manifest.json'
            result = subprocess.run(['curl', '--disable', '--silent', '--show-error', '--location',
                                     '--proto', '=https', '--proto-redir', '=https', '--connect-timeout', '10',
                                     '--max-time', '30', '--max-filesize', str(MAX_BYTES), '--output', str(path),
                                     '--write-out', '%{http_code}', BASE + tag + '/self-update.json'],
                                    capture_output=True, timeout=35, check=False)
            if result.returncode or not re.fullmatch(rb'[0-9]{3}', result.stdout):
                return 0, b''
            status = int(result.stdout)
            if status != 200:
                return status, b''
            if not path.is_file() or path.stat().st_size > MAX_BYTES:
                return 0, b''
            return status, path.read_bytes()
    except (OSError, subprocess.TimeoutExpired):
        return 0, b''


def check(fetch):
    report = []
    for channel, tag, required in CHANNELS:
        code, raw = fetch(tag)
        item = {'channel': channel, 'required': required, 'http': code}
        if code == 404:
            item['status'] = 'REQUIRED_MANIFEST_MISSING' if required else 'OPTIONAL_MANIFEST_MISSING'
        elif code == 0:
            item['status'] = 'TRANSFER_FAILED'
        elif code != 200:
            item['status'] = 'HTTP_ERROR'
        else:
            try:
                item['versionCode'] = validate(raw, channel, tag)
                item['status'] = 'MANIFEST_VALID'
            except (ValueError, TypeError, RecursionError):
                item['status'] = 'MANIFEST_INVALID'
        report.append(item)
    return report, int(any(r['status'] not in ('MANIFEST_VALID', 'OPTIONAL_MANIFEST_MISSING') for r in report))


if __name__ == '__main__':
    report, code = check(fetch_public)
    print(json.dumps({'channels': report, 'apk_signature_verified': False}, indent=2))
    raise SystemExit(code)
