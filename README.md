# DPC-AIO 1.2.0 — Enterprise + Samsung OEM AIO

Android Enterprise DPC workspace with production QR enrollment, protected enterprise operations, deterministic capability routing and explicit verification evidence.

## 1.2.0 highlights

- **QR Release Bundle:** tagged/continuous GitHub releases now include dedicated Work Profile, Device Owner and compatibility QR assets plus JSON/payload/metadata, validation reports, `SHA256SUMS.txt`, `RELEASE-INDEX.json`, `QR-README.md` and a single `DPC-AIO-1.2.0-QR-RELEASE-BUNDLE.zip`.
- The bundle is created only after the signed APK, QR/APK binding checks and AOSP Device Owner runtime smoke succeed in CI.

- **GitHub Upload Ready / Hardened:** read-only build permissions, isolated write-only publish job, step-scoped signing secrets, immutable SHA-pinned GitHub Actions and signing-material cleanup before emulator execution.
- **Build/Runtime Readiness:** pinned NDK 28.2.13676358 + CMake 3.22.1, machine-readable Android build preflight and CI evidence artifact before Gradle compilation.

- 1.0.2 production QR invariants retained: `enterpriseRelease`, stable signing, exact public APK URL/byte/certificate binding and explicit Full Offline routing.
- Protected Targets/Operations guard prevents normal UI, batch and offline automation from disabling the DPC or recovery-critical components.
- Enterprise Transaction Engine performs preview/revalidation/apply/readback and compare-and-set rollback without overwriting later external state.
- Capability routing prefers Android DPM, then official Knox, Samsung SEM and finally the isolated OEM Internals Lab; unsupported routes remain explicit.
- Package Trust 2.0 verifies signer lineage/multi-signer semantics, install-source evidence, runtime integrity and split diagnostics.
- Hardened APK+ importer treats archives as data only and rejects traversal, symlinks, duplicates, decompression abuse and package/version/signer mismatch.
- Work Profile Lifecycle 2.0, encrypted Credential Recovery 2.0, Application Restrictions readback, whole-app state and Device-Owner location policy.


## Enrollment quick start

Repository target: `local-localhost-app-system/dpc_android`.

Two CI enrollment paths are intentionally separate:

- **Build AIO + enrollment QR** (`.github/workflows/build-aio-enrollment.yml`) — production path. Requires a stable signing keystore/fingerprint in GitHub Secrets/Variables, runs the Android Device Owner smoke test, publishes the exact APK + QR bundle, and verifies the public APK bytes after release.
- **Emergency enrollment (ephemeral signing)** (`.github/workflows/build-emergency-enrollment.yml`) — first-run/lab path when stable signing secrets are not configured yet. It requires no manual configuration inputs, resolves safe defaults automatically, and creates a run-specific PKCS12 signer, builds `enterpriseRelease`, generates Work Profile + Device Owner QR codes bound to that exact APK, publishes them to the `dpc-aio-emergency-enrollment` prerelease, and verifies the public APK bytes. The resulting APK can be enrolled, but a later build signed with a different key cannot update it in place.

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
