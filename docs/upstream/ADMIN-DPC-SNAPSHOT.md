# ser-mk/admin-dpc upstream snapshot

Reference-only upstream. It is not added to the DPC-AIO Gradle classpath.

- Repository: `ser-mk/admin-dpc`
- Branch: `master`
- Commit observed: `2bc77ccd902f9b23de24fffbbf8336f22e502276`
- Tree observed: `a927bfd15184bab1c82d41c0f1316c4183d2cb68`
- Commit date: 2023-01-23
- Release observed: `v0.1`
- License: Apache-2.0

The upstream tree includes classic DPC/TestDPC-style policy surfaces such as app management, kiosk/LockTask,
password and keyguard policies, Wi-Fi, VPN/network usage, system update policy, profile policy, app restrictions,
delegation, cross-profile intent filters and permission management.

A complete source archive is intentionally fetched outside the Android build tree. Run either:

- `tools/upstream/fetch_full_upstreams.sh`
- `tools/upstream/fetch_full_upstreams.ps1`

Both scripts pin this exact commit instead of following a moving branch.
