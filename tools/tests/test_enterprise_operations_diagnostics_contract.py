from pathlib import Path
R=Path(__file__).resolve().parents[2]
t=(R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsSnapshot.kt').read_text()
for n in ['affiliatedUser','securityLoggingEnabled','networkLoggingEnabled','pendingNetworkBatchToken','systemUpdateMode','freezePeriodCount','caCertificateCount','crossProfilePackageCount','managedProfileMaximumTimeOffMillis']:
    assert n in t, f'missing diagnostics field {n}'
for forbidden in ['enrollmentToken','privateKey','licenseKey']:
    assert f'put("{forbidden}"' not in t
print('test_enterprise_operations_diagnostics_contract: PASS')
