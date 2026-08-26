---
name: dpc-aio-build
description: Use when an existing DPC-AIO repository needs a specific Android build variant compiled or its APK/provisioning outputs located and the result must distinguish real Gradle success from partial checks.
---

# DPC-AIO Build

## Use this skill when

Use it for repository-native DPC-AIO builds such as `enterpriseDebug`, `systemPrivilegedRelease`, `labDebug`, `tstDebug`, or `engRelease`. Do not use it to install a different Gradle distribution, mutate the Android SDK, or invent a successful APK when Gradle failed.

## Procedure

1. Resolve the repository root and inspect `apps/dpc/app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, and the requested flavor/build type.
2. Normalize the requested variant to one supported script value: `EnterpriseDebug`, `EnterpriseRelease`, `SystemPrivilegedDebug`, `SystemPrivilegedRelease`, `LabDebug`, `LabRelease`, `TstDebug`, `TstRelease`, `EngDebug`, or `EngRelease`.
3. Check prerequisites that the repository actually declares: executable-capable `gradlew`, wrapper JAR/properties, Java version, Android SDK platform/build-tools, and any Python provisioning requirements.
4. Run `./scripts/build_variant.sh <repo-root> <Variant>`. Preserve its exit code and capture the first failing Gradle task/source location.
5. On success, locate the APK under `apps/dpc/build/outputs/apk/<flavor>/<buildType>/`. When provisioning is configured, also inspect `apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/`.
6. Report one of these states explicitly: **APK build passed**, **APK build failed**, or **APK build not executed**. Static/contract checks passing never imply APK compilation passed.

## Boundaries

Use only the repository wrapper. Do not add `sudo`, alternative Gradle installers, hidden API stubs, signing secrets, or downloader fallbacks. Do not alter Android/Gradle versions unless a concrete failure proves that change is required.
