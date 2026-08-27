# DPC-AIO UI + Enterprise Policy Expansion Design

Date: 2026-08-26
Status: Approved in chat; pending written-spec review

## 1. Goal

Upgrade DPC-AIO so the application remains fully usable on modern Android displays (status bar, navigation bar, gesture navigation, display cutouts, IME) and exposes a substantially broader set of supported DevicePolicyManager capabilities, inspired by the organization and coverage of Google TestDPC and ser-mk/admin-dpc without copying deprecated or unsupported behavior.

The upgrade must preserve existing provisioning, QR generation, signing, offline enrollment, Knox/Samsung, permissions, Activity Explorer, diagnostics, and application PIN behavior.

## 2. Non-goals

- Do not bypass Android ownership, provisioning, lockscreen, signature, or permission boundaries.
- Do not emulate deprecated DevicePolicyManager APIs when a supported public replacement exists.
- Do not make unavailable policies appear executable.
- Do not gate ManagedProvisioning entry points behind the DPC application PIN.
- Do not replace the existing module architecture with TestDPC's architecture.

## 3. Current problems

### 3.1 Layout clipping

Several activities create their view hierarchy programmatically using `Activity`, `ScrollView`, `LinearLayout`, and fixed `setPadding(...)`. They do not consistently consume `WindowInsets`. On edge-to-edge Android versions this can place content under status/navigation bars, display cutouts, or the IME.

### 3.2 Navigation density

`AioDashboardActivity` is a single long stack of buttons. It scales poorly as features are added and makes discovery difficult.

### 3.3 Narrow enterprise policy catalog

`EnterprisePolicyCatalog` currently exposes only a small set of advanced policies. TestDPC's current policy surface is much broader and organized by functional category. The existing DPC-AIO capability resolver is suitable for representing availability based on API level, ownership, visibility, risk, OEM, and related constraints; the catalog should expand rather than bypass that resolver.

## 4. Reference behavior

Google TestDPC is the primary behavioral reference for supported public Android Enterprise APIs. Its current policy surface includes categories such as application management, certificates, Wi-Fi/networking, telephony/eSIM/APN, accessibility, account management, cross-profile behavior, system settings, delegation, kiosk/lock task, camera/screen capture, password constraints, and system update management.

`ser-mk/admin-dpc` is a secondary reference, especially for classic single-use/kiosk controls such as status bar, keyguard, and lock task. It is not treated as authoritative when behavior is deprecated or conflicts with current Android APIs.

## 5. UI architecture

### 5.1 `DpcUiShell`

Introduce a reusable UI shell/helper in the app module. It will be used by programmatic activities instead of each activity manually creating an un-inset-aware root.

Responsibilities:

- create a root container and scrollable content area;
- set `isFillViewport = true` for scroll content;
- use `ViewCompat.setOnApplyWindowInsetsListener` / WindowInsetsCompat;
- apply content padding as `basePadding + systemBars + displayCutout`;
- apply IME bottom inset when relevant;
- preserve minimum touch target spacing;
- optionally provide a fixed header/title row that also respects top insets;
- provide consistent section headers, action rows, status text, warning text, and disabled-state descriptions;
- avoid content being placed underneath gesture/navigation areas;
- support font scaling and narrow displays without horizontal clipping.

### 5.2 Migration rule

Interactive activities that currently use raw `setContentView(LinearLayout/ScrollView)` will migrate to `DpcUiShell` when touched by this feature. The dashboard, policy hub, PIN settings, enterprise operations, work-profile/COPE, network, credentials, lifecycle, permission manager, diagnostics, activity explorer, Knox/Samsung centers, and enrollment status/setup screens are priority targets.

A contract test will detect app activities that still build a top-level scrolling UI without the shell, with explicit allowlist exceptions only for ManagedProvisioning bridge activities or intentionally minimal system callbacks.

### 5.3 Dashboard redesign

Replace the single linear button list with a categorized, searchable dashboard.

Top-level groups:

1. Enrollment
2. Device
3. Apps
4. Security
5. Network
6. Accounts
7. Work Profile / COPE
8. Certificates
9. OEM / Knox
10. Diagnostics / Lab

Each group opens an existing center or a focused policy category. The dashboard includes a local search field that filters actions/centers by title and keywords. Developer/lab entries remain gated by existing developer-mode visibility.

The dashboard must continue to enforce the DPC application PIN before showing protected UI. `ProvisioningModeActivity` and `PolicyComplianceActivity` remain outside this application-PIN gate.

## 6. Policy model expansion

### 6.1 Catalog structure

Expand `EnterprisePolicyGroup` beyond Device/Applications/Network to include at minimum:

- DEVICE
- APPLICATIONS
- SECURITY
- NETWORK
- ACCOUNTS
- CROSS_PROFILE
- CERTIFICATES
- KIOSK
- SYSTEM
- TELEPHONY

`EnterprisePolicyDescriptor` remains the source of truth for title, group, capability requirements, and action mapping. Add optional metadata only when required for rendering or execution, such as description, keywords, dangerous confirmation requirement, and action type.

### 6.2 Availability

Every policy is resolved through `CapabilityResolver` before interaction.

UI states:

- available: normal action;
- unavailable: disabled with a concise reason (API level, ownership, affiliation, OEM, permission, or unsupported state);
- hidden: only for entries intentionally hidden by existing visibility/developer-mode rules;
- high-risk: visible but requires explicit confirmation before mutation.

No handler may assume Device Owner/Profile Owner based only on the screen from which it was launched.

## 7. Policy scope to add

Implement public, current Android APIs where supported by the project's min/compile SDK and ownership model.

### 7.1 Kiosk / single-use

- LockTask package allowlist
- start/stop LockTask where permitted
- LockTask feature configuration
- status bar disable/enable where supported
- keyguard disable/enable where supported

### 7.2 Application management

- enable system apps by package/intent where supported
- install existing package
- hide/unhide package
- suspend/unsuspend package
- block/unblock uninstall
- clear application user data
- managed configurations / application restrictions
- permission policy entry points via existing Permission Manager
- keep-uninstalled packages when supported

### 7.3 Security / capture / audio

- camera disable/enable
- screen-capture disable/enable
- audio mute where supported
- lock-now action
- password-quality/complexity and password-policy controls using supported APIs only

Deprecated direct password reset flows are excluded unless there is a currently supported API path appropriate for the target Android version and ownership state.

### 7.4 System settings

- time/timezone controls where DevicePolicyManager permits
- automatic time / timezone policies already present
- screen brightness where permitted
- screen-off timeout where permitted
- profile/organization naming and supported organization metadata

### 7.5 Network and Wi-Fi

- always-on VPN policy
- global HTTP proxy where supported
- Wi-Fi policy/configuration controls available to DPC ownership modes
- retain existing DNS/DoH tooling as a separate operational tool
- network logging remains in Enterprise Operations

### 7.6 Telephony

- override APN management where supported
- eSIM management entry points when public APIs and ownership requirements are satisfied

### 7.7 Accounts and delegation

- account-management restrictions
- delegation scopes using supported DPM APIs
- app restrictions managing package / delegated certificate installer where applicable

### 7.8 Cross-profile

- cross-profile calendar policy where supported
- cross-profile package / intent access controls where supported
- existing Work Profile / COPE lifecycle tooling remains the primary profile lifecycle screen

### 7.9 Certificates

Certificate-management actions stay centralized in `CredentialCenterActivity`. The expanded catalog links to that center or individual supported operations rather than duplicating credential code.

## 8. Execution architecture

Do not put large DevicePolicyManager mutation logic directly into activities.

Preferred layering:

- app UI: renders state and collects user input;
- policy core: models action requests/results;
- policy android: performs DevicePolicyManager calls;
- capability resolver: decides visibility/availability;
- existing execution/router infrastructure: used where it already fits.

Extend `DevicePolicyGateway` / Android implementation with focused methods. Use small request/result models for multi-parameter policies.

Every mutating action returns a structured `PolicyResult` or equivalent current project result type. Runtime `SecurityException`, unsupported API state, and illegal ownership state are converted into visible results rather than application crashes.

## 9. Safety and confirmations

Actions that can materially restrict device usability must use an explicit confirmation dialog and display the current management context.

Examples:

- disabling status bar/keyguard;
- entering kiosk/LockTask;
- blocking uninstall;
- suspending/hiding packages;
- disabling camera/screen capture;
- global proxy/VPN changes;
- account restrictions;
- device-wide password/security policy changes.

The UI must offer the reverse operation when the API provides one.

## 10. Provisioning invariants

This upgrade must not change the provisioning contract unless a dedicated provisioning change is separately approved.

Required invariants:

- production workflow continues to generate `work-profile-qr.png`;
- production workflow continues to generate `device-owner-qr.png` with expected mode `fully-managed`;
- general `provisioning-qr.png` continues to follow configured default mode;
- QR checksum remains bound to the exact built/signed APK;
- `ProvisioningModeActivity` remains callable by ManagedProvisioning;
- `PolicyComplianceActivity` remains callable by ManagedProvisioning;
- DPC application PIN does not block these system provisioning activities;
- signing/path gates and release URL validation remain unchanged.

## 11. Testing

### 11.1 UI contract tests

Add tests that verify:

- priority interactive activities use `DpcUiShell`;
- `DpcUiShell` applies system-bars/display-cutout/IME insets;
- scroll containers use fill-viewport and bottom inset padding;
- dashboard has all required categories and search;
- provisioning bridge activities are excluded from the app-PIN gate.

### 11.2 Policy model tests

For each new policy descriptor verify:

- minimum API;
- ownership requirement;
- risk classification;
- expected group;
- capability resolver result for representative Device Owner/Profile Owner/COPE/unmanaged contexts.

### 11.3 Android gateway contract tests

Add source-level/host tests for DevicePolicyManager method mappings and runtime guards. Where Android unit execution is unavailable locally, CI Gradle compilation remains authoritative.

### 11.4 Regression tests

Existing tests that must remain green include:

- provisioning build integration;
- work-profile and fully-managed QR validation;
- dense QR decoder;
- release/signing/path contracts;
- zero-settings workflow contracts;
- app-PIN/provisioning-mode contract;
- secret scan;
- repository layout/migration checks.

## 12. Delivery order

1. Introduce `DpcUiShell` and migrate dashboard/PIN/policy hub first.
2. Add categorized searchable dashboard.
3. Expand policy groups/catalog and capability metadata.
4. Extend core/android policy gateways in small feature batches.
5. Wire UI category screens/actions.
6. Migrate remaining high-use programmatic activities to the safe-insets shell.
7. Add/expand contract tests.
8. Run host tests, YAML/Python/Bash validation, and Gradle compile when network allows.
9. Re-run QR generation/validation using a real built APK artifact.
10. Refresh checksums and package final ZIP.

## 13. Acceptance criteria

The change is complete when:

- no priority interactive DPC screen is clipped by status/navigation bars, cutouts, or IME on edge-to-edge Android;
- dashboard navigation is categorized and searchable;
- the expanded policy catalog exposes the approved public policy groups and actions with correct capability gating;
- unsupported/unavailable actions cannot execute and explain why;
- high-risk policy mutations require confirmation and provide a reverse action when supported;
- existing work-profile and fully-managed provisioning remains unchanged and passes validation;
- app PIN still protects the DPC UI without blocking ManagedProvisioning;
- all repository host/contract tests pass;
- Gradle compile passes in CI or, if local network prevents Gradle bootstrap, the limitation is documented without claiming a local compile success;
- final ZIP passes integrity and repository checksum validation.
