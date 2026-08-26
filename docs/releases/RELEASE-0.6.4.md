# DPC-AIO 0.6.4

Release focus: repository structure cleanup without changing stable Gradle module IDs.

## Changes

- Moved the final DPC app to `apps/dpc/`.
- Grouped feature modules under `modules/<domain>/{core,android}` where applicable.
- Grouped Shizuku, Dhizuku and native diagnostics under `integrations/`.
- Moved provisioning server to `services/provisioning/`.
- Moved companion plugin to `plugins/chatgpt-companion/`.
- Consolidated lab-only code and fixtures under `lab/`.
- Consolidated release/checkpoint/change documentation under `docs/`.
- Moved pre-push verification to `tools/release/verify-before-push.sh`.
- Removed the empty orphan `knox-license-android/` skeleton.
- Added canonical project-path mapping in `tools/project_layout.py`.
- Added `tools/tests/test_project_layout.py` to prevent top-level module sprawl from returning.
- Updated GitHub Actions, host tests, plugin checks and provisioning tests for the new physical paths.

## Compatibility

Gradle project IDs remain unchanged. Existing commands such as
`./gradlew :app-dpc:assembleEnterpriseDebug` and dependency declarations such as
`project(":policy-core")` remain valid.
