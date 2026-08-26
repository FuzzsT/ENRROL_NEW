# DPC App PIN + Android Enterprise provisioning validation

## Provisioning modes

The production workflow always emits and validates both explicit Android Enterprise enrollment artifacts:

- `work-profile-qr.png` -> expected mode `work-profile`
- `device-owner-qr.png` -> expected mode `fully-managed`

The generic `provisioning-qr.png` follows `DPC_AIO_PROVISIONING_MODE` (`auto`, `work-profile`, or `fully-managed`), but the explicit work-profile and fully-managed artifacts are generated independently every run.

Real APK artifact validation:

- QR decode: PASS
- QR payload == provisioning JSON: PASS
- APK SHA-256 checksum: PASS
- work-profile expected mode: PASS
- fully-managed expected mode: PASS
- component: `io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver`: PASS

## App PIN

The launcher dashboard now includes `App PIN / Security` with:

- set PIN
- change PIN
- enable PIN
- disable PIN
- remove PIN
- lock DPC now

Security properties:

- digits only, 4-12 digits
- PBKDF2-HMAC-SHA256, 120000 iterations
- random 128-bit salt
- constant-time hash comparison
- 5 failed attempts -> 30 second delay
- process-memory unlock session timeout: 5 minutes
- PIN protects human-facing DPC UI only

System provisioning callbacks (`ProvisioningModeActivity`, `PolicyComplianceActivity`) are intentionally not PIN-gated, so Setup Wizard / ManagedProvisioning can complete work-profile and fully-managed enrollment.

## Local checks

- `test_115_app_pin_and_provisioning_modes_contract.py`: PASS
- dense QR decoder: PASS
- provisioning build integration: PASS
- QR release bundle builder: PASS
- QR readiness contract 102: PASS
- QR release bundle contract 114: PASS
- Python AST: PASS
- Bash syntax: PASS
- GitHub workflow YAML parse: PASS

A full local Gradle compilation could not start because this execution environment could not resolve `services.gradle.org`; this is an environment/DNS limitation rather than a Kotlin compilation diagnostic.
