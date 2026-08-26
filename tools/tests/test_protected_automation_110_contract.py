from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
text = (ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflinePolicyApplier.kt').read_text('utf-8')
assert 'automated = true' in text, 'offline mutations are not marked automated'
assert 'packageName = rule.packageName' in text, 'permission protection target package is not forwarded'
assert 'ComponentControlRequest' in text
print('test_protected_automation_110_contract: PASS')
