import http from 'node:http';
import { readFileSync } from 'node:fs';
import { createPrivateKey, createPublicKey, generateKeyPairSync, randomBytes } from 'node:crypto';
import { TokenStore } from './token-store.mjs';
import { buildProvisioningPayload } from './payload.mjs';
import { signBootstrap } from './bootstrap-signing.mjs';

async function readJson(req, limit = 64 * 1024) {
  let total = 0;
  const chunks = [];
  for await (const chunk of req) {
    total += chunk.length;
    if (total > limit) throw new Error('BODY_TOO_LARGE');
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function json(res, status, body) {
  const data = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': data.length,
    'cache-control': 'no-store',
  });
  res.end(data);
}

export function createProvisioningServer({ tokenStore = new TokenStore(), signingKeyPair = null, bootstrapTtlMs = 5 * 60 * 1000 } = {}) {
  const keys = signingKeyPair ?? generateKeyPairSync('ed25519');
  return http.createServer(async (req, res) => {
    try {
      if (req.method === 'GET' && req.url === '/health') {
        return json(res, 200, { ok: true, service: 'dpc-aio-provisioning' });
      }

      if (req.method === 'POST' && req.url === '/v1/enrollments') {
        const body = await readJson(req);
        const ttlSeconds = Number(body.ttlSeconds ?? 600);
        if (!Number.isFinite(ttlSeconds) || ttlSeconds < 30 || ttlSeconds > 86400) {
          return json(res, 400, { error: 'INVALID_TTL' });
        }
        const created = tokenStore.create(
          {
            policyProfile: String(body.policyProfile ?? 'default'),
            provisioningMode: String(body.provisioningMode ?? 'work-profile'),
            deviceLabel: body.deviceLabel ?? null,
          },
          ttlSeconds * 1000,
        );
        return json(res, 201, created);
      }

      if (req.method === 'POST' && req.url === '/v1/provisioning/payload') {
        const body = await readJson(req);
        const record = tokenStore.peek(String(body.enrollmentToken ?? ''));
        if (!record) return json(res, 401, { error: 'INVALID_OR_EXPIRED_TOKEN' });
        const payload = buildProvisioningPayload({
          apkUrl: body.apkUrl,
          apkChecksum: body.apkChecksum,
          signatureChecksum: body.signatureChecksum,
          enrollmentToken: body.enrollmentToken,
          policyProfile: record.metadata.policyProfile,
          provisioningMode: record.metadata.provisioningMode ?? 'work-profile',
          packageName: body.packageName,
          adminClassName: body.adminClassName,
          allowOffline: body.allowOffline,
          enrollmentEndpoint: body.enrollmentEndpoint,
          enrollmentSource: body.enrollmentSource ?? 'qr',
        });
        return json(res, 200, { tokenId: record.id, payload, qrText: JSON.stringify(payload) });
      }

      if (req.method === 'POST' && req.url === '/v1/enrollments/consume') {
        const body = await readJson(req);
        const record = tokenStore.consume(String(body.enrollmentToken ?? ''));
        if (!record) return json(res, 401, { error: 'INVALID_OR_EXPIRED_TOKEN' });
        return json(res, 200, { tokenId: record.id, metadata: record.metadata, consumed: true });
      }

      if (req.method === 'POST' && req.url === '/v2/enrollments/reserve') {
        const body = await readJson(req);
        const reserved = tokenStore.reserve(
          String(body.enrollmentToken ?? ''),
          String(body.requestId ?? ''),
          {
            sessionId: String(body.sessionId ?? ''),
            provisioningMode: String(body.provisioningMode ?? ''),
            policyProfile: body.policyProfile ?? null,
          },
          Number(body.reservationTtlMs ?? 5 * 60 * 1000),
        );
        if (!reserved) return json(res, 401, { error: 'INVALID_EXPIRED_OR_RESERVED_TOKEN' });
        return json(res, 201, reserved);
      }

      if (req.method === 'POST' && req.url === '/v2/enrollments/validate') {
        const body = await readJson(req);
        const reservation = tokenStore.reservation(String(body.reservationId ?? ''));
        if (!reservation || reservation.sessionId !== String(body.sessionId ?? '')) {
          return json(res, 409, { error: 'RESERVATION_MISMATCH' });
        }
        const deviceFacts = body.deviceFacts ?? {};
        if (!Number.isInteger(Number(deviceFacts.androidApi ?? 0)) || Number(deviceFacts.androidApi ?? 0) < 29) {
          return json(res, 400, { error: 'DEVICE_NOT_ELIGIBLE' });
        }
        return json(res, 200, { ok: true, status: 'VALIDATED', sessionId: body.sessionId, reservationId: body.reservationId });
      }

      if (req.method === 'POST' && req.url === '/v2/enrollments/bootstrap') {
        const body = await readJson(req);
        const reservation = tokenStore.reservation(String(body.reservationId ?? ''));
        if (!reservation || reservation.sessionId !== String(body.sessionId ?? '')) {
          return json(res, 409, { error: 'RESERVATION_MISMATCH' });
        }
        const now = Date.now();
        const mode = reservation.metadata.provisioningMode || reservation.metadata?.metadata?.provisioningMode || reservation.metadata?.metadata?.mode || 'work-profile';
        const profileId = reservation.metadata.policyProfile || reservation.metadata?.metadata?.policyProfile || 'default';
        const envelope = signBootstrap({
          payload: {
            schemaVersion: 1,
            profileId,
            allowedModes: [mode],
            minimumAndroidApi: 29,
            minimumDpcVersion: '0.9.0',
            requiredCapabilities: ['profile-owner-or-device-owner'],
            bootstrap: {},
          },
          sessionId: String(body.sessionId ?? ''),
          reservationId: String(body.reservationId ?? ''),
          nonce: randomBytes(16).toString('base64url'),
          issuedAt: now,
          expiresAt: now + bootstrapTtlMs,
        }, keys.privateKey, 'enrollment-v1');
        return json(res, 200, envelope);
      }

      if (req.method === 'POST' && req.url === '/v2/enrollments/commit') {
        const body = await readJson(req);
        const committed = tokenStore.commit(String(body.reservationId ?? ''), String(body.requestId ?? ''));
        if (!committed) return json(res, 409, { error: 'COMMIT_REJECTED' });
        return json(res, 200, committed);
      }

      if (req.method === 'POST' && req.url === '/v2/enrollments/release') {
        const body = await readJson(req);
        const released = tokenStore.release(String(body.reservationId ?? ''), String(body.requestId ?? ''));
        if (!released) return json(res, 409, { error: 'RELEASE_REJECTED' });
        return json(res, 200, released);
      }

      if (req.method === 'GET' && req.url?.startsWith('/v2/enrollments/') && req.url.endsWith('/status')) {
        const sessionId = decodeURIComponent(req.url.slice('/v2/enrollments/'.length, -'/status'.length));
        const status = tokenStore.status(sessionId);
        if (!status) return json(res, 404, { error: 'SESSION_NOT_FOUND' });
        return json(res, 200, status);
      }

      return json(res, 404, { error: 'NOT_FOUND' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'INTERNAL_ERROR';
      return json(res, message === 'BODY_TOO_LARGE' ? 413 : 400, { error: message });
    }
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT ?? 8080);
  const storagePath = process.env.DPC_AIO_TOKEN_STORE_PATH ?? './data/enrollment-token-store.json';
  const signingKeyFile = process.env.DPC_AIO_ENROLLMENT_SIGNING_PRIVATE_KEY_FILE;
  const allowEphemeral = process.env.DPC_AIO_ALLOW_EPHEMERAL_SIGNING_KEY === '1';
  let signingKeyPair;
  if (signingKeyFile) {
    const privateKey = createPrivateKey(readFileSync(signingKeyFile));
    signingKeyPair = { privateKey, publicKey: createPublicKey(privateKey) };
  } else if (allowEphemeral) {
    signingKeyPair = generateKeyPairSync('ed25519');
    console.warn('WARNING: using ephemeral enrollment signing key; existing DPC trust anchors will not survive restart');
  } else {
    throw new Error('DPC_AIO_ENROLLMENT_SIGNING_PRIVATE_KEY_FILE is required unless DPC_AIO_ALLOW_EPHEMERAL_SIGNING_KEY=1');
  }
  const tokenStore = new TokenStore(() => Date.now(), { storagePath });
  const server = createProvisioningServer({ tokenStore, signingKeyPair });
  server.listen(port, '0.0.0.0', () => {
    console.log(`DPC-AIO provisioning server listening on :${port}`);
    console.log(`Token store: ${storagePath}`);
  });
}
