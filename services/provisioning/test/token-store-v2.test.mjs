import test from 'node:test';
import assert from 'node:assert/strict';
import { TokenStore } from '../src/token-store.mjs';

test('v2 reservation commits exactly once and is idempotent by request id', () => {
  let now = 1_000;
  const store = new TokenStore(() => now);
  const created = store.create({ policyProfile: 'corporate' }, 60_000);
  const reserved = store.reserve(created.token, 'reserve-1', { sessionId: 's1', policyProfile: 'corporate' }, 30_000);
  assert.equal(reserved.status, 'RESERVED');
  assert.equal(store.reserve(created.token, 'reserve-1', { sessionId: 's1' }, 30_000).reservationId, reserved.reservationId);
  const committed = store.commit(reserved.reservationId, 'commit-1');
  assert.equal(committed.status, 'COMMITTED');
  assert.equal(store.commit(reserved.reservationId, 'commit-1').status, 'COMMITTED');
  assert.equal(store.peek(created.token), null);
});

test('expired reservation returns still-valid token to ISSUED', () => {
  let now = 1_000;
  const store = new TokenStore(() => now);
  const created = store.create({ policyProfile: 'corporate' }, 60_000);
  const reserved = store.reserve(created.token, 'reserve-2', { sessionId: 's2' }, 5_000);
  now += 5_001;
  const status = store.status('s2');
  assert.equal(status.status, 'ISSUED');
  assert.equal(store.peek(created.token).metadata.policyProfile, 'corporate');
  assert.ok(reserved.reservationId);
});
