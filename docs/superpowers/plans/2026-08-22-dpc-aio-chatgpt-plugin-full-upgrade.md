# DPC-AIO ChatGPT/Codex Plugin Full Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a public-safe, skills-only ChatGPT/Codex companion plugin to DPC-AIO, validate it against the installed Plugin Autopilot contract, and produce deterministic release ZIPs without changing the Android runtime architecture.

**Architecture:** Keep the Android/Gradle project at repository root and add an independent `plugins/chatgpt-companion/` package. The plugin contains four focused Skills (build, CI repair, enrollment, verification), deterministic SVG branding, local/private-safe metadata, and bounded scripts that invoke repository-native tooling rather than reimplementing DPC behavior. Public publication remains blocked until publisher identity and stable public listing URLs are verified.

**Tech Stack:** Markdown Skills, JSON plugin manifest, POSIX shell, Python 3 standard library, existing Gradle wrapper, existing DPC-AIO verification/provisioning scripts, Plugin Autopilot `validate_plugin.py` and `package_plugin.py`.

**Spec:** `docs/superpowers/specs/2026-08-22-dpc-aio-chatgpt-plugin-design.md`

## Global Constraints

- Plugin architecture is **skills-only** under `plugins/chatgpt-companion/`; do not add `.mcp.json`, app UI, or lifecycle hooks.
- Android runtime architecture and existing DPC module behavior remain unchanged.
- Plugin public package must not contain Android source tree copies, APKs, Gradle caches, SDK/NDK, keystores, private keys, tokens, lab credentials, or build outputs.
- Do not represent lab/mock state as a genuine Samsung Knox license.
- Do not add root/CVE exploit payloads, stealth/anti-detection, attestation bypasses, signature spoofing, credential cloning/recovery, arbitrary foreign-process memory dumping, or unrestricted privileged Binder tooling.
- Manifest uses strict semver, explicit `./skills/`, valid square branding assets, and final-directory interface limits from the verified 2026-08-22 Plugin Autopilot contract.
- Public website/privacy/terms/support URLs are omitted rather than fabricated; Plugin Directory publication is a stop condition until those URLs and publisher identity are verified.
- Validation must pass with explicit public exclusions and deterministic packaging must produce two byte-identical ZIPs with identical SHA-256.
- Never claim Android APK build success unless an actual Gradle build exits 0.

---

## File Map

### New plugin package

- `plugins/chatgpt-companion/.codex-plugin/plugin.json` — native plugin manifest.
- `plugins/chatgpt-companion/assets/logo.svg` — square directory logo.
- `plugins/chatgpt-companion/assets/icon.svg` — square composer icon.
- `plugins/chatgpt-companion/skills/dpc-aio-build/SKILL.md` — build workflow and failure reporting.
- `plugins/chatgpt-companion/skills/dpc-aio-build/scripts/build_variant.sh` — bounded Gradle wrapper runner.
- `plugins/chatgpt-companion/skills/dpc-aio-ci-repair/SKILL.md` — root-cause-first GitHub Actions diagnosis workflow.
- `plugins/chatgpt-companion/skills/dpc-aio-ci-repair/references/known-failures.md` — evidence-backed CI failure patterns already observed in DPC-AIO.
- `plugins/chatgpt-companion/skills/dpc-aio-enrollment/SKILL.md` — provisioning/QR generation and verification workflow.
- `plugins/chatgpt-companion/skills/dpc-aio-enrollment/references/provisioning-contract.md` — exact repository artifact paths and checksum rules.
- `plugins/chatgpt-companion/skills/dpc-aio-verify/SKILL.md` — pre-push verification workflow.
- `plugins/chatgpt-companion/skills/dpc-aio-verify/scripts/verify_repo.sh` — bounded repository-native verifier.
- `plugins/chatgpt-companion/README.md` — plugin purpose, installation scope, publication boundary.
- `plugins/chatgpt-companion/PRIVACY.md` — local-only behavior and data boundary.
- `plugins/chatgpt-companion/TERMS.md` — local/private companion terms and safety boundary.

### New repository tests/tooling

- `tools/plugin/test_plugin_contract.py` — structural and public-boundary tests for the companion plugin.
- `tools/plugin/test_plugin_scripts.py` — tests for bounded script behavior using temporary fake repositories.
- `tools/plugin/public_exclusions.txt` — canonical explicit exclusions passed to Autopilot validation/packaging.
- `tools/plugin/run_plugin_checks.py` — one-command plugin validation orchestrator.
- `tools/plugin/autopilot/validate_plugin.py` — vendored exact installed Autopilot validator snapshot used for reproducible repository checks.
- `tools/plugin/autopilot/package_plugin.py` — vendored exact installed Autopilot packager snapshot used for deterministic ZIP creation.

### Documentation

- `docs/plugin/VALIDATION.md` — validation commands, expected outputs, current release boundary.
- `docs/plugin/PUBLICATION.md` — explicit blockers for public submission and required verified metadata.

---

### Task 1: Establish failing plugin contract tests

**Files:**
- Create: `tools/plugin/test_plugin_contract.py`
- Create: `tools/plugin/public_exclusions.txt`

**Interfaces:**
- Consumes: approved design spec and repository root.
- Produces: `PLUGIN_ROOT = ROOT / "plugins/chatgpt-companion"` contract and canonical exclusion entries used by Tasks 2–7.

- [ ] **Step 1: Write the failing structural/public-boundary test**

Create `tools/plugin/test_plugin_contract.py` with checks equivalent to:

```python
from pathlib import Path
import json
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

if (PLUGIN / ".codex-plugin/plugin.json").is_file():
    manifest = json.loads((PLUGIN / ".codex-plugin/plugin.json").read_text(encoding="utf-8"))
    if manifest.get("skills") != "./skills/":
        errors.append("manifest skills path must be ./skills/")
    if any(key in manifest for key in ("mcpServers", "apps", "hooks")):
        errors.append("skills-only plugin must not declare MCP/apps/hooks")

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
```

Create `tools/plugin/public_exclusions.txt` with exact secret/artifact slugs that must never appear in the plugin package:

```text
dpc-aio-lab-private.pem
dpc-aio-lab-klm.token
dpc-aio-lab-public.pem
public-key-x509-base64.txt
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
python3 tools/plugin/test_plugin_contract.py
```

Expected: non-zero exit with `PLUGIN_CONTRACT: FAIL` because `plugins/chatgpt-companion/` does not exist yet.

- [ ] **Step 3: Commit the failing contract test**

```bash
git add tools/plugin/test_plugin_contract.py tools/plugin/public_exclusions.txt
git commit -m "test(plugin): define companion package contract"
```

---

### Task 2: Create manifest and deterministic branding

**Files:**
- Create: `plugins/chatgpt-companion/.codex-plugin/plugin.json`
- Create: `plugins/chatgpt-companion/assets/logo.svg`
- Create: `plugins/chatgpt-companion/assets/icon.svg`
- Create: `plugins/chatgpt-companion/README.md`
- Create: `plugins/chatgpt-companion/PRIVACY.md`
- Create: `plugins/chatgpt-companion/TERMS.md`
- Test: `tools/plugin/test_plugin_contract.py`

**Interfaces:**
- Consumes: `PLUGIN_ROOT` contract from Task 1.
- Produces: valid skills-only plugin root and branding paths consumed by Autopilot validator in Task 6.

- [ ] **Step 1: Add a manifest fixture assertion before creating production files**

Extend `tools/plugin/test_plugin_contract.py` so, once the manifest exists, it requires:

```python
assert manifest["name"] == "dpc-aio-companion"
assert manifest["version"] == "0.1.0"
assert manifest["skills"] == "./skills/"
assert manifest["interface"]["displayName"] == "DPC-AIO Companion"
assert manifest["interface"]["category"] == "Developer Tools"
assert manifest["interface"]["logo"] == "./assets/logo.svg"
assert manifest["interface"]["composerIcon"] == "./assets/icon.svg"
```

Also require `websiteURL`, `privacyPolicyURL`, `termsOfServiceURL`, and `supportURL` to be absent until verified public URLs exist.

- [ ] **Step 2: Run the contract test and verify RED remains**

```bash
python3 tools/plugin/test_plugin_contract.py
```

Expected: FAIL because production manifest/assets do not yet exist.

- [ ] **Step 3: Create the minimal valid plugin manifest**

Create `plugins/chatgpt-companion/.codex-plugin/plugin.json` with this shape:

```json
{
  "name": "dpc-aio-companion",
  "version": "0.1.0",
  "description": "Build, diagnose, provision, and verify the DPC-AIO Android Enterprise repository using bounded repository-native workflows.",
  "author": {"name": "DPC-AIO Project"},
  "skills": "./skills/",
  "interface": {
    "displayName": "DPC-AIO Companion",
    "shortDescription": "Build and verify DPC-AIO",
    "longDescription": "A skills-only companion for the DPC-AIO Android Enterprise repository. It guides repository-native Gradle builds, root-cause-first CI diagnosis, Android Enterprise provisioning QR verification, and bounded pre-push checks without owning device-management execution.",
    "developerName": "DPC-AIO Project",
    "category": "Developer Tools",
    "capabilities": [
      "Build DPC-AIO Gradle variants using the repository wrapper",
      "Diagnose GitHub Actions build failures from concrete logs",
      "Verify Android Enterprise provisioning QR artifacts",
      "Run repository-native pre-push and structural checks"
    ],
    "defaultPrompt": [
      "Build the enterpriseDebug DPC-AIO variant and report the first blocker.",
      "Diagnose this DPC-AIO GitHub Actions failure from the log.",
      "Verify the DPC-AIO repository before I push a new build."
    ],
    "logo": "./assets/logo.svg",
    "composerIcon": "./assets/icon.svg",
    "brandColor": "#245A8D",
    "brandColorDark": "#6FB1E7"
  }
}
```

- [ ] **Step 4: Create deterministic square SVG branding**

Create both SVG files with numeric square `viewBox="0 0 512 512"`, no external resources, no trademarked logos, and simple project initials/geometric device-management symbolism. Keep each SVG below 10 KiB.

- [ ] **Step 5: Create package boundary documents**

`README.md` must state that the plugin is a skills-only companion and does not contain or execute the Android DPC itself. `PRIVACY.md` must state that the package has no plugin-owned remote service or telemetry. `TERMS.md` must state that the package does not grant device-owner privileges and that Android management actions remain subject to device/platform authorization.

- [ ] **Step 6: Run the structural test**

```bash
python3 tools/plugin/test_plugin_contract.py
```

Expected: still FAIL only for the four missing Skills.

- [ ] **Step 7: Commit manifest/branding boundary**

```bash
git add chatgpt-plugin tools/plugin/test_plugin_contract.py
git commit -m "feat(plugin): add companion manifest and branding"
```

---

### Task 3: Implement build and CI-repair Skills

**Files:**
- Create: `plugins/chatgpt-companion/skills/dpc-aio-build/SKILL.md`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-build/scripts/build_variant.sh`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-ci-repair/SKILL.md`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-ci-repair/references/known-failures.md`
- Create: `tools/plugin/test_plugin_scripts.py`

**Interfaces:**
- Consumes: repository `gradlew`, `gradle/libs.versions.toml`, existing `.github/workflows/build-aio-enrollment.yml`.
- Produces: `build_variant.sh <repo-root> <variant>` exit-code-preserving build runner and CI diagnosis instructions.

- [ ] **Step 1: Write failing script behavior tests**

Create `tools/plugin/test_plugin_scripts.py` using `tempfile.TemporaryDirectory()` and `subprocess.run()` to assert:

```python
# 1. Missing repo root => non-zero, clear error.
# 2. Repo without gradlew => non-zero, clear error.
# 3. Fake executable gradlew records exactly ':app-dpc:assembleEnterpriseDebug'.
# 4. A failing fake gradlew exit code propagates unchanged.
```

The fake `gradlew` should write received arguments to a temp file and exit using `FAKE_GRADLE_EXIT` so no Android SDK is required for this test.

- [ ] **Step 2: Run script tests and verify RED**

```bash
python3 tools/plugin/test_plugin_scripts.py
```

Expected: FAIL because `build_variant.sh` does not exist.

- [ ] **Step 3: Implement the bounded Gradle build wrapper**

Create `build_variant.sh` with:

```bash
#!/usr/bin/env bash
set -euo pipefail
repo=${1:?usage: build_variant.sh <repo-root> <variant>}
variant=${2:?usage: build_variant.sh <repo-root> <variant>}
case "$variant" in
  EnterpriseDebug|EnterpriseRelease|SystemPrivilegedDebug|SystemPrivilegedRelease|LabDebug|LabRelease|TstDebug|TstRelease|EngDebug|EngRelease) ;;
  *) echo "unsupported DPC-AIO variant: $variant" >&2; exit 64 ;;
esac
cd "$repo"
test -f ./gradlew || { echo "missing Gradle wrapper: $repo/gradlew" >&2; exit 66; }
chmod +x ./gradlew
exec ./gradlew ":app-dpc:assemble${variant}"
```

Do not add downloaders, alternative Gradle installations, `sudo`, or environment mutation.

- [ ] **Step 4: Write `dpc-aio-build/SKILL.md`**

Frontmatter:

```yaml
---
name: dpc-aio-build
description: Build an existing DPC-AIO Android variant with the repository Gradle wrapper, locate its APK/provisioning artifacts, and report the first real build blocker without inventing success.
---
```

Body must require prerequisite inspection, exact variant resolution, repository-native wrapper execution, and explicit distinction between “static verification passed” and “APK build passed”.

- [ ] **Step 5: Write CI repair Skill/reference**

`dpc-aio-ci-repair/SKILL.md` frontmatter:

```yaml
---
name: dpc-aio-ci-repair
description: Diagnose and repair DPC-AIO GitHub Actions or Gradle CI failures from concrete logs using root-cause-first, minimal fixes and commit-SHA verification.
---
```

`known-failures.md` should document only the already observed safe build patterns: `sdkmanager` broken-pipe under `pipefail`, preview Android platform package discovery, missing executable bit on `gradlew`, and hidden public-SDK-incompatible `UserHandle` helpers. Each entry must say to verify the current source/log before applying a fix.

- [ ] **Step 6: Run script and structural tests**

```bash
python3 tools/plugin/test_plugin_scripts.py
python3 tools/plugin/test_plugin_contract.py
```

Expected: script tests PASS; structural contract remains FAIL only for the two remaining Skills.

- [ ] **Step 7: Commit build/CI Skills**

```bash
git add plugins/chatgpt-companion/skills/dpc-aio-build plugins/chatgpt-companion/skills/dpc-aio-ci-repair tools/plugin/test_plugin_scripts.py
git commit -m "feat(plugin): add DPC build and CI repair skills"
```

---

### Task 4: Implement enrollment and verification Skills

**Files:**
- Create: `plugins/chatgpt-companion/skills/dpc-aio-enrollment/SKILL.md`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-enrollment/references/provisioning-contract.md`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-verify/SKILL.md`
- Create: `plugins/chatgpt-companion/skills/dpc-aio-verify/scripts/verify_repo.sh`
- Modify: `tools/plugin/test_plugin_scripts.py`

**Interfaces:**
- Consumes: `tools/provisioning/generate_provisioning.py`, `apps/dpc/build/outputs/provisioning/...`, `tools/verify_project.py`, `tools/verify_android_contracts.py`, `tools/release_gate.py`, `tools/release/verify-before-push.sh` when present.
- Produces: bounded verifier script and exact provisioning artifact contract.

- [ ] **Step 1: Add failing verifier-script tests**

Extend `tools/plugin/test_plugin_scripts.py` to create a temporary fake repo containing stub verifier scripts and assert that `verify_repo.sh`:

```python
# runs verify_project.py and verify_android_contracts.py when present;
# runs release_gate.py when present;
# rejects any '*.kt' containing UserHandle.myUserId( or UserHandle.getUserId(;
# reports missing gradle-wrapper.jar and gradle-wrapper.properties;
# propagates any verifier failure as non-zero.
```

- [ ] **Step 2: Run tests and verify RED**

```bash
python3 tools/plugin/test_plugin_scripts.py
```

Expected: FAIL because `verify_repo.sh` does not exist.

- [ ] **Step 3: Implement `verify_repo.sh`**

Use only POSIX-available shell plus `python3`. Required sequence:

```bash
#!/usr/bin/env bash
set -euo pipefail
repo=${1:?usage: verify_repo.sh <repo-root>}
cd "$repo"
test -f gradlew
test -f gradle/wrapper/gradle-wrapper.jar
test -f gradle/wrapper/gradle-wrapper.properties
if grep -R -n -E 'UserHandle\.(myUserId|getUserId)\(' --include='*.kt' .; then
  echo "hidden UserHandle user-id API reference found" >&2
  exit 1
fi
[ -f tools/verify_project.py ] && python3 tools/verify_project.py
[ -f tools/verify_android_contracts.py ] && python3 tools/verify_android_contracts.py
[ -f tools/release_gate.py ] && python3 tools/release_gate.py
```

Do not make this script silently run a full Android build; it is a pre-push verifier, not a substitute for `build_variant.sh`.

- [ ] **Step 4: Write enrollment Skill and provisioning reference**

Frontmatter:

```yaml
---
name: dpc-aio-enrollment
description: Generate or verify DPC-AIO Android Enterprise Device Owner provisioning artifacts from an actual built APK, download URL, component name, and real checksum data.
---
```

Reference must preserve current repository paths:

```text
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/provisioning.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/provisioning-payload.txt
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/provisioning-metadata.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/provisioning-qr.png
```

It must state that a QR is not verified unless the referenced APK exists and checksum/signature data was produced from that APK.

- [ ] **Step 5: Write verification Skill**

Frontmatter:

```yaml
---
name: dpc-aio-verify
description: Run bounded DPC-AIO repository verification before push or CI, including wrapper, project contracts, public-SDK compatibility checks, and honest partial-verification reporting.
---
```

Body must distinguish structural checks, host tests, and actual Android APK build verification.

- [ ] **Step 6: Run tests and verify GREEN for all four Skills**

```bash
python3 tools/plugin/test_plugin_scripts.py
python3 tools/plugin/test_plugin_contract.py
```

Expected:

```text
PLUGIN_SCRIPT_TESTS: PASS
PLUGIN_CONTRACT: PASS
```

- [ ] **Step 7: Commit enrollment/verification Skills**

```bash
git add plugins/chatgpt-companion/skills/dpc-aio-enrollment plugins/chatgpt-companion/skills/dpc-aio-verify tools/plugin/test_plugin_scripts.py
git commit -m "feat(plugin): add enrollment and verification skills"
```

---

### Task 5: Vendor exact Autopilot validator/packager snapshot and add orchestrator

**Files:**
- Create: `tools/plugin/autopilot/validate_plugin.py`
- Create: `tools/plugin/autopilot/package_plugin.py`
- Create: `tools/plugin/run_plugin_checks.py`
- Create: `docs/plugin/VALIDATION.md`
- Create: `docs/plugin/PUBLICATION.md`

**Interfaces:**
- Consumes: exact installed Plugin Autopilot validator/packager snapshot verified on 2026-08-22.
- Produces: reproducible local `run_plugin_checks.py` interface and deterministic artifacts under caller-selected output directory.

- [ ] **Step 1: Add failing orchestrator invocation test**

Extend `tools/plugin/test_plugin_contract.py` to require the three tooling files above and check that the vendored validator exposes `validate_plugin` and the packager exposes `build_archive` by parsing their source text.

- [ ] **Step 2: Run contract test and verify RED**

```bash
python3 tools/plugin/test_plugin_contract.py
```

Expected: FAIL because vendored Autopilot tooling does not exist.

- [ ] **Step 3: Copy the exact installed Autopilot scripts**

Populate `tools/plugin/autopilot/validate_plugin.py` and `package_plugin.py` byte-for-byte from the installed Plugin Autopilot resources read on 2026-08-22. Do not alter validation limits or semantics.

- [ ] **Step 4: Implement `run_plugin_checks.py`**

Required interface:

```bash
python3 tools/plugin/run_plugin_checks.py --output-dir /tmp/dpc-aio-plugin-release
```

Required behavior:

```python
# 1. Run tools/plugin/test_plugin_contract.py.
# 2. Run tools/plugin/test_plugin_scripts.py.
# 3. Run tools/plugin/autopilot/validate_plugin.py chatgpt-plugin --json
#    with every non-empty line from tools/plugin/public_exclusions.txt passed as --exclude.
# 4. Build plugin-a.zip and plugin-b.zip with identical exclusion args.
# 5. Compare bytes and SHA-256; fail if different.
# 6. Inspect ZIP entries; require .codex-plugin/plugin.json at archive root.
# 7. Reject forbidden secret/artifact filename suffixes inside the ZIP.
# 8. Emit machine-readable validation-report.json with validator result, ZIP SHA-256,
#    archive entry count, and publicationBlocked=true.
```

- [ ] **Step 5: Write validation/publication documentation**

`docs/plugin/VALIDATION.md` must document exact commands and the distinction between plugin validation and Android APK build validation. `docs/plugin/PUBLICATION.md` must list the stop conditions: unavailable verified publisher identity, unverified public listing URLs, unavailable writable target repo, or failed validation.

- [ ] **Step 6: Run contract and tooling tests**

```bash
python3 tools/plugin/test_plugin_contract.py
python3 tools/plugin/test_plugin_scripts.py
```

Expected: PASS.

- [ ] **Step 7: Commit reproducible Autopilot tooling**

```bash
git add tools/plugin docs/plugin
git commit -m "build(plugin): add reproducible Autopilot validation"
```

---

### Task 6: Run full plugin validation and deterministic packaging gate

**Files:**
- Generated outside plugin root: `/tmp/dpc-aio-plugin-release/plugin-a.zip`
- Generated outside plugin root: `/tmp/dpc-aio-plugin-release/plugin-b.zip`
- Generated outside plugin root: `/tmp/dpc-aio-plugin-release/validation-report.json`

**Interfaces:**
- Consumes: complete `plugins/chatgpt-companion/` plus Task 5 orchestrator.
- Produces: validated deterministic public-safe plugin ZIP hash and exact validation evidence.

- [ ] **Step 1: Run repository-native plugin checks**

```bash
python3 tools/plugin/test_plugin_contract.py
python3 tools/plugin/test_plugin_scripts.py
python3 tools/verify_project.py
python3 tools/verify_android_contracts.py
python3 tools/release_gate.py
```

Expected: all commands exit 0. These checks do **not** prove the Android APK compiles.

- [ ] **Step 2: Run exact Autopilot validation/packaging orchestrator**

```bash
rm -rf /tmp/dpc-aio-plugin-release
python3 tools/plugin/run_plugin_checks.py --output-dir /tmp/dpc-aio-plugin-release
```

Expected: exit 0 and `validation-report.json` records architecture `skills-only`, four Skills, zero validator errors, `publicationBlocked: true`, and one deterministic SHA-256 for both ZIPs.

- [ ] **Step 3: Independently compare package bytes**

```bash
cmp /tmp/dpc-aio-plugin-release/plugin-a.zip /tmp/dpc-aio-plugin-release/plugin-b.zip
sha256sum /tmp/dpc-aio-plugin-release/plugin-a.zip /tmp/dpc-aio-plugin-release/plugin-b.zip
```

Expected: `cmp` exit 0 and hashes identical.

- [ ] **Step 4: Inspect archive root and exclusions**

```bash
unzip -Z1 /tmp/dpc-aio-plugin-release/plugin-a.zip
```

Expected: `.codex-plugin/plugin.json`, `skills/`, `assets/`, and package docs at archive root; no Android repository modules, `.apk`, private-key/token files, `.mcp.json`, or `.app.json`.

- [ ] **Step 5: Commit validation-ready source state**

```bash
git add chatgpt-plugin tools/plugin docs/plugin
git commit -m "chore(plugin): pass full companion validation gate"
```

---

### Task 7: Build final DPC-AIO source package and release report

**Files:**
- Create: `PLUGIN-FULL-UPGRADE.md`
- Generated: `/mnt/data/DPC-AIO-0.6.2-PLUGIN-FULL-UPGRADE-123.zip`
- Generated: `/mnt/data/dpc-aio-companion-0.1.0.zip`

**Interfaces:**
- Consumes: validated repository source and deterministic plugin ZIP from Task 6.
- Produces: user-deliverable source archive, standalone plugin archive, hashes, and explicit residual blockers.

- [ ] **Step 1: Write the release report**

Create `PLUGIN-FULL-UPGRADE.md` containing:

```text
Plugin architecture: skills-only
Plugin version: 0.1.0
Skills: dpc-aio-build, dpc-aio-ci-repair, dpc-aio-enrollment, dpc-aio-verify
Autopilot validation: PASS
Deterministic packaging: PASS
Android APK build: NOT CLAIMED unless an actual Gradle build exited 0
Plugin Directory publication: BLOCKED pending verified publisher identity/public listing URLs/write access
```

Include exact SHA-256 from Task 6.

- [ ] **Step 2: Copy the deterministic standalone plugin ZIP**

```bash
cp /tmp/dpc-aio-plugin-release/plugin-a.zip /mnt/data/dpc-aio-companion-0.1.0.zip
```

- [ ] **Step 3: Create a password-protected full source ZIP**

Package the upgraded repository as `/mnt/data/DPC-AIO-0.6.2-PLUGIN-FULL-UPGRADE-123.zip` using password `123`, excluding transient `.git/`, `build/`, `.gradle/`, `__pycache__/`, and generated Android APK outputs. Preserve `plugins/chatgpt-companion/`, `tools/plugin/`, docs, wrapper files, and existing source.

- [ ] **Step 4: Verify both deliverables from scratch**

```bash
unzip -t /mnt/data/dpc-aio-companion-0.1.0.zip
unzip -P 123 -t /mnt/data/DPC-AIO-0.6.2-PLUGIN-FULL-UPGRADE-123.zip
sha256sum /mnt/data/dpc-aio-companion-0.1.0.zip /mnt/data/DPC-AIO-0.6.2-PLUGIN-FULL-UPGRADE-123.zip
```

Expected: archive integrity PASS for both.

- [ ] **Step 5: Final verification-before-completion gate**

Run fresh:

```bash
python3 tools/plugin/test_plugin_contract.py
python3 tools/plugin/test_plugin_scripts.py
python3 tools/plugin/run_plugin_checks.py --output-dir /tmp/dpc-aio-plugin-release-final
python3 tools/verify_project.py
python3 tools/verify_android_contracts.py
python3 tools/release_gate.py
```

Only after every command exits 0 may the plugin Full Upgrade be reported as validated. Report Android APK compilation separately according to actual Gradle evidence.

---

## Self-Review

### Spec coverage

- Separate `plugins/chatgpt-companion/` root: Tasks 2–4.
- Four focused Skills: Tasks 3–4.
- No MCP/app/hooks: Tasks 1–2 contract checks.
- Public safety exclusions: Tasks 1, 5, 6.
- Accurate/no-fabricated URLs publication boundary: Tasks 2, 5, 7.
- Branding: Task 2.
- Repository-native checks: Tasks 4 and 6.
- Exact Autopilot validation and deterministic packaging: Tasks 5–6.
- Final standalone plugin ZIP and upgraded DPC-AIO source ZIP: Task 7.
- No false Android build claim: Global Constraints and Tasks 3, 6, 7.

### Placeholder scan

No unresolved placeholder language remains. Publication metadata is intentionally omitted by design until verified, and the explicit stop condition is part of the required behavior rather than an implementation gap.

### Type/interface consistency

- `build_variant.sh <repo-root> <variant>` is defined in Task 3 and consumed only as that interface.
- `verify_repo.sh <repo-root>` is defined in Task 4 and consumed by the verification Skill.
- `run_plugin_checks.py --output-dir <path>` is defined in Task 5 and used unchanged in Tasks 6–7.
- Canonical exclusions come only from `tools/plugin/public_exclusions.txt` and are passed to both validation and packaging.
