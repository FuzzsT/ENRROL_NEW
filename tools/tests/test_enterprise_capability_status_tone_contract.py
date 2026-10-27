from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = (root / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterpriseOperationsActivity.kt").read_text(encoding="utf-8")

assert 'status.contains("AVAILABLE")' not in source, "UNAVAILABLE must never be classified as AVAILABLE by substring"
assert 'status == "AVAILABLE"' in source, "capability success tone must require exact AVAILABLE status"
print("ENTERPRISE_CAPABILITY_STATUS_TONE_CONTRACT: PASS")
