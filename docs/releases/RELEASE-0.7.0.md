# DPC-AIO 0.7.0

## Highlights

- Monolithic AIO retained: all existing Gradle modules remain part of `:app-dpc`.
- Module Center gains persistent **Show hidden**, **Developer / Lab**, experimental visibility, capability status and filters.
- New Enterprise Policy Hub for documented Android enterprise APIs: USB data signaling, automatic time/timezone, Thread, NFC restrictions, App Functions and per-app local-network runtime permission.
- New local DPC Diagnostics screen and JSON export.
- Capability resolver distinguishes compiled/integrated from currently available/executable.
- Dual work-profile/device-owner QR provisioning remains unchanged and remains a release gate.
- Final release staging excludes private-key material.

## Safety boundary

Visibility does not bypass Android ownership, permissions, API levels, OEM requirements or Knox licensing. Unsupported actions stay disabled and are labeled with the blocking reason.
