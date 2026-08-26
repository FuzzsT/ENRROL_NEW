from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflineBundleReader.kt'
a=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
b=ROOT/'apps/dpc/app/build.gradle.kts'
assert p.is_file(),p
t=p.read_text(); ui=a.read_text(); gradle=b.read_text()
for n in ['ZipFile','manifest.json','manifest.sig','JSONObject','OfflineBundleVerifier','MessageDigest','signingCertificateSha256']:
    assert n in t,n
assert 'OFFLINE_SIGNING_PUBLIC_KEY' in gradle
assert 'AndroidOfflineBundleReader' in ui and 'OFFLINE_SIGNING_PUBLIC_KEY' in ui
assert 'file.length() > 0L' not in ui
print('OFFLINE_BUNDLE_ANDROID_VERIFIER_CONTRACT: PASS')
