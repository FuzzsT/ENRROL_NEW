# Activity Launcher Future Timestamp Guard Design

## Goal
Prevent Activity Launcher install/update age filters from treating future timestamps as recent.

## Scope
- Add one shared helper in `ActivityLauncherHubActivity.kt`:
  `private fun isTimestampWithinAge(timestamp: Long, now: Long, maxAgeMs: Long): Boolean`
- The helper returns true only when:
  - `timestamp > 0L`
  - `timestamp <= now`
  - `now - timestamp <= maxAgeMs`
- Use the helper for install-age filters `24H`, `7D`, `30D` based on `firstInstallTime`.
- Use the helper for update-age filters `24H`, `7D`, `30D` based on `lastUpdateTime`.
- Keep `ALL` behavior unchanged.

## Expected behavior
- Timestamp exactly equal to `now`: included in every matching age window.
- Positive timestamp within the selected age window: included.
- Positive timestamp older than the selected age window: excluded.
- Timestamp `0` or negative: excluded from age-specific filters.
- Timestamp greater than `now`: excluded from age-specific filters.
- `ALL`: remains unfiltered by timestamp validity.

## Non-goals
- No UI changes.
- No changes to friendly date labels.
- No changes to sorting, search, paging, selection, reset, summary, source/state/target/debug filters.
- No new permissions, Shizuku paths, hidden APIs, package mutations, or system mutations.

## Verification strategy
Use the existing Activity Launcher app-manager source contract as the first RED seam by requiring the helper and helper-backed predicates while rejecting the six old inline predicates. Observe the expected CI failure before production code is changed. Then implement the minimum helper substitution and require full Android CI GREEN before exact-head merge.
