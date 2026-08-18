import http from 'node:http';
import { TokenStore } from './token-store.mjs';
import { buildProvisioningPayload } from './payload.mjs';

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

export function createProvisioningServer({ tokenStore = new TokenStore() } = {}) {
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
          { policyProfile: String(body.policyProfile ?? 'default'), deviceLabel: body.deviceLabel ?? null },
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
          packageName: body.packageName,
          adminClassName: body.adminClassName,
          allowOffline: body.allowOffline,
        });
        return json(res, 200, { tokenId: record.id, payload, qrText: JSON.stringify(payload) });
      }

      if (req.method === 'POST' && req.url === '/v1/enrollments/consume') {
        const body = await readJson(req);
        const record = tokenStore.consume(String(body.enrollmentToken ?? ''));
        if (!record) return json(res, 401, { error: 'INVALID_OR_EXPIRED_TOKEN' });
        return json(res, 200, { tokenId: record.id, metadata: record.metadata, consumed: true });
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
  const server = createProvisioningServer();
  server.listen(port, '0.0.0.0', () => {
    console.log(`DPC-AIO provisioning server listening on :${port}`);
  });
}
