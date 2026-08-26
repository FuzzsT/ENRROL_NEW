#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/release/read_apk_signing_cert.py"
SPEC = importlib.util.spec_from_file_location("read_apk_signing_cert", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def fake_apksigner(directory: Path, output: str, status: int = 0) -> Path:
    script = directory / "apksigner"
    script.write_text(f"#!/bin/sh\nprintf '%s\\n' '{output}'\nexit {status}\n", encoding="utf-8")
    script.chmod(0o755)
    return script


def main() -> None:
    digest = "AC1FC99648F38BDB816FAC3046B72F9886BEE2822663AC3F1E89B3A17DB06A1F"
    with tempfile.TemporaryDirectory() as temp:
        directory = Path(temp)
        apk = directory / "app.apk"
        apk.touch()
        signer = fake_apksigner(
            directory,
            "Signer #1 certificate SHA-256 digest: "
            + ":".join(digest[index : index + 2] for index in range(0, len(digest), 2)),
        )
        assert MODULE.read_fingerprint(signer, apk) == digest

        signer = fake_apksigner(directory, "DOES NOT VERIFY", 1)
        try:
            MODULE.read_fingerprint(signer, apk)
        except RuntimeError as error:
            assert "DOES NOT VERIFY" in str(error)
        else:
            raise AssertionError("an apksigner verification failure must fail closed")
    print("READ_APK_SIGNING_CERT: PASS")


if __name__ == "__main__":
    main()
