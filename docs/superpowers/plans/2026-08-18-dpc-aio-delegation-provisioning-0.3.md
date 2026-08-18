# DPC-AIO Delegation + Provisioning 0.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a typed delegated-capability broker, a safe Dhizuku-compatible provider subset, Shizuku runtime/UserService execution, and a zero-dependency enrollment server for the 0.3 development checkpoint.

**Architecture:** Delegated callers are authenticated by package + UID + user + certificate + scope before typed operations reach capability executors. Dhizuku compatibility preserves the MIT provider/AIDL handshake and official delegated-scope APIs, but deliberately rejects raw binder forwarding, arbitrary remote processes, and arbitrary user-service loading in enterprise builds. Shizuku uses the official API and a narrowly typed UserService; provisioning is an independent Node service using one-time expiring tokens and deterministic Android Enterprise payloads.

**Tech Stack:** Kotlin/JVM host tests, Android API 29-37 adapters, AIDL, official Shizuku API 13.1.5, Node.js built-ins, Python structural/release verification.

**Spec:** `docs/specs/dpc-aio-v1.md`

## Global Constraints
- minSdk 29; compileSdk 37; targetSdk 37.
- `enterpriseRelease` cannot depend on `lab-tools`, Xposed/LSPosed, HiddenApiBypass, or raw unrestricted binder/shell forwarding.
- Dhizuku API source copied from the uploaded MIT-licensed Dhizuku-API reference must retain MIT provenance.
- Shizuku permission is explicit user authorization; runtime identity is reported as shell/root and never relabeled as the DPC UID.
- Enrollment tokens are one-time, cryptographically random, expire, and are never embedded in audit logs in full.

---

### Task 1: Typed delegation broker
**Files:** `delegation-core/src/main/kotlin/io/dpcaio/delegation/DelegationBroker.kt`, test counterpart.
**Interfaces:** `DelegationBroker.execute(DelegatedRequest): DelegatedResult`; `DelegatedOperationExecutor.execute(DelegatedRequest)`.
- [x] Write RED tests for allowed, scope-denied, identity-mismatch, and executor-failure paths.
- [x] Implement the minimal typed broker.
- [x] Run host tests GREEN.
- [x] Commit.

### Task 2: Safe Dhizuku-compatible provider subset
**Files:** Dhizuku AIDL under `dhizuku-compat/src/main/aidl`, `SafeDhizukuProvider.kt`, `SafeDhizukuService.kt`, manifest and provenance.
**Interfaces:** supports provider `client` handshake, version/permission/delegated-scope calls; rejects raw remote transact/process/user-service operations.
- [x] Add contract test that requires provider/AIDL handshake and explicit rejection of unrestricted operations.
- [x] Copy MIT AIDL compatibility contracts and preserve license notice.
- [x] Implement caller identity resolution and safe service.
- [x] Run static Android contracts GREEN.
- [x] Commit.

### Task 3: Shizuku runtime + typed UserService
**Files:** `shizuku-adapter` runtime, AIDL service, manifest, version catalog.
**Interfaces:** `ShizukuRuntime.probe/requestPermission`; typed UserService supports identity probe, explicit activity start, and explicit broadcast only.
- [x] Add static contract test for official Shizuku APIs and no `newProcess`/hidden-api bypass.
- [x] Add official API/provider dependencies and provider manifest.
- [x] Implement typed UserService and client adapter.
- [x] Run contracts/release gate GREEN.
- [x] Commit.

### Task 4: Enrollment/provisioning server
**Files:** `provisioning-server/src/*.mjs`, Node tests, settings/project verifier integration.
**Interfaces:** one-time token create/peek/consume and deterministic Android Enterprise provisioning payload builder.
- [x] Write RED Node tests for TTL, one-time consumption, and payload fields/checksum.
- [x] Implement token store and payload builder using Node built-ins only.
- [x] Add HTTP endpoints for health, enrollment token creation/consumption, and provisioning payload generation.
- [x] Run Node tests GREEN.
- [x] Commit.

### Task 5: Integrated verification/checkpoint
**Files:** test runners, project verifier, contracts, `CHECKPOINT-0.3.0.md`.
- [x] Add new Kotlin/Node/static tests to unified runner.
- [x] Run fresh host suite and release gate.
- [x] Attempt Android Gradle build and record exact blocker if toolchain/network remains unavailable.
- [x] Package password-protected development checkpoint and record SHA-256.
