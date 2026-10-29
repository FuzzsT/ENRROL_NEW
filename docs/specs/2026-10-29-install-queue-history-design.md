# Install Queue + History — Design

## Goal

Extend the existing APK/APKS Installer and App Updater with a durable, fully sequential install queue and bounded local history while preserving the current Android PackageInstaller trust boundary.

## Scope

The feature adds:

- single-file `Add to queue`,
- multi-select file import,
- FIFO execution,
- one active PackageInstaller session at a time,
- per-item lifecycle state,
- retry of failed items only,
- clearing completed queue rows without deleting history,
- restart-safe persisted queue state,
- bounded local history of the most recent 100 completed outcomes.

The feature does not add shell/root installation, `pm install`, silent confirmation bypasses, APK repackaging, package-name rewriting, signer bypasses, downgrade bypasses, or background installation outside Android's PackageInstaller rules.

## User flow

1. The user opens APK/APKS Installer or App Updater.
2. The user adds one package with `Add to queue` or selects multiple files in one picker.
3. Each selected item is imported into app-private cache/storage and represented by a queue item.
4. Queue items are processed strictly FIFO.
5. Before a session is created, the existing APK/APKS validation runs:
   - supported file type,
   - APKS path/size/split validation,
   - package identity,
   - updater target match,
   - downgrade block,
   - signer continuity.
6. Exactly one item may own an active PackageInstaller session.
7. When Android requires confirmation, the queue item remains active in `WAITING_USER` until the final PackageInstaller callback arrives.
8. The final callback transitions the item to `SUCCESS`, `FAILED`, or `SKIPPED`, records a history entry, releases the active slot, and starts the next queued item.
9. `Retry failed` requeues only failed items.
10. `Clear completed` removes terminal items from the queue view/state but leaves history intact.

## Queue states

Queue items use these persisted states:

- `QUEUED` — waiting for execution.
- `PREFLIGHT` — validating file/package metadata.
- `WAITING_USER` — session committed and Android user action may be required.
- `INSTALLING` — PackageInstaller accepted the session and completion is pending.
- `SUCCESS` — final successful result.
- `FAILED` — final failed result or preflight failure.
- `SKIPPED` — explicitly skipped/non-runnable item.

At most one item may be in `PREFLIGHT`, `WAITING_USER`, or `INSTALLING` at a time.

## Restart recovery

Queue state is persisted in app-private storage. On process restart:

- terminal states remain terminal,
- `QUEUED` remains queued,
- an item left in `PREFLIGHT`, `WAITING_USER`, or `INSTALLING` is not blindly recommitted,
- the coordinator marks such an item `FAILED` with a recovery-safe reason such as `INTERRUPTED_RECHECK_REQUIRED` unless the PackageInstaller session/result can be safely reconciled,
- the user may retry it explicitly.

This avoids duplicate commits after process death.

## Components

### InstallQueueStore

Owns durable queue and history serialization. It exposes read/replace/update operations and enforces the history cap of 100 records. It stores only app-private imported file references needed for retry; history does not retain APK/APKS bytes or original external paths.

### InstallQueueCoordinator

Owns the state machine. Responsibilities:

- enqueue one or many items,
- choose the next FIFO runnable item,
- ensure only one active item/session exists,
- call the existing package parsing, trust, APKS staging, and PackageInstaller paths,
- map final callbacks to the owning queue item,
- advance to the next item only after a terminal callback,
- retry failed items,
- clear completed queue items,
- recover interrupted states safely after restart.

### AppInstallStatusReceiver

The receiver continues to receive Android PackageInstaller results, but callbacks include a stable queue item identifier in addition to session information. It records the final result through the queue coordinator/store rather than only overwriting a single global "last result" string.

### AppPackageManagerActivity

The activity remains a UI/controller surface. It displays the queue and history and delegates execution to the coordinator. Existing single-install and updater validation logic should be extracted/reused rather than duplicated.

## Queue item model

Each queue item contains at least:

- stable item ID,
- enqueue timestamp,
- mode (`INSTALLER` or `UPDATER`),
- imported app-private file reference,
- display name,
- file kind (`APK` or `APKS`),
- selected updater target package when applicable,
- discovered package/version after preflight,
- current state,
- PackageInstaller session ID when allocated,
- short result/error code and user-readable summary.

No signing secrets or external filesystem paths are persisted.

## History model

A history record stores:

- completion timestamp,
- queue item ID,
- package name if known,
- incoming version if known,
- APK/APKS type,
- installer/updater mode,
- final result,
- short message.

Keep only the newest 100 records. History contains metadata only, not APK/APKS content.

## Multi-select

The picker uses `Intent.EXTRA_ALLOW_MULTIPLE` and accepts the same MIME/file constraints as the current picker. Both `data.data` and `clipData` are normalized into an ordered URI list. Duplicate URI selections in the same import operation are de-duplicated before enqueue.

## Error handling

Preflight failures become terminal `FAILED` items and do not block the queue. Session creation/staging/commit failures abandon the session when possible, record `FAILED`, and advance. Android cancellation is recorded as a terminal non-success result. A failure in one item never automatically deletes or rolls back other queue items.

## Retry semantics

`Retry failed` creates a fresh execution attempt for existing `FAILED` rows using the persisted app-private imported package. It clears stale session IDs and puts only those items back to `QUEUED`. Successful and skipped entries are not retried automatically.

## Cleanup semantics

Imported package files remain only while required by an active/retriable queue item. Removing a terminal queue item may delete its imported private copy when no other queue item references it. History remains metadata-only.

## UI

The existing installer/updater page gains:

- `Add to queue`,
- `Add multiple`,
- queue summary and rows with state/result,
- `Start / Resume queue`,
- `Retry failed`,
- `Clear completed`,
- a compact `History` section showing recent terminal results.

The interface must make it clear that Android can request confirmation for each installation and that queue execution pauses until each final result is known.

## Tests

Host/source contract tests must require the queue/store/coordinator integration and preserve existing signer/downgrade/APKS guards.

Focused logic tests cover:

- FIFO ordering,
- only one active session at a time,
- no next-item execution before final callback,
- callback-to-item correlation,
- retry only `FAILED`,
- `Clear completed` preserving history,
- restart recovery without duplicate commit,
- multi-select normalization/de-duplication,
- history capped at 100 newest results.

Android CI must still pass the full host/source suite, enterprise debug APK assembly, APK existence verification, and aggregate `verify` check.

## Acceptance criteria

The feature is complete when:

1. multiple APK/APKS files can be queued,
2. queue execution is strictly sequential and callback-driven,
3. no two PackageInstaller sessions are actively processed by the coordinator at once,
4. existing package/signature/version/APKS validation remains enforced,
5. final PackageInstaller results are correlated to the correct queue item,
6. failed items can be explicitly retried,
7. restart recovery cannot blindly duplicate an interrupted commit,
8. the latest 100 history results survive activity/process recreation,
9. no shell/root/silent-install bypass is introduced,
10. full PR CI is GREEN.
