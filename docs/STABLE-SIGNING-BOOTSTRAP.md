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

## GitHub Actions credentials only

Release signing passwords must be provided via GitHub Actions Secrets, not manual workflow inputs. The legacy route uses only `DPC_AIO_LEGACY_SIGNING_PASSWORD` and requires the original password for the published encrypted keystore. A newly chosen password cannot decrypt it. The workflow verifies the downloaded ciphertext against its GitHub release asset SHA-256 before attempting decryption; matching metadata does not prove the ciphertext was originally encrypted with the configured password. A mismatch blocks the build. If decryption remains blocked, recover the original key/password or use the separately configured Continuous v2 route. A complete stable keystore configuration takes precedence; a partial stable configuration blocks the build rather than mixing credential sources. Keep v2 credentials separate. Do not put signing passwords in Variables, workflow inputs, commits or logs.

If a certificate fingerprint exists as both a GitHub Variable and a Secret, both must normalize to the same SHA-256 value. An invalid or conflicting value blocks signing. No fingerprint is silently preferred over another.

## If the previous keystore is available

Prefer restoring that keystore and its original passwords/alias. Base64-encode the keystore and set the four secrets above, then set the certificate SHA-256 as `DPC_AIO_EXPECTED_SIGNING_CERT_SHA256`.

Do not commit the keystore, passwords, base64, or recovery files to Git.

## If a completely new signing lineage is intentional

For this repository, use the isolated [Continuous v2 operations guide](publishing/CONTINUOUS-V2-OPERATIONS.md) when the original signer cannot be recovered. Do not run the legacy bootstrap below against the existing Continuous release with a different signer. It is appropriate only for a genuinely new release lineage without existing installations or an approved signing transition.

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
