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
    provisioningMode: 'work-profile',
    packageName: 'io.dpcaio.app',
    adminClassName: 'io.dpcaio.app.AioDeviceAdminReceiver',
  });
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'], 'io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver');
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'], 'https://mdm.example/dpc.apk');
  assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'], checksum);
  assert.deepEqual(payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'], {
    enrollmentToken: 'token-123',
    policyProfile: 'corporate',
    'io.dpcaio.extra.PROVISIONING_MODE': 'work-profile',
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


test('defaults public provisioning requests to explicit work-profile mode', () => {
  const checksum = sha256UrlSafe(Buffer.from('default-mode-apk'));
  const payload = buildProvisioningPayload({
    apkUrl: 'https://mdm.example/dpc.apk',
    apkChecksum: checksum,
    enrollmentToken: 'token-default',
  });
  assert.equal(
    payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE']['io.dpcaio.extra.PROVISIONING_MODE'],
    'work-profile',
  );
});

test('embeds enrollment endpoint and source for Enrollment Engine v2', () => {
  const checksum = sha256UrlSafe(Buffer.from('v2-enrollment-apk'));
  const payload = buildProvisioningPayload({
    apkUrl: 'https://mdm.example/dpc.apk',
    apkChecksum: checksum,
    enrollmentToken: 'token-v2',
    enrollmentEndpoint: 'https://enroll.example/v2',
    enrollmentSource: 'qr',
  });
  const extras = payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'];
  assert.equal(extras['io.dpcaio.extra.ENROLLMENT_ENDPOINT'], 'https://enroll.example/v2');
  assert.equal(extras['io.dpcaio.extra.ENROLLMENT_SOURCE'], 'qr');
  assert.throws(() => buildProvisioningPayload({
    apkUrl: 'https://mdm.example/dpc.apk',
    apkChecksum: checksum,
    enrollmentToken: 'token-v2',
    enrollmentEndpoint: 'http://insecure.example/v2',
  }), /https/);
});
