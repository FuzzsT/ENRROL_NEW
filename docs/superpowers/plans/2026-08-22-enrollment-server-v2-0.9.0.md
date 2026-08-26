# Enrollment Server v2 0.9.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace one-shot token consume with persistent reservation/commit semantics and signed bootstrap responses.

**Architecture:** `TokenStore` becomes a durable JSON-backed repository with hashed tokens and reservation state. Server v2 endpoints are idempotent by request ID; v1 remains backward-compatible.

**Tech Stack:** Node.js ESM, node:crypto, node:fs, node:http, node:test.

**Spec:** `docs/superpowers/specs/2026-08-22-enrollment-engine-0.9.0-design.md`

## Global Constraints
- Store SHA-256 token digests only.
- No plaintext token in persistence.
- Reservation expiration returns token to ISSUED when valid.
- Bootstrap signing private key is server-only.

---

### Task 1: Reservation State Store

**Files:**
- Modify: `services/provisioning/src/token-store.mjs`
- Test: `services/provisioning/test/token-store.test.mjs`

**Interfaces:**
- Produces `reserve(token, requestId, metadata, ttlMs)`, `commit(reservationId, requestId)`, `release(reservationId, requestId)`, `status(sessionId)`.

- [ ] **Step 1: Write failing tests for ISSUED→RESERVED→COMMITTED and timeout release**

```js
const r = store.reserve(token, 'req-1', { sessionId: 's1' }, 30_000);
assert.equal(r.status, 'RESERVED');
assert.equal(store.commit(r.reservationId, 'req-2').status, 'COMMITTED');
```

- [ ] **Step 2: Run Node tests and verify RED**

```bash
cd services/provisioning && node --test test/token-store.test.mjs
```

- [ ] **Step 3: Implement state machine with hashed tokens and optional durable path**

```js
const STATES = Object.freeze({ ISSUED:'ISSUED', RESERVED:'RESERVED', COMMITTED:'COMMITTED', EXPIRED:'EXPIRED', REVOKED:'REVOKED' });
```

Write snapshots atomically via temporary file + rename when `storagePath` is configured.

- [ ] **Step 4: Re-run tests and expect PASS**

```bash
node --test test/token-store.test.mjs
```

### Task 2: v2 HTTP Contract and Signed Bootstrap

**Files:**
- Modify: `services/provisioning/src/server.mjs`
- Create: `services/provisioning/src/bootstrap-signing.mjs`
- Modify: `services/provisioning/test/server.test.mjs`
- Create: `services/provisioning/test/bootstrap-signing.test.mjs`

**Interfaces:**
- Adds `/v2/enrollments/reserve`, `/validate`, `/bootstrap`, `/commit`, `/release`, `GET /v2/enrollments/:sessionId/status`.

- [ ] **Step 1: Write RED HTTP tests**

```js
assert.equal((await post('/v2/enrollments/reserve', body)).statusCode, 201);
assert.equal((await post('/v2/enrollments/commit', commit)).json.status, 'COMMITTED');
```

- [ ] **Step 2: Add RED signing test**

```js
const signed = signBootstrap(payload, privateKeyPem, 'k1');
assert.equal(verifyBootstrap(signed, publicKeyPem), true);
```

- [ ] **Step 3: Implement canonical JSON signing using Ed25519**

```js
const signature = sign(null, Buffer.from(canonicalPayload), privateKey).toString('base64url');
```

- [ ] **Step 4: Implement v2 endpoints with requestId idempotency**

Return `409` for conflicting reuse of a request ID and `401` for invalid/expired tokens.

- [ ] **Step 5: Run all service tests**

```bash
cd services/provisioning && node --test test/*.test.mjs
```
