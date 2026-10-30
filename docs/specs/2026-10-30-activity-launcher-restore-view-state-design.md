# Activity Launcher View-State Restoration Design

## Goal

Preserve the current Activity Launcher view state across Android Activity recreation, such as configuration changes, without turning that state into a persistent application preference.

## Current Problem

`ActivityLauncherHubActivity` currently saves only `selectedPackages` in `onSaveInstanceState()`. After Activity recreation, the following state returns to defaults:

- selected tab;
- selected sort;
- sort direction;
- state filter;
- install-source filter;
- install-age filter;
- update-age filter;
- target-SDK filter;
- debuggable filter;
- search query;
- current visible-app pagination limit.

This causes the catalog to visibly jump back to its default view even though Android provided a saved-instance-state restoration path.

## Chosen Behavior

Restore all transient Activity Launcher view state from `savedInstanceState`:

- `currentTab`;
- `currentSort`;
- `currentSortDirection`;
- `currentStateFilter`;
- `currentInstallSourceFilter`;
- `currentInstallAgeFilter`;
- `currentUpdateAgeFilter`;
- `currentTargetSdkFilter`;
- `currentDebuggableFilter`;
- search text;
- `visibleAppLimit`;
- `selectedPackages` (existing behavior retained).

The restored state applies only to the recreated Activity instance. Do not persist these values in `SharedPreferences` or another durable store.

## Restore Safety

Enum values must be restored defensively. Saved values may be missing or stale after future enum changes, so restoration must fall back to the existing defaults rather than throw.

Recommended shape:

```kotlin
private inline fun <reified T : Enum<T>> restoredEnum(
    state: Bundle?,
    key: String,
    fallback: T,
): T = state?.getString(key)
    ?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } }
    ?: fallback
```

Equivalent safe logic is acceptable if it does not use unchecked `valueOf()` on untrusted saved state.

`visibleAppLimit` must be clamped to at least `INITIAL_RENDER_LIMIT` so corrupted or stale saved state cannot restore an invalid non-positive page size.

## Lifecycle Shape

In `onCreate(savedInstanceState)`:

1. restore enum/filter/sort/tab state before rendering controls;
2. restore `visibleAppLimit`;
3. restore the existing selected package set;
4. render the shell;
5. restore the saved search query after the `EditText` exists, with search rendering temporarily suppressed so restoration does not trigger an unnecessary intermediate catalog render;
6. start installed-app discovery as today.

In `onSaveInstanceState(outState)` save all transient state listed above using stable key constants.

## Scope

This change must not alter:

- default values for a fresh Activity launch;
- `SharedPreferences` behavior for favorites/recents;
- filtering semantics;
- date sorting semantics;
- selection semantics;
- package inventory discovery;
- permissions, Shizuku, OEM/Knox or package mutation behavior;
- visible labels or layout except that controls/search return to their prior state after recreation.

## Test Strategy

Extend the active Activity Launcher source contract to require:

- save keys for every transient state field;
- safe enum restoration with fallback;
- restoration of search text with `suppressSearchRender` guarding `setText`;
- restoration/clamping of `visibleAppLimit`;
- existing selected-package restoration remains present;
- no new `SharedPreferences` writes for the transient state.

Establish genuine RED before modifying production code, then make the smallest implementation and require full Android CI GREEN (`Source contracts`, Enterprise debug APK, APK verification and aggregate `verify`) before merge.

## Acceptance Criteria

- Rotate/recreate while on any tab: the same tab is selected afterward.
- Sort field and direction survive recreation.
- Every filter survives recreation.
- Search text survives recreation.
- Loaded pagination depth survives recreation and is never restored below `INITIAL_RENDER_LIMIT`.
- Selected packages continue to survive recreation.
- Missing/unknown saved enum names fall back to existing defaults without crashing.
- A completely fresh Activity launch still starts with the current defaults.
- Closing and reopening the Activity later does not restore the previous transient view state from durable preferences.

## Unresolved Product Decisions

None.