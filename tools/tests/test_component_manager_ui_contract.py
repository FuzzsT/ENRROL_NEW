from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
u=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
h=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
a=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppOpsVerifiedEditorActivity.kt'
s=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentStateSnapshotStore.kt'
d=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt'
c=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt'
p=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
autostart=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt'
picker=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/RuntimeTargetPicker.kt'
verification=ROOT/'apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ComponentMutationVerification.kt'
route_executor=ROOT/'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityRouteExecutor.kt'
manifest=ROOT/'apps/dpc/app/src/main/AndroidManifest.xml'
assert s.is_file(),s
assert picker.is_file(),picker
assert verification.is_file(),verification
assert route_executor.is_file(),route_executor
t=u.read_text(); ht=h.read_text(); at=a.read_text(); st=s.read_text(); dt=d.read_text(); ct=c.read_text(); pt=p.read_text(); pick=picker.read_text(encoding='utf-8'); verify=verification.read_text(encoding='utf-8'); route=route_executor.read_text(encoding='utf-8')
for n in ['Activity Launcher','Target user ID','Manifest','Override','Effective','Enable','Disable','Restore default','Enable & Launch','Batch Enable','Batch Disable','Batch Restore Default','Batch Preview','Apply batch','Restore snapshot','BATCH_NOT_ATOMIC','setStates(','PROTECTED_DPC_COMPONENT','AndroidComponentStateGateway','ShizukuComponentStateExecutor','ComponentMutationVerification.statusLine','ComponentMutationVerification.canLaunchAfterEnable','result.observedState','result.verified','Choose package','Choose user','RuntimeTargetPicker.showPackagePicker','RuntimeTargetPicker.showUserPicker','intent.getIntExtra("userId", current)','takeIf { it >= 0 }']:
    assert n in t,n
assert 'SUBMITTED, cross-user readback not claimed' not in t
assert 'readback via rescan' not in t

restore_start=t.index('private fun restoreSnapshot()')
restore_end=t.index('\n    private fun launch(', restore_start)
restore=t[restore_start:restore_end]
for n in [
    'val operationUserId = targetUserId()',
    'snapshotStore.list(pkg, operationUserId)',
    'decision(activity, snapshot.previousOverrideState, operationUserId)',
    'userId = operationUserId',
    'snapshotStore.clear(pkg, operationUserId)',
]:
    assert n in restore,f'Activity Explorer snapshot restore missing stable-user guard: {n}'
assert restore.count('targetUserId()') == 1, \
    'Activity Explorer snapshot restore must capture target user exactly once per restore operation'

for n in [
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'SortDirection { ASCENDING, DESCENDING }',
    'private var currentSortDirection = SortDirection.ASCENDING',
    'val firstInstallTime: Long',
    'val lastUpdateTime: Long',
    'firstInstallTime = packageInfo.firstInstallTime',
    'lastUpdateTime = packageInfo.lastUpdateTime',
    'private fun isValidTimestamp(timestamp: Long, now: Long): Boolean',
    'timestamp > 0L && timestamp <= now',
    'private fun dateSortComparator(',
    'compareByDescending<AppEntry> { timestamp(it) }',
    'compareBy<AppEntry> { timestamp(it) }',
    '.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }',
    '.thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }',
    'private fun sortByTimestampUnknownLast(',
    'val (valid, unknown) = apps.partition { isValidTimestamp(timestamp(it), now) }',
    'return sortedValid + unknown',
    'AppSort.INSTALL_DATE -> sortByTimestampUnknownLast(filtered) { it.firstInstallTime }',
    'AppSort.UPDATE_DATE -> sortByTimestampUnknownLast(filtered) { it.lastUpdateTime }',
    'AppSort.NAME, AppSort.PACKAGE -> if (currentSortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted',
    'AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> sorted',
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

for n in ['object RuntimeTargetPicker','showUserPicker(','showPackagePicker(','showPackageMultiPicker(','setMultiChoiceItems','listUsers()','listPackagesForUser(userId)','AlertDialog.Builder']:
    assert n in pick,f'Runtime target picker missing {n}'
assert r'UserInfo\\{' in pick, 'Runtime target picker must keep the escaped UserInfo regex source contract'

for n in [
    'object ComponentMutationVerification',
    'fun statusLine(',
    'requested.name',
    'observed?.name ?: "UNKNOWN"',
    'append(verified)',
    'fun canLaunchAfterEnable(',
    'requested == ComponentOverrideState.ENABLED',
    'observed == ComponentOverrideState.ENABLED',
]:
    assert n in verify,f'Component mutation verification helper missing {n}'

for n in [
    'UserHandle.getUserHandleForUid(Process.myUid())',
    'user != currentUser',
    'Cross-user route requires LauncherApps or a privileged executor',
    'ActivityRoute.FRAMEWORK_EXPLICIT, ActivityRoute.SAME_UID',
    'ActivityRoute.DEEP_LINK',
]:
    assert n in route, f'Cross-user route guard missing {n}'

assert autostart.is_file(), 'AutostartManagerActivity.kt missing'
aut=autostart.read_text(encoding='utf-8')
for n in [
    'Autostart Manager',
    'Intent.ACTION_BOOT_COMPLETED',
    'Intent.ACTION_LOCKED_BOOT_COMPLETED',
    'PackageManager.MATCH_DISABLED_COMPONENTS',
    'queryBroadcastReceivers',
    'AndroidComponentStateGateway',
    'ShizukuComponentStateExecutor',
    'ComponentMutationVerification.statusLine',
    'result.observedState',
    'result.verified',
    'ComponentOverrideState.ENABLED',
    'ComponentOverrideState.DISABLED',
    'Target user ID',
    'Choose user',
    'RuntimeTargetPicker.showUserPicker',
    'CURRENT_USER_READBACK',
    'TARGET_USER_VERIFIED',
    'TARGET_USER_FAILED',
    'Restore default',
    'queryLocalBootReceivers()',
    'queryCrossUserBootReceivers(selectedUser)',
    'shizuku.queryBroadcastReceivers(action, userId)',
    'LOCAL_PACKAGE_MANAGER',
    'CROSS_USER_SHIZUKU',
    'TARGET_USER_INVENTORY',
    'lastVerified=',
    'Cross-user inventory unavailable',
    'manifestEnabled = null',
    'private var scanGeneration = 0L',
    'val generation = ++scanGeneration',
    'if (generation != scanGeneration) return@runOnUiThread',
]:
    assert n in aut,f'Autostart Manager missing {n}'
assert 'TARGET_USER_SUBMITTED' not in aut
assert 'else queryLocalBootReceivers()' not in aut, 'cross-user scan must not silently fall back to current-user inventory'
mt=manifest.read_text(encoding='utf-8')
assert '.AutostartManagerActivity' in mt,'Autostart Manager manifest entry missing'
assert '"Autostart Manager"' in dt and 'AutostartManagerActivity::class.java' in dt,'Dashboard must expose Autostart Manager'
print('COMPONENT_MANAGER_UI_CONTRACT: PASS')
