# Engineering Intent Center Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded engineering center for broadcast diagnostics, USSD/dial intents, Samsung secret-code capability discovery, safe calculator launch, and developer activity discovery.

**Architecture:** Keep intent construction and classification in small focused helpers, expose them through one Engineering Intent Center, and use a dedicated developer-activity scanner that hands packages to the existing guarded Activity Explorer. Existing ActivityAccessPlanner and typed Shizuku routes stay authoritative; no arbitrary shell bridge is added.

**Tech Stack:** Kotlin, Android SDK, existing Gradle multi-module project, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints
- No arbitrary `am`/shell command input.
- No root bridge or signature-permission bypass.
- Default USSD behavior is `ACTION_DIAL`; direct call requires existing Android permission.
- Secret-code discovery can report protected receivers but must not bypass their permissions.
- Destructive or unknown-risk secret codes are never automatically executed.
- Calculator integration only opens the system calculator category; it does not inject service codes.
- Existing Activity Explorer launch guards remain authoritative.

---

### Task 1: Typed broadcast and dial helpers

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/BroadcastIntentBuilder.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/UssdIntentFactory.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Produces: `BroadcastRequest`, `BroadcastExtra`, `BroadcastIntentBuilder.build(request)`, `UssdIntentFactory.dial(raw)`, `UssdIntentFactory.call(raw)`.

- [x] Add failing source contract requiring typed extras, explicit package/component support, URI support, `ACTION_DIAL`, `ACTION_CALL`, and no shell execution.
- [x] Verify RED through host/source suite.
- [x] Implement minimum helpers with deterministic validation.
- [ ] Verify final GREEN through the full source gate.

### Task 2: Samsung secret-code capability discovery and calculator launch

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/SamsungSecretCodeCatalog.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/SamsungSecretCodeDiscovery.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EngineeringIntentCenterActivity.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Produces: `SecretCodeRisk`, `SecretCodeEntry`, `SecretCodeReceiver`, `SamsungSecretCodeDiscovery.discover(code)`.

- [x] Require `TelephonyManager.ACTION_SECRET_CODE`, legacy action compatibility, `android_secret_code://`, risk classification, and protected receiver reporting.
- [x] Implement discovery via `PackageManager.queryBroadcastReceivers` only.
- [x] Keep user-supplied/unknown-risk entries non-executable.
- [x] Add safe calculator launcher via `Intent.CATEGORY_APP_CALCULATOR`; no service-code injection.

### Task 3: Engineering Intent Center UI and app integration

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EngineeringIntentCenterActivity.kt`
- Modify: `apps/dpc/app/src/main/AndroidManifest.xml`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Consumes helpers from Tasks 1-2.
- Produces one dashboard/module entry and explicit preview/send/open actions.

- [x] Register activity and Dashboard/Module Center entry.
- [x] Implement Broadcast Lab, USSD/Dial Lab, secret-code receiver scan, calculator launch, and Developer Activities shortcut using `ModernUi` conventions.
- [ ] Run final source contracts and APK compile.

### Task 4: Guarded developer activity discovery

**Files:**
- Create: `apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/DeveloperActivityClassifier.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DeveloperActivitiesActivity.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Produces: `DeveloperActivityClassifier.isDeveloperLike(activity)`.
- `DeveloperActivitiesActivity` lists matching metadata and opens the selected package in existing `ActivityExplorerActivity`.

- [x] Implement token-based classification using package/class/permission metadata only.
- [x] Show exported/enabled/required-permission metadata.
- [x] Do not add a hidden-component bypass; forward package inspection to the existing Activity Explorer.
- [ ] Run focused source contracts, full host/source suite, enterprise APK build, APK existence check, and aggregate `verify`.

## Externally observable decisions
- Broadcast dispatch uses Android framework `sendBroadcast` and reports exceptions; it does not promise ordered-result codes.
- `ACTION_CALL` is attempted only when `CALL_PHONE` is already granted; the feature does not request that permission.
- User-supplied secret-code discovery reports receiver availability and required permission but does not execute the code.
- Calculator integration opens the system-selected calculator only.
- Developer activity discovery never overrides the existing Activity Explorer route/permission decisions.