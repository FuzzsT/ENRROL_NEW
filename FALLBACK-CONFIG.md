# DPC-AIO Zero-Settings / fallback configuration

Manual `Build AIO + enrollment QR` requires only `release_signing_password` (minimum 12 characters).
No GitHub repository Secrets or Variables are required for the rolling `dpc-aio-continuous` release.

Configuration precedence:

1. GitHub Secrets / Variables, when present.
2. `.github/dpc-aio-defaults.env` committed in the repository.
3. Safe built-in defaults in the workflow.

Fresh repository behavior when nothing is configured:

- release APK: `DPC-AIO-enterprise-release.apk`
- continuous release tag: `dpc-aio-continuous`
- policy profile: `default`
- provisioning mode: `work-profile`
- offline mode: `ONLINE`
- allow offline: `false`
- enrollment endpoint: empty -> standalone/default routing
- enrollment token: empty
- enrollment signing public key: empty

## Password-only persistent release signing

When stable GitHub signing Secrets are absent, a manual run uses `PASSWORD_RELEASE_KEYSTORE` mode:

1. The workflow checks the rolling release for `DPC-AIO-signing-keystore.enc`.
2. If the asset exists, it is decrypted with the manually entered password and validated with `keytool`.
3. If it does not exist (first successful release), a new 3072-bit RSA PKCS12 signing key is created.
4. The keystore is encrypted with AES-256-CBC + PBKDF2 (600000 iterations) and published as `DPC-AIO-signing-keystore.enc` together with the rolling release.
5. Future manual runs using the same password restore the same signing certificate, so APK updates remain signature-compatible.

A wrong password does **not** silently rotate the signer. The workflow fails with `PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH` / `PERSISTED_SIGNING_KEY_INVALID`.

If only part of the four stable signing Secrets is configured, a manual run ignores the incomplete set and uses the password/release-keystore path. Fully configured stable Secrets still take priority.

Automatic tag/push releases cannot securely obtain a password that was never stored in GitHub Settings, so they still require the complete stable signing Secrets. The fully Settings-free path is the manual `workflow_dispatch` rolling release.

Do not put real secrets or plaintext signing keys in `.github/dpc-aio-defaults.env` or the repository.

## Emergency workflow

`Emergency enrollment (ephemeral signing)` is now fully automatic: it has no manual configuration fields. It resolves policy/provisioning/offline/release names from GitHub Variables, then `.github/dpc-aio-defaults.env`, then built-ins. Its signer remains intentionally ephemeral, so its APK is not update-compatible across independent emergency runs.

## Password input note

GitHub `workflow_dispatch` has no secret input type. The production workflow masks the entered password immediately on the runner and never writes plaintext into build artifacts or release assets, but a workflow-dispatch string is not equivalent to a repository Actions Secret. Use a unique passphrase that is not reused for other accounts. For highest-assurance production signing, configure stable signing Secrets.
