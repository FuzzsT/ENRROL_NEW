from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
s=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentStateSnapshotStore.kt'
assert s.is_file(),s
t=u.read_text(); st=s.read_text()
for n in ['Activity Manager 2.0','Target user ID','Manifest','Override','Effective','Enable','Disable','Restore default','Enable & Launch','Batch Enable','Batch Disable','Batch Restore Default','Batch Preview','Apply batch','Restore snapshot','BATCH_NOT_ATOMIC','setStates(','PROTECTED_DPC_COMPONENT','AndroidComponentStateGateway','ShizukuComponentStateExecutor']:
    assert n in t,n
assert 'createDeviceProtectedStorageContext' in st
assert 'previousOverrideState' in st
print('COMPONENT_MANAGER_UI_CONTRACT: PASS')
