import { createHash, randomBytes } from 'node:crypto';

function digestToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

export class TokenStore {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.records = new Map();
    this.used = new Set();
  }

  create(metadata = {}, ttlMs = 10 * 60 * 1000) {
    if (!Number.isFinite(ttlMs) || ttlMs <= 0) throw new TypeError('ttlMs must be positive');
    const token = randomBytes(32).toString('base64url');
    const digest = digestToken(token);
    const createdAt = this.now();
    const expiresAt = createdAt + ttlMs;
    this.records.set(digest, {
      id: digest.slice(0, 16),
      metadata: structuredClone(metadata),
      createdAt,
      expiresAt,
    });
    return { token, tokenId: digest.slice(0, 16), createdAt, expiresAt };
  }

  peek(token) {
    const digest = digestToken(token);
    if (this.used.has(digest)) return null;
    const record = this.records.get(digest);
    if (!record) return null;
    if (record.expiresAt <= this.now()) {
      this.records.delete(digest);
      return null;
    }
    return structuredClone(record);
  }

  consume(token) {
    const digest = digestToken(token);
    const record = this.peek(token);
    if (!record) return null;
    this.records.delete(digest);
    this.used.add(digest);
    return record;
  }

  prune() {
    for (const [digest, record] of this.records) {
      if (record.expiresAt <= this.now()) this.records.delete(digest);
    }
  }
}
