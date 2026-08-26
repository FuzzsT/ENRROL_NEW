# Enterprise Operations Center 0.8.0

DPC-AIO 0.8.0 expands the 0.7.0 Enterprise Policy Hub with four operational surfaces while retaining the monolithic APK and capability-aware hidden-mode model.

## Compliance and updates

Security/network logging use documented DevicePolicyManager APIs. Callback availability/batch tokens are stored in device-protected state. Retrieved batches are bounded in private local storage and exported only on explicit user action with redaction. System update policy supports automatic/windowed/postpone/default and recurring freeze periods validated before DPM apply.

## Credentials

The Credential Center imports administrator-selected X.509 PEM/DER or PKCS#12 documents through Android Storage Access Framework. PKCS#12 passwords are used in memory and overwritten after import. Managed key pairs can be granted to apps; there is no private-key export/dump operation.

## Device lifecycle

Lock Task exposes package allowlisting and documented system feature flags. Device security covers password complexity, failed-attempt wipe threshold, keyguard biometrics, camera and screen capture. Application control adds uninstall blocking, application restrictions, user-control-disabled packages and confirmed clear-data. FRP policy is previewed/applied/read back without triggering a reset.

## Work profile / COPE

COPE adds cross-profile package control, Android 14+ PackagePolicy for contacts/caller-ID, managed-profile maximum-time-off with the Android 72-hour floor, personal-app suspension, organization identity and affiliation IDs. `ACTION_CHECK_POLICY_COMPLIANCE` is handled by the DPC.

## Knox

Knox integration remains limited to public/runtime-detectable capability state. The 0.8.0 center labels ApplicationPolicy, CertificatePolicy, Kiosk, Firewall/VPN and Enhanced Attestation capability surfaces. Knox AuditLog is disabled as deprecated on modern Android. No KLMS Agent, KnoxGuard, HDM private protocol, private endpoint or signature-only service is reimplemented.
