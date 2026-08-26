# DPC-AIO Android Enterprise QR provisioning

DPC-AIO 1.0.1 generates two explicit enrollment artifacts from the final APK:

- `work-profile-*` for managed-profile / Profile Owner enrollment;
- `device-owner-*` for fully managed / Device Owner enrollment.

A legacy compatibility `provisioning-*` set is retained. Its default mode is now `work-profile`; `auto` remains available only when explicitly selected.

## Build locally

Install the QR dependency:

```bash
python3 -m pip install -r tools/provisioning/requirements.txt
```

Set an HTTPS URL reachable by Android Setup Wizard:

```bash
export DPC_AIO_PROVISIONING_APK_URL="https://mdm.example.org/DPC-AIO-enterprise-debug.apk"
export DPC_AIO_ENROLLMENT_TOKEN="optional-one-time-token"
export DPC_AIO_POLICY_PROFILE="default"
export DPC_AIO_PROVISIONING_MODE="work-profile"
export DPC_AIO_ALLOW_OFFLINE="false"
./gradlew :app-dpc:assembleEnterpriseDebug
```

The build finalizer creates:

```text
apps/dpc/build/outputs/provisioning/enterprise/debug/
├── provisioning.json
├── provisioning-payload.txt
├── provisioning-metadata.json
├── provisioning-qr.png
├── work-profile-provisioning.json
├── work-profile-provisioning-payload.txt
├── work-profile-provisioning-metadata.json
├── work-profile-qr.png
├── device-owner-provisioning.json
├── device-owner-provisioning-payload.txt
├── device-owner-provisioning-metadata.json
└── device-owner-qr.png
```

The generator prefers the APK signing-certificate SHA-256 checksum when `apksigner` can read it. Otherwise it uses the SHA-256 checksum of the APK file. Exactly one checksum field is emitted.

`android.app.extra.PROVISIONING_ALLOW_OFFLINE` is kept as a top-level provisioning extra. Set `DPC_AIO_ALLOW_OFFLINE=true` only for deployments that intentionally need Android 13+ offline provisioning.

## Validate both QR files

```bash
python3 tools/provisioning/verify_provisioning_qr.py \
  --json work-profile-provisioning.json \
  --qr work-profile-qr.png \
  --apk DPC-AIO-enterprise-debug.apk \
  --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" \
  --expected-mode work-profile

python3 tools/provisioning/verify_provisioning_qr.py \
  --json device-owner-provisioning.json \
  --qr device-owner-qr.png \
  --apk DPC-AIO-enterprise-debug.apk \
  --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" \
  --expected-mode fully-managed
```

Validation covers the DPC component, HTTPS download URL, optional exact release-URL binding, exactly one checksum, requested mode, QR-to-JSON equality and APK checksum/signature agreement. The release workflow also downloads the published APK and requires it to be byte-identical to the APK used to generate the QR, for both continuous and tag releases.

## Android 12+ admin-integrated provisioning

`ProvisioningModeActivity` handles `android.app.action.GET_PROVISIONING_MODE`. It only returns a mode that Android included in `EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES`:

- `work-profile` → `PROVISIONING_MODE_MANAGED_PROFILE`;
- `fully-managed` → `PROVISIONING_MODE_FULLY_MANAGED_DEVICE`.

If the requested explicit mode is not allowed, provisioning is cancelled rather than silently switching ownership mode.

The DPC component is:

```text
io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver
```

The APK URL must be HTTPS and reachable without a signed-in GitHub session.
