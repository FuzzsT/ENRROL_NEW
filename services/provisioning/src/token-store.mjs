import { createHash, randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

function digestToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

const STATES = Object.freeze({
  ISSUED: 'ISSUED',
  RESERVED: 'RESERVED',
  COMMITTED: 'COMMITTED',
  EXPIRED: 'EXPIRED',
  REVOKED: 'REVOKED',
});

function clone(value) {
  return value == null ? value : structuredClone(value);
}

export class TokenStore {
  constructor(now = () => Date.now(), { storagePath = null } = {}) {
    this.now = now;
    this.storagePath = storagePath;
    this.records = new Map();
    this.used = new Set();
    this.idempotency = new Map();
    this.#load();
  }

  create(metadata = {}, ttlMs = 10 * 60 * 1000) {
    if (!Number.isFinite(ttlMs) || ttlMs <= 0) throw new TypeError('ttlMs must be positive');
    const token = randomBytes(32).toString('base64url');
    const digest = digestToken(token);
    const createdAt = this.now();
    const expiresAt = createdAt + ttlMs;
    this.records.set(digest, {
      id: digest.slice(0, 16),
      metadata: clone(metadata),
      createdAt,
      expiresAt,
      status: STATES.ISSUED,
      reservation: null,
    });
    this.#persist();
    return { token, tokenId: digest.slice(0, 16), createdAt, expiresAt, status: STATES.ISSUED };
  }

  peek(token) {
    const digest = digestToken(token);
    if (this.used.has(digest)) return null;
    const record = this.records.get(digest);
    if (!record) return null;
    this.#refreshRecord(digest, record);
    if (record.expiresAt <= this.now() || record.status === STATES.EXPIRED || record.status === STATES.COMMITTED || record.status === STATES.REVOKED) return null;
    if (record.status === STATES.RESERVED) return null;
    return clone(record);
  }

  consume(token) {
    const digest = digestToken(token);
    const record = this.peek(token);
    if (!record) return null;
    this.records.delete(digest);
    this.used.add(digest);
    this.#persist();
    return record;
  }

  reserve(token, requestId, metadata = {}, ttlMs = 5 * 60 * 1000) {
    if (!requestId) throw new TypeError('requestId required');
    if (!Number.isFinite(ttlMs) || ttlMs <= 0) throw new TypeError('reservation ttlMs must be positive');
    const idemKey = `reserve:${requestId}`;
    if (this.idempotency.has(idemKey)) return clone(this.idempotency.get(idemKey));

    const digest = digestToken(token);
    const record = this.records.get(digest);
    if (!record) return null;
    this.#refreshRecord(digest, record);
    if (record.expiresAt <= this.now() || record.status !== STATES.ISSUED) return null;

    const now = this.now();
    const reservation = {
      reservationId: randomBytes(16).toString('base64url'),
      requestId,
      sessionId: String(metadata.sessionId ?? ''),
      createdAt: now,
      expiresAt: Math.min(record.expiresAt, now + ttlMs),
      metadata: clone(metadata),
    };
    record.status = STATES.RESERVED;
    record.reservation = reservation;
    const response = {
      tokenId: record.id,
      reservationId: reservation.reservationId,
      sessionId: reservation.sessionId,
      status: STATES.RESERVED,
      expiresAt: reservation.expiresAt,
      metadata: clone(record.metadata),
    };
    this.idempotency.set(idemKey, response);
    this.#persist();
    return clone(response);
  }

  commit(reservationId, requestId) {
    if (!reservationId || !requestId) throw new TypeError('reservationId and requestId required');
    const idemKey = `commit:${requestId}`;
    if (this.idempotency.has(idemKey)) return clone(this.idempotency.get(idemKey));
    const found = this.#findByReservation(reservationId);
    if (!found) return null;
    const { digest, record } = found;
    this.#refreshRecord(digest, record);
    if (record.status === STATES.COMMITTED) {
      const response = this.#statusResponse(record);
      this.idempotency.set(idemKey, response);
      return clone(response);
    }
    if (record.status !== STATES.RESERVED || !record.reservation || record.reservation.expiresAt <= this.now()) return null;
    record.status = STATES.COMMITTED;
    record.committedAt = this.now();
    this.used.add(digest);
    const response = this.#statusResponse(record);
    this.idempotency.set(idemKey, response);
    this.#persist();
    return clone(response);
  }

  release(reservationId, requestId) {
    if (!reservationId || !requestId) throw new TypeError('reservationId and requestId required');
    const idemKey = `release:${requestId}`;
    if (this.idempotency.has(idemKey)) return clone(this.idempotency.get(idemKey));
    const found = this.#findByReservation(reservationId);
    if (!found) return null;
    const { record } = found;
    if (record.status !== STATES.RESERVED) return null;
    record.status = record.expiresAt <= this.now() ? STATES.EXPIRED : STATES.ISSUED;
    record.reservation = null;
    const response = this.#statusResponse(record);
    this.idempotency.set(idemKey, response);
    this.#persist();
    return clone(response);
  }

  reservation(reservationId) {
    const found = this.#findByReservation(reservationId);
    if (!found) return null;
    this.#refreshRecord(found.digest, found.record);
    return found.record.reservation ? clone({ ...this.#statusResponse(found.record), reservation: found.record.reservation }) : null;
  }

  status(sessionId) {
    for (const [digest, record] of this.records) {
      this.#refreshRecord(digest, record);
      if (record.reservation?.sessionId === sessionId || record.lastSessionId === sessionId) return clone(this.#statusResponse(record));
    }
    return null;
  }

  revoke(token) {
    const digest = digestToken(token);
    const record = this.records.get(digest);
    if (!record) return false;
    record.status = STATES.REVOKED;
    record.reservation = null;
    this.#persist();
    return true;
  }

  prune() {
    let changed = false;
    for (const [digest, record] of this.records) {
      const before = record.status;
      this.#refreshRecord(digest, record);
      if (before !== record.status) changed = true;
    }
    if (changed) this.#persist();
  }

  #refreshRecord(digest, record) {
    const now = this.now();
    if (record.status === STATES.RESERVED && record.reservation && record.reservation.expiresAt <= now) {
      record.lastSessionId = record.reservation.sessionId;
      record.reservation = null;
      record.status = record.expiresAt <= now ? STATES.EXPIRED : STATES.ISSUED;
      this.#persist();
    } else if (record.expiresAt <= now && record.status !== STATES.COMMITTED && record.status !== STATES.REVOKED) {
      record.status = STATES.EXPIRED;
      record.reservation = null;
      this.#persist();
    }
  }

  #findByReservation(reservationId) {
    for (const [digest, record] of this.records) {
      if (record.reservation?.reservationId === reservationId) return { digest, record };
    }
    return null;
  }

  #statusResponse(record) {
    return {
      tokenId: record.id,
      status: record.status ?? STATES.ISSUED,
      sessionId: record.reservation?.sessionId ?? record.lastSessionId ?? null,
      reservationId: record.reservation?.reservationId ?? null,
      metadata: clone(record.metadata),
      expiresAt: record.expiresAt,
      committedAt: record.committedAt ?? null,
    };
  }

  #persist() {
    if (!this.storagePath) return;
    mkdirSync(dirname(this.storagePath), { recursive: true });
    const tmp = `${this.storagePath}.tmp`;
    const payload = {
      version: 1,
      records: Array.from(this.records.entries()),
      used: Array.from(this.used),
      idempotency: Array.from(this.idempotency.entries()),
    };
    writeFileSync(tmp, JSON.stringify(payload), { mode: 0o600 });
    renameSync(tmp, this.storagePath);
  }

  #load() {
    if (!this.storagePath) return;
    try {
      const payload = JSON.parse(readFileSync(this.storagePath, 'utf8'));
      this.records = new Map(payload.records ?? []);
      this.used = new Set(payload.used ?? []);
      this.idempotency = new Map(payload.idempotency ?? []);
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
  }
}

export { STATES as TOKEN_STATES };
