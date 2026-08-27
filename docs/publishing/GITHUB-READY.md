> Current release target: DPC-AIO 1.2.0 Enterprise + Samsung OEM AIO

# GitHub-ready release source

DPC-AIO 1.2.0 preserves the 1.0.2 QR Production Readiness invariants and adds protected operations, transactional mutation/rollback, capability-routed Android/Knox/SEM/OEM handling, Package Trust 2.0, hardened APK+ import, Work Profile Lifecycle 2.0 and Credential Recovery 2.0.

## Production enrollment invariants
- Production provisioning builds `:app-dpc:assembleEnterpriseRelease`.
- Release signing is stable and secret-managed; no debug-sign fallback is allowed.
- QR payloads remain bound to the exact public HTTPS APK URL and exact APK bytes/certificate.
- `FULL_OFFLINE` uses the DPC-owned offline route and never silently falls through to online enrollment.

## Verification semantics
Source, APK build, Android Enterprise runtime, Knox runtime, SEM runtime, OEM runtime, package trust, Device Owner and Work Profile evidence are independent. `BLOCKED`, `NOT_RUN`, `UNAVAILABLE`, `PARTIAL`, `CONFLICT` and `ROLLED_BACK` are not promoted to `PASS`.

## Security boundaries
No signature-permission bypass, hidden-API exemption, guessed Binder transaction, root/su fallback, framework patch, forged Knox/license/attestation state, private signing key, KPE secret or plaintext credential-reset token is part of this release.
