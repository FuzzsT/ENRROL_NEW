from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "plugins" / "chatgpt-companion"
MANIFEST = PLUGIN / ".codex-plugin" / "plugin.json"
README = PLUGIN / "README.md"
ENROLL = PLUGIN / "skills" / "dpc-aio-enrollment" / "SKILL.md"
VERIFY = PLUGIN / "skills" / "dpc-aio-verify" / "SKILL.md"

PROJECT_VERSION = "1.1.4"
PLUGIN_VERSION = "0.2.1"


def test_plugin_manifest_targets_current_dpc_release() -> None:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert data["version"] == PLUGIN_VERSION
    text = json.dumps(data, ensure_ascii=False)
    assert f"DPC-AIO {PROJECT_VERSION}" in text
    assert "DPC-AIO 1.1.0" not in text


def test_plugin_readme_and_skills_reference_current_release() -> None:
    readme = README.read_text(encoding="utf-8")
    enroll = ENROLL.read_text(encoding="utf-8")
    verify = VERIFY.read_text(encoding="utf-8")

    assert f"DPC-AIO Companion {PLUGIN_VERSION}" in readme
    assert f"DPC-AIO {PROJECT_VERSION}" in readme
    assert "## 1.1.4 verification coverage" in readme

    assert f"For DPC-AIO {PROJECT_VERSION}" in enroll
    assert "For DPC-AIO 1.1.0" not in enroll

    assert "## DPC-AIO 1.1.4 Enterprise + Samsung OEM coverage" in verify
    assert "test_114_qr_release_bundle_contract.py" in verify
    assert "test_113_release_gate_contract.py" in verify
    assert "test_112_release_gate_contract.py" in verify
