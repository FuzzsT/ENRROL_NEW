from pathlib import Path
R=Path(__file__).resolve().parents[2]
m=(R/'apps/dpc/app/src/main/AndroidManifest.xml').read_text(); d=(R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
p=R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterpriseOperationsActivity.kt'
assert p.exists(); t=p.read_text()
for n in ['EnterpriseOperationsActivity','CapabilityResolver','Security Logging','Network Logging','System Update Policy','Preview','retrieveSecurityLogs','retrieveNetworkLogs','setSystemUpdatePolicySpec','Freeze periods (MM-DD:MM-DD;...)','FreezePeriodTextParser']:
    assert n in t
assert '.EnterpriseOperationsActivity' in m and 'Enterprise Operations Center' in d
assert 'http://' not in t and 'https://' not in t
print('test_enterprise_operations_ui_contract: PASS')
