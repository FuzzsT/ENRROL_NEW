# DPC-AIO 0.6.5

## App-owned repository layout

This release corrects the remaining ownership ambiguity from 0.6.4. All Gradle modules that form the Android DPC now live inside the DPC product boundary under `apps/dpc/`.

- `apps/dpc/app` — Android application module (`:app-dpc`)
- `apps/dpc/modules` — feature/core Android Gradle modules
- `apps/dpc/integrations` — Shizuku, Dhizuku and native diagnostics adapters
- `apps/dpc/lab` — DPC-specific lab Gradle modules
- `services`, `plugins`, `tools`, `docs`, `gradle` — repository infrastructure outside the Android application boundary
- `lab/license` — repository-level lab/license fixture material, intentionally outside the shipped Android source tree

Stable Gradle project IDs are preserved through `settings.gradle.kts`, so commands such as `./gradlew :app-dpc:assembleEnterpriseDebug` do not change.

The application module still declares the feature modules with `implementation(project(":..."))`; the change is physical ownership/layout, not removal or re-adding of application dependencies.
