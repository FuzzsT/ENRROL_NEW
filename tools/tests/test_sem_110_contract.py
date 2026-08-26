from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
core=ROOT/'apps/dpc/modules/samsung/core/src/main/kotlin/io/dpcaio/samsung/sem/SemCapability.kt'
cat=ROOT/'apps/dpc/modules/samsung/core/src/main/kotlin/io/dpcaio/samsung/sem/SemCapabilityCatalog.kt'
probe=ROOT/'apps/dpc/modules/samsung/android/src/main/kotlin/io/dpcaio/samsung/sem/android/SemRuntimeProbe.kt'
assert core.exists() and cat.exists() and probe.exists(), 'SEM files missing'
t='\n'.join(p.read_text('utf-8') for p in [core,cat,probe])
for marker in ['CLASS_PRESENT','METHOD_PRESENT','PERMISSION_SATISFIED','CALL_SUCCEEDED','READBACK_VERIFIED']:
    assert marker in t, f'missing SEM evidence stage {marker}'
assert 'readOnly = true' in cat.read_text('utf-8'), 'SEM catalog must default read-only for discovery features'
assert 'setHiddenApiExemptions' not in t
print('test_sem_110_contract: PASS')
