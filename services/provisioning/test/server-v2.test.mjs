import test from 'node:test';
import assert from 'node:assert/strict';
import { createProvisioningServer } from '../src/server.mjs';

async function withServer(fn) {
  const server = createProvisioningServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}
async function post(base, path, body) {
  const response = await fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  return { status: response.status, body: await response.json() };
}

test('v2 reserve validate bootstrap commit flow', async () => {
  await withServer(async base => {
    const created = await post(base, '/v1/enrollments', { policyProfile: 'corporate', provisioningMode: 'work-profile', ttlSeconds: 60 });
    const token = created.body.token;
    const reserve = await post(base, '/v2/enrollments/reserve', { enrollmentToken: token, requestId: 'reserve-1', sessionId: 's1', provisioningMode: 'work-profile' });
    assert.equal(reserve.status, 201);
    assert.equal(reserve.body.status, 'RESERVED');
    const validate = await post(base, '/v2/enrollments/validate', { sessionId: 's1', reservationId: reserve.body.reservationId, requestId: 'validate-1', deviceFacts: { androidApi: 37, dpcVersion: '0.9.0' } });
    assert.equal(validate.status, 200);
    const bootstrap = await post(base, '/v2/enrollments/bootstrap', { sessionId: 's1', reservationId: reserve.body.reservationId, requestId: 'bootstrap-1' });
    assert.equal(bootstrap.status, 200);
    assert.equal(bootstrap.body.payload.schemaVersion, 1);
    const commit = await post(base, '/v2/enrollments/commit', { sessionId: 's1', reservationId: reserve.body.reservationId, requestId: 'commit-1' });
    assert.equal(commit.status, 200);
    assert.equal(commit.body.status, 'COMMITTED');
  });
});
