# Engineering Intent Center Design

## Goal
Add a bounded engineering diagnostics center for explicit Android broadcasts, USSD/dial intents, Samsung secret-code capability discovery, and developer-activity classification without introducing an arbitrary shell/root bridge or bypassing signature/permission gates.

## Architecture
The feature is split into four focused units:

1. `BroadcastIntentBuilder` builds explicit/implicit `Intent` instances from a typed request containing action, optional package/component, optional data URI, and typed extras. The UI sends only through Android `Context.sendBroadcast`; there is no free-form `am broadcast` shell execution.
2. `UssdIntentFactory` produces `ACTION_DIAL` by default and only exposes `ACTION_CALL` when the caller explicitly chooses it and Android permission checks allow it. The UI previews the normalized `tel:` URI before launching.
3. `SamsungSecretCodeCatalog` classifies code entries and `SamsungSecretCodeDiscovery` queries receivers for `TelephonyManager.ACTION_SECRET_CODE` and the legacy `android.provider.Telephony.SECRET_CODE` action with `android_secret_code://` data. Destructive/unknown-risk entries are discovery-only and cannot be auto-executed.
4. `DeveloperActivityClassifier` scores discovered activities from class/package/permission tokens and feeds a new Developer quick filter in `ActivityExplorerActivity`. Existing ActivityAccessPlanner/route execution remains authoritative for launchability.

## UI
A new `EngineeringIntentCenterActivity` is added to Dashboard and Module Center. It contains:
- Broadcast Lab: action, package, component, data URI, typed extra rows, preview, send.
- USSD / Dial Lab: input, preview URI, Dial action, optional Call action only when permission is granted.
- Samsung Secret Code Center: capability scan and safe catalog entries; risky entries are labelled and disabled for execution.
- Developer Activities shortcut that opens `ActivityExplorerActivity` with the Developer filter preselected.

## Safety and compatibility constraints
- No arbitrary shell command input.
- No root bridge.
- No signature-permission bypass.
- Non-exported or permission-gated activities remain subject to existing route planning.
- Secret-code discovery may identify protected receivers that cannot be invoked by this app; this must be reported as a capability/permission limitation rather than bypassed.
- No destructive Samsung code is auto-run.
- All intents are previewable before dispatch/launch.

## Verification
Focused source contracts cover all four units, manifest/dashboard/module registration, and developer filtering. Final verification requires full host/source suite, `:app-dpc:assembleEnterpriseDebug`, APK existence, and aggregate `verify`.