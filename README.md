# DPC-AIO 1.1.4 — Enterprise + Samsung OEM AIO

Android Enterprise DPC workspace with production QR enrollment, protected enterprise operations, deterministic capability routing and explicit verification evidence.

## 1.1.4 highlights

- **QR Release Bundle:** tagged/continuous GitHub releases now include dedicated Work Profile, Device Owner and compatibility QR assets plus JSON/payload/metadata, validation reports, `SHA256SUMS.txt`, `RELEASE-INDEX.json`, `QR-README.md` and a single `DPC-AIO-1.1.4-QR-RELEASE-BUNDLE.zip`.
- The bundle is created only after the signed APK, QR/APK binding checks and AOSP Device Owner runtime smoke succeed in CI.
- **Unified enrollment workflow:** stable and emergency enrollment now share `.github/workflows/build-aio-enrollment.yml`; `signing_mode=auto|stable|emergency` selects the signing/release route without duplicating the Android build, QR validation, runtime smoke or publish pipeline.
- **Signing isolation:** stable/emergency signing credentials are written only to a runner-temp `0600` env file, sourced by the first-party preflight/build/verify steps, and deleted together with the keystore before third-party emulator actions.

- **GitHub Upload Ready / Hardened:** read-only build permissions, isolated write-only publish job, step-scoped signing secrets, immutable SHA-pinned GitHub Actions and signing-material cleanup before emulator execution.
- **Build/Runtime Readiness:** pinned NDK 28.2.13676358 + CMake 3.22.1, machine-readable Android build preflight and CI evidence artifact before Gradle compilation.

- 1.0.2 production QR invariants retained: `enterpriseRelease`, stable signing, exact public APK URL/byte/certificate binding and explicit Full Offline routing.
- Protected Targets/Operations guard prevents normal UI, batch and offline automation from disabling the DPC or recovery-critical components.
- Enterprise Transaction Engine performs preview/revalidation/apply/readback and compare-and-set rollback without overwriting later external state.
- Capability routing prefers Android DPM, then official Knox, Samsung SEM and finally the isolated OEM Internals Lab; unsupported routes remain explicit.
- Package Trust 2.0 verifies signer lineage/multi-signer semantics, install-source evidence, runtime integrity and split diagnostics.
- Hardened APK+ importer treats archives as data only and rejects traversal, symlinks, duplicates, decompression abuse and package/version/signer mismatch.
- Work Profile Lifecycle 2.0, encrypted Credential Recovery 2.0, Application Restrictions readback, whole-app state and Device-Owner location policy.

## Dual Apps workspace model

`Dual Apps` follows the useful isolation model identified in AppDualZuku: additional instances are separate Android user/profile sandboxes rather than repackaged copies inside one user ID. The module creates managed or clone profiles when supported by the OS, then enables an already-installed package in the selected profile.

This means application signatures, package identity and normal update compatibility are preserved. Android and OEM limits on profile count and profile types still apply, so the number of instances is bounded by the device rather than artificially claimed as unlimited.

A selected package is enabled in another profile through the existing authorized Shizuku `install-existing` route. The UI never accepts arbitrary shell commands; workspace operations are exposed as typed AIDL methods with argument validation.

The same module reports the connected Shizuku service UID for diagnostics. UID 1000 is only detected when the service already has that identity; DPC-AIO does not contain a path to obtain, spoof, or escalate to system UID.

## PR CI layout

Pull-request verification is split into parallel `Source contracts` and `Enterprise debug APK` jobs. A final `verify` job aggregates both results, preserving a stable required-check name while making failures much easier to locate.

## Modern UI work

The current UI modernization is tracked in PR #34 (`ui/safe-area-motion-refresh`). It adds a shared classic-View design system (`ModernUi`), global safe-area handling for modern Android edge-to-edge behavior, lightweight Activity entrance motion, and refreshed Dashboard, Permission Manager, Enterprise Policy Hub, and Module Center surfaces.

Production signing recovery remains tracked separately in Issue #33.

## Enrollment quick start

Repository target: `FuzzsT/ENRROL_NEW`.

Enrollment CI is intentionally unified in `.github/workflows/build-aio-enrollment.yml`:

- **Stable production** — tag `v*` automatically uses stable signing. A manual run can select `signing_mode=stable`. It requires the stable signing keystore/fingerprint in GitHub Secrets/Variables, runs Device Owner smoke, publishes the exact APK + QR bundle, and verifies public APK byte identity after release.
- **Emergency enrollment** — push to `main` uses emergency mode, and a manual run can select `signing_mode=emergency`. It creates a run-specific PKCS12 signer, builds the same `enterpriseRelease`, generates Work Profile + Device Owner QR codes bound to that exact APK, publishes them to `dpc-aio-emergency-enrollment`, and verifies the public APK bytes. The resulting APK can be enrolled, but a later build signed with a different key cannot update it in place.

The convenience launchers `START-EMERGENCY-ENROLLMENT.sh` and `START-EMERGENCY-ENROLLMENT.ps1` dispatch the unified workflow with `signing_mode=emergency`.

For a **Device Owner** QR, use a clean/factory-reset test device and scan `device-owner-qr.png` during Android Setup. For a **Work Profile**, use `work-profile-qr.png` in a supported managed-profile provisioning flow. Do not treat QR generation alone as runtime proof; use the CI validation JSON and Device Owner smoke evidence attached to the run/release.

## Repository layout

- `apps/dpc/app/` — final Android application module (`:app-dpc`).
- `apps/dpc/modules/` — Android Enterprise, protection, installer, Samsung/Knox/OEM and other domain modules.
- `apps/dpc/integrations/` — explicitly authorized Shizuku/Dhizuku/native adapters.
- `services/provisioning/` — provisioning HTTP service and tests.
- `plugins/chatgpt-companion/` — companion plugin package.
- `tools/` — host verification, QR, release and plugin tooling.
- `docs/` — release, publishing and architecture documentation.

## Verification

```bash
./tools/run_host_tests.sh
python3 tools/tests/test_113_release_gate_contract.py
python3 tools/tests/test_non_sdk_api_scan.py
python3 tools/tests/test_release_secret_scan.py
./tools/verify-aio build-readiness --root . --offline
```

Runtime claims require actual platform evidence. Source/static PASS never implies APK build, Device Owner, Work Profile, Knox, SEM or OEM runtime verification.
