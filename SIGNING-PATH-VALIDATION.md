# DPC-AIO Signing / APK Path Validation

Validated: 2026-08-26

## Effective order

### Main workflow
1. Prepare signing material (keystore only; this step does not sign an APK).
2. Verify the keystore exists, is non-empty, resolves under `$RUNNER_TEMP`, and contains the configured alias.
3. Run `:app-dpc:assembleEnterpriseRelease`. Android Gradle Plugin creates and signs the release APK as one build operation.
4. Resolve the built APK under `apps/dpc/app/build/outputs/apk/enterprise/release`.
5. Require exactly one APK, canonicalize it with `realpath -e`, ensure it stays inside the expected output directory, and require a non-empty regular file.
6. Persist that exact path as `DPC_AIO_BUILT_APK_PATH`.
7. Verify the APK certificate with stable Build Tools `36.0.0/apksigner`.
8. Reuse `DPC_AIO_BUILT_APK_PATH` for collection into `dist/`; do not search a second time.
9. Validate QR/APK binding, runtime smoke, bundle, artifacts, then publish release.

### Emergency workflow
The same keystore-path and built-APK-path gates are applied before signer verification and asset collection.

## Failure behavior

The workflow fails before release publication when:
- signing keystore is missing or empty;
- signing keystore resolves outside runner temporary storage;
- expected alias/password cannot open the keystore;
- APK output directory does not exist;
- zero or multiple APK files are present in the enterprise release output directory;
- APK resolves outside the expected output directory;
- APK is missing or zero bytes;
- signer digest differs from the signing certificate selected before build.

## Local contracts

- workflow YAML parse: PASS
- signing/APK path contract: PASS
- provisioning integration: PASS
- QR production readiness: PASS
- runtime smoke contract: PASS
- GitHub upload readiness: PASS
- QR release bundle contract: PASS
- release secret scan: PASS
- dense QR decoder: PASS
- DPC Android migration contract: PASS after transient bytecode cleanup
