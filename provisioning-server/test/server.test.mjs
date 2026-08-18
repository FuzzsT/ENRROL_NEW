import test from 'node:test';
import assert from 'node:assert/strict';
import { createProvisioningServer } from '../src/server.mjs';
import { sha256UrlSafe } from '../src/payload.mjs';

async function withServer(fn) {
  const server = createProvisioningServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('HTTP enrollment creates payload then consumes token once', async () => {
  await withServer(async base => {
    const created = await fetch(`${base}/v1/enrollments`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ policyProfile: 'corporate', ttlSeconds: 60 }),
    }).then(r => r.json());
    assert.ok(created.token);

    const checksum = sha256UrlSafe(Buffer.from('apk'));
    const payloadResponse = await fetch(`${base}/v1/provisioning/payload`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        enrollmentToken: created.token,
        apkUrl: 'https://mdm.example/dpc.apk',
        apkChecksum: checksum,
        packageName: 'io.dpcaio.app',
        adminClassName: 'io.dpcaio.app.AioDeviceAdminReceiver',
      }),
    });
    assert.equal(payloadResponse.status, 200);
    const payload = await payloadResponse.json();
    assert.equal(payload.payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'].policyProfile, 'corporate');

    const consume = () => fetch(`${base}/v1/enrollments/consume`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ enrollmentToken: created.token }),
    });
    assert.equal((await consume()).status, 200);
    assert.equal((await consume()).status, 401);
  });
});

test('HTTP provisioning payload accepts TestDPC-style signature checksum', async () => {
  await withServer(async base => {
    const created = await fetch(`${base}/v1/enrollments`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ policyProfile: 'lab', ttlSeconds: 60 }),
    }).then(r => r.json());
    const signatureChecksum = sha256UrlSafe(Buffer.from('signer-cert'));
    const response = await fetch(`${base}/v1/provisioning/payload`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        enrollmentToken: created.token,
        apkUrl: 'https://mdm.example/dpc.apk',
        signatureChecksum,
        packageName: 'io.dpcaio.app',
        adminClassName: 'io.dpcaio.app.AioDeviceAdminReceiver',
      }),
    });
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'], signatureChecksum);
    assert.equal(result.payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'], undefined);
  });
});
