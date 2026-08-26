# DPC-AIO Development Checkpoint 0.6.0-R1

## Scope
This checkpoint consolidates the previous DPC-AIO work into one application/project and adds:

- Knox Mock Binder + DPM package-control fallback.
- KnoxZT Framework recovery for `com.samsung.android.knox.zt.framework`.
  - Detect ready / disabled / uninstalled-for-user / missing.
  - Device Owner `enableSystemApp()` and `installExistingPackage()`.
  - Typed Shizuku fallback for `pm enable` and `cmd package install-existing`.
  - Optional trusted HTTPS APK source with APK SHA-256 + signer SHA-256 verification before PackageInstaller commit.
  - Startup retry hooks and Activity Explorer integration.
- One AIO dashboard with Activity Explorer, Permission Manager, Samsung Settings, Google Account Manager, KnoxZT, Network/DoH, Scenario Recorder/Replay, and NFC Lab.
- Device Owner Private DNS controls + DoH diagnostic client + pure hosts-style DNS rules engine.
- Scenario recorder, deterministic replay planner, import/export archive codec, and overlay controller.
- NFC Lab:
  - NfcA, NfcB, NfcF, NfcV, IsoDep, NDEF, NdefFormatable, NfcBarcode, MifareClassic, MifareUltralight.
  - owned/synthetic trace import/export and HCE replay.
  - supplied-key MIFARE read/write helpers; no key-recovery implementation.
- Native trace bridge with monotonic clock, runtime page size, and trace markers.
- Project gates are non-blocking audits and contain no technology denylist.

## Platform
- minSdk 29
- compileSdk 37
- targetSdk 37
- Java/Kotlin host toolchain target 21
- ABIs: arm64-v8a, armeabi-v7a, x86_64, x86

## Verification
Host/pure tests and Android contract tests are executed through `tools/run_host_tests.sh`.
The actual Android Gradle build is not claimed successful in this environment because the Gradle wrapper distribution is not cached and the sandbox cannot resolve `services.gradle.org`.

Attempted command:

```text
./gradlew :app-dpc:assembleEnterpriseDebug --offline
```

Observed blocker:

```text
java.net.UnknownHostException: services.gradle.org
```

No APK is included or claimed as compiled by this checkpoint.
