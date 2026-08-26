#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "plugins/chatgpt-companion"
AUTOPILOT = ROOT / "tools/plugin/autopilot"
EXCLUSIONS_FILE = ROOT / "tools/plugin/public_exclusions.txt"


def run_checked(args: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(args, cwd=ROOT, text=True, capture_output=capture)
    if proc.returncode != 0:
        if capture:
            sys.stdout.write(proc.stdout)
            sys.stderr.write(proc.stderr)
        raise SystemExit(proc.returncode)
    return proc


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def exclusions() -> list[str]:
    if not EXCLUSIONS_FILE.is_file():
        raise SystemExit(f"missing exclusions file: {EXCLUSIONS_FILE}")
    return [line.strip() for line in EXCLUSIONS_FILE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")]


def exclusion_args(items: list[str]) -> list[str]:
    result: list[str] = []
    for item in items:
        result += ["--exclude", item]
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    out = args.output_dir.expanduser().resolve()
    out.mkdir(parents=True, exist_ok=True)

    run_checked([sys.executable, "tools/plugin/test_plugin_contract.py"])
    run_checked([sys.executable, "tools/plugin/test_plugin_scripts.py"])

    excluded = exclusions()
    validator_cmd = [
        sys.executable, str(AUTOPILOT / "validate_plugin.py"), str(PLUGIN), "--json",
        *exclusion_args(excluded),
    ]
    validation_proc = run_checked(validator_cmd, capture=True)
    validator = json.loads(validation_proc.stdout)
    if not validator.get("ok"):
        raise SystemExit("validator returned ok=false")

    zip_a = out / "plugin-a.zip"
    zip_b = out / "plugin-b.zip"
    packager = AUTOPILOT / "package_plugin.py"
    for target in (zip_a, zip_b):
        run_checked([
            sys.executable, str(packager), str(PLUGIN), str(target), "--json",
            *exclusion_args(excluded),
        ], capture=True)

    bytes_a = zip_a.read_bytes()
    bytes_b = zip_b.read_bytes()
    if bytes_a != bytes_b:
        raise SystemExit("deterministic packaging failed: plugin-a.zip != plugin-b.zip")
    digest_a = hashlib.sha256(bytes_a).hexdigest()
    digest_b = hashlib.sha256(bytes_b).hexdigest()
    if digest_a != digest_b:
        raise SystemExit("deterministic packaging failed: SHA-256 mismatch")

    with zipfile.ZipFile(zip_a) as archive:
        names = archive.namelist()
    if ".codex-plugin/plugin.json" not in names:
        raise SystemExit("archive root invalid: missing .codex-plugin/plugin.json")
    forbidden_suffixes = (".apk", ".keystore", ".jks", ".pem", ".token", ".aab")
    forbidden_names = {"gradle-wrapper.jar", ".mcp.json", ".app.json"}
    bad = [name for name in names if not name.endswith("/") and
           (Path(name).name in forbidden_names or name.lower().endswith(forbidden_suffixes))]
    if bad:
        raise SystemExit("forbidden public archive members: " + ", ".join(bad))
    top_level = {name.split("/", 1)[0] for name in names if name}
    allowed_top = {".codex-plugin", "assets", "skills", "README.md", "PRIVACY.md", "TERMS.md"}
    unexpected = sorted(top_level - allowed_top)
    if unexpected:
        raise SystemExit("unexpected archive-root content: " + ", ".join(unexpected))

    report = {
        "ok": True,
        "plugin": {
            "name": validator.get("name"),
            "version": validator.get("version"),
            "architecture": validator.get("architecture"),
            "skills": validator.get("skills"),
        },
        "validator": validator,
        "deterministicPackaging": True,
        "sha256": digest_a,
        "sha256A": digest_a,
        "sha256B": digest_b,
        "archiveEntries": len(names),
        "publicationBlocked": True,
        "publicationBlockers": [
            "verified publisher identity not established in this workspace",
            "stable public website/privacy/terms/support URLs not verified",
            "writable target repository/publication credentials not available in this workspace",
        ],
    }
    report_path = out / "validation-report.json"
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("PLUGIN_CHECKS: PASS")
    print(f"architecture={validator.get('architecture')} skills={len(validator.get('skills', []))}")
    print(f"sha256={digest_a}")
    print(f"report={report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
