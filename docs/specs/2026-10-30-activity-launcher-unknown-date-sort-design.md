# Activity Launcher Unknown Date Sorting Design

## Goal

Keep applications whose install/update timestamp is unknown (`<= 0`) at the end of date-sorted results, regardless of whether the selected date direction is ascending or descending.

## Current Problem

`INSTALL_DATE` and `UPDATE_DATE` currently sort directly by the raw timestamp and then reverse the full list for descending order. A timestamp of `0` therefore appears before valid timestamps in ascending order, even though the UI renders it as `unknown`.

## Chosen Behavior

For `AppSort.INSTALL_DATE` and `AppSort.UPDATE_DATE`:

- Treat timestamps `> 0L` as known.
- Treat timestamps `<= 0L` as unknown.
- Sort only known entries by the selected date direction.
- Append unknown entries after all known entries.
- Keep unknown entries at the end for both `Oldest → newest` and `Newest → oldest`.
- Do not filter unknown entries out of the catalog.

## Scope

This change is limited to Activity Launcher date sorting. It must not change:

- NAME or PACKAGE sorting.
- RECENT ordering.
- install/update age filters.
- search, paging, selection, favorites, actions or visible metadata.
- default sort directions or direction labels.
- permissions, Shizuku usage or package mutation behavior.

## Implementation Shape

Introduce a small private date-sorting helper or equivalent local logic that:

1. partitions the filtered list into known and unknown timestamps;
2. sorts known entries ascending by timestamp;
3. reverses only the known portion when descending is selected;
4. appends unknown entries afterward.

Avoid reversing the entire result for date sorts, because that would move unknown entries back to the front in descending mode.

## Test Strategy

Extend the existing Activity Launcher app-manager/source contract so RED requires:

- an explicit unknown-date guard (`timestamp > 0L` or equivalent);
- date sorting that keeps unknown entries after known entries;
- no direct full-list reverse path for INSTALL_DATE / UPDATE_DATE that can move unknown entries to the front;
- existing NAME/PACKAGE sort behavior and RECENT behavior remain present.

Then implement the smallest production change, run the focused contract, and require the full Android CI (`Source contracts`, `Enterprise debug APK`, APK verification, aggregate `verify`) to be GREEN before merge.

## Acceptance Criteria

- Ascending install-date sort: valid oldest → newest, then unknown.
- Descending install-date sort: valid newest → oldest, then unknown.
- Ascending update-date sort: valid oldest → newest, then unknown.
- Descending update-date sort: valid newest → oldest, then unknown.
- NAME, PACKAGE and RECENT ordering remain unchanged.
- No entries disappear solely because their date is unknown.
