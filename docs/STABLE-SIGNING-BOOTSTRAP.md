# Stable signing bootstrap

The production workflow `.github/workflows/build-aio-enrollment.yml` intentionally fails closed unless a persistent release signer is configured.

## Preserve update continuity first

`dpc-aio-continuous` already exists. If any device has an APK installed from that release, recover the **same** signing keystore before changing production signing. Android normally will not accept an ordinary APK update signed by a different certificate.

Required repository values:

- Secret `DPC_AIO_RELEASE_KEYSTORE_B64`
- Secret `DPC_AIO_RELEASE_STORE_PASSWORD`
- Secret `DPC_AIO_RELEASE_KEY_ALIAS`
- Secret `DPC_AIO_RELEASE_KEY_PASSWORD`
- Variable or Secret `DPC_AIO_EXPECTED_SIGNING_CERT_SHA256`

The workflow checks all five before Gradle starts and verifies the final APK signer against the configured SHA-256 fingerprint.

## If the previous keystore is available

Prefer restoring that keystore and its original passwords/alias. Base64-encode the keystore and set the four secrets above, then set the certificate SHA-256 as `DPC_AIO_EXPECTED_SIGNING_CERT_SHA256`.

Do not commit the keystore, passwords, base64, or recovery files to Git.

## If a completely new signing lineage is intentional

A guarded helper is included:

```bash
python3 tools/release/bootstrap_stable_signing.py --repo FuzzsT/ENRROL_NEW --allow-new-key
```

The helper:

1. requires `keytool` and authenticated GitHub CLI `gh`;
2. requires an explicit `NEW SIGNER` confirmation;
3. generates an RSA-4096 PKCS12 signer locally;
4. computes the certificate SHA-256 from the exported DER certificate;
5. sends secret values to `gh secret set` through stdin rather than command-line arguments;
6. sets `DPC_AIO_EXPECTED_SIGNING_CERT_SHA256` as a GitHub Actions variable;
7. verifies that the four secret names are present;
8. writes the keystore and recovery material under `~/DPC-AIO-stable-signing-backup/<UTC timestamp>/` by default.

The helper does **not** print passwords, keystore bytes, or base64.

Creating a new signer can make existing installations signed by the old signer non-updatable. Keep the generated recovery directory in at least two protected backup locations.

## Run production after signing is configured

From an authenticated GitHub CLI session:

```bash
gh workflow run build-aio-enrollment.yml --repo FuzzsT/ENRROL_NEW --ref main
```

A successful production run must pass, in order:

- stable-signing preflight;
- source/release contract tests;
- `:app-dpc:assembleEnterpriseRelease`;
- APK signer SHA-256 verification;
- Work Profile / Device Owner QR exact-APK binding;
- AOSP Device Owner runtime smoke (with one bounded fresh-AVD retry only for the known pre-existing-account eligibility flake);
- QR release bundle generation;
- artifact upload;
- `dpc-aio-continuous` publication;
- public APK byte-identity verification.

Do not treat the old emergency prerelease as update-stable: its signer is intentionally run-specific.
