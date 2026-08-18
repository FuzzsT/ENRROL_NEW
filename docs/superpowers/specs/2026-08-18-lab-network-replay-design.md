# DPC-AIO 0.6 Lab Network and Replay Design

## Goal
Add a local Knox-compatible mock bridge for lab builds, Device Owner network policy controls, a deterministic scenario recorder/replayer, safe native timing helpers, and an NFC lab harness for owned/synthetic test tags and HCE APDU scenarios.

## Product boundaries
- Android 10/API 29 through Android 17/API 37.
- `enterprise` may use public DevicePolicyManager, VpnService, Binder/AIDL, intents, providers, and optional Shizuku typed routes.
- `lab/tst/eng` may enable local Knox mock and own-process/debuggable instrumentation.
- No exploit/root path from Root-My-Galaxy is imported.
- No fake Samsung KLM/KPE or hook of Samsung license verification.
- NFC replay is limited to synthetic or explicitly test-owned traces. Credential-bearing access/payment/authentication traces are rejected by replay validation.
- No key recovery, MIFARE Classic key cracking, DESFire key extraction, secure-element secret extraction, or cloning of access/payment credentials.

## Architecture
### Knox local mock
`KnoxGateway` routes to `RealKnox`, `LabKnoxMock`, or `DpmFallback`. `LabKnoxMock` exposes a signature-protected local Binder service in lab/tst/eng only. Package operations are verified through DPM where equivalent public APIs exist; Knox-only operations report `REAL_KNOX_REQUIRED`.

### Network control
`network-control` is a pure policy module with hosts-style rules and DNS profiles. `network-android` provides Device Owner Private DNS (DoT) configuration and a local DoH query client for diagnostics/managed companion use. Full-device interception is represented by an always-on VPN capability boundary; this checkpoint does not claim a transparent system-wide DoH proxy until packet forwarding is implemented and verified.

### Scenario recorder/replay
`scenario-core` stores timestamped typed events and deterministic replay plans. `scenario-android` records DPC-owned activity lifecycle, explicit intent/broadcast operations, policy actions, and test markers. `lab-tools` hosts the optional overlay controller for lab builds. Replay only invokes typed executors already authorized by the DPC.

### NFC lab
`nfc-lab-core` defines sanitized APDU/NDEF traces, replay eligibility, schema/versioning, and deterministic timing. `nfc-lab-android` implements synthetic HCE via `HostApduService`, NDEF/tag inventory, and controlled ISO-DEP test exchanges initiated by the lab harness. Replay validation rejects traces marked credential-bearing or non-test-owned.

### Native support
`native-diagnostics` gains a small C++ JNI library for monotonic timestamps, page-size reporting, and bounded ring-buffer trace markers. It does not patch ART, hook foreign processes, or access protected NFC/Knox internals.

## Verification
- Host Kotlin tests cover routing, rule matching, scenario ordering, replay validation, and Knox mock state.
- Static Android contract tests verify manifests, flavor boundaries, AIDL/service declarations, VpnService/HostApduService declarations, and no production dependency on lab hooks.
- Release gate rejects exploit/root/kernel payload patterns and lab-only NFC replay services from enterprise source sets.
