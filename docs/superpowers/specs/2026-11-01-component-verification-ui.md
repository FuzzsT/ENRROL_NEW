# Component verification UI — short design

Intent: surface the verified target-user component mutation result that is already produced by `ShizukuComponentStateExecutor`, so the operator can distinguish a confirmed state change from a mismatch or unknown readback.

Goals:
- Autostart status must show `VERIFIED`, requested state and observed state for Shizuku mutations.
- Activity Explorer single-component, batch and snapshot-restore flows must expose the same verified result instead of saying readback is not claimed or only happens via rescan.
- `Enable & Launch` must launch after Shizuku mutation only when the target-user state is verified as `ENABLED`.
- Add a host-executed runtime smoke that proves VERIFIED / MISMATCH / UNKNOWN behavior and the launch gate.

Non-goals:
- No new Android permissions, hidden APIs, ADB bypasses or privilege expansion.
- No redesign of component routing or target selection.
- No change to the PackageManager shell command contract introduced in PR #217.

Approaches considered:
1. Keep raw status strings in each Activity. Small diff, but duplicates verification formatting and makes future drift likely.
2. Add one shared pure helper in `activity/core` for status formatting and the launch gate, then feed it the verified Shizuku result from Autostart and Activity Explorer. Chosen: consistent semantics and executable host coverage without Android/Shizuku runtime dependencies.
3. Move presentation into the Shizuku integration module. Rejected because the integration layer should not own UI/diagnostic wording.

Constraints:
- Existing `ShizukuComponentMutationResult` remains the source of truth at the Android integration boundary.
- Unknown/mismatched readback remains fail-closed.
- Existing protected-component guards remain unchanged.
