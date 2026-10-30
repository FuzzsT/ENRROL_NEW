# Activity Launcher Future-Date Consistency Design

## Goal

Use one consistent definition of a valid application timestamp across Activity Launcher age filters, date sorting, and date display.

## Current Problem

The age filters already reject future timestamps by requiring `timestamp <= now`, but two other paths are less strict:

- `sortByTimestampUnknownLast(...)` currently treats every `timestamp > 0L` as valid, so a future install/update timestamp can sort as the newest valid date;
- `formatInstallDate(...)` currently formats every positive timestamp, so a future timestamp is displayed as an apparently real future date.

This creates inconsistent behavior for the same underlying metadata.

## Chosen Behavior

Define a valid timestamp as:

- `timestamp > 0L`; and
- `timestamp <= now`.

Anything else is invalid/unknown.

Apply that rule consistently:

1. Age filters keep their existing maximum-age check, but reuse the shared validity rule.
2. Date sorting treats invalid timestamps (`<= 0L` or `> now`) as unknown and keeps them at the end in both sort directions.
3. Date presentation renders invalid timestamps as `"unknown"` instead of displaying a future date.

Normal valid timestamps keep their existing behavior.

## Recommended API Shape

Use a small shared helper:

```kotlin
private fun isValidTimestamp(timestamp: Long, now: Long): Boolean =
    timestamp > 0L && timestamp <= now
```

Then keep the age helper as:

```kotlin
private fun isTimestampWithinAge(timestamp: Long, now: Long, maxAgeMs: Long): Boolean =
    isValidTimestamp(timestamp, now) && now - timestamp <= maxAgeMs
```

`sortByTimestampUnknownLast(...)` should capture one `now` value for the operation and partition by `isValidTimestamp(timestamp(it), now)`.

`formatInstallDate(...)` should also validate against a single current-time value before formatting.

Equivalent factoring is acceptable if the same validity rule is used in all three paths and no unchecked future timestamp is treated as valid.

## Time Consistency

Each filtering/sorting/render operation may obtain its own `System.currentTimeMillis()` snapshot. Do not introduce a persistent clock or durable time state.

Within a single helper invocation, use one captured `now` value rather than calling the clock repeatedly per item where practical.

## Scope

This change must not alter:

- the 24H / 7D / 30D age thresholds;
- sort direction behavior for valid timestamps;
- the rule that unknown/invalid dates remain last;
- NAME/PACKAGE/RECENT sorting behavior;
- filtering for state, install source, target SDK, debuggable state, favorites, or search;
- saved Activity state behavior added in PR #154;
- package inventory discovery;
- permissions, Shizuku, OEM/Knox, package mutation, or setup behavior.

No new UI controls are added.

## Test Strategy

Extend the active Activity Launcher source contract to require:

- a shared valid-timestamp rule equivalent to `timestamp > 0L && timestamp <= now`;
- `isTimestampWithinAge(...)` to reuse that validity rule;
- sorting to partition using the shared validity rule instead of only `timestamp > 0L`;
- date formatting to return `"unknown"` for future timestamps;
- the stale direct `timestamp > 0L` sort validity check to be absent.

Establish genuine RED before modifying production code, then apply the minimal implementation and require full Android CI GREEN before merge.

## Acceptance Criteria

- `timestamp <= 0L` is invalid everywhere.
- `timestamp > now` is invalid everywhere.
- `timestamp == now` is valid.
- A valid timestamp exactly on an age boundary remains accepted by the existing age filters.
- Future install/update timestamps appear at the end of date sorts in both directions.
- Future install/update timestamps display as `unknown`.
- Existing valid dates still display in `yyyy-MM-dd` format.
- NAME, PACKAGE and RECENT behavior is unchanged.

## Unresolved Product Decisions

None.
