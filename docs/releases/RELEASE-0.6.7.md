# DPC-AIO 0.6.7

## Enrollment QR hardening

- The public/default provisioning mode is now explicit `work-profile`; `auto` remains available only when selected.
- Every Android build QR finalizer publishes both `work-profile-*` and `device-owner-*` artifacts.
- Fully managed provisioning now has the canonical `device-owner-qr.png` filename while keeping `fully-managed-qr.png` as a generator compatibility alias.
- Added `tools/provisioning/verify_provisioning_qr.py` for mode-aware QR/JSON/APK validation; the old work-profile verifier remains as a compatibility wrapper.
- GitHub Actions validates and publishes both ownership-mode QR sets.
- Added `allow_offline` workflow input / `DPC_AIO_ALLOW_OFFLINE` build wiring while preserving Android's top-level `PROVISIONING_ALLOW_OFFLINE` extra.
- Fixed the work-profile selector test path after the repository moved the application into `apps/dpc/app`.

## Versions

- DPC-AIO: `0.6.7` (`versionCode 14`)
- DPC-AIO Companion: `0.1.4`
