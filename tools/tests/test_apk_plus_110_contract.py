from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidApkPlusStager.kt'
assert p.exists(), 'AndroidApkPlusStager missing'
t=p.read_text('utf-8')
for needle in ['ApkPlusArchivePlanner','ZipCentralDirectoryAttributes','getPackageArchiveInfo','GET_SIGNING_CERTIFICATES','accepted','staging']:
    assert needle in t, needle
for forbidden in ['DexClassLoader','PathClassLoader','Runtime.getRuntime().exec','ProcessBuilder']:
    assert forbidden not in t, forbidden

adapter=(ROOT/'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageInstallerAdapter.kt').read_text('utf-8')
for needle in ['fun stageApkSet(', 'session.openWrite', 'session.fsync']:
    assert needle in adapter, needle

activity=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt')
assert activity.exists(), 'AppPackageManagerActivity missing'
a=activity.read_text('utf-8')
for needle in ['ACTION_OPEN_DOCUMENT','AndroidApkPlusStager','AndroidPackageInstallerAdapter','APK/APKS Installer','App Updater','loadInstalledPackages()','getPackageArchiveInfo','USER_ACTION_REQUIRED']:
    assert needle in a, needle
for forbidden in ['Runtime.getRuntime().exec','ProcessBuilder','pm install','su -c']:
    assert forbidden not in a, forbidden

manifest=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
for needle in ['.AppPackageManagerActivity','.AppInstallStatusReceiver']:
    assert needle in manifest, needle

dashboard=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text('utf-8')
for needle in ['APK / APKS Installer','App Updater','AppPackageManagerActivity']:
    assert needle in dashboard, needle

print('test_apk_plus_110_contract: PASS')
