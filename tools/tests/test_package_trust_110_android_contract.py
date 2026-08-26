from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
gw=ROOT/'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageTrustGateway.kt'
receiver=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineInstallStatusReceiver.kt'
assert gw.exists(), 'AndroidPackageTrustGateway missing'
t=gw.read_text('utf-8')
for needle in ['GET_SIGNING_CERTIFICATES','signingCertificateHistory','apkContentsSigners','getInstallSourceInfo','splitNames','PackageTrustPlanner']:
    assert needle in t, needle
r=receiver.read_text('utf-8')
for needle in ['AndroidPackageTrustGateway','acceptedForOffline','PACKAGE_TRUST_MISMATCH']:
    assert needle in r, needle
print('test_package_trust_110_android_contract: PASS')
