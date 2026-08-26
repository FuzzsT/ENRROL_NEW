# DPC-AIO checkpoint 0.5.2

## Knox offline/fail-open integration

This checkpoint adds an internal MDM license gate for lab/tst/eng that is intentionally separate from the real Samsung Knox license state.

### Runtime states
- `MDM_GATE_ACTIVE`: allows the DPC/MDM application to start and continue its own workflow.
- `REAL_KNOX_ACTIVE`: true only after a real Knox callback has been recorded as active.
- `LAB_SIMULATED_ACTIVE`: true only for lab/tst/eng when the bundled ECDSA LAB token verifies.
- `DPM_PACKAGE_CONTROL`: Device Owner/Profile Owner package hide/suspend fallback.
- `KNOX_ONLY_APIS`: enabled only for a real Knox-active state.

### Startup triggers
- Device admin enabled.
- Profile provisioning complete.
- App process start.
- BOOT_COMPLETED / LOCKED_BOOT_COMPLETED.
- MY_PACKAGE_REPLACED.

### Package-control fallback
`KnoxAwarePackageController.disableLike()` tries verified `DevicePolicyManager.setApplicationHidden()` first, then verified `setPackagesSuspended()`. If both fail, it returns `REAL_KNOX_REQUIRED`; it never labels a simulated LAB state as a real Knox permission.

### Flavor separation
- `enterprise`, `systemPrivileged`: local LAB license provider always returns false.
- `lab`, `tst`, `eng`: share LAB token/public-key assets and the dedicated `knox-license-lab` verifier module.
- Production code depends only on `knox-license-core`; test token parsing/verifier code lives in `knox-license-lab`.

### Fail-open guarantee
The Knox startup path contains no reboot, shutdown, factory reset, or wipe action. A pending/missing real Knox license does not by itself disable application startup. Real Knox-only capabilities remain unavailable until real authorization exists.

## Verification
- Kotlin core tests: PASS, including KnoxStartupGateTest, KnoxPackageControlPlannerTest, KnoxRuntimeAccessPolicyTest and KnoxLabLicenseVerifierTest.
- Android startup contract: PASS.
- Knox fail-open contract: PASS.
- Knox LAB separation/token bundle contracts: PASS.
- Shizuku/Dhizuku/provisioning contracts: PASS.
- Provisioning server tests: 3/3 PASS.
- ANDROID_CONTRACTS: PASS.
- PROJECT_VERIFY: PASS.
- RELEASE_GATE: PASS.

## Android build limitation
`:app-dpc:assembleLabDebug` cannot run in this sandbox because Gradle 9.7.0 is not cached locally and DNS access to `services.gradle.org` is unavailable. No APK is claimed as built.
