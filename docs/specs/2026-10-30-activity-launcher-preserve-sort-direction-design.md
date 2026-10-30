# Activity Launcher Preserve Sort Direction Design

## Goal

Prevent re-selecting the already active sort key from unexpectedly resetting the user's current sort direction.

## Current Problem

`renderSortBar()` currently assigns both `currentSort` and a default `currentSortDirection` every time a sort pill is clicked. This means a user can set NAME to `Z → A` (or INSTALL_DATE to `Oldest → newest`) using the Direction control, then lose that choice simply by tapping the already-selected sort pill again.

## Chosen Behavior

A sort-pill click has two cases:

1. **Different sort key selected** — update `currentSort` and set the selected key's existing default direction:
   - NAME / PACKAGE → ASCENDING;
   - INSTALL_DATE / UPDATE_DATE → DESCENDING.
2. **Already active sort key selected** — keep both `currentSort` and `currentSortDirection` unchanged.

The existing Direction button remains the only control that toggles the direction for the active sort key.

## Scope

This change affects only sort-pill click behavior in Activity Launcher.

It must not change:

- default direction when switching to a different sort key;
- `toggleSortDirection()` behavior;
- deterministic date tie-breakers from PR #156;
- unknown-last date sorting;
- NAME / PACKAGE comparison rules;
- RECENT behavior, where sort controls remain disabled and most-recent-first is preserved;
- tabs, filters, search, selection, pagination, favorites, inventory discovery or lifecycle-state restoration;
- permissions, Shizuku, OEM/Knox, package mutation or enrollment/setup behavior.

No new UI control is added.

## Recommended Implementation Shape

Keep the current sort-pill handler, but only assign a new default direction when `sort != currentSort`.

Equivalent logic:

```kotlin
if (sort != currentSort) {
    currentSort = sort
    currentSortDirection = defaultSortDirection(sort)
}
renderSortBar()
renderApps()
```

A small helper such as `defaultSortDirection(sort: AppSort)` is acceptable if it reduces duplication, but it is not required. Do not make an active-pill click toggle direction because the existing Direction button already owns that behavior.

## Edge Cases

- NAME selected + DESCENDING + NAME tapped again → remains DESCENDING.
- INSTALL_DATE selected + ASCENDING + INSTALL_DATE tapped again → remains ASCENDING.
- NAME DESCENDING → UPDATE_DATE selected → UPDATE_DATE becomes DESCENDING (its existing default).
- UPDATE_DATE ASCENDING → PACKAGE selected → PACKAGE becomes ASCENDING (its existing default).
- RECENT tab → sort controls remain disabled; no behavior change.

## Test Strategy

Extend an active Activity Launcher source contract or add a focused contract that requires a guard preventing direction reset on an already-active sort key while preserving default-direction assignment when the sort key changes.

Establish genuine RED before modifying production code. Then implement the minimum handler change and require full Android CI GREEN: source contracts, enterprise debug APK build, APK existence verification and aggregate `verify`.

## Acceptance Criteria

- Re-selecting the active sort key does not change sort direction.
- Switching to another sort key still applies that key's current default direction.
- Direction button still toggles ASCENDING/DESCENDING normally.
- RECENT and all unrelated Activity Launcher behavior remain unchanged.

## Unresolved Product Decisions

None.
