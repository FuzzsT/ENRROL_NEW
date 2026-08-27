# GitHub publishing and security notes

For the command-line import/preflight workflow, see `docs/releases/GITHUB-PUBLISH.md` and `docs/releases/GITHUB-SECRETS.example`.

DPC-AIO 1.2.0 is structured for GitHub upload with a read-only build job and a separate publish job that alone receives `contents: write`. Release signing secrets are scoped only to the shell steps that need them, and the decoded keystore is deleted before third-party emulator/runtime actions execute. External GitHub Actions are pinned to immutable full commit SHAs.

## GitHub Actions

- Build job: `contents: read`.
- Publish job: `contents: write`, only after the verified build job succeeds.
- The publish job receives only the verified `dist/` artifact; it never receives signing passwords or keystore bytes.
- Keep Dependabot/Renovate or a manual review process for updating action SHA pins. Review the upstream release before changing any pin.

## Required repository configuration

Configure release signing values as GitHub Actions secrets: `DPC_AIO_RELEASE_KEYSTORE_B64`, `DPC_AIO_RELEASE_STORE_PASSWORD`, `DPC_AIO_RELEASE_KEY_ALIAS`, and `DPC_AIO_RELEASE_KEY_PASSWORD`. Configure the expected certificate SHA-256 as a repository variable when possible (`DPC_AIO_EXPECTED_SIGNING_CERT_SHA256`). Do not commit keystores, private KPE/KLM material, reset tokens, or `.env` files.

## License

This source package intentionally does **not** invent or select an open-source license on the owner's behalf. GitHub does not require a `LICENSE` file for repository upload. Before describing the repository as open source or granting reuse rights, the repository owner should deliberately choose and add the appropriate license.

## Upload checklist

1. Extract the source ZIP and initialize/import it as the repository root (do not upload the outer ZIP as the repository contents).
2. Confirm GitHub Actions secrets/variables are configured.
3. Push a branch first and let source/security checks run.
4. Trigger `workflow_dispatch` for a non-tag continuous enrollment build.
5. Only create a `v*` tag after build, runtime-smoke, signing-certificate and QR validation gates are green.
