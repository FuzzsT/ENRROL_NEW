#!/usr/bin/env python3
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
GEN = ROOT / 'tools' / 'provisioning' / 'generate_provisioning.py'
VERIFY = ROOT / 'tools' / 'provisioning' / 'verify_provisioning_qr.py'
ADMIN_EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'
MODE_KEY = 'io.dpcaio.extra.PROVISIONING_MODE'
ALLOW_OFFLINE = 'android.app.extra.PROVISIONING_ALLOW_OFFLINE'


def run_gen(*args):
    return subprocess.run([sys.executable, str(GEN), *map(str, args)], cwd=ROOT,
                          text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def decode_qr(path: Path) -> dict:
    import cv2
    image = cv2.imread(str(path))
    decoded, points, _ = cv2.QRCodeDetector().detectAndDecode(image)
    assert points is not None and decoded, f'QR must decode: {path}'
    return json.loads(decoded)


def test_default_generator_mode_is_explicit_work_profile():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'default-mode-apk')
        out = td / 'out'
        proc = run_gen('--apk', apk, '--apk-url', 'https://mdm.example.test/dpc.apk',
                       '--out-dir', out, '--checksum-mode', 'package')
        assert proc.returncode == 0, proc.stderr
        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        assert payload[ADMIN_EXTRAS][MODE_KEY] == 'work-profile'
        assert (out / 'work-profile-qr.png').is_file()


def test_fully_managed_uses_device_owner_filename_and_validator():
    assert VERIFY.is_file(), 'general provisioning validator must exist'
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'device-owner-apk')
        out = td / 'out'
        proc = run_gen('--apk', apk, '--apk-url', 'https://mdm.example.test/dpc.apk',
                       '--out-dir', out, '--checksum-mode', 'package',
                       '--provisioning-mode', 'fully-managed')
        assert proc.returncode == 0, proc.stderr
        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        qr = out / 'device-owner-qr.png'
        assert qr.is_file(), 'fully-managed mode must emit device-owner-qr.png'
        assert decode_qr(qr) == payload

        checked = subprocess.run([
            sys.executable, str(VERIFY), '--json', str(out / 'provisioning.json'),
            '--qr', str(qr), '--apk', str(apk), '--expected-mode', 'fully-managed',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert checked.returncode == 0, checked.stderr or checked.stdout
        report = json.loads(checked.stdout)
        assert report['ok'] is True
        assert report['provisioningMode'] == 'fully-managed'
        assert report['qrMatchesPayload'] is True
        assert report['apkChecksumMatches'] is True

        rejected = subprocess.run([
            sys.executable, str(VERIFY), '--json', str(out / 'provisioning.json'),
            '--apk', str(apk), '--expected-mode', 'work-profile',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert rejected.returncode != 0, 'validator must reject a mode mismatch'



def test_validator_rejects_unexpected_apk_url():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'url-bound-apk')
        out = td / 'out'
        proc = run_gen('--apk', apk, '--apk-url', 'https://release.example.test/DPC-AIO-enterprise-debug.apk',
                       '--out-dir', out, '--checksum-mode', 'package',
                       '--provisioning-mode', 'fully-managed')
        assert proc.returncode == 0, proc.stderr

        accepted = subprocess.run([
            sys.executable, str(VERIFY), '--json', str(out / 'provisioning.json'),
            '--apk', str(apk), '--expected-mode', 'fully-managed',
            '--expected-apk-url', 'https://release.example.test/DPC-AIO-enterprise-debug.apk',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert accepted.returncode == 0, accepted.stderr or accepted.stdout
        accepted_report = json.loads(accepted.stdout)
        assert accepted_report['ok'] is True

        checked = subprocess.run([
            sys.executable, str(VERIFY), '--json', str(out / 'provisioning.json'),
            '--apk', str(apk), '--expected-mode', 'fully-managed',
            '--expected-apk-url', 'https://wrong.example.test/DPC-AIO-enterprise-debug.apk',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert checked.returncode != 0, 'validator must reject a payload bound to a different APK URL'
        assert checked.stdout.strip(), f'verifier must emit JSON for expected URL validation; stderr={checked.stderr!r}'
        report = json.loads(checked.stdout)
        assert any('APK download URL mismatch' in error for error in report['errors'])

def test_allow_offline_remains_a_top_level_provisioning_extra():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'offline-apk')
        out = td / 'out'
        proc = run_gen('--apk', apk, '--apk-url', 'https://mdm.example.test/dpc.apk',
                       '--out-dir', out, '--checksum-mode', 'package',
                       '--provisioning-mode', 'work-profile', '--allow-offline')
        assert proc.returncode == 0, proc.stderr
        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        assert payload[ALLOW_OFFLINE] is True
        assert ALLOW_OFFLINE not in payload[ADMIN_EXTRAS]


if __name__ == '__main__':
    test_default_generator_mode_is_explicit_work_profile()
    test_fully_managed_uses_device_owner_filename_and_validator()
    test_validator_rejects_unexpected_apk_url()
    test_allow_offline_remains_a_top_level_provisioning_extra()
    print('test_dual_provisioning_qr: PASS')
