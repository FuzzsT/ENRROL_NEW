# Permission Manager v0.5

- Discover permission groups and permissions from the running device instead of relying on a static Android list.
- Mark entries as public-SDK or undocumented/vendor candidates using `android.Manifest.permission` and `permission_group` public constants.
- Keep raw permission grant, AppOp/special access, and effective capability separate.
- AUTO routes may use DPC runtime grants, Samsung Knox special-access gateway, typed Shizuku runtime/AppOps operations, actual system/privileged grants, or user Settings UI.
- Java/ART hook simulation is lab-only and never changes or reports a real platform permission grant.
- Signature/privileged permissions are not converted into runtime permissions by spoofing or hidden-API bypass.
