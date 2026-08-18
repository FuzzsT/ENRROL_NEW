import test from 'node:test';
import assert from 'node:assert/strict';
import { buildProvisioningPayload, sha256UrlSafe } from '../src/payload.mjs';

test('builds Android 10+ QR provisioning payload with checksum and token extras', () => {
  const checksum = sha256UrlSafe(Buffer.from('apk-bytes'));
  const payload = buildProvisioningPayload({
    apkUrl: 'https://mdm.example/dpc.apk',
    apkChecksum: checksum,
    enrollmentToken: 'token-123',
    policyProfile: 'corporate',
    packageName: 'io.dpcaio.app',
    adminClassName: 'io.dpcaio.app.AioDeviceAdminReceiver',
  });
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'], 'io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver');
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'], 'https://mdm.example/dpc.apk');
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'], checksum);
  assert.deepEqual(payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'], {
    enrollmentToken: 'token-123',
    policyProfile: 'corporate',
  });
});

test('builds TestDPC-style payload with signature checksum', () => {
  const signatureChecksum = sha256UrlSafe(Buffer.from('signer-cert-der'));
  const payload = buildProvisioningPayload({
    apkUrl: 'https://mdm.example/dpc.apk',
    signatureChecksum,
    enrollmentToken: 'token-456',
    packageName: 'io.dpcaio.app',
    adminClassName: 'io.dpcaio.app.AioDeviceAdminReceiver',
  });
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'], signatureChecksum);
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'], undefined);
});
