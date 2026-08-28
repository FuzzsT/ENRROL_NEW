#!/usr/bin/env python3
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
GEN = ROOT / 'tools' / 'provisioning' / 'generate_provisioning.py'
MODE_KEY = 'io.dpcaio.extra.PROVISIONING_MODE'
ADMIN_EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'


def test_generator_emits_explicit_work_profile_mode_and_decodable_qr():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'work-profile-test-apk')
        out = td / 'out'
        proc = subprocess.run([
            sys.executable, str(GEN),
            '--apk', str(apk),
            '--apk-url', 'https://mdm.example.test/dpc.apk',
            '--out-dir', str(out),
            '--checksum-mode', 'package',
            '--provisioning-mode', 'work-profile',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert proc.returncode == 0, proc.stderr

        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        assert payload[ADMIN_EXTRAS][MODE_KEY] == 'work-profile'
        metadata = json.loads((out / 'provisioning-metadata.json').read_text('utf-8'))
        assert metadata['provisioningMode'] == 'work-profile'
        assert (out / 'work-profile-qr.png').is_file()

        import cv2
        image = cv2.imread(str(out / 'work-profile-qr.png'))
        decoded, points, _ = cv2.QRCodeDetector().detectAndDecode(image)
        assert points is not None and decoded, 'generated work-profile QR must be decodable'
        assert json.loads(decoded) == payload


def test_android_mode_selector_prefers_requested_work_profile_not_fully_managed():
    selector = ROOT / 'apps' / 'dpc' / 'app' / 'src' / 'main' / 'kotlin' / 'io' / 'dpcaio' / 'app' / 'ProvisioningModeSelector.kt'
    assert selector.is_file(), 'ProvisioningModeSelector.kt must exist'
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        harness = td / 'Harness.kt'
        harness.write_text(r'''
import io.dpcaio.app.ProvisioningModeSelector

fun main() {
    val fm = 1
    val mp = 2
    check(ProvisioningModeSelector.select("work-profile", listOf(fm, mp), fm, mp) == mp)
    check(ProvisioningModeSelector.select("fully-managed", listOf(fm, mp), fm, mp) == fm)
    check(ProvisioningModeSelector.select("work-profile", listOf(fm), fm, mp) == null)
    check(ProvisioningModeSelector.select("auto", listOf(fm, mp), fm, mp) == fm)
    // TestDPC-compatible fallback: absent/empty allowed modes means both core modes are available.
    check(ProvisioningModeSelector.select("work-profile", emptyList(), fm, mp) == mp)
    check(ProvisioningModeSelector.select("fully-managed", emptyList(), fm, mp) == fm)
    check(ProvisioningModeSelector.select("auto", emptyList(), fm, mp) == fm)
}
''', encoding='utf-8')
        jar = td / 'selector-test.jar'
        compile_proc = subprocess.run([
            'kotlinc', str(selector), str(harness), '-include-runtime', '-d', str(jar)
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert compile_proc.returncode == 0, compile_proc.stderr
        run_proc = subprocess.run(['java', '-jar', str(jar)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert run_proc.returncode == 0, run_proc.stderr


def test_activity_reads_admin_extras_and_receiver_enables_profile():
    activity = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeActivity.kt').read_text('utf-8')
    selector = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeSelector.kt').read_text('utf-8')
    receiver = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDeviceAdminReceiver.kt').read_text('utf-8')
    for token in ['EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE', 'ProvisioningModeSelector.EXTRA_REQUESTED_MODE', 'ProvisioningModeSelector.select']:
        assert token in activity, f'ProvisioningModeActivity missing {token}'
    assert 'io.dpcaio.extra.PROVISIONING_MODE' in selector, 'selector must define the admin-extra key'
    for token in ['DevicePolicyManager', 'setProfileEnabled', 'isProfileOwnerApp']:
        assert token in receiver, f'AioDeviceAdminReceiver missing {token}'


def test_work_profile_validator_accepts_generated_payload_and_rejects_auto_mode():
    validator = ROOT / 'tools' / 'provisioning' / 'verify_work_profile_qr.py'
    assert validator.is_file(), 'verify_work_profile_qr.py must exist'
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'dpc.apk'
        apk.write_bytes(b'validator-test-apk')
        work = td / 'work'
        proc = subprocess.run([
            sys.executable, str(GEN), '--apk', str(apk),
            '--apk-url', 'https://mdm.example.test/dpc.apk', '--out-dir', str(work),
            '--checksum-mode', 'package', '--provisioning-mode', 'work-profile',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert proc.returncode == 0, proc.stderr
        checked = subprocess.run([
            sys.executable, str(validator), '--json', str(work / 'provisioning.json'),
            '--qr', str(work / 'work-profile-qr.png'), '--apk', str(apk),
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert checked.returncode == 0, checked.stderr
        report = json.loads(checked.stdout)
        assert report['ok'] is True
        assert report['provisioningMode'] == 'work-profile'
        assert report['qrMatchesPayload'] is True
        assert report['apkChecksumMatches'] is True

        auto = td / 'auto'
        proc = subprocess.run([
            sys.executable, str(GEN), '--apk', str(apk),
            '--apk-url', 'https://mdm.example.test/dpc.apk', '--out-dir', str(auto),
            '--checksum-mode', 'package', '--provisioning-mode', 'auto',
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert proc.returncode == 0, proc.stderr
        rejected = subprocess.run([
            sys.executable, str(validator), '--json', str(auto / 'provisioning.json'),
            '--apk', str(apk),
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert rejected.returncode != 0, 'validator must reject non-work-profile payloads'


if __name__ == '__main__':
    test_generator_emits_explicit_work_profile_mode_and_decodable_qr()
    test_android_mode_selector_prefers_requested_work_profile_not_fully_managed()
    test_activity_reads_admin_extras_and_receiver_enables_profile()
    test_work_profile_validator_accepts_generated_payload_and_rejects_auto_mode()
    print('test_work_profile_provisioning: PASS')
