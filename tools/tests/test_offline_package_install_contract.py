from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
i=ROOT/'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflinePackageInstaller.kt'
r=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineInstallStatusReceiver.kt'
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
m=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
assert r.is_file(),r
it=i.read_text(); rt=r.read_text(); ui=u.read_text()
for n in ['stageBundle','ZipFile','createPackageSession','stageFile','createMultiPackageSession']:
    assert n in it,n
for n in ['PackageInstaller.EXTRA_STATUS','STATUS_SUCCESS','PACKAGES_INSTALLED','OfflineRecoveryReceiver']:
    assert n in rt,n
assert 'AndroidOfflinePackageInstaller' in ui and 'PendingIntent' in ui and 'commit(' in ui
assert '.OfflineInstallStatusReceiver' in m
print('OFFLINE_PACKAGE_INSTALL_CONTRACT: PASS')
