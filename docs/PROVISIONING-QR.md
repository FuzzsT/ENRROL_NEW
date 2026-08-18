# DPC-AIO Android Setup QR provisioning

DPC-AIO follows the Device Owner QR structure used by Google's TestDPC sample.

## Build locally

Install the QR build dependency once:

```bash
python3 -m pip install -r tools/provisioning/requirements.txt
```

Set a HTTPS URL that Android Setup Wizard can reach after factory reset:

```bash
export DPC_AIO_PROVISIONING_APK_URL="https://mdm.example.org/DPC-AIO-enterprise-debug.apk"
export DPC_AIO_ENROLLMENT_TOKEN="optional-one-time-token"
export DPC_AIO_POLICY_PROFILE="default"
./gradlew :app-dpc:assembleEnterpriseDebug
```

`assembleEnterpriseDebug` finalizes with `generateEnterpriseDebugProvisioningQr` and creates:

```text
app-dpc/build/outputs/provisioning/enterprise/debug/
├── provisioning.json
├── provisioning-payload.txt
├── provisioning-metadata.json
└── provisioning-qr.png
```

The generator runs against the final APK. It prefers the SHA-256 digest of the signing certificate when
`apksigner verify --print-certs` is available. If the signer certificate cannot be read, it uses the SHA-256
package checksum of the final APK.

## GitHub build

`.github/workflows/build-aio-enrollment.yml` builds `enterpriseDebug`, generates the matching QR and uploads:

```text
DPC-AIO-enterprise-debug.apk
provisioning.json
provisioning-payload.txt
provisioning-metadata.json
provisioning-qr.png
SHA256SUMS.txt
```

For `v*` tags the same files are published as GitHub Release assets. The QR download URL is calculated before
the build, so its checksum and URL correspond to the APK uploaded to that tag release.

For a manual workflow run, `apk_url` can override the download location.

## Android Setup Wizard

1. Factory-reset the test device.
2. On the initial Android welcome screen, invoke QR provisioning (commonly six taps on supported Android builds).
3. Connect the device to the network required by the provisioning URL.
4. Scan `provisioning-qr.png`.
5. Android downloads the DPC, validates the configured checksum and starts Device Owner provisioning.

The DPC component encoded by default is:

```text
io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver
```

The download location must be HTTPS and accessible to Setup Wizard. A GitHub Actions artifact URL that requires
a signed-in GitHub session is not suitable; use a public Release asset or another HTTPS server.
