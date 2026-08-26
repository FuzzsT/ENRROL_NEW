from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidApkPlusStager.kt'
assert p.exists(), 'AndroidApkPlusStager missing'
t=p.read_text('utf-8')
for needle in ['ApkPlusArchivePlanner','ZipCentralDirectoryAttributes','getPackageArchiveInfo','GET_SIGNING_CERTIFICATES','accepted','staging']:
    assert needle in t, needle
for forbidden in ['DexClassLoader','PathClassLoader','Runtime.getRuntime().exec','ProcessBuilder']:
    assert forbidden not in t, forbidden
print('test_apk_plus_110_contract: PASS')
