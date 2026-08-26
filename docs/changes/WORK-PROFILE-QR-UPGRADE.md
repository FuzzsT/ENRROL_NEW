# DPC-AIO Work Profile QR Upgrade

Date: 2026-08-22

## Result

DPC-AIO now has an explicit company-owned work-profile provisioning path in addition to the existing auto / fully-managed path.

### Generator

`tools/provisioning/generate_provisioning.py` now accepts:

```text
--provisioning-mode auto|work-profile|fully-managed
```

The requested mode is placed in `android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE` under:

```text
io.dpcaio.extra.PROVISIONING_MODE
```

A work-profile generation also emits `work-profile-qr.png`.

### Android provisioning mode

`ProvisioningModeActivity` reads the admin extras and delegates mode selection to `ProvisioningModeSelector`.

For `work-profile`, it returns `DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE` only when that mode is present in `EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES`. It does not silently fall back to fully managed.

### Profile completion

`AioDeviceAdminReceiver.onProfileProvisioningComplete()` checks `isProfileOwnerApp()` and calls `DevicePolicyManager.setProfileEnabled()` before continuing normal startup work.

### Build outputs

Every Gradle provisioning run now produces the primary provisioning set plus an explicit work-profile set:

```text
work-profile-provisioning.json
work-profile-provisioning-payload.txt
work-profile-provisioning-metadata.json
work-profile-qr.png
```

### CI validation

GitHub Actions runs:

```bash
python3 tools/provisioning/verify_work_profile_qr.py \
  --json dist/work-profile-provisioning.json \
  --qr dist/work-profile-qr.png \
  --apk dist/DPC-AIO-enterprise-debug.apk
```

and writes `work-profile-validation.json` before uploading/releasing the artifacts.

The validator checks:

- DPC component
- HTTPS APK URL shape
- exactly one valid SHA-256 package or signature checksum
- explicit `work-profile` mode
- QR PNG decodes to the same JSON payload
- package checksum against the actual APK, or signing-certificate checksum through `apksigner`

## Verification performed locally

```text
test_work_profile_provisioning: PASS
test_build_provisioning_qr: PASS
test_provisioning_build_integration: PASS
test_provisioning_android_contract: PASS
all tools/tests/test_*.py: PASS
provisioning-server: 5/5 PASS
PROJECT_VERIFY: PASS
RELEASE_AUDIT: PASS
WORKFLOW_YAML: PASS
```

The generated test QR was decoded with OpenCV and the decoded JSON matched `provisioning.json` exactly.

## Remaining live-enrollment gate

This workspace cannot complete the real Android APK build because its shell cannot resolve `services.gradle.org`, so no claim is made that a real production enrollment has already succeeded.

A QR becomes enrollment-ready only after GitHub Actions builds the real APK, the new work-profile validator exits 0 against that APK, and the HTTPS APK URL is actually reachable by Android Setup Wizard.

The six-tap / OOBE QR path is intended for company-owned provisioning. Personally-owned/BYOD work-profile enrollment uses the personal-device managed-profile enrollment flow rather than treating the OOBE QR as the BYOD path.

## Continuous release URL fix

Manual `workflow_dispatch` runs without a custom `apk_url` no longer embed `releases/latest/download/...` in the QR.
They use the stable public asset path:

```text
https://github.com/<owner>/<repo>/releases/download/dpc-aio-continuous/DPC-AIO-enterprise-debug.apk
```

After the work-profile validator passes, the workflow creates or updates the prerelease `dpc-aio-continuous` and uploads the current APK plus work-profile artifacts with `gh release upload --clobber`.

The workflow then downloads the APK again through that HTTPS URL with plain unauthenticated `curl -fL` and requires it to be byte-identical to the just-built APK. A private or otherwise non-public release URL therefore fails CI instead of producing a QR that only looks valid.

Tagged builds continue to use their tag-specific GitHub Release URL and the existing tag-release publishing step. A manually supplied `apk_url` is left untouched and does not update `dpc-aio-continuous`.
