# Offline cross-user runtime permission readback

Goal: make `OfflinePolicyApplier` verify Shizuku runtime-permission mutations for the actual target user instead of treating every non-current user as unverifiable.

Design:
- Add a pure permission-core parser for `dumpsys package <package>` user-scoped runtime permission output.
- Parser returns `GRANTED`, `DENIED`, or `UNKNOWN`. Missing permission inside a found user block is `DENIED`; missing user block or malformed dump is `UNKNOWN`.
- Extend the typed Shizuku UserService with read-only `getRuntimePermissionState(packageName, permissionName, userId)`.
- The service executes `/system/bin/dumpsys package <package>` with typed `ProcessBuilder` arguments and feeds output to the parser.
- Bump UserService version because the AIDL surface changes.
- `OfflinePolicyApplier` uses this readback for Shizuku GRANTED/DENIED on any target user; mismatch/UNKNOWN remains fail-closed.

Constraints:
- No hidden API, reflection, root, local ADB impersonation, or privilege escalation.
- No change to permission mutation routing.
- `DEFAULT` remains unavailable through Shizuku.
- Existing DPC/delegated readback behavior remains unchanged.
