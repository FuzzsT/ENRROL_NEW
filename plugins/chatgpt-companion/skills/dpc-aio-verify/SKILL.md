---
name: dpc-aio-verify
description: Use when a DPC-AIO repository needs bounded verification before push or CI, especially wrapper integrity, project contracts, hidden public-SDK API checks, or clarification of what was and was not actually built.
---

# DPC-AIO Verify

## Verification levels

Keep these states separate:

- **Structural verification** checks wrapper files and public-SDK-incompatible hidden `UserHandle` references.
- **Host/project verification** runs repository-native Python contract/audit tools when present.
- **APK build verification** requires an actual Gradle assemble command to exit 0 and is not performed by this Skill's verifier script.

## DPC-AIO 1.2.0 Enterprise + Samsung OEM coverage

For 1.2.0, verify `python3 tools/tests/test_114_qr_release_bundle_contract.py` when present, then retain the 1.1.3 GitHub publish-readiness (`test_113_release_gate_contract.py`), 1.1.2 Android runtime-smoke (`test_112_release_gate_contract.py`), 1.1.1 build-readiness, 1.1.0 enterprise (`test_110_release_gate_contract.py`) and retained 1.0.1 compatibility (`test_101_release_gate_contract.py`) gates as prerequisites/backward-compatibility checks. The 1.2.0 gate adds deterministic QR release-bundle packaging and publication contracts on top of Protected Targets/Operations, Enterprise Transaction routing, official Knox capability prerequisites, SEM/OEM Internals safety, Package Trust 2.0, hardened APK+ import, Work Profile Lifecycle 2.0 and Credential Recovery 2.0. Retained compatibility coverage still includes Full Offline, Permission Manager 2.0, Activity/Component Manager 2.0, Build Resolver and Device Harness. OEM Internals remains Lab-only and runtime capability is never inferred from class presence.

Build Resolver and Device Harness are evidence-producing tools; a BLOCKED result must never be rewritten as PASS.

## Procedure

1. Run `./scripts/verify_repo.sh <repo-root>`. The verifier runs repository structural checks and, when present, the 1.0.1 release contract plus retained compatibility gates.
2. If it fails, report the first failing structural check or repository verifier exactly. Do not bypass or weaken the verifier to obtain green output.
3. If it passes, report that pre-push/host checks passed. Do not state that the APK compiles unless a separate build using `dpc-aio-build` completed successfully.
4. For CI preparation, also inspect `.github/workflows/build-aio-enrollment.yml` as YAML and confirm the checked-in commit contains the intended source changes.
5. Treat `tools/release_gate.py` according to its repository contract: it is an audit compatibility entry point, not a technology denylist or proof of APK compilation.

## Boundaries

Do not add hidden Android stubs, SDK bypasses, or privileged execution just to make a static check pass. Fix public-SDK incompatibilities at their source and preserve raw capability/runtime state distinctions in DPC-AIO.
