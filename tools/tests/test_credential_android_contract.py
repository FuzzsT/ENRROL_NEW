from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
g=(ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt').read_text()
c=(ROOT/'apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt').read_text()
for n in ['installCaCert','getInstalledCaCerts','uninstallCaCert','installKeyPair','removeKeyPair','grantKeyPairToApp','getKeyPairGrants','DELEGATION_CERT_INSTALL','DELEGATION_CERT_SELECTION']:
    assert n in g or n in c, f'missing credential API {n}'
for n in ['installCaCertificate','getInstalledCaCertificates','uninstallCaCertificate','installManagedKeyPair','removeManagedKeyPair','grantManagedKeyPairToApp','getManagedKeyPairGrants']:
    assert n in c, f'missing core credential gateway {n}'
combined=(g+'\n'+c).lower()
assert 'exportprivatekey' not in combined
assert 'dumpprivatekey' not in combined
assert 'getdeclaredmethod(' not in g.lower()
print('test_credential_android_contract: PASS')
