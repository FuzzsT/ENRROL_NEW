# Enrollment readiness — DPC-AIO 1.1.4 / dpc_android

Target repository: `local-localhost-app-system/dpc_android`.

## Source state

- DPC source is expanded at repository root.
- `:app-dpc` is the final Android application module.
- Device Owner and Work Profile provisioning are separate generated payloads.
- APK+ installer nullability and Android module classpath fixes through the latest v9 source are included.
- Gradle source completeness guard confirms declared modules contain real sources; normal Kotlin-only `compileJava NO-SOURCE`, resource-less `processResources NO-SOURCE`, and non-JNI `mergeNativeLibs NO-SOURCE` are not treated as failures.

## Production enrollment

Workflow: `.github/workflows/build-aio-enrollment.yml`

Requires stable release signing material. It builds `enterpriseRelease`, verifies signer SHA-256, validates QRs against the exact APK, runs Device Owner emulator smoke, publishes release assets, re-downloads the public APK, and requires byte equality.

## Emergency enrollment

Workflow: `.github/workflows/build-emergency-enrollment.yml`

Requires no signing secrets. It creates a run-specific PKCS12 signer in runner temporary storage, builds `enterpriseRelease`, validates Device Owner/Work Profile QRs against the exact APK, runs Device Owner emulator smoke, publishes prerelease `dpc-aio-emergency-enrollment`, and verifies the public APK bytes.

Emergency signing is suitable for an initial/lab enrollment but not for update continuity between independent runs. Production-managed devices should use one backed-up stable signer from the first production enrollment onward.

## What still requires GitHub/runtime evidence

The repository is source/CI ready, but a real enrollment-ready release is only established after a GitHub Actions run successfully produces the APK, signer evidence, QR validation reports, public-download equality check, and runtime smoke. Physical target-device provisioning remains separate hardware evidence.
