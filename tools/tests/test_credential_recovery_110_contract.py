from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
gw=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidCredentialRecoveryGateway.kt'
vault=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/CredentialRecoveryVault.kt'
assert gw.exists() and vault.exists(), 'credential recovery android files missing'
t=gw.read_text('utf-8'); v=vault.read_text('utf-8')
for needle in ['SecureRandom','ByteArray(32)','setResetPasswordToken','isResetPasswordTokenActive','clearResetPasswordToken','resetPasswordWithToken','CredentialRecoveryPlanner','AndroidUserId.fromUid(Process.myUid())']:
    assert needle in t, needle
for needle in ['AndroidKeyStore','AES/GCM/NoPadding','Cipher.ENCRYPT_MODE','Cipher.DECRYPT_MODE','createDeviceProtectedStorageContext']:
    assert needle in v, needle
combined=t+'\n'+v
for forbidden in ['Process.myUserHandle().hashCode()', 'println(token','Log.d(', 'Log.i(', 'token.toString()', 'Base64.getEncoder().encodeToString(token)']:
    assert forbidden not in combined, forbidden
print('test_credential_recovery_110_contract: PASS')
