# Google TestDPC provisioning reference

Upstream reference used for DPC-AIO enrollment QR compatibility:

- Repository: `googlesamples/android-testdpc`
- Branch: `master`
- Reference commit observed during integration: `d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e`
- Upstream sample `qrcode.png` blob SHA observed in repository listing: `10a442b94890ec03fe1ce3f103828cee3dbdb1ed`
- Reference file: `README.md`, section **QR code provisioning (Device Owner N+ only)**
- Upstream repository also publishes a sample `qrcode.png`.

The TestDPC example QR contains these Android Enterprise keys:

- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`

DPC-AIO follows that structure. The build generator prefers
`PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` when `apksigner` can read the final APK signer certificate.
If signer information is unavailable, it emits the supported
`android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM` based on SHA-256 of the final APK.

DPC-AIO component:

`io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver`

The download location must be an HTTPS URL reachable by Android Setup Wizard. For GitHub tag builds,
the workflow computes a GitHub Release asset URL before building and publishes the APK and matching QR together.
