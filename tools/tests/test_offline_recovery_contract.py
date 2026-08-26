from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
r=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineRecoveryReceiver.kt'
j=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineRecoveryJobService.kt'
m=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
for p in [r,j]: assert p.is_file(),p
rt=r.read_text(); jt=j.read_text()
assert 'JobScheduler' in rt and 'schedule(' in rt
for forbidden in ['HttpURLConnection','EnrollmentHttpClient','PackageInstaller']:
    assert forbidden not in rt, forbidden
assert 'OfflineDeploymentStore' in jt
assert '.OfflineRecoveryReceiver' in m and '.OfflineRecoveryJobService' in m
print('OFFLINE_RECOVERY_CONTRACT: PASS')
