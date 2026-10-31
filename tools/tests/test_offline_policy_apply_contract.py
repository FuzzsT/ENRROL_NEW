from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
r=ROOT/'apps/dpc/modules/offline/android/src/main/kotlin/io/dpcaio/offline/android/AndroidOfflinePolicyReader.kt'
a=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflinePolicyApplier.kt'
j=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineRecoveryJobService.kt'
for p in [r,a,j]: assert p.is_file(),p
rt=r.read_text(); at=a.read_text(); jt=j.read_text()
for n in ['defaultPermissionPolicy','permissions','components','required']:
    assert n in rt,n
for n in [
    'AndroidPermissionManagerGateway',
    'AndroidComponentStateGateway',
    'ShizukuComponentStateExecutor',
    'ComponentMutationVerification.statusLine',
    'componentMutation.verified',
    'componentMutation.observedState',
    'getRuntimePermissionState',
    'shizukuObserved',
    'observed=${shizukuObserved ?: "UNKNOWN"}',
    'POLICY_READBACK_MISMATCH',
    'COMPONENT_STATE_MISMATCH',
    'OFFLINE_VERIFIED',
    'SYNC_PENDING',
]:
    assert n in at,n
old_shizuku_submission = 'ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId, desired).submitted'
assert old_shizuku_submission not in at, 'offline component verification must not reduce Shizuku result to submitted-only state'
assert 'targetUserId != currentUser -> false' not in at, 'offline Shizuku permission verification must use target-user readback instead of rejecting all cross-user results'
for forbidden in ['HttpURLConnection','EnrollmentHttpClient','java.net.URL(','InetAddress']:
    assert forbidden not in at,forbidden
assert 'OfflinePolicyApplier' in jt and 'PACKAGES_INSTALLED' in jt
print('OFFLINE_POLICY_APPLY_CONTRACT: PASS')
