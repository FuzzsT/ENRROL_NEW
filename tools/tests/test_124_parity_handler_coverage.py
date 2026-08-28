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

# Task 6: source-verified BACKEND_ONLY entries must now delegate through existing gateways.
assert "HANDLER_NOT_BOUND" not in router, "Task 6 must remove placeholder backend bindings"
assert "notBound(" not in router, "Task 6 must remove placeholder notBound handlers"
assert "DevicePolicyManager" not in router, "router must delegate through gateways instead of calling DPM directly"
for token in [
    "AndroidWorkProfileLifecycleGateway",
    "AlwaysOnVpnController",
    "AndroidGlobalLocationPolicyGateway",
    "AndroidAccountReorderGateway",
    "AndroidCredentialRecoveryGateway",
    "AndroidDevicePolicyGateway",
    "setProfileName(",
    "upsertCrossProfileRule(",
    "clearDpcRules(",
    "setGoogleAccountManagementDisabled(",
    "isAccountManagementDisabled(",
    "resetCredential(",
    "removeManagedKeyPair(",
]:
    assert token in router, f"router missing Task 6 gateway binding marker {token}"

# Catalog inputs required by the approved Task 6 plan.
def entry_line(key: str) -> str:
    needle = f'testDpcKey = "{key}"'
    matches = [line for line in catalog.splitlines() if needle in line]
    assert len(matches) == 1, f"expected one catalog line for {key}, got {len(matches)}"
    return matches[0]

expected_inputs = {
    "set_profile_name": ["name"],
    "add_cross_profile_intent_filter": ["action", "categories", "direction"],
    "clear_cross_profile_intent_filters": [],
    "set_always_on_vpn": ["package_name", "lockdown"],
    "set_location_enabled": ["enabled"],
    "set_disable_account_management": ["disabled"],
    "get_disable_account_management": [],
    "reset_password": ["token", "new_credential"],
    "remove_key_certificate": ["alias"],
}
for key, fields in expected_inputs.items():
    line = entry_line(key)
    for field in fields:
        assert f'key = "{field}"' in line, f"{key} missing Task 6 input {field}"

location_mode = entry_line("set_location_mode")
assert "MODERN_EQUIVALENT" in location_mode, "set_location_mode must stay MODERN_EQUIVALENT"
assert "replacementGuidance" in location_mode, "set_location_mode must explain the current replacement"

reset_line = entry_line("reset_password")
assert "destructive = true" in reset_line, "reset_password must require destructive confirmation"
remove_key_line = entry_line("remove_key_certificate")
assert "destructive = true" in remove_key_line, "remove_key_certificate must require destructive confirmation"

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
