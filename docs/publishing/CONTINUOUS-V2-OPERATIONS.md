# Continuous v2: first release and ongoing operations

## Select the correct signing route

The existing `dpc-aio-continuous` release requires the original signing certificate. A new unrelated key cannot update APKs signed by the old key. If the original signer cannot be recovered, preserve the old release and use the separate `dpc-aio-continuous-v2` route. Do not use the legacy bootstrap helper to replace the historical signing key. Existing installations need an authorized migration or a fresh installation after the administrator follows the supported deprovisioning and backup process.

## Configure the dedicated signer

Use a new, administrator-controlled PKCS12 keystore, stored securely outside the repository with protected backups. Keep the same store and private-key password for PKCS12. Never paste a private key, password or Base64 keystore into a chat, issue, workflow input or commit.

In Settings > Secrets and variables > Actions, configure these repository secrets:

- `DPC_AIO_V2_RELEASE_KEYSTORE_B64` — Base64 of the dedicated PKCS12 file.
- `DPC_AIO_V2_RELEASE_STORE_PASSWORD` — store password.
- `DPC_AIO_V2_RELEASE_KEY_ALIAS` — private-key alias.
- `DPC_AIO_V2_RELEASE_KEY_PASSWORD` — key password.

Set the public certificate fingerprint as a repository variable (or secret) named `DPC_AIO_V2_EXPECTED_SIGNING_CERT_SHA256`. Independently verify it against the keystore certificate; the workflow rejects the historical certificate and mismatched pins. Use only one source, or matching values in both sources. Conflicting or malformed values stop both build and publication; neither source silently overrides the other.

If using the separately distributed local Signer Setup ZIP, the first command is an audit only. It does not set repository values:

```powershell
python import_continuous_v2_signing.py --keystore "C:\keys\dpc-aio-v2.p12" --alias dpc-aio-v2
```

After independently checking the reported certificate fingerprint, an administrator may configure the repository with the same tool. Replace the placeholder with the verified public SHA-256 fingerprint:

```powershell
python import_continuous_v2_signing.py --keystore "C:\keys\dpc-aio-v2.p12" --alias dpc-aio-v2 --expected-sha256 YOUR_PUBLIC_CERT_SHA256 --apply
```

The tool prompts for the password and explicit confirmation. It is first-configuration-only and refuses existing v2 values or releases. If configuration partially fails, inspect the v2 entries and complete the verified configuration manually; do not delete or change historical credentials. The tool does not dispatch a workflow.

## Build-only validation

Open [Enrollment build + release](https://github.com/FuzzsT/ENRROL_NEW/actions/workflows/build-aio-enrollment.yml) and select main, continuous-v2 mode. Leave both publication flags false. Alternatively, an authenticated administrator can run:

```bash
gh workflow run build-aio-enrollment.yml --repo FuzzsT/ENRROL_NEW --ref main -f signing_mode=continuous-v2 -f publish_continuous_v2=false -f first_continuous_v2_release=false -f repair_manifests_only=false
```

Review the verified build artifact, signing certificate, self-update manifest, QR validation and runtime smoke results. Debug CI alone does not prove production signing. Build-only artifacts are not public releases and their provisioning download URL is not usable until the APK is actually published.

## First public release

After an administrator approves the verified build and checks the v2 release does not exist, run the same workflow with `publish_continuous_v2=true` and `first_continuous_v2_release=true`. Subsequent releases must use `first_continuous_v2_release=false`. The workflow verifies the prior release and fails closed on mismatched cert, digest, URL or non-increasing version (an identical APK at the same version is permitted). Do not bypass a failed check or replace a key to make a check pass.

Verify the public APK, certificate fingerprint, manifest and QR metadata after publication. Uploads of multiple assets are not atomic. If an upload is interrupted, stop and inspect the actual release and compare hashes against the verified build before any administrator-approved recovery. Do not assume rerunning an earlier build is safe.

## Troubleshooting

| Message | Action |
| --- | --- |
| PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH | Original signer recovery is blocked; verify original password and encrypted backup integrity. |
| CONTINUOUS_V2_SIGNING_CONFIG_MISSING | Check the dedicated v2 configuration names and availability. |
| CONTINUOUS_V2_CERT_PIN_INVALID / SIGNER_MISMATCH | Verify the certificate and private key locally; do not change a pin just to pass a build. |
| CONTINUOUS_V2_FIRST_RELEASE_CONFIRMATION_REQUIRED | Check that no v2 release exists; explicitly approve initial publication. |
| CONTINUOUS_V2_ALREADY_EXISTS | Do not request first publication of an existing release. |
| CONTINUOUS_V2_SIGNER_CHANGED / ROLLBACK / VERSION_REUSE | Stop and verify the intended key, build version and existing release. |

Normal push builds still use the original channel and its signing configuration. Automatic v2 publication is not configured. No new workflow file is required.
