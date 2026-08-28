#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/TestDpcParityCatalog.kt"
ROUTER = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/TestDpcParityActionRouter.kt"
DETAIL = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/TestDpcParityDetailActivity.kt"
CONTRACTS = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/ParityActionContracts.kt"

catalog = CATALOG.read_text("utf-8")
handler_ids = re.findall(r'handlerId\s*=\s*"([^"]+)"', catalog)
assert handler_ids, "catalog must expose handler-backed parity entries"
assert len(handler_ids) == len(set(handler_ids)), "duplicate handlerId in parity catalog"

for line in catalog.splitlines():
    if "DEPRECATED_UNAVAILABLE" in line:
        assert "handlerId =" not in line, "deprecated parity entry must not expose an executable handler"

assert CONTRACTS.is_file(), "ParityActionContracts.kt missing"
contracts = CONTRACTS.read_text("utf-8")
for token in ["ParityActionRequest", "ParityActionResult", "ParityActionHandler", "PolicyResult<String>"]:
    assert token in contracts, f"action contracts missing {token}"

assert ROUTER.is_file(), "TestDpcParityActionRouter.kt missing"
router = ROUTER.read_text("utf-8")
registered = set(re.findall(r'"([^"]+)"\s+to\s+ParityActionHandler', router))
assert set(handler_ids) == registered, (
    f"handler registry mismatch: missing={sorted(set(handler_ids)-registered)} "
    f"extra={sorted(registered-set(handler_ids))}"
)
for token in [
    "PolicyStatus.UNSUPPORTED",
    "UNKNOWN_HANDLER",
    "SecurityException",
    "UnsupportedOperationException",
    "ParityActionResult",
]:
    assert token in router, f"router missing fail-closed marker {token}"

assert DETAIL.is_file(), "TestDpcParityDetailActivity.kt missing"
detail = DETAIL.read_text("utf-8")
for token in [
    "TestDpcParityActionRouter",
    "AlertDialog.Builder",
    "entry.destructive",
    "entry.googleTitle",
    "setPositiveButton",
    "Execute",
]:
    assert token in detail, f"detail activity missing routing/confirmation marker {token}"
assert "DevicePolicyManager" not in detail, "detail activity must not execute DPM directly"

print("test_124_parity_handler_coverage: PASS")
