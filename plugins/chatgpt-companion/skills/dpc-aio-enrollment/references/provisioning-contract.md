# DPC-AIO provisioning contract

DPC-AIO 0.6.7 always exposes two explicit Android Enterprise enrollment sets in addition to the compatibility `provisioning-*` outputs.

```text
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/work-profile-provisioning.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/work-profile-provisioning-payload.txt
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/work-profile-provisioning-metadata.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/work-profile-qr.png

apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/device-owner-provisioning.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/device-owner-provisioning-payload.txt
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/device-owner-provisioning-metadata.json
apps/dpc/build/outputs/provisioning/<flavor>/<buildType>/device-owner-qr.png
```

The compatibility set remains:

```text
provisioning.json
provisioning-payload.txt
provisioning-metadata.json
provisioning-qr.png
```

Its default mode is `work-profile`. `auto` is retained only when explicitly requested.

Generator: `tools/provisioning/generate_provisioning.py`.
Validator: `tools/provisioning/verify_provisioning_qr.py`.

Default component:

```text
io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver
```

Provisioning keys include:

```text
android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION
android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM
android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE
android.app.extra.PROVISIONING_ALLOW_OFFLINE
```

DPC-AIO places the requested ownership mode in `PROVISIONING_ADMIN_EXTRAS_BUNDLE`:

```text
io.dpcaio.extra.PROVISIONING_MODE = auto | work-profile | fully-managed
```

For explicit artifacts the values are fixed: `work-profile` or `fully-managed`. On Android 12+ `ProvisioningModeActivity` may return only a value present in `EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES`; explicit mode mismatch cancels provisioning rather than falling back.

`PROVISIONING_ALLOW_OFFLINE` remains a top-level provisioning extra. The Gradle/CI switch is `DPC_AIO_ALLOW_OFFLINE`.

Checksum mode `auto` prefers the signing-certificate SHA-256 digest from `apksigner`; otherwise it uses the APK-file SHA-256 checksum. Exactly one provisioning checksum field is valid.

Verify work profile:

```bash
python3 tools/provisioning/verify_provisioning_qr.py \
  --json work-profile-provisioning.json \
  --qr work-profile-qr.png \
  --apk DPC-AIO-enterprise-debug.apk \
  --expected-mode work-profile
```

Verify device owner:

```bash
python3 tools/provisioning/verify_provisioning_qr.py \
  --json device-owner-provisioning.json \
  --qr device-owner-qr.png \
  --apk DPC-AIO-enterprise-debug.apk \
  --expected-mode fully-managed
```
