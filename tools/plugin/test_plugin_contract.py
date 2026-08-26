from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "plugins/chatgpt-companion"

required = [
    ".codex-plugin/plugin.json",
    "assets/logo.svg",
    "assets/icon.svg",
    "skills/dpc-aio-build/SKILL.md",
    "skills/dpc-aio-ci-repair/SKILL.md",
    "skills/dpc-aio-enrollment/SKILL.md",
    "skills/dpc-aio-verify/SKILL.md",
]

errors = [rel for rel in required if not (PLUGIN / rel).is_file()]

manifest_path = PLUGIN / ".codex-plugin/plugin.json"
if manifest_path.is_file():
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"manifest unreadable: {exc}")
    else:
        expected_manifest = {
            "name": "dpc-aio-companion",
            "skills": "./skills/",
        }
        for key, expected in expected_manifest.items():
            if manifest.get(key) != expected:
                errors.append(f"manifest {key} must be {expected!r}")
        version = manifest.get("version")
        if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?", version):
            errors.append("manifest version must be strict semver")
        gradle_path = ROOT / "apps/dpc/app/build.gradle.kts"
        project_version = None
        if gradle_path.is_file():
            match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_path.read_text(encoding="utf-8", errors="ignore"))
            if match:
                project_version = match.group(1)
        if not project_version:
            errors.append("unable to resolve DPC versionName from apps/dpc/app/build.gradle.kts")
        else:
            public_manifest_text = json.dumps(manifest, ensure_ascii=False)
            if f"DPC-AIO {project_version}" not in public_manifest_text:
                errors.append(f"manifest public metadata must target DPC-AIO {project_version}")
        interface = manifest.get("interface") if isinstance(manifest.get("interface"), dict) else {}
        expected_interface = {
            "displayName": "DPC-AIO Companion",
            "category": "Developer Tools",
            "logo": "./assets/logo.svg",
            "composerIcon": "./assets/icon.svg",
        }
        for key, expected in expected_interface.items():
            if interface.get(key) != expected:
                errors.append(f"manifest interface.{key} must be {expected!r}")
        for url_field in ("websiteURL", "privacyPolicyURL", "termsOfServiceURL", "supportURL"):
            if url_field in interface:
                errors.append(f"manifest interface.{url_field} must be absent until verified")
        if any(key in manifest for key in ("mcpServers", "apps", "hooks")):
            errors.append("skills-only plugin must not declare MCP/apps/hooks")


plugin_readme = PLUGIN / "README.md"
if plugin_readme.is_file() and manifest_path.is_file():
    readme_text = plugin_readme.read_text(encoding="utf-8", errors="ignore")
    manifest_version = manifest.get("version") if isinstance(locals().get("manifest"), dict) else None
    if manifest_version and f"# DPC-AIO Companion {manifest_version}" not in readme_text:
        errors.append("plugin README heading must match manifest version")
    gradle_path = ROOT / "apps/dpc/app/build.gradle.kts"
    if gradle_path.is_file():
        match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_path.read_text(encoding="utf-8", errors="ignore"))
        if match and f"DPC-AIO {match.group(1)}" not in readme_text:
            errors.append(f"plugin README must target DPC-AIO {match.group(1)}")

verify_skill = PLUGIN / "skills/dpc-aio-verify/SKILL.md"
if verify_skill.is_file():
    verify_text = verify_skill.read_text(encoding="utf-8", errors="ignore")
    for marker in ("Offline", "Permission Manager", "Component", "Build Resolver", "Device Harness", "test_101_release_gate_contract.py", "test_110_release_gate_contract.py", "test_112_release_gate_contract.py", "test_113_release_gate_contract.py", "test_114_qr_release_bundle_contract.py", "Package Trust 2.0", "OEM Internals"):
        if marker not in verify_text:
            errors.append(f"verify skill missing required release marker: {marker}")

required_tooling = [
    "tools/plugin/autopilot/validate_plugin.py",
    "tools/plugin/autopilot/package_plugin.py",
    "tools/plugin/run_plugin_checks.py",
]
for rel in required_tooling:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing plugin tooling: {rel}")

validator = ROOT / "tools/plugin/autopilot/validate_plugin.py"
if validator.is_file() and "def validate_plugin(" not in validator.read_text(encoding="utf-8", errors="ignore"):
    errors.append("vendored validator must expose validate_plugin")
packager = ROOT / "tools/plugin/autopilot/package_plugin.py"
if packager.is_file() and "def build_archive(" not in packager.read_text(encoding="utf-8", errors="ignore"):
    errors.append("vendored packager must expose build_archive")


for rel in (
    "skills/dpc-aio-build/scripts/build_variant.sh",
    "skills/dpc-aio-verify/scripts/verify_repo.sh",
):
    path = PLUGIN / rel
    if not path.is_file():
        errors.append(f"missing executable plugin script: {rel}")
    elif not (path.stat().st_mode & 0o100):
        errors.append(f"plugin script must be executable: {rel}")

for forbidden in (
    "dpc-aio-lab-private.pem",
    "dpc-aio-lab-klm.token",
    "gradle-wrapper.jar",
    ".apk",
    ".keystore",
):
    for path in PLUGIN.rglob("*") if PLUGIN.exists() else []:
        if path.is_file() and forbidden in path.name:
            errors.append(f"forbidden public artifact: {path.relative_to(PLUGIN)}")

if errors:
    print("PLUGIN_CONTRACT: FAIL")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)
print("PLUGIN_CONTRACT: PASS")
