> Current release target: DPC-AIO 1.2.0 Enterprise + Samsung OEM AIO

# DPC-AIO checkpoint 1.2.0

- versionCode 26 / versionName 1.2.0
- 1.0.2 QR Production Readiness retained as a hard prerequisite
- Protected Targets / Protected Operations Guard shared by permission, component, app and offline mutation paths
- Enterprise Transaction Engine with revalidation, readback and compare-and-set rollback
- deterministic capability route: Android DPM -> official Knox -> Samsung SEM -> OEM Internals Lab -> unavailable
- official Knox adapter is optional/compileOnly and never bundles private SDK or license material
- SEM exact catalog and OEM Internals `:oem_lab` process with bounded probes and circuit breaker
- Package Trust 2.0 and hardened data-only APK+ staging
- Work Profile Lifecycle 2.0 and Credential Recovery 2.0
- whole-app state, application restrictions readback and Device-Owner-only global location policy
- hardware/license-dependent verification remains separate from source verification
