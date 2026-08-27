# DPC-AIO 1.2.0 FULL

This is the next minor source release after 1.1.4.

## Version
- versionName: `1.2.0`
- versionCode: `26`

## Included application changes
- Activity Manager 3.0 with expandable per-app activity lists.
- Search, filters, sorting, favorites and favorite groups.
- Safe WindowInsets UI shell for status/navigation bars, cutouts and IME.
- Categorized DPC dashboard/menu.
- Expanded DevicePolicyManager lifecycle controls.
- App PIN controls remain separated from provisioning activities.
- Explicit work-profile and fully-managed/device-owner QR generation remains mandatory.
- Persistent release signing/path gates and zero-settings workflow remain intact.

## Full-source packaging policy
The buildable DPC-AIO source tree stays dependency-clean. Full external reference projects are placed outside the Gradle project in the distributed FULL bundle under `_upstream/`. They are not automatically compiled or merged into the DPC application.

The exact upstream observations are locked in `docs/upstream/UPSTREAM-LOCK.json`.
