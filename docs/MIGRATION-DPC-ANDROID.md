# DPC-AIO migration target: dpc_android

Destination repository: `https://github.com/local-localhost-app-system/dpc_android`

This repository is the expanded DPC-AIO 1.1.4 source tree. The Android application is built directly from the repository root; source is not hidden inside a release archive.

## Enrollment release paths

### Production / update-safe

Run **Build AIO + enrollment QR** after configuring:

- `DPC_AIO_RELEASE_KEYSTORE_B64` (secret)
- `DPC_AIO_RELEASE_STORE_PASSWORD` (secret)
- `DPC_AIO_RELEASE_KEY_ALIAS` (secret)
- `DPC_AIO_RELEASE_KEY_PASSWORD` (secret)
- `DPC_AIO_EXPECTED_SIGNING_CERT_SHA256` (repository variable or secret)

The workflow fails closed when stable signing is absent or the observed APK signer does not match the expected fingerprint.

### Emergency / first enrollment

Run **Emergency enrollment (ephemeral signing)**. No signing secret is required. The workflow creates a temporary PKCS12 key, builds and verifies the APK and QR payloads, publishes them to prerelease tag `dpc-aio-emergency-enrollment`, re-downloads the public APK and requires byte equality.

This is enrollment-capable but **not update-stable** across independent runs because each run gets a new signer. Re-enrolling or moving to the stable production signer requires an explicit migration/reset strategy.

## Device Owner

Use `device-owner-qr.png` only on a clean/factory-reset device during Setup Wizard. The QR binds the download URL and checksum to the published `DPC-AIO-enterprise-release.apk`.

## Work Profile

Use `work-profile-qr.png` with a supported managed-profile provisioning path. The DPC implements the provisioning mode/compliance lifecycle and keeps Device Owner and Work Profile payloads separate.

## Evidence

Do not promote source/static PASS to hardware verification. Enrollment readiness is layered: build -> signer verification -> QR/APK binding -> release publication/public byte check -> emulator/device smoke -> real target-device provisioning.

## One-command helpers

Windows:

```text
PUSH-TO-GITHUB.bat
START-EMERGENCY-ENROLLMENT.bat
```

Linux/macOS:

```text
./PUSH-TO-GITHUB.sh
./START-EMERGENCY-ENROLLMENT.sh
```

The push helper reads the current remote `main` SHA and uses `--force-with-lease`, so it refuses to overwrite an unexpectedly changed remote branch. The enrollment helper requires an authenticated GitHub CLI (`gh auth login`) and starts the no-secret emergency workflow, then watches it to completion.
