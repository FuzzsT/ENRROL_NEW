#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "tools/release/extract_apksigner_sha256.py"
WORKFLOW = ROOT / ".github/workflows/build-aio-enrollment.yml"

spec = importlib.util.spec_from_file_location("extract_apksigner_sha256", HELPER)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

cases = {
    "Signer #1 certificate SHA-256 digest: AA:BB:CC": "AABBCC",
    "Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: 11:22:aa:ff": "1122AAFF",
    "Signer arbitrary-label certificate SHA-256 digest: deadbeef": "DEADBEEF",
    "V2 Signer: certificate SHA-256 digest: a0f7ec6b742e0725b560be271eb21047c6df2496e3b57cdee57f72c7d60cca5c": "A0F7EC6B742E0725B560BE271EB21047C6DF2496E3B57CDEE57F72C7D60CCA5C",
    "V3 Signer: certificate SHA-256 digest: 00:11:aa:FF": "0011AAFF",
}
for output, expected in cases.items():
    observed = module.extract_signer_sha256(output)
    assert observed == expected, (output, expected, observed)

try:
    module.extract_signer_sha256("Verified using v3 scheme (APK Signature Scheme v3): true")
except ValueError:
    pass
else:
    raise AssertionError("parser must fail closed when no signer SHA-256 digest is present")

workflow = WORKFLOW.read_text(encoding="utf-8")
assert "extract_apksigner_sha256.py" in workflow
assert "sed -n 's/^Signer #1 certificate SHA-256 digest: //p'" not in workflow
assert "cert_output=" in workflow
assert "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256" in workflow
assert "Prepare stable signer" in workflow
assert "Prepare emergency ephemeral signer" in workflow
assert "APKSIGNER_CERT_PARSE_FAILED" in HELPER.read_text(encoding="utf-8")

print("APKSIGNER_CERT_PARSER_114_CONTRACT: PASS")
