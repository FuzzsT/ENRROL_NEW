#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = ROOT / "apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/parity/AndroidAppParityGateway.kt"
ROUTER = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/TestDpcParityActionRouter.kt"
CATALOG = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/TestDpcParityCatalog.kt"

assert GATEWAY.is_file(), "Task 7 AndroidAppParityGateway.kt missing"
gateway = GATEWAY.read_text("utf-8")
for token in [
    "class AndroidAppParityGateway",
    "fun enableSystemApp(packageName: String): PolicyResult<Unit>",
    "fun enableSystemAppsByIntent(intent: Intent): PolicyResult<Int>",
    "fun installExistingPackage(packageName: String): PolicyResult<String>",
    "fun setKeepUninstalledPackages(packages: List<String>): PolicyResult<Unit>",
    "fun setMeteredDataDisabledPackages(packages: List<String>): PolicyResult<List<String>>",
    "fun uninstallPackage(packageName: String): PolicyResult<Unit>",
    "fun setApplicationRestrictionsManagingPackage(packageName: String?): PolicyResult<Unit>",
    "enableSystemApp(admin, packageName)",
    "enableSystemApp(admin, intent)",
    "installExistingPackage(admin, packageName)",
    "setKeepUninstalledPackages(admin, packages)",
    "setMeteredDataDisabledPackages(admin, packages)",
    "setApplicationRestrictionsManagingPackage(admin, packageName)",
    "packageInstaller.uninstall",
    "SecurityException",
    "PolicyStatus.UNSUPPORTED",
]:
    assert token in gateway, f"AndroidAppParityGateway missing {token}"

router = ROUTER.read_text("utf-8")
assert "DevicePolicyManager" not in router, "Task 7 router must not call DPM directly"
for token in [
    "AndroidAppParityGateway",
    "AndroidDevicePolicyGateway",
    "Intent.parseUri",
    '"app.enable_system_package" to ParityActionHandler',
    '"app.enable_system_intent" to ParityActionHandler',
    '"app.install_existing" to ParityActionHandler',
    '"app.uninstall" to ParityActionHandler',
    '"app.hide" to ParityActionHandler',
    '"app.unhide" to ParityActionHandler',
    '"app.suspend" to ParityActionHandler',
    '"app.unsuspend" to ParityActionHandler',
    '"app.clear_data" to ParityActionHandler',
    '"app.keep_uninstalled" to ParityActionHandler',
    '"app.managed_configurations" to ParityActionHandler',
    '"app.disable_metered_data" to ParityActionHandler',
    '"app.restrictions_manager" to ParityActionHandler',
    '"delegation.set_scopes" to ParityActionHandler',
    '"app.block_uninstall" to ParityActionHandler',
    '"app.block_uninstall_list" to ParityActionHandler',
    "setApplicationHidden(",
    "setPackagesSuspended(",
    "clearManagedApplicationData(",
    "setManagedApplicationRestrictions(",
    "setDelegatedScopes(",
    "setUninstallBlockedPolicy(",
]:
    assert token in router, f"Task 7 router missing {token}"

catalog = CATALOG.read_text("utf-8")

def entry_line(key: str) -> str:
    needle = f'testDpcKey = "{key}"'
    matches = [line for line in catalog.splitlines() if needle in line]
    assert len(matches) == 1, f"expected exactly one catalog line for {key}, got {len(matches)}"
    return matches[0]

handler_specs = {
    "enable_system_apps_by_package_name": ("app.enable_system_package", ["package_name"]),
    "enable_system_apps_by_intent": ("app.enable_system_intent", ["intent_uri"]),
    "install_existing_packages": ("app.install_existing", ["package_name"]),
    "uninstall_package": ("app.uninstall", ["package_name"]),
    "hide_apps": ("app.hide", ["package_name"]),
    "unhide_apps": ("app.unhide", ["package_name"]),
    "suspend_apps": ("app.suspend", ["packages"]),
    "unsuspend_apps": ("app.unsuspend", ["packages"]),
    "clear_app_data": ("app.clear_data", ["package_name"]),
    "keep_uninstalled_packages": ("app.keep_uninstalled", ["packages"]),
    "managed_configurations": ("app.managed_configurations", ["package_name", "restrictions"]),
    "disable_metered_data": ("app.disable_metered_data", ["packages"]),
    "app_restrictions_managing_package": ("app.restrictions_manager", ["package_name"]),
    "generic_delegation": ("delegation.set_scopes", ["package_name", "scopes"]),
    "block_uninstallation_by_pkg": ("app.block_uninstall", ["package_name", "blocked"]),
    "block_uninstallation_list": ("app.block_uninstall_list", ["packages", "blocked"]),
}
for key, (handler, inputs) in handler_specs.items():
    line = entry_line(key)
    assert f'handlerId = "{handler}"' in line, f"{key} missing handler {handler}"
    for field in inputs:
        assert f'key = "{field}"' in line, f"{key} missing input {field}"

for key in ["uninstall_package", "clear_app_data"]:
    assert "destructive = true" in entry_line(key), f"{key} must require destructive confirmation"

for key in ["hide_apps_parent", "unhide_apps_parent", "app_feedback_notifications"]:
    line = entry_line(key)
    assert "IMPLEMENT_PUBLIC_API" not in line, f"{key} must be truthfully reclassified in Task 7"
    assert ("MODERN_EQUIVALENT" in line or "DEPRECATED_UNAVAILABLE" in line), f"{key} must be modern-equivalent or unavailable"
    assert ("unavailableReason" in line or "replacementGuidance" in line), f"{key} needs a precise reason/guidance"

# Already-supported account/cert/installer surfaces stay truthful instead of being duplicated.
assert "EXPOSE_BACKEND" in entry_line("set_disable_account_management")
assert "EXPOSE_BACKEND" in entry_line("get_disable_account_management")
assert "GOOGLE_ACCOUNT_MANAGER" in entry_line("add_account")
assert "MODERN_EQUIVALENT" in entry_line("remove_account")
assert "MODERN_EQUIVALENT" in entry_line("install_apk_package")
assert "MODERN_EQUIVALENT" in entry_line("manage_cert_installer")

print("test_130_app_package_parity_contract: PASS")
