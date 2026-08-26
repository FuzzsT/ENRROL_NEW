# DPC-AIO ChatGPT/Codex Plugin Full Upgrade Design

**Date:** 2026-08-22

## Objective

Add a separate, public-safe ChatGPT/Codex **skills-only companion plugin** to the existing DPC-AIO Android/Gradle repository without changing the Android runtime architecture. The plugin will help users build, diagnose CI failures, generate Android Enterprise enrollment QR artifacts, and run pre-push verification against the DPC-AIO repository.

## Authoritative sources

- Existing DPC-AIO repository remains authoritative for Android behavior and build commands.
- Plugin Autopilot installed skill is authoritative for packaging/validation workflow.
- Official OpenAI Plugin/Skill documentation was re-checked on 2026-08-22 before this design. Current official documentation remains authoritative over snapshots bundled with the Autopilot skill.

## Architecture

Use a **skills-only** plugin rooted at `plugins/chatgpt-companion/`.

The Android project remains at repository root. The plugin is an independent package and does not bundle the Android source tree, APKs, Gradle caches, Android SDK/NDK, private test keys, or lab secrets into the public plugin archive.

Proposed shape:

```text
plugins/chatgpt-companion/
├── .codex-plugin/
│   └── plugin.json
├── skills/
│   ├── dpc-aio-build/
│   │   ├── SKILL.md
│   │   └── scripts/
│   ├── dpc-aio-ci-repair/
│   │   ├── SKILL.md
│   │   └── references/
│   ├── dpc-aio-enrollment/
│   │   ├── SKILL.md
│   │   └── references/
│   └── dpc-aio-verify/
│       ├── SKILL.md
│       └── scripts/
├── assets/
│   ├── logo.svg
│   └── icon.svg
├── PRIVACY.md
├── TERMS.md
└── README.md
```

No `.mcp.json`, remote MCP server, app UI, or lifecycle hook is required for this release.

## Plugin responsibilities

### `dpc-aio-build`

- Identify the requested DPC-AIO Gradle variant.
- Verify Java/Gradle/Android SDK prerequisites.
- Use the repository's wrapper and existing build tasks.
- Build the requested APK without inventing success when Gradle fails.
- Locate the final APK and provisioning output produced by the repository.
- Surface exact build blockers with the failing task and source location.

### `dpc-aio-ci-repair`

- Diagnose GitHub Actions logs using root-cause-first workflow.
- Separate warnings from blocking failures.
- Prefer bounded CI fixes over broad workflow rewrites.
- Preserve current Android/Gradle versions unless the failure proves a version incompatibility.
- Check that a new commit actually contains a source fix before advising a new CI run.

### `dpc-aio-enrollment`

- Generate or verify Android Enterprise Device Owner provisioning artifacts using the repository's existing provisioning tooling.
- Require the QR payload to correspond to the actual built/signed APK.
- Verify component name, download URL, package/signature checksum field, and optional admin extras.
- Never fabricate a checksum for an APK that has not been built.

### `dpc-aio-verify`

- Run repository-native pre-push/static checks.
- Verify workflow YAML and Gradle wrapper presence.
- Detect public-SDK-incompatible hidden `UserHandle.myUserId/getUserId` references.
- Run the existing Android contract and project verification scripts when present.
- Report partial verification honestly if the full Android build cannot execute.

## Safety and publication boundary

The public plugin must **exclude** the following repository content and capabilities:

- `lab/license/` private keys, tokens, generated credentials, or secret material.
- Knox license forgery/bypass behavior or any claim that lab state is a genuine Samsung license.
- Root/CVE/exploit payloads, stealth/anti-detection instructions, attestation bypasses, signature spoofing, or arbitrary privileged transaction tooling.
- Credential cloning, payment/access-card cloning, key recovery, or replay of third-party authentication credentials.
- Arbitrary foreign-process memory dumping.
- APK binaries, keystores, signing keys, Android SDK/NDK, Gradle caches, or build outputs.

Public Skills may explain safe build, CI, provisioning, verification, and owned/synthetic NFC lab boundaries already represented by the repository, but must not broaden them into excluded capabilities.

## Manifest requirements

Create `.codex-plugin/plugin.json` with:

- strict semver version for the plugin companion release;
- ASCII plugin identifier <= 64 characters;
- accurate author name;
- interface display name and short description <= 30 characters;
- long description <= 4,000 characters;
- developer name <= 80 characters;
- <= 20 concise capability strings;
- <= 3 one-line default prompts with no `@mention`;
- square logo/composer icon references;
- skills path explicitly pointing to `./skills`.

Public listing URLs must not be fabricated. If stable website/privacy/terms/support URLs cannot be verified, the package may be validated as a skills-only local/private artifact but publication must stop before Plugin Directory submission.

## Branding

Use deterministic square SVG assets stored inside `plugins/chatgpt-companion/assets/`. They should represent DPC-AIO generically and must not copy Android, Samsung, Knox, Google, or OpenAI trademarks/logos.

## Validation and packaging

Required gates:

1. Repository-native checks relevant to plugin scripts and DPC-AIO integration.
2. Autopilot `validate_plugin.py` against `plugins/chatgpt-companion/`.
3. Public-exclusion scan covering plugin text and archive entries.
4. Package twice with Autopilot `package_plugin.py`.
5. Require byte-identical ZIPs and equal SHA-256 hashes.
6. Inspect archive root and ensure only the plugin package is present.
7. Verify no secret/private-key material appears in the plugin ZIP.
8. Smoke-test install only on surfaces actually available in this environment; unavailable surfaces must be reported as untested.

## Release boundary

This Full Upgrade will produce:

- the upgraded DPC-AIO repository with `plugins/chatgpt-companion/` companion source;
- a validated deterministic plugin ZIP;
- validation report including exact SHA-256;
- an updated full DPC-AIO source ZIP containing the plugin companion.

Commit/tag/push/public Plugin Directory publication are allowed only when repository identity, write credentials, publisher identity, and required public listing URLs are available and verified. Missing any of those is a stop condition, not a reason to fabricate metadata.

## Non-goals

- Do not convert the Android DPC itself into an MCP service.
- Do not make the plugin own Android device-management execution.
- Do not duplicate DPC-AIO runtime logic inside opaque plugin scripts.
- Do not publish internal/lab-only capabilities merely because they exist in the repository.
