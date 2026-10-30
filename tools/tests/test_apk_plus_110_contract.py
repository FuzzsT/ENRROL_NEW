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

models=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueModels.kt'
store=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueStore.kt'
coordinator=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueCoordinator.kt'
assert models.exists(), 'InstallQueueModels missing'
assert store.exists(), 'InstallQueueStore missing'
assert coordinator.exists(), 'InstallQueueCoordinator missing'
m=models.read_text('utf-8')
s=store.read_text('utf-8')
c=coordinator.read_text('utf-8')
for needle in ['QUEUED','PREFLIGHT','WAITING_USER','INSTALLING','SUCCESS','FAILED','SKIPPED','InstallHistoryEntry']:
    assert needle in m, f'InstallQueueModels missing {needle}'
for needle in ['loadQueue','replaceQueue','updateItem','loadHistory','appendHistory','clearCompleted','100']:
    assert needle in s, f'InstallQueueStore missing {needle}'
for needle in ['enqueue','startOrResume','onPackageInstallerResult','retryFailed','clearCompleted','recoverInterrupted']:
    assert needle in c, f'InstallQueueCoordinator missing {needle}'

receiver=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppInstallStatusReceiver.kt').read_text('utf-8')
for needle in ['QUEUE_ITEM_ID','SESSION_ID','STATUS_PENDING_USER_ACTION']:
    assert needle in receiver, f'AppInstallStatusReceiver missing {needle}'

for needle in ['EXTRA_ALLOW_MULTIPLE','clipData','Add to queue','Add multiple','Start / Resume queue','Retry failed','Clear completed','History']:
    assert needle in a, f'AppPackageManagerActivity missing {needle}'

manifest=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
for needle in ['.AppPackageManagerActivity','.AppInstallStatusReceiver']:
    assert needle in manifest, needle

dashboard=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text('utf-8')
for needle in ['APK / APKS Installer','App Updater','AppPackageManagerActivity']:
    assert needle in dashboard, needle

# ShizukuPlus/OpenLoader-inspired clean-room improvements: capability doctor + local operation history.
doctor_model=ROOT/'apps/dpc/modules/installer/core/src/main/kotlin/io/dpcaio/installer/InstallerDoctorModels.kt'
assert doctor_model.exists(), 'InstallerDoctorModels missing'
dm=doctor_model.read_text('utf-8')
for needle in ['enum class InstallerBackendKind','PACKAGE_INSTALLER','DEVICE_OWNER_CONTEXT','SHIZUKU_DIAGNOSTIC','data class InstallerDoctorSnapshot','enum class InstallHistoryStatus','PENDING_USER_ACTION','SUCCEEDED','FAILED']:
    assert needle in dm, needle

doctor=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallerDoctor.kt'
assert doctor.exists(), 'InstallerDoctor missing'
d=doctor.read_text('utf-8')
for needle in ['canRequestPackageInstalls','DevicePolicyManager','AndroidShizukuRuntime','binderAlive','permissionGranted','DIAGNOSTIC_ONLY']:
    assert needle in d, needle
for forbidden in ['Runtime.getRuntime().exec','ProcessBuilder','pm install','su -c','packageManager.packageInstaller != null']:
    assert forbidden not in d, forbidden
assert 'runCatching { packageManager.packageInstaller; true }.getOrDefault(false)' in d, 'PackageInstaller availability must be exception-based, not null-based'

history=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppInstallHistoryStore.kt'
assert history.exists(), 'AppInstallHistoryStore missing'
h=history.read_text('utf-8')
for needle in ['class AppInstallHistoryStore','SharedPreferences','InstallHistoryRecord','fun append','fun recent']:
    assert needle in h, needle

for needle in ['Installer Doctor','Installation history','InstallerDoctor','AppInstallHistoryStore']:
    assert needle in a, needle

receiver=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppInstallStatusReceiver.kt').read_text('utf-8')
for needle in ['InstallHistoryStatus.PENDING_USER_ACTION','InstallHistoryStatus.SUCCEEDED','InstallHistoryStatus.FAILED','AppInstallHistoryStore']:
    assert needle in receiver, needle

print('test_apk_plus_110_contract: PASS')
