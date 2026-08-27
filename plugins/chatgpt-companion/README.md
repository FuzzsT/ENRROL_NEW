# DPC-AIO Companion 0.2.1

DPC-AIO Companion is a **skills-only** companion for DPC-AIO 1.2.0 Enterprise + Samsung OEM AIO. It provides bounded workflows for repository-native Gradle builds, root-cause-first CI diagnosis, production Android Enterprise enrollment validation, Plugin Autopilot packaging and release verification.

The plugin does **not** contain or execute the Android DPC itself. It does not bundle APKs, Android SDK/NDK components, Gradle caches, signing material, KPE/KLM credentials, reset tokens or private keys. Android/Knox/SEM/OEM actions remain authorized and executed by DPC-AIO and the target platform.

## 1.2.0 verification coverage

- 1.0.2 production QR signing/public-APK/Full Offline prerequisite.
- Protected Targets/Operations and transaction/rollback contracts.
- Android DPM → official Knox → Samsung SEM → OEM Internals Lab capability evidence.
- Package Trust 2.0 and hardened data-only APK+ import.
- Work Profile Lifecycle 2.0 and Credential Recovery 2.0.
- Secret and non-SDK safety scans with runtime evidence kept separate from source PASS.
- 1.1.1 build-readiness, 1.1.2 Android runtime-smoke, 1.1.3 GitHub publish-readiness and 1.2.0 QR release-bundle contracts.

This package intentionally has no MCP server, app UI, lifecycle hooks, plugin-owned remote service or fabricated public listing URLs.
