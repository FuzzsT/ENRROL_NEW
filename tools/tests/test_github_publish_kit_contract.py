#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import stat
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

preflight = ROOT / "tools/release/github_publish_preflight.py"
publish = ROOT / "tools/release/publish_to_github.sh"
doc = ROOT / "docs/releases/GITHUB-PUBLISH.md"
secret_example = ROOT / "docs/releases/GITHUB-SECRETS.example"

for path in (preflight, publish, doc, secret_example):
    if not path.is_file():
        errors.append(f"missing {path.relative_to(ROOT)}")

if errors:
    raise SystemExit("GITHUB_PUBLISH_KIT: FAIL\n- " + "\n- ".join(errors))

preflight_text = preflight.read_text("utf-8")
publish_text = publish.read_text("utf-8")
doc_text = doc.read_text("utf-8")
secret_text = secret_example.read_text("utf-8")

for marker in [
    "--repo",
    "--json-out",
    "gh auth status",
    "gh secret list",
    "gh variable list",
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
    "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
]:
    if marker not in preflight_text:
        errors.append(f"preflight missing marker {marker}")

for marker in [
    "set -euo pipefail",
    "--repo",
    "gh auth status",
    "git push -u origin",
    "github_publish_preflight.py",
]:
    if marker not in publish_text:
        errors.append(f"publish helper missing marker {marker}")

for forbidden in ["git push --force", "git push -f", "--force-with-lease", "GH_TOKEN=", "GITHUB_TOKEN="]:
    if forbidden in publish_text:
        errors.append(f"publish helper contains forbidden pattern {forbidden}")

mode = publish.stat().st_mode
if not (mode & stat.S_IXUSR):
    errors.append("publish helper is not executable")

for marker in [
    "DPC_AIO_RELEASE_KEYSTORE_B64",
    "DPC_AIO_RELEASE_STORE_PASSWORD",
    "DPC_AIO_RELEASE_KEY_ALIAS",
    "DPC_AIO_RELEASE_KEY_PASSWORD",
    "DPC_AIO_EXPECTED_SIGNING_CERT_SHA256",
]:
    if marker not in secret_text:
        errors.append(f"secret example missing {marker}")

# Example file must contain names/placeholders only, never secret-looking material.
for suspicious in ["BEGIN PRIVATE KEY", "BEGIN CERTIFICATE", "github_pat_", "ghp_", "sk-proj-"]:
    if suspicious in secret_text:
        errors.append(f"secret example contains sensitive-looking material: {suspicious}")

host_text = (ROOT / "tools/run_host_tests.sh").read_text("utf-8")
if "test_github_publish_kit_contract.py" not in host_text:
    errors.append("host suite missing test_github_publish_kit_contract.py")

release_gate_text = (ROOT / "tools/tests/test_113_release_gate_contract.py").read_text("utf-8")
for marker in [
    "test_github_publish_kit_contract.py",
    "tools/release/github_publish_preflight.py",
    "tools/release/publish_to_github.sh",
]:
    if marker not in release_gate_text:
        errors.append(f"1.1.3 release gate missing publish-kit marker {marker}")

release_report = json.loads((ROOT / "RELEASE-VERIFICATION.json").read_text("utf-8"))
if release_report.get("sourceEvidence", {}).get("githubPublishKit113") != "PASS":
    errors.append("release evidence missing githubPublishKit113=PASS")

for marker in ["GitHub Publish Kit", "workflow_dispatch", "DPC_AIO_RELEASE_KEYSTORE_B64", "android-runtime-smoke.json"]:
    if marker not in doc_text:
        errors.append(f"publish doc missing marker {marker}")

# Behavioral smoke: fake git/gh environment must yield READY without exposing values.
with tempfile.TemporaryDirectory(prefix="dpc-aio-publish-kit-") as td:
    td_path = Path(td)
    repo = td_path / "repo"
    repo.mkdir()
    (repo / ".git").mkdir()
    workflow = repo / ".github" / "workflows" / "build-aio-enrollment.yml"
    workflow.parent.mkdir(parents=True)
    workflow.write_text("name: test\n", "utf-8")
    bin_dir = td_path / "bin"
    bin_dir.mkdir()

    git = bin_dir / "git"
    git.write_text(
        "#!/bin/sh\n"
        "case \"$1 $2\" in\n"
        "  'rev-parse --is-inside-work-tree') echo true;;\n"
        "  'status --porcelain') :;;\n"
        "  'branch --show-current') echo main;;\n"
        "  'remote get-url') echo https://github.com/example/dpc-aio.git;;\n"
        "  *) exit 0;;\n"
        "esac\n",
        "utf-8",
    )
    git.chmod(0o755)

    gh = bin_dir / "gh"
    gh.write_text(
        "#!/bin/sh\n"
        "if [ \"$1 $2\" = 'auth status' ]; then exit 0; fi\n"
        "if [ \"$1 $2\" = 'repo view' ]; then exit 0; fi\n"
        "if [ \"$1 $2\" = 'secret list' ]; then\n"
        "  printf '%s\\n' DPC_AIO_RELEASE_KEYSTORE_B64 DPC_AIO_RELEASE_STORE_PASSWORD DPC_AIO_RELEASE_KEY_ALIAS DPC_AIO_RELEASE_KEY_PASSWORD\n"
        "  exit 0\n"
        "fi\n"
        "if [ \"$1 $2\" = 'variable list' ]; then\n"
        "  printf '%s\\n' DPC_AIO_EXPECTED_SIGNING_CERT_SHA256\n"
        "  exit 0\n"
        "fi\n"
        "exit 0\n",
        "utf-8",
    )
    gh.chmod(0o755)

    out = td_path / "status.json"
    env = os.environ.copy()
    env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
    cp = subprocess.run(
        ["python3", str(preflight), "--repo", "example/dpc-aio", "--root", str(repo), "--json-out", str(out)],
        text=True,
        capture_output=True,
        env=env,
        timeout=20,
    )
    if cp.returncode != 0:
        errors.append(f"preflight READY smoke failed rc={cp.returncode}: {cp.stdout} {cp.stderr}")
    elif not out.is_file():
        errors.append("preflight did not write JSON output")
    else:
        data = json.loads(out.read_text("utf-8"))
        if data.get("status") != "READY":
            errors.append(f"preflight fake environment status={data.get('status')!r}, expected READY")
        rendered = json.dumps(data)
        if "secret-value" in rendered:
            errors.append("preflight JSON exposed a secret value")

if errors:
    raise SystemExit("GITHUB_PUBLISH_KIT: FAIL\n- " + "\n- ".join(errors))
print("GITHUB_PUBLISH_KIT: PASS")
