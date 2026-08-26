# DPC-AIO 0.6.7 dual QR change

The build now treats ownership mode as an explicit enrollment artifact boundary.

- `work-profile-qr.png` requests managed profile / Profile Owner.
- `device-owner-qr.png` requests fully managed / Device Owner.
- Both are generated from the same final APK and validated against that APK.
- The compatibility `provisioning-qr.png` defaults to work profile.
- Offline provisioning is opt-in through `DPC_AIO_ALLOW_OFFLINE` and remains an Android provisioning extra, not an admin-extra field.
