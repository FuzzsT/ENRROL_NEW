# Continuous v2 recovery plan (proposal; not active)

## Evidence and constraint

The existing Continuous release is still signed by the historical signer
(E7873449EDF167F6D6DEA07FB27567834DC455222C66B09C77EDB92E579F4AD4).
The release run 45692523814 resolved stable mode, detected the legacy secret,
and failed in Prepare stable signer with PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH.
PRs #294, #295 and #296 protect the old key, improve diagnostics and support
verified backup import. None recovers an unavailable private key.

If the old key is recovered, prefer the existing verified recovery/import
procedure. A certificate fingerprint is not a private key. Android signing
lineage rotation also requires the historical private key; see
tools/release/prepare_signing_rotation.py.

## Proposed isolated replacement channel

- Keep dpc-aio-continuous, dpc-aio-stable and dpc-aio-emergency-enrollment intact.
- New release tag: dpc-aio-continuous-v2; explicit channel identifier continuous-v2.
- New dedicated repository signing secrets and public certificate pin, independent
  of the legacy credentials. Generate the new key locally only after approval;
  keep an encrypted offline backup. No signing password in Variables or source.
- Reuse the existing build-aio-enrollment.yml workflow, with explicit opt-in
  first publication and a separately configurable automatic main build later.
  Never fall back to emergency signing for v2. No new workflow file.
- Keep application ID io.dpcaio.app for this proposed replacement. A new signer
  cannot update an existing installation without a valid signing lineage.
  New installation is required if lineage cannot be recovered; on managed
  devices use authorized administrator deprovisioning and enrollment procedures.
  Do not automatically uninstall, reset, or change device ownership.

## Implementation checklist (not implemented)

1. Add explicit v2 selection, dedicated signing credentials, strict certificate
   pin enforcement and an isolated tag in .github/workflows/build-aio-enrollment.yml.
   Audit all release edit/upload, alias, manifest repair and asset cleanup paths
   so no v2 job can mutate a legacy tag. Do not publish a replacement under
   the old versioned stable alias.
2. Update tools/release/build_self_update_manifest.py and the self-update
   channel model, parser and planner contracts. Add the v2 manifest URL and
   selection in SelfUpdateActivity.kt and HTTP diagnostics in SelfUpdateClient.kt.
   Set the default channel appropriately for the v2 build, without changing
   old build defaults. Verify all exhaustive enum branches.
3. Make provisioning and QR assets reference the exact v2 APK URL and bytes.
   Keep package, signer, version, manifest and checksum verification enabled.
4. Reject a missing or mismatched v2 signer before publication. Require a
   monotonically increasing versionCode within the v2 channel and reject
   cross-channel or wrong-signer updates.
5. Test legacy and emergency release targets unchanged, no mutation of legacy
   assets by v2, first v2 install and a subsequent v2-to-v2 update with the
   same signer, rejection of old-to-v2 without lineage, QR enrollment and
   runtime smoke on supported Android versions.
6. Only after the new signer is configured and CI passes, dispatch a real
   v2 build and verify all published asset digests, signer and manifest.
   Report production status from the actual release, not debug CI.

## Acceptance gate

This document authorizes no key generation, secret changes, release overwrite,
migration or deployment. The current Continuous APK remains the old release.
Separate administrator approval and access to signing credentials are required
for activation. Until then, this is a proposal, not a recovery result.
