# Zero-Settings validation

Validated configuration target: manual `Build AIO + enrollment QR` with no repository Secrets/Variables and only `release_signing_password` supplied.

## Result

- Password-only workflow input: PASS
- Repository defaults -> built-ins fallback: PASS
- Build-to-publish propagation of APK name/release tag: PASS
- First-run password-backed signer creation: PASS
- AES-256-CBC/PBKDF2 encrypted signer export: PASS
- Second-run signer restore with same password: PASS (certificate SHA-256 identical)
- Wrong password fails instead of signer rotation: PASS
- Dense QR version 20 / 655-char decoding: PASS
- Provisioning build integration: PASS
- QR release bundle contracts: PASS
- Runtime smoke contract: PASS
- GitHub artifact/release/publish contracts: PASS
- Release gate contracts through 1.2.0: PASS
- Secret scan: PASS
- Kotlin host model tests: PASS
- Python host contract suite: PASS (segmented execution due local command time limits)
- Provisioning Node tests: 12/12 PASS
- Bash syntax scan: PASS
- Python compile scan: PASS (154 files)
- Workflow YAML parse: PASS (2 workflows)
- Node syntax scan: PASS (11 files)

## Gradle note

A final local `./gradlew :app-dpc:tasks --all --no-daemon` could not download Gradle 9.7.0 because the execution environment could not resolve `services.gradle.org`. The same project build path had already completed successfully on GitHub Actions; the Zero-Settings changes are limited to workflow/signing/configuration files.

## Zero-Settings signing behavior

The first successful manual rolling release creates `DPC-AIO-signing-keystore.enc` as a release asset. Future manual runs restore it with the same password. Keep the password: changing it intentionally causes the workflow to stop rather than silently publish an APK signed by a different certificate.
