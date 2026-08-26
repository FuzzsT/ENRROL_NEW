# DPC-AIO development checkpoint 0.5.0

## Added
- Samsung Settings Editor for Settings.System/Secure/Global with explicit routes and multi-read stability verification.
- Typed Shizuku settings get/put/delete and permission/AppOps helpers.
- Read-only permission/sysconfig XML index for system/system_ext/product/vendor/odm.
- Dynamic Permission Catalog based on PackageManager groups, declared permissions and public SDK constants.
- Vendor/undocumented candidate classification for Samsung/GMS/custom permission namespaces.
- Permission AUTO planner and Android coordinator with DPC runtime, Knox adapter hook point, Shizuku runtime/AppOps, system-privileged verification and user-action fallbacks.
- Permission Manager Activity with search, extended Shizuku scan and requested-permission AUTO execution.
- Lab-only Java/ART post-hook activity routes limited to the app's own debuggable package.
- systemPrivileged manifest requests WRITE_SECURE_SETTINGS and MANAGE_IPSEC_TUNNELS; actual grants remain platform-controlled.

## Safety/release boundary
- Enterprise modules do not depend on lab-tools.
- No Xposed/LSPosed or hidden-api enforcement bypass in production modules.
- Hook/native routes never report a raw Android permission as granted.
- Signature/privileged permissions remain platform/system-image controlled.

## Known build environment limitation
- Full Android/Gradle build depends on a locally available Gradle distribution/Android SDK. The sandbox may not be able to download Gradle from services.gradle.org.
