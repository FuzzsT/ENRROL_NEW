# DPC-AIO checkpoint 0.4.0

## Added
- `account-manager` pure Kotlin module.
- `account-android` Android AccountManager / DevicePolicyManager adapter.
- Google account inventory for account type `com.google`.
- DPC-local selected Google account (`PRIMARY_FOR_AIO`) without credentials/tokens.
- Guided observed-order reorder workflow with explicit confirmation.
- Temporary removal + re-add plan for accounts that precede the selected target.
- Account-management policy rollback after workflow completion.
- System account chooser, add-account and sync/account-settings intents.
- `GoogleAccountManagerActivity` bootstrap launcher UI.
- `systemPrivileged` account permissions source-set manifest.
- Capability types `ACCOUNT_READ`, `ACCOUNT_SELECT`, `ACCOUNT_REORDER`.
- Android account contract gate and two host tests.

## Semantics
Android exposes no public `setPrimaryGoogleAccount()` API. The UI therefore reports `OBSERVED_SYSTEM_ORDER` from accounts visible to AccountManager and only marks `GREEN_VERIFIED` after read-back shows the target first. App-specific Google account selection may still differ.

## Safety/data handling
- No password storage.
- No auth-token reads/writes.
- Account removal requires explicit user confirmation.
- If direct removal is not authorized, system account settings are used as a user-assisted fallback.

## Verification
- Full host suite: PASS.
- Android static/public-API contracts: PASS.
- Release gate: PASS.
- Full Android APK build: not verified in this sandbox because Gradle 9.7.0 distribution is not cached and `services.gradle.org` DNS/network access is unavailable.
