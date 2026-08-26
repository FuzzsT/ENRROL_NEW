# DPC-AIO Continuous Enrollment Release Fix

Date: 2026-08-22

## Root cause

The manual GitHub Actions path used `releases/latest/download/DPC-AIO-enterprise-debug.apk` when `apk_url` was omitted, while only tag builds published GitHub Release assets. The provisioning payload could therefore be structurally valid while Android Setup Wizard received HTTP 404 for the APK.

## Fix

- Manual `workflow_dispatch` without `apk_url` uses `dpc-aio-continuous`.
- The release is created as a prerelease on first use and updated on later runs.
- Assets are replaced with `gh release upload --clobber`.
- The APK URL embedded in the QR is downloaded again without GitHub authentication.
- CI requires the downloaded APK to be byte-identical to `dist/DPC-AIO-enterprise-debug.apk`.
- Custom `apk_url` and tagged release flows are unchanged.
- Regression tests reject a return to `releases/latest/download/...`.

## Important

This makes the GitHub-hosted default fail closed for a private/non-public repository: the unauthenticated download check will fail. That is intentional because Android Setup Wizard cannot use a GitHub-authenticated Actions artifact as an unattended APK download URL.

A real device enrollment is still not claimed until GitHub Actions completes the actual Android build and the remote public-URL check passes.
