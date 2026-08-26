import { createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalize(value[key])]));
  }
  return value;
}

function signingObject(envelope) {
  return canonicalize({
    payload: envelope.payload,
    sessionId: envelope.sessionId,
    reservationId: envelope.reservationId,
    nonce: envelope.nonce,
    issuedAt: envelope.issuedAt,
    expiresAt: envelope.expiresAt,
    keyId: envelope.keyId,
  });
}

export function canonicalBootstrapBytes(envelope) {
  return Buffer.from(JSON.stringify(signingObject(envelope)), 'utf8');
}

export function signBootstrap(data, privateKeyInput, keyId = 'enrollment-v1') {
  const privateKey = typeof privateKeyInput === 'string' || Buffer.isBuffer(privateKeyInput)
    ? createPrivateKey(privateKeyInput)
    : privateKeyInput;
  const envelope = { ...data, keyId };
  const signature = sign(null, canonicalBootstrapBytes(envelope), privateKey).toString('base64url');
  return { ...envelope, signature };
}

export function verifyBootstrap(envelope, publicKeyInput) {
  try {
    const publicKey = typeof publicKeyInput === 'string' || Buffer.isBuffer(publicKeyInput)
      ? createPublicKey(publicKeyInput)
      : publicKeyInput;
    return verify(null, canonicalBootstrapBytes(envelope), publicKey, Buffer.from(envelope.signature ?? '', 'base64url'));
  } catch {
    return false;
  }
}
