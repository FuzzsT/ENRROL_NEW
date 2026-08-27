from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
s=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentStateSnapshotStore.kt'
f=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityFavoriteStore.kt'
assert s.is_file(),s
assert f.is_file(),f
t=u.read_text(); st=s.read_text(); ft=f.read_text()
for n in [
    'Activity Manager 3.0','Target user ID','Manifest','Override','Effective','Enable','Disable','Restore default',
    'Enable & Launch','Preview Batch Enable','Preview Batch Disable','Preview Restore Default','Batch Preview','Apply batch',
    'Restore snapshot','BATCH_NOT_ATOMIC','setStates(','PROTECTED_DPC_COMPONENT','AndroidComponentStateGateway',
    'ShizukuComponentStateExecutor','listApps(','expandedPackages','loadedActivities','favoritesOnly','favoriteGroup'
]:
    assert n in t,n
assert 'createDeviceProtectedStorageContext' in st
assert 'previousOverrideState' in st
assert 'createDeviceProtectedStorageContext' in ft
assert 'toggleAppFavorite' in ft and 'toggleActivityFavorite' in ft
print('COMPONENT_MANAGER_UI_CONTRACT: PASS')
