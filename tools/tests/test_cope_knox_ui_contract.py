from pathlib import Path
R=Path(__file__).resolve().parents[2]
m=(R/'apps/dpc/app/src/main/AndroidManifest.xml').read_text(); d=(R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
cope=R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/WorkProfileCopeActivity.kt'; knox=R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt'
assert cope.exists() and knox.exists()
a=cope.read_text(); k=knox.read_text()
for n in ['CapabilityResolver','Contacts access','Caller ID access','Cross-profile packages','Maximum time off','Personal apps','Organization','Affiliation','72']:
    assert n in a
for n in ['Knox KPE','KNOX_AUDIT_LOG','DEPRECATED_PLATFORM_API','Enhanced Attestation','ApplicationPolicy','CertificatePolicy','Kiosk','Firewall / VPN']:
    assert n in k
assert '.WorkProfileCopeActivity' in m and '.KnoxEnterpriseCenterActivity' in m
assert 'Work Profile / COPE' in d and 'Knox Enterprise Center' in d
print('test_cope_knox_ui_contract: PASS')
