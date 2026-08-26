import test from 'node:test';
import assert from 'node:assert/strict';
import { TokenStore } from '../src/token-store.mjs';

test('token is one-time and expires', () => {
  let now = 1_000;
  const store = new TokenStore(() => now);
  const created = store.create({ profile: 'corporate' }, 5_000);
  assert.equal(created.token.length >= 32, true);
  assert.equal(store.peek(created.token).metadata.profile, 'corporate');
  const consumed = store.consume(created.token);
  assert.equal(consumed.metadata.profile, 'corporate');
  assert.equal(store.consume(created.token), null);

  const expiring = store.create({ profile: 'kiosk' }, 1_000);
  now += 1_001;
  assert.equal(store.peek(expiring.token), null);
});
