# Engineering Intent Center Design

## Goal
Add a bounded engineering diagnostics center for explicit Android broadcasts, USSD/dial intents, Samsung secret-code capability discovery, safe calculator launch, and developer-activity classification without introducing an arbitrary shell/root bridge or bypassing signature/permission gates.

## Architecture
The feature is split into focused units:

1. `BroadcastIntentBuilder` builds explicit/implicit `Intent` instances from a typed request containing action, optional package/component, optional data URI, and typed extras. The UI sends only through Android `Context.sendBroadcast`; there is no free-form `am broadcast` shell execution.
2. `UssdIntentFactory` produces `ACTION_DIAL` by default and exposes `ACTION_CALL` only for an explicit user action; the UI starts it only when `CALL_PHONE` is already granted. The UI previews the normalized `tel:` URI before launching.
3. `SamsungSecretCodeCatalog` classifies code entries and `SamsungSecretCodeDiscovery` queries receivers for `TelephonyManager.ACTION_SECRET_CODE` and the legacy `android.provider.Telephony.SECRET_CODE` action with `android_secret_code://` data. User-supplied/unknown-risk entries are discovery-only and cannot be auto-executed.
4. Calculator integration uses `Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)` only. It opens the system-selected calculator and does not inject or execute service codes.
5. `DeveloperActivityClassifier` identifies developer/debug/engineering/diagnostic/test-like activities from package, class and permission metadata. `DeveloperActivitiesActivity` performs the focused discovery and forwards the selected package to the existing `ActivityExplorerActivity`, whose existing ActivityAccessPlanner/route guards remain authoritative for launchability.

## UI
A new `EngineeringIntentCenterActivity` is added to Dashboard and Module Center. It contains:
- Broadcast Lab: action, package, component, data URI, typed extra, preview, send.
- USSD / Dial Lab: input, preview URI, Dial action, optional direct Call action only when permission is already granted.
- Samsung Secret Code Center: receiver capability scan for a user-supplied code plus a safe system calculator launcher. Unknown-risk codes are not executed.
- Developer Activities shortcut opening the dedicated guarded metadata scanner.

## Safety and compatibility constraints
- No arbitrary shell command input.
- No root bridge.
- No signature-permission bypass.
- Non-exported or permission-gated activities remain subject to existing Activity Explorer route planning.
- Secret-code discovery may identify protected receivers that cannot be invoked by this app; this is reported as a capability/permission limitation rather than bypassed.
- No destructive or unknown Samsung code is auto-run.
- Calculator integration does not type or dispatch secret codes.
- Broadcast and dial intents are previewable before dispatch/launch.

## Verification
Focused source contracts cover broadcast construction, USSD/dial construction, Samsung receiver discovery, safe calculator launch, developer classification/scanning, and manifest/dashboard/module registration. Final verification requires the full host/source suite, `:app-dpc:assembleEnterpriseDebug`, APK existence, and aggregate `verify`.