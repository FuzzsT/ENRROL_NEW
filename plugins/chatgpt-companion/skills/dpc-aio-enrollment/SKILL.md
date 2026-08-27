---
name: dpc-aio-enrollment
description: Use when DPC-AIO Android Enterprise provisioning JSON or QR artifacts for a company-owned work profile or fully managed device must be generated, checked, or matched to a real built APK and HTTPS download location.
---

# DPC-AIO Enrollment

## Required evidence

Treat provisioning as verified only when the referenced APK exists and the checksum/signature data was derived from that APK. Never fabricate or reuse an unrelated checksum. Distinguish company-owned work-profile provisioning from fully managed provisioning before approving a QR.

## Production QR / Full Offline prerequisite

For DPC-AIO 1.2.0, retain the 1.0.2 production invariant: production Setup QR uses `enterpriseRelease`, a stable secret-managed signing identity, an anonymously downloadable HTTPS APK URL, and checksum/certificate data derived from that exact APK. `FULL_OFFLINE` and `OFFLINE_THEN_SYNC` must carry the DPC-owned `io.dpcaio.extra.ENROLLMENT_OFFLINE_MODE` admin extra; a `FULL_OFFLINE` failure must not silently fall through to online enrollment.

## Enrollment Engine 0.9.0

When v2 enrollment is configured, require an HTTPS `io.dpcaio.extra.ENROLLMENT_ENDPOINT`, a build-time Ed25519 public trust anchor, and a persistent server token store. Verify the `reserve → validate → signed bootstrap → commit` flow; a reservation must not consume the token before commit. Treat QR, KME, and zero-touch parsing as structural support only; KME/zero-touch are verified only after real service/device tests. Never expose enrollment tokens, KME passwords, signing private keys, or auth headers in diagnostics.

## Procedure

1. Identify the built APK under `apps/dpc/build/outputs/apk/<flavor>/<buildType>/`.
2. Verify the Setup Wizard download URL is HTTPS and points to the same APK that will be distributed. Authenticated GitHub Actions artifact URLs are not suitable for unattended Setup Wizard download; use a public release asset or another reachable HTTPS endpoint.
3. For company-owned work-profile provisioning, use `--provisioning-mode work-profile` or the build-produced `work-profile-*` artifacts. The payload must carry `io.dpcaio.extra.PROVISIONING_MODE=work-profile` inside `PROVISIONING_ADMIN_EXTRAS_BUNDLE`.
4. Run `tools/provisioning/verify_work_profile_qr.py --json <work-profile-json> --qr <work-profile-png> --apk <apk>` before calling a work-profile QR structurally verified.
5. Verify the DPC component defaults to `io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver` unless the build intentionally changes it.
6. Verify the payload uses either the signing certificate SHA-256 checksum when `apksigner` can read it, or the package SHA-256 checksum fallback from the final APK.
7. Confirm `ProvisioningModeActivity` can return `PROVISIONING_MODE_MANAGED_PROFILE` and that `AioDeviceAdminReceiver.onProfileProvisioningComplete()` enables the profile with `DevicePolicyManager.setProfileEnabled()`.
8. Do not describe a six-tap/OOBE QR as the BYOD flow. Personally-owned work profiles use the managed-profile enrollment path initiated from Android settings or the EMM enrollment flow.

Read `references/provisioning-contract.md` for exact paths and keys.
