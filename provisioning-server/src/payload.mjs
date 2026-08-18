import { createHash } from 'node:crypto';

const KEY_COMPONENT = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME';
const KEY_DOWNLOAD = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION';
const KEY_CHECKSUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM';
const KEY_SIGNATURE_CHECKSUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM';
const KEY_EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE';
const KEY_OFFLINE = 'android.app.extra.PROVISIONING_ALLOW_OFFLINE';

export function sha256UrlSafe(bytes) {
  return createHash('sha256').update(bytes).digest('base64url');
}

export function buildProvisioningPayload({
  apkUrl,
  apkChecksum,
  signatureChecksum,
  enrollmentToken,
  policyProfile = 'default',
  packageName = 'io.dpcaio.app',
  adminClassName = 'io.dpcaio.app.AioDeviceAdminReceiver',
  allowOffline = false,
}) {
  if (!/^https:\/\//i.test(apkUrl)) throw new TypeError('apkUrl must use https');
  const validChecksum = value => /^[A-Za-z0-9_-]{43}$/.test(value || '');
  const hasApkChecksum = validChecksum(apkChecksum);
  const hasSignatureChecksum = validChecksum(signatureChecksum);
  if (hasApkChecksum === hasSignatureChecksum) {
    throw new TypeError('provide exactly one valid apkChecksum or signatureChecksum');
  }
  if (!enrollmentToken) throw new TypeError('enrollmentToken is required');
  if (!packageName || !adminClassName) throw new TypeError('DPC component is required');

  const payload = {
    [KEY_COMPONENT]: `${packageName}/${adminClassName}`,
    [KEY_DOWNLOAD]: apkUrl,
    [KEY_OFFLINE]: Boolean(allowOffline),
    [KEY_EXTRAS]: {
      enrollmentToken,
      policyProfile,
    },
  };
  if (hasSignatureChecksum) payload[KEY_SIGNATURE_CHECKSUM] = signatureChecksum;
  else payload[KEY_CHECKSUM] = apkChecksum;
  return payload;
}
