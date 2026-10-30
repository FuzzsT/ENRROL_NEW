# Activity Launcher: friendly date filter labels

## Goal
Make the compact install/update age filters easier to read without changing filter semantics or state.

## Current state
The Activity Launcher uses one compact date-age bar with enum-derived labels such as `INST HOURS_24`, `INST DAYS_7`, `UPD HOURS_24`, and `UPD DAYS_30`. Filtering already works from `InstallAgeFilter` and `UpdateAgeFilter` and uses the existing 24-hour, 7-day, and 30-day thresholds.

## Proposed UI
Render the same states with shorter human-facing labels:

- Install: `INST ALL`, `INST 24H`, `INST 7D`, `INST 30D`
- Update: `UPD ALL`, `UPD 24H`, `UPD 7D`, `UPD 30D`

The enum names remain unchanged internally.

## Implementation shape
Add small label helpers for `InstallAgeFilter` and `UpdateAgeFilter`, or one shared helper if it stays simple and type-safe. `renderDateAgeBar()` uses the helper output instead of `filter.name` for visible text.

No changes to:
- `currentInstallAgeFilter` / `currentUpdateAgeFilter`
- filter order or predicates
- 24h / 7d / 30d constants
- reset behavior
- active filter counting
- filter summary state values
- tabs, sorting, search, paging, selection, or actions

## Error handling
There is no new failure mode. Every enum value must have an explicit label mapping so adding a new enum value causes a compile-time or contract-visible change rather than silently displaying an unexpected label.

## Testing
Use the existing Activity Launcher host/source contract in a RED -> GREEN cycle.

RED should require friendly visible labels such as `INST 24H`, `INST 7D`, `INST 30D`, `UPD 24H`, `UPD 7D`, and `UPD 30D` while preserving the existing enum/filter contracts.

GREEN should be the minimum production change in `ActivityLauncherHubActivity.kt` that satisfies those UI tokens. Then run the full Android CI: source contracts, enterprise debug APK build, APK existence verification, and final aggregate verify.

## Safety and scope
UI-label-only change. No new Android permissions, no Shizuku/root/hidden API changes, no package mutations, no settings mutations, and no security or enrollment behavior changes.

## Acceptance criteria
1. The compact date filter bar displays `INST ALL / 24H / 7D / 30D` and `UPD ALL / 24H / 7D / 30D`.
2. Existing install/update age filtering behavior is unchanged.
3. Reset and active-filter count behave exactly as before.
4. Existing filter summary continues to expose the internal enum state values.
5. Full Android CI is GREEN before merge.
6. Merge uses an exact-head guard and the resulting `main` commit is verified.
