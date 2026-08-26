from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
s=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsSnapshot.kt').read_text()
a=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsActivity.kt').read_text()
for token in ['offlineBundleId','offlineStage','offlineSyncPending','offlineLastError','OfflineDeploymentStore']:
    assert token in s, token
for token in ['Offline bundle','Offline stage','Offline sync pending']:
    assert token in a, token
for forbidden in ['bundlePath','offline-vault/','manifest.sig']:
    assert f'put("{forbidden}"' not in s
print('OFFLINE_DIAGNOSTICS_CONTRACT: PASS')
