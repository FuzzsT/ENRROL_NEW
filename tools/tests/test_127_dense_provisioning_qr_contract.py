#!/usr/bin/env python3
import json
from pathlib import Path
import subprocess
import sys
import tempfile

import cv2
import qrcode

ROOT = Path(__file__).resolve().parents[2]
GEN = ROOT / "tools" / "provisioning" / "generate_provisioning.py"
VERIFY = ROOT / "tools" / "provisioning" / "verify_provisioning_qr.py"
CANONICAL_URL = "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/DPC-AIO-enterprise-release.apk"


def classic_decode(path: Path) -> str:
    image = cv2.imread(str(path))
    assert image is not None
    decoded, points, _ = cv2.QRCodeDetector().detectAndDecode(image)
    assert points is not None and decoded, f"classic OpenCV must decode dense provisioning QR: {path}"
    return decoded


def main() -> None:
    with tempfile.TemporaryDirectory() as td_raw:
        td = Path(td_raw)
        apk = td / "DPC-AIO-enterprise-release.apk"
        apk.write_bytes(b"dense-provisioning-regression-apk")
        out = td / "out"
        generated = subprocess.run([
            sys.executable, str(GEN),
            "--apk", str(apk),
            "--apk-url", CANONICAL_URL,
            "--out-dir", str(out),
            "--checksum-mode", "package",
            "--provisioning-mode", "fully-managed",
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert generated.returncode == 0, generated.stderr

        payload_text = (out / "provisioning-payload.txt").read_text("utf-8").strip()
        assert len(payload_text) > 500, "fixture must remain dense enough to exercise the regression"
        assert json.loads(classic_decode(out / "device-owner-qr.png")) == json.loads(payload_text)

        # Reproduce the legacy border=4 artifact from run 32939065414. The
        # validator must remain able to verify already-published QR assets.
        legacy = td / "legacy-border4.png"
        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=8,
            border=4,
        )
        qr.add_data(payload_text)
        qr.make(fit=True)
        qr.make_image(fill_color="black", back_color="white").save(legacy)

        checked = subprocess.run([
            sys.executable, str(VERIFY),
            "--json", str(out / "provisioning.json"),
            "--qr", str(legacy),
            "--apk", str(apk),
            "--expected-mode", "fully-managed",
            "--expected-apk-url", CANONICAL_URL,
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert checked.returncode == 0, checked.stderr or checked.stdout
        report = json.loads(checked.stdout)
        assert report["ok"] is True
        assert report["qrMatchesPayload"] is True
        assert report["apkChecksumMatches"] is True

    print("PASS: dense provisioning QR contract")


if __name__ == "__main__":
    main()
