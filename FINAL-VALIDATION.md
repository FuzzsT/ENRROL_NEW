# DPC-AIO final validation

Final audit scope: zero-GitHub-Settings configuration, signing, APK path gates, Android Enterprise QR provisioning, workflow topology, artifacts and release publication.

## Workflow UX

- `Build AIO + enrollment QR`: exactly one manual input, `release_signing_password`.
- `Emergency enrollment (ephemeral signing)`: zero manual inputs; configuration resolves automatically.
- Configuration precedence: GitHub Variables/Secrets -> `.github/dpc-aio-defaults.env` -> built-in safe defaults.

## Signing / APK ordering

Production order is enforced as:
1. create/restore signing keystore;
2. verify keystore exists, is non-empty, canonicalize with `realpath`, require runner-temp location, validate alias/password;
3. Gradle `assembleEnterpriseRelease` creates and signs APK;
4. require exactly one APK in the expected enterprise/release output directory;
5. canonicalize APK path, verify non-empty and path confinement;
6. verify APK signature/certificate with stable Android Build Tools `apksigner`;
7. copy that same verified APK path into `dist/`;
8. bind QR checksums to the copied APK;
9. publish only after local validation.

Password-backed production signing uses PKCS12. The encrypted rolling-release keystore restore test produced the same signing certificate SHA-256 on the second run.

## Android Enterprise QR

Official provisioning fields retained:
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`
- exactly one SHA-256 package/signature checksum (production generator currently prefers package checksum when no signer is explicitly requested)
- `android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE`

Online QR optimization:
- omitted `PROVISIONING_ALLOW_OFFLINE=false` because Android defines false as the default;
- omitted custom `ENROLLMENT_OFFLINE_MODE=ONLINE` because the DPC parser defaults to ONLINE;
- work-profile payload: approximately 550 chars, QR version 18;
- fully-managed payload: approximately 551 chars, QR version 18;
- both decode successfully and match the exact JSON payload and APK SHA-256.

Offline behavior remains explicit: when requested, `PROVISIONING_ALLOW_OFFLINE=true`, offline mode and offline bundle id are serialized and validated.

DPC manifest includes:
- exported `DeviceAdminReceiver` protected by `android.permission.BIND_DEVICE_ADMIN`;
- `android.app.action.GET_PROVISIONING_MODE` activity;
- `android.app.action.ADMIN_POLICY_COMPLIANCE` activity.

## Validation results

PASS:
- YAML parse for both workflows
- Bash syntax for repository shell scripts
- Python AST parse
- DPC Android migration contract
- signing/APK path contract
- zero-settings fallback contract
- CI workflow topology contract
- QR production readiness contract
- build provisioning QR contract
- dual Work Profile / Device Owner QR contract
- Work Profile provisioning contract
- provisioning Android contract
- provisioning build integration contract
- release gates 112 and 113
- manual signing bootstrap 114
- QR release bundle 114
- GitHub upload/publish kit contracts
- release secret scan
- non-SDK API scan
- Node provisioning tests: 12/12
- real APK QR binding check for work-profile and fully-managed
- password-backed signing create -> encrypted export -> restore with identical certificate

Local Gradle wrapper execution could not be repeated in the artifact container because DNS resolution for `services.gradle.org` is blocked there. The most recent GitHub Actions runner build independently completed `assembleEnterpriseRelease` successfully; final changes after that build are workflow/Python/docs/test changes, not Android/Kotlin/Gradle source changes.

Current public `dpc-aio-continuous` release may remain 404 until this final workflow is committed and a successful manual production run reaches the publish job. The workflow then creates/updates the release, uploads the verified APK and checks the public download byte-for-byte.

Security note: GitHub `workflow_dispatch` string inputs are not Actions Secrets. The password is immediately masked on the runner and is not written to artifacts, but for highest-assurance production signing stable GitHub Actions Secrets remain preferable. Use a unique password not reused elsewhere when using zero-settings mode.
