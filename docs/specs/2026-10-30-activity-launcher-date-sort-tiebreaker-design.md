# Activity Launcher Date Sort Tie-Breaker Design

## Goal

Make INSTALL_DATE and UPDATE_DATE sorting deterministic when multiple applications have the same valid timestamp.

## Current Problem

`sortByTimestampUnknownLast(...)` currently sorts valid entries only by the selected timestamp. Kotlin's stable sort preserves the incoming order for equal timestamps, but that incoming `PackageManager` inventory order is not a user-facing ordering contract. Equal-date rows can therefore appear in an arbitrary-looking order across inventory refreshes or devices.

## Chosen Behavior

For valid timestamps, sort by:

1. selected timestamp;
2. application label, case-insensitively, ascending;
3. package name, case-insensitively, ascending.

Sort direction applies only to the timestamp key:

- ASCENDING: oldest timestamp first;
- DESCENDING: newest timestamp first.

The tie-breakers always remain alphabetical ascending so rows sharing one date have the same deterministic order in both date-sort directions.

Invalid/unknown timestamps (`timestamp <= 0L` or `timestamp > now`) continue to stay after all valid timestamps. Their existing stable input order remains unchanged; this PR does not add an alphabetical order for unknown entries.

## Scope

This change affects only INSTALL_DATE and UPDATE_DATE ordering of valid entries that share the same timestamp.

It must not change:

- timestamp validity rules from PR #155;
- unknown/invalid timestamps staying last;
- age filters or displayed date formatting;
- NAME or PACKAGE sorting;
- RECENT ordering;
- tabs, search, filters, selection, pagination, lifecycle state restoration, favorites, inventory discovery;
- permissions, Shizuku, OEM/Knox, package mutation, or enrollment/setup behavior.

No new UI controls are added.

## Recommended Implementation Shape

Keep `sortByTimestampUnknownLast(...)` as the single date-sort helper. Partition valid and unknown entries using the existing shared timestamp validity rule, then sort valid entries with a comparator equivalent to:

```kotlin
val validComparator = compareBy<AppEntry> { timestamp(it) }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
```

For descending date order, reverse only the timestamp key rather than calling `reversed()` on the entire fully sorted list, because reversing the whole list would also invert the alphabetical tie-breakers.

Equivalent comparator construction is acceptable if it preserves the same behavior.

## Error Handling / Edge Cases

- Equal timestamp + equal case-insensitive labels falls back to package name.
- If labels differ only by case and package names also compare equal case-insensitively, the stable sort may preserve input order; package names are unique in normal Android inventory, so this is not expected to affect distinct apps.
- Invalid timestamps remain unknown and last.
- `timestamp == now` remains valid.

## Test Strategy

Extend the active Activity Launcher contracts to require deterministic date tie-breaking and to reject whole-list `reversed()` for date sorting if that would reverse tie-breakers.

Establish genuine RED before modifying production code. Then implement the minimum comparator change and require full Android CI GREEN, including source contracts, enterprise debug APK build, APK existence verification, and aggregate `verify`.

## Acceptance Criteria

- Valid equal-date apps are ordered case-insensitively by label and then package name.
- ASCENDING date sort orders timestamps oldest-to-newest while tie-breakers stay A-to-Z.
- DESCENDING date sort orders timestamps newest-to-oldest while tie-breakers still stay A-to-Z.
- Unknown/invalid timestamps remain last.
- NAME, PACKAGE, RECENT, filters and lifecycle behavior are unchanged.

## Unresolved Product Decisions

None.
