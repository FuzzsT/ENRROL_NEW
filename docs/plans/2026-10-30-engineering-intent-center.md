# Engineering Intent Center Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded engineering center for broadcast diagnostics, USSD/dial intents, Samsung secret-code capability discovery, and developer activity filtering.

**Architecture:** Keep intent construction and classification in small pure/Android-facing helpers, then integrate them through one app activity and the existing Activity Explorer. Existing ActivityAccessPlanner and typed Shizuku routes stay authoritative; no arbitrary shell bridge is added.

**Tech Stack:** Kotlin, Android SDK, existing Gradle multi-module project, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints
- No arbitrary `am`/shell command input.
- No root bridge or signature-permission bypass.
- Default USSD behavior is `ACTION_DIAL`; direct call requires existing Android permission.
- Secret-code discovery can report protected receivers but must not bypass their permissions.
- Destructive or unknown-risk secret codes are never automatically executed.
- Existing activity launch guards remain authoritative.

---

### Task 1: Typed broadcast and dial helpers

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/BroadcastIntentBuilder.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/UssdIntentFactory.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Produces: `BroadcastRequest`, `BroadcastExtra`, `BroadcastIntentBuilder.build(request)`, `UssdIntentFactory.dial(raw)`, `UssdIntentFactory.call(raw)`.

- [ ] Add failing source contract requiring typed extras, explicit package/component support, URI support, `ACTION_DIAL`, `ACTION_CALL`, and no shell execution.
- [ ] Verify RED through host/source suite.
- [ ] Implement minimum helpers with deterministic parsing and validation.
- [ ] Re-run focused contract and host/source suite.

### Task 2: Samsung secret-code capability discovery

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/SamsungSecretCodeCatalog.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/SamsungSecretCodeDiscovery.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Produces: `SecretCodeRisk`, `SecretCodeEntry`, `SecretCodeReceiver`, `SamsungSecretCodeDiscovery.discover(code)`.

- [ ] Add failing assertions for `TelephonyManager.ACTION_SECRET_CODE`, legacy action compatibility, `android_secret_code://`, risk classification, and protected receiver reporting.
- [ ] Verify RED.
- [ ] Implement discovery via `PackageManager.queryBroadcastReceivers` only.
- [ ] Keep destructive/unknown-risk entries non-executable by design.

### Task 3: Engineering Intent Center UI

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EngineeringIntentCenterActivity.kt`
- Modify: `apps/dpc/app/src/main/AndroidManifest.xml`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt`
- Test: `tools/tests/test_engineering_intent_center_contract.py`

**Interfaces:**
- Consumes helpers from Tasks 1-2.
- Produces one dashboard/module entry and explicit preview/send/open actions.

- [ ] Add failing integration assertions for activity registration, dashboard/module entry, preview controls, broadcast send, USSD dial, capability scan, and Developer Activities shortcut.
- [ ] Verify RED.
- [ ] Implement minimum UI using `ModernUi` conventions.
- [ ] Run source contracts and APK compile.

### Task 4: Hidden developer activity classification

**Files:**
- Create: `apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/DeveloperActivityClassifier.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt`
- Test: `tools/tests/test_activity_explorer_launcher_ui_contract.py`

**Interfaces:**
- Produces: `DeveloperActivityClassifier.isDeveloperLike(activity)` and `ActivityQuickFilter.DEVELOPER`.

- [ ] Add failing assertions for classifier tokens and Developer quick filter.
- [ ] Verify RED.
- [ ] Implement token-based classification using class/package/permission metadata only.
- [ ] Keep existing ActivityAccessPlanner route logic unchanged.
- [ ] Run focused source contracts, full host/source suite, enterprise APK build, APK existence check, and aggregate `verify`.

## Externally observable decisions
- Broadcast dispatch uses Android framework `sendBroadcast` and reports exceptions; it does not promise ordered-result codes.
- `ACTION_CALL` is shown only when `CALL_PHONE` is already granted; the feature does not auto-request it.
- Secret-code discovery reports receiver availability and required permission; execution is limited to safe/info entries and still subject to Android permission checks.