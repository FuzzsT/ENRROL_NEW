from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
h=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
s=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentStateSnapshotStore.kt'
assert s.is_file(),s
t=u.read_text(); ht=h.read_text(); st=s.read_text()
for n in ['Activity Launcher','Target user ID','Manifest','Override','Effective','Enable','Disable','Restore default','Enable & Launch','Batch Enable','Batch Disable','Batch Restore Default','Batch Preview','Apply batch','Restore snapshot','BATCH_NOT_ATOMIC','setStates(','PROTECTED_DPC_COMPONENT','AndroidComponentStateGateway','ShizukuComponentStateExecutor']:
    assert n in t,n
for n in [
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'val firstInstallTime: Long',
    'val lastUpdateTime: Long',
    'firstInstallTime = packageInfo.firstInstallTime',
    'lastUpdateTime = packageInfo.lastUpdateTime',
    'AppSort.INSTALL_DATE -> filtered.sortedByDescending { it.firstInstallTime }',
    'AppSort.UPDATE_DATE -> filtered.sortedByDescending { it.lastUpdateTime }',
    'formatInstallDate(app.firstInstallTime)',
    'formatInstallDate(app.lastUpdateTime)',
]:
    assert n in ht,n
assert 'createDeviceProtectedStorageContext' in st
assert 'previousOverrideState' in st
print('COMPONENT_MANAGER_UI_CONTRACT: PASS')
