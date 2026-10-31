# Component verification UI — short design

Intent: surface the verified target-user component mutation result that is already produced by `ShizukuComponentStateExecutor`, so the operator can distinguish a confirmed state change from a mismatch or unknown readback.

Goals:
- Autostart status must show `VERIFIED`, requested state and observed state for Shizuku mutations.
- Activity Explorer single-component, batch and snapshot-restore flows must expose the same verified result instead of saying readback is not claimed or only happens via rescan.
- `Enable & Launch` must launch after Shizuku mutation only when the target-user state is verified as `ENABLED`.
- Add a host/source runtime-smoke contract that follows UserService v4 -> verified executor -> both UI call sites.

Non-goals:
- No new Android permissions, hidden APIs, ADB bypasses or privilege expansion.
- No redesign of component routing or target selection.
- No change to the PackageManager shell command contract introduced in PR #217.

Approaches considered:
1. Keep raw status strings in each Activity. Small diff, but duplicates verification formatting and makes future drift likely.
2. Add one shared app-level formatter for Shizuku component mutation results, then use it in Autostart and Activity Explorer. Chosen: small surface, consistent status semantics, and easy source-contract coverage.
3. Move presentation into the Shizuku integration module. Rejected because the integration layer should not own UI wording.

Constraints:
- Existing `ShizukuComponentMutationResult` remains the source of truth.
- Unknown/mismatched readback remains fail-closed.
- Existing protected-component guards remain unchanged.
