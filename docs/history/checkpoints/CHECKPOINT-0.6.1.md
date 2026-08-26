# DPC-AIO checkpoint 0.6.1

Adds TestDPC-style Android Setup QR provisioning to the consolidated 0.6 project.

## Added

- Build-time `provisioning.json` and `provisioning-qr.png` generation after every app variant assemble task.
- Signature checksum via `apksigner --print-certs` with package SHA-256 fallback.
- Optional enrollment token and policy profile in `PROVISIONING_ADMIN_EXTRAS_BUNDLE`.
- GitHub Actions build that packages APK + provisioning artifacts and publishes tag release assets.
- Provisioning server support for either package or signature checksum.
- Upstream TestDPC provisioning reference documentation.

## Build limitation in this sandbox

The Android Gradle build could not be completed because the wrapper distribution is not cached and the sandbox
cannot resolve `services.gradle.org`. The failure occurs while fetching Gradle 9.7.0, before Android compilation.
Host tests, provisioning tests, structural contracts, YAML parsing and release audit were run independently.
