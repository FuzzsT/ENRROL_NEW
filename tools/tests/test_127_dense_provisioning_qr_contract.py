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


def verify(json_path: Path, qr_path: Path, apk: Path) -> dict:
    checked = subprocess.run([
        sys.executable, str(VERIFY),
        "--json", str(json_path),
        "--qr", str(qr_path),
        "--apk", str(apk),
        "--expected-mode", "fully-managed",
        "--expected-apk-url", CANONICAL_URL,
    ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    assert checked.returncode == 0, checked.stderr or checked.stdout
    report = json.loads(checked.stdout)
    assert report["ok"] is True
    assert report["qrMatchesPayload"] is True
    assert report["apkChecksumMatches"] is True
    return report


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

        generated_qr = out / "device-owner-qr.png"
        generated_image = cv2.imread(str(generated_qr))
        legacy_image = cv2.imread(str(legacy))
        assert generated_image is not None and legacy_image is not None
        # border=6 adds one 8px module per side beyond the old border=4 on
        # both axes: four additional modules total -> +32px width/height.
        assert generated_image.shape[1] == legacy_image.shape[1] + 32
        assert generated_image.shape[0] == legacy_image.shape[0] + 32

        verify(out / "provisioning.json", generated_qr, apk)
        verify(out / "provisioning.json", legacy, apk)

    print("PASS: dense provisioning QR contract")


if __name__ == "__main__":
    main()
