import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { TokenStore } from '../src/token-store.mjs';

test('reservation and request id remain idempotent across server restart', () => {
  const dir = mkdtempSync(join(tmpdir(), 'dpc-aio-token-store-'));
  const path = join(dir, 'tokens.json');
  try {
    const first = new TokenStore(() => 1000, { storagePath: path });
    const created = first.create({ policyProfile: 'corp' }, 60_000);
    const reserved = first.reserve(created.token, 'reserve-stable', { sessionId: 'session-stable' }, 30_000);
    const second = new TokenStore(() => 2000, { storagePath: path });
    const repeated = second.reserve(created.token, 'reserve-stable', { sessionId: 'session-stable' }, 30_000);
    assert.equal(repeated.reservationId, reserved.reservationId);
    const committed = second.commit(reserved.reservationId, 'commit-stable');
    assert.equal(committed.status, 'COMMITTED');
    const third = new TokenStore(() => 3000, { storagePath: path });
    assert.equal(third.commit(reserved.reservationId, 'commit-stable').status, 'COMMITTED');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
