from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
recv=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDeviceAdminReceiver.kt').read_text()
store_path=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterpriseLogStore.kt'
state_path=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterpriseLogStateStore.kt'
assert 'override fun onSecurityLogsAvailable' in recv
assert 'override fun onNetworkLogsAvailable' in recv
assert 'recordNetworkBatchToken(batchToken, networkLogsCount)' in recv
assert 'markSecurityLogsAvailable()' in recv
store=store_path.read_text()
state=state_path.read_text()
assert 'MAX_BATCHES = 10' in store
assert 'MAX_BYTES = 5L * 1024L * 1024L' in store
assert 'redact' in store
assert '<redacted-ip>' in store
assert '<redacted-host-or-package>' in store
assert 'createDeviceProtectedStorageContext()' in state
print('test_enterprise_log_store_contract: PASS')
