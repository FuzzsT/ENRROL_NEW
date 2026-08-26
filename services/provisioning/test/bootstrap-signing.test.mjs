import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { signBootstrap, verifyBootstrap } from '../src/bootstrap-signing.mjs';

test('Ed25519 bootstrap signatures verify and reject tampering', () => {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const payload = { schemaVersion: 1, profileId: 'corporate', allowedModes: ['work-profile'] };
  const envelope = signBootstrap({ payload, sessionId: 's1', reservationId: 'r1', nonce: 'n1', issuedAt: 1000, expiresAt: 2000 }, privateKey, 'k1');
  assert.equal(verifyBootstrap(envelope, publicKey), true);
  assert.equal(verifyBootstrap({ ...envelope, payload: { ...payload, profileId: 'tampered' } }, publicKey), false);
});
