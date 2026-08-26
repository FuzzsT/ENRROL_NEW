# DPC-AIO 0.6.3

## Fixed
- Removed the remaining Gradle fallback to `releases/latest/download/...`.
- Prevented non-enterpriseDebug variants from auto-generating repository URLs for assets the continuous release does not publish.
- Aligned app version, dashboard label, package metadata, and release docs.

## Improved
- Continuous manual releases use the configurable `DPC_AIO_CONTINUOUS_RELEASE_TAG` (default `dpc-aio-continuous`).
- Continuous releases include the full provisioning JSON/payload/metadata/QR set for primary and work-profile enrollment.
- Release verification includes a regression test for the safe provisioning fallback and a release-version contract.

## Enrollment validity
A QR is not considered device-ready until CI builds the APK and the unauthenticated public URL re-download matches the local build byte-for-byte. Private/non-public GitHub release URLs intentionally fail this check.
