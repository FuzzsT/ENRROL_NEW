# DPC-AIO Autopilot final audit

Audit date: 2026-08-27.

This audit was executed against a fresh extraction of `ENRROL_NEW-FINAL-UI-ACTIVITY-MANAGER-3-VALIDATED.zip`.

## Executed evidence

- Original repository checksum manifest before report corrections: 705/705 entries matched.
- Pure/core Kotlin test mains from `tools/run_host_tests.sh`: 63/63 PASS, exit 0.
- Python contract scripts referenced by `tools/run_host_tests.sh`: 120/120 PASS.
- Provisioning server Node tests: 12/12 PASS.
- Python AST parse: 163 source files, 0 failures.
- GitHub Actions workflow YAML parse: 2 files, 0 failures.
- Bash syntax: 9 `.sh` scripts plus `gradlew`, 0 failures.
- Non-SDK API scan: PASS.
- Release secret scan: PASS.
- Activity Manager 3.0, favorites/groups persistence, safe-insets UI, dashboard menu and expanded Device Lifecycle contracts: PASS.
- Release/QR gates 102 and 111-115: PASS.

## Real APK QR rebinding

The current provisioning generator was rerun against the uploaded Actions APK `DPC-AIO-enterprise-release.apk`.

- APK SHA-256: `76aab13fa043144581a047d26e3a5254afbc48b8af294540d72d2051a10035ef`.
- Work-profile QR: `ok=true`, QR payload match, APK checksum match, exact DPC component and exact HTTPS APK URL.
- Fully-managed QR: `ok=true`, QR payload match, APK checksum match, exact DPC component and exact HTTPS APK URL.

## Gradle compile boundary

A real Gradle invocation was attempted from the fresh extraction using `./gradlew --version`. The wrapper correctly requested Gradle 9.7.0, but the sandbox could not resolve `services.gradle.org` and returned `java.net.UnknownHostException`. Therefore this environment cannot provide an authoritative Android compile for the new UI source. A new GitHub Actions build remains required for compile/install evidence.

## Public rolling release

A fresh GitHub API read for release tag `dpc-aio-continuous` returned HTTP 404 on 2026-08-27. The production workflow must reach its publish job to create/update the rolling release before Setup Wizard can download the canonical APK URL from that tag.

## Artifact policy

The final package excludes build trees, `.gradle`, `.git`, `__pycache__`, `.pyc` files and other transient generated state. `REPO-SHA256SUMS.txt` is regenerated after this report update and verified against the packaged bytes.
