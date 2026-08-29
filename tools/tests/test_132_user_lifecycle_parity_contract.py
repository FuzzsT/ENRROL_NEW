#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = ROOT / "apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/parity/AndroidUserParityGateway.kt"
CATALOG = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/TestDpcParityCatalog.kt"
ROUTER = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/TestDpcParityActionRouter.kt"
RUNNER = ROOT / "tools/run_host_tests.sh"

assert GATEWAY.is_file(), "AndroidUserParityGateway.kt missing"
assert CATALOG.is_file(), "TestDpcParityCatalog.kt missing"
assert ROUTER.is_file(), "TestDpcParityActionRouter.kt missing"

gateway = GATEWAY.read_text("utf-8")
catalog = CATALOG.read_text("utf-8")
router = ROUTER.read_text("utf-8")
runner = RUNNER.read_text("utf-8")

for token in [
    "class AndroidUserParityGateway",
    "createAndManageUser",
    "removeUser",
    "switchUser",
    "startUserInBackground",
    "stopUser",
    "logoutUser",
    "setLogoutEnabled",
    "setUserSessionMessages",
    "setUserRestriction",
    "setShortSupportMessage",
    "setLongSupportMessage",
    "isAffiliatedUser",
    "isEphemeralUser",
    "requestBugreport",
    "setBackupServiceEnabled",
    "setCommonCriteriaModeEnabled",
    "reboot",
    "wipeManagedProfile",
    "factoryResetDevice",
    "transferOwnership",
    "getUserForSerialNumber",
    "USER_NOT_FOUND",
]:
    assert token in gateway, token

for forbidden in [
    "Class.forName",
    "getDeclaredMethod",
    "ServiceManager",
    "IBinder.transact",
    "TRANSACTION_",
    "setHiddenApiExemptions",
    "clearDeviceOwnerApp(",
]:
    assert forbidden not in gateway, f"non-public parity transport present: {forbidden}"

handler_ids = [
    "user.create",
    "user.remove",
    "user.switch",
    "user.start_background",
    "user.stop",
    "user.logout",
    "user.logout_enabled",
    "user.session_messages",
    "user.is_affiliated",
    "user.is_ephemeral",
    "user.restriction",
    "user.restriction_parent",
    "user.short_support",
    "user.long_support",
    "device.request_bugreport",
    "device.backup_service",
    "device.common_criteria",
    "device.reboot",
    "device.wipe_profile",
    "device.factory_reset",
    "device.transfer_ownership",
]
for handler in handler_ids:
    assert f'handlerId = "{handler}"' in catalog, f"catalog missing {handler}"
    assert f'"{handler}" to ParityActionHandler' in router, f"router missing {handler}"

for key in [
    "remove_user",
    "stop_user",
    "remove_managed_profile",
    "factory_reset_device",
    "reboot",
    "transfer_ownership_to_component",
]:
    marker = f'testDpcKey = "{key}"'
    start = catalog.index(marker)
    line = catalog[start:catalog.find("\n", start)]
    assert "destructive = true" in line, f"{key} must require destructive confirmation"

remove_owner = catalog[catalog.index('testDpcKey = "remove_device_owner"'):]
remove_owner = remove_owner[:remove_owner.find("\n")]
assert "TestDpcImplementationState.DEPRECATED_UNAVAILABLE" in remove_owner
assert "clearDeviceOwnerApp" in remove_owner

managed_profile = catalog[catalog.index('testDpcKey = "create_managed_profile"'):]
managed_profile = managed_profile[:managed_profile.find("\n")]
assert "TestDpcImplementationState.MODERN_EQUIVALENT" in managed_profile
assert "ParityDestination.WORK_PROFILE_COPE" in managed_profile

assert "test_132_user_lifecycle_parity_contract.py" in runner
print("USER_LIFECYCLE_PARITY_CONTRACT_132_OK")
