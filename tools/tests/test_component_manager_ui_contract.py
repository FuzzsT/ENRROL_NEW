from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
h=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
a=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppOpsVerifiedEditorActivity.kt'
s=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentStateSnapshotStore.kt'
d=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt'
c=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt'
p=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
assert s.is_file(),s
t=u.read_text(); ht=h.read_text(); at=a.read_text(); st=s.read_text(); dt=d.read_text(); ct=c.read_text(); pt=p.read_text()
for n in ['Activity Launcher','Target user ID','Manifest','Override','Effective','Enable','Disable','Restore default','Enable & Launch','Batch Enable','Batch Disable','Batch Restore Default','Batch Preview','Apply batch','Restore snapshot','BATCH_NOT_ATOMIC','setStates(','PROTECTED_DPC_COMPONENT','AndroidComponentStateGateway','ShizukuComponentStateExecutor']:
    assert n in t,n
for n in [
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'SortDirection { ASCENDING, DESCENDING }',
    'private var currentSortDirection = SortDirection.ASCENDING',
    'val firstInstallTime: Long',
    'val lastUpdateTime: Long',
    'firstInstallTime = packageInfo.firstInstallTime',
    'lastUpdateTime = packageInfo.lastUpdateTime',
    'AppSort.INSTALL_DATE -> filtered.sortedBy { it.firstInstallTime }',
    'AppSort.UPDATE_DATE -> filtered.sortedBy { it.lastUpdateTime }',
    'return if (currentSortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted',
    'AppSort.NAME, AppSort.PACKAGE -> SortDirection.ASCENDING',
    'AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> SortDirection.DESCENDING',
    'Direction: ${sortDirectionLabel()}',
    'A → Z',
    'Z → A',
    'Newest → oldest',
    'Oldest → newest',
    'Recent keeps most-recent-first order.',
    'formatInstallDate(app.firstInstallTime)',
    'formatInstallDate(app.lastUpdateTime)',
    'selectedPackages',
    'renderSelectionBar()',
    'Select visible',
    'Clear selection',
    'Copy selected packages',
    'Favorite selected',
    'toggleSelected(',
    'SELECTED',
    'AppOps',
    'AppOpsVerifiedEditorActivity.EXTRA_TARGET_PACKAGE',
    'filteredApps().take(visibleAppLimit)',
    'STATE_SELECTED_PACKAGES',
    'override fun onSaveInstanceState',
    'putStringArrayList(STATE_SELECTED_PACKAGES',
    'savedInstanceState?.getStringArrayList(STATE_SELECTED_PACKAGES)',
    'selectedPackages.addAll(',
]:
    assert n in ht,n
for n in [
    'targetUserIdOrNull()',
    'takeIf { it >= 0 }',
    'User ID must be a non-negative integer',
]:
    assert n in at,n
assert 'AppOpsVerifiedEditorActivity::class.java' in dt, 'Dashboard must expose Verified AppOps navigation'
assert 'FunctionEntry("Verified AppOps"' in ct, 'Control Center must expose Verified AppOps navigation'
for n in [
    'Open Verified AppOps',
    'AppOpsVerifiedEditorActivity.EXTRA_TARGET_PACKAGE',
    'targetPackage.text.toString().trim()',
    'AppOpsVerifiedEditorActivity::class.java',
]:
    assert n in pt, f'Permission Manager AppOps navigation missing: {n}'
assert 'createDeviceProtectedStorageContext' in st
assert 'previousOverrideState' in st
print('COMPONENT_MANAGER_UI_CONTRACT: PASS')
