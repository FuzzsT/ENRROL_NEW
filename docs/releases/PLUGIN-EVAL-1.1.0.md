# DPC-AIO Companion 0.2.0 — Plugin Eval Static Report

## At a Glance

- Target: `plugins/chatgpt-companion`
- Architecture: `skills-only`
- Skills: 4 (`dpc-aio-build`, `dpc-aio-ci-repair`, `dpc-aio-enrollment`, `dpc-aio-verify`)
- Plugin contract: PASS
- Plugin script tests: PASS
- Plugin Autopilot validator: PASS, 0 errors, 0 warnings
- Deterministic packaging: PASS (`plugin-a.zip` byte-identical to `plugin-b.zip`)
- Autopilot package SHA-256: `0b67bf72c13575a37013861dce83002935da9f4932b750af6b7b1db57424c103`
- `plugin-eval` CLI: UNAVAILABLE in this runtime; no synthetic Plugin Eval score or measured token benchmark is claimed.

## Why It Matters

The companion now describes DPC-AIO 1.1.0 rather than the old 1.0.1 baseline, verifies the 1.1.0 release gate and retained 1.0.2 QR prerequisite, and keeps build/runtime evidence separate. Executable script permissions are covered by the plugin contract so the packaging regression found during this release cannot silently recur.

The public plugin archive excludes APKs, Gradle wrapper JARs, private/public lab key fixtures, KPE/KLM token fixtures and other repository-only material. The package remains skills-only and does not claim an MCP server, app, hook or device-management execution surface.

## Fix First

No local validation blocker remains. Publication itself is still blocked because this workspace does not establish a verified publisher identity, stable public website/privacy/terms/support URLs, or writable publication credentials. Those are publication prerequisites, not source-validation failures.

## Recommended Next Step

When a runtime with `plugin-eval` is available, run the official analysis/benchmark flow against `plugins/chatgpt-companion` and record its JSON result separately. Until then, use the Autopilot validator/test report as the locally measured plugin evidence and do not invent a Plugin Eval score.
