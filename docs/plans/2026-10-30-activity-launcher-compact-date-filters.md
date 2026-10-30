# Activity Launcher compact date filters

## Goal
Reduce vertical space used by date filters while preserving existing filtering behavior.

## Design
- Replace separate `installAgeBar` and `updateAgeBar` containers with one `dateAgeBar`.
- Render both install-age and update-age controls in one horizontally scrollable row.
- Use shorter labels: `INST ...` and `UPD ...`.
- Preserve `InstallAgeFilter` and `UpdateAgeFilter` state, reset behavior, summary, filtering order, thresholds, sorting, search, selection and actions.
- No new permissions, Shizuku/root/hidden APIs, or package/system mutations.

## Verification
- Active host contract first fails on missing compact date-filter layout.
- Minimal Kotlin change satisfies the contract.
- Full Android CI must pass before merge.
- Merge is guarded by exact PR head SHA.
