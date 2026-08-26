from pathlib import Path
R=Path(__file__).resolve().parents[2]
m=(R/'apps/dpc/app/src/main/AndroidManifest.xml').read_text(); d=(R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
p=R/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/CredentialCenterActivity.kt'
assert p.exists(); t=p.read_text(); low=t.lower()
for n in ['CredentialCenterActivity','CapabilityResolver','INSTALLED_USER_CA','installCaCertificate','getInstalledCaCertificates','installManagedKeyPair','getManagedKeyPairGrants','DELEGATION_CERT_INSTALL','DELEGATION_CERT_SELECTION','Intent.ACTION_OPEN_DOCUMENT','application/x-pkcs12','CertificateFactory.getInstance("X.509")','KeyStore.getInstance("PKCS12")','contentResolver.openInputStream','Import CA PEM/DER','Import PKCS#12']:
    assert n in t
assert 'export private key' not in low and 'dump private key' not in low
assert '.CredentialCenterActivity' in m and 'Certificate & Credential Center' in d
print('test_credential_center_contract: PASS')
