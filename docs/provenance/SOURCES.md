# Source provenance

This project is a clean integration workspace. Reference archives remain external inputs unless a file is explicitly copied with compatible licensing and attribution.

## Production-reference sources

- Google TestDPC 9.0.12 — Apache-2.0. Canonical reference for DevicePolicyManager behavior and provisioning flows.
- Dhizuku-API — MIT. API/AIDL compatibility reference; Gradle wrapper in this workspace was copied from the supplied MIT archive.
- Dhizuku application — GPL-3.0. Behavioral/reference source only unless a future module explicitly adopts GPL-compatible distribution terms.
- InstallerX Revived — GPL-family source. Workflow/reference source; AIO installer logic is implemented behind local interfaces rather than copied wholesale.
- InstallerX MIO — GPL-family source. Additional workflow/error/reference source only.
- enroll_android — provisioning/reference material.

## Lab/reference-only sources

- Dhizuku-API-Xposed — Xposed compatibility research; never a dependency of enterpriseRelease.
- testjne — native/stealth research reference; only neutral self-process diagnostics may be reimplemented in lab/native modules.
- dpt-shell — APK protection research/reference; not a runtime dependency of enterpriseRelease.

## Input archive SHA-256

```
bca09766a4e63cc552f2f7aa2e6c1ed7a48c34e246e4a9e5e4f82984b29e2824  /mnt/data/android-testdpc-9.0.12.zip
fceea8f80e30e886e990e177c294a737f4a005108c94a2bec8dd2172252f079d  /mnt/data/Dhizuku-API-main (2).zip
9f07a6bdd7945877f466bf25ad243f601f893944f0d72ad1217993b7983ac644  /mnt/data/Dhizuku-main(1).zip
13aae03042bd2bb8da88009ef01bc0f459395567d6f9fe136ef3ee2061809d58  /mnt/data/InstallerX-Revived-main.zip
9028ab7e372d79efea9b2157261f2b822f3755e9de089b270306254e4a916941  /mnt/data/InstallerX-MIO-main (2).zip
00d688c3405f77afcd10370913fd72b857411288dc42a33f7c37b7a997697f8a  /mnt/data/enroll_android-main(2).zip
455d9c097fd3b147c40976996d1be7d9c217406d982f72dfe193da70ad00718b  /mnt/data/Dhizuku-API-Xposed-main (1).zip
e63bb8ed3ea4fd67345c0b5777d0f91fc348dfd54703da2110778551ed60f2d2  /mnt/data/testjne-main(2).zip
dd3414875e67ad3dddd7d1d721fb8c88e74bab67b742975e7c078177a7d6993c  /mnt/data/dpt-shell-main(1).zip
```

## Dhizuku-API compatibility contracts
- Source supplied by the user: `Dhizuku-API-main.zip`.
- Upstream project: `iamr0s/Dhizuku-API`.
- License: MIT. The copied AIDL compatibility contracts retain the upstream MIT license in `dhizuku-compat/LICENSE-DHIZUKU-API-MIT.txt`.
- DPC-AIO implements a restricted enterprise compatibility service: raw Binder forwarding, arbitrary remote processes, and arbitrary user-service loading are deliberately disabled.

## Shizuku API
- Upstream: `RikkaApps/Shizuku-API`, MIT license.
- DPC-AIO 0.3 targets official API/provider 13.1.5 and uses explicit Shizuku permission plus a typed UserService.
- The production adapter does not use deprecated `newProcess`, hidden-API bypass, or arbitrary shell command strings; its UserService accepts only typed activity-start and broadcast operations.
