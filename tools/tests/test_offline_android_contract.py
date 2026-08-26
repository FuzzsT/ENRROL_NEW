from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
store = ROOT / 'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflineBundleStore.kt'
installer = ROOT / 'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflinePackageInstaller.kt'
deploy = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineDeploymentStore.kt'
coord = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineDeploymentCoordinator.kt'
for p in [store, installer, deploy, coord]:
    assert p.is_file(), p
s = store.read_text()
i = installer.read_text()
d = deploy.read_text()
c = coord.read_text()
assert 'ACTION_OPEN_DOCUMENT' in s
assert 'contentResolver.openInputStream' in s
assert 'PackageInstaller.SessionParams' in i
assert 'setMultiPackage' in i
assert 'createDeviceProtectedStorageContext' in d
assert 'FULL_OFFLINE' in c
for forbidden in ['HttpURLConnection', 'OkHttp', 'EnrollmentHttpClient', 'java.net.URL(', 'InetAddress']:
    assert forbidden not in c, forbidden
print('OFFLINE_ANDROID_CONTRACT: PASS')
