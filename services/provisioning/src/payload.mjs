import { createHash } from 'node:crypto';

const KEY_COMPONENT = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME';
const KEY_DOWNLOAD = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION';
const KEY_CHECKSUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM';
const KEY_SIGNATURE_CHECKSUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM';
const KEY_EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE';
const KEY_OFFLINE = 'android.app.extra.PROVISIONING_ALLOW_OFFLINE';
const KEY_REQUESTED_MODE = 'io.dpcaio.extra.PROVISIONING_MODE';
const KEY_ENROLLMENT_ENDPOINT = 'io.dpcaio.extra.ENROLLMENT_ENDPOINT';
const KEY_ENROLLMENT_SOURCE = 'io.dpcaio.extra.ENROLLMENT_SOURCE';

export function sha256UrlSafe(bytes) {
  return createHash('sha256').update(bytes).digest('base64url');
}

export function buildProvisioningPayload({
  apkUrl,
  apkChecksum,
  signatureChecksum,
  enrollmentToken,
  policyProfile = 'default',
  provisioningMode = 'work-profile',
  packageName = 'io.dpcaio.app',
  adminClassName = 'io.dpcaio.app.AioDeviceAdminReceiver',
  allowOffline = false,
  enrollmentEndpoint = '',
  enrollmentSource = '',
}) {
  if (!/^https:\/\//i.test(apkUrl)) throw new TypeError('apkUrl must use https');
  const validChecksum = value => /^[A-Za-z0-9_-]{43}$/.test(value || '');
  const hasApkChecksum = validChecksum(apkChecksum);
  const hasSignatureChecksum = validChecksum(signatureChecksum);
  if (hasApkChecksum === hasSignatureChecksum) {
    throw new TypeError('provide exactly one valid apkChecksum or signatureChecksum');
  }
  if (!enrollmentToken) throw new TypeError('enrollmentToken is required');
  if (!['auto', 'work-profile', 'fully-managed'].includes(provisioningMode)) {
    throw new TypeError('provisioningMode must be auto, work-profile, or fully-managed');
  }
  if (!packageName || !adminClassName) throw new TypeError('DPC component is required');
  if (enrollmentEndpoint && !/^https:\/\//i.test(enrollmentEndpoint)) throw new TypeError('enrollmentEndpoint must use https');

  const adminExtras = {
    enrollmentToken,
    policyProfile,
    [KEY_REQUESTED_MODE]: provisioningMode,
  };
  if (enrollmentEndpoint) adminExtras[KEY_ENROLLMENT_ENDPOINT] = enrollmentEndpoint;
  if (enrollmentSource) adminExtras[KEY_ENROLLMENT_SOURCE] = enrollmentSource;
  const payload = {
    [KEY_COMPONENT]: `${packageName}/${adminClassName}`,
    [KEY_DOWNLOAD]: apkUrl,
    [KEY_OFFLINE]: Boolean(allowOffline),
    [KEY_EXTRAS]: adminExtras,
  };
  if (hasSignatureChecksum) payload[KEY_SIGNATURE_CHECKSUM] = signatureChecksum;
  else payload[KEY_CHECKSUM] = apkChecksum;
  return payload;
}
