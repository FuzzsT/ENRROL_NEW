#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = ROOT / "apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/parity/AndroidNetworkParityGateway.kt"
ROUTER = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/TestDpcParityActionRouter.kt"
CATALOG = ROOT / "apps/dpc/modules/policy/core/src/main/kotlin/io/dpcaio/policy/parity/TestDpcParityCatalog.kt"

assert GATEWAY.exists(), "AndroidNetworkParityGateway.kt missing"
gateway = GATEWAY.read_text("utf-8")
router = ROUTER.read_text("utf-8")
catalog = CATALOG.read_text("utf-8")

# Public Android networking/Wi-Fi APIs only; no Settings.Global or reflection fallbacks.
for token in [
    "class AndroidNetworkParityGateway",
    "setRecommendedGlobalProxy",
    "setPreferentialNetworkServiceEnabled",
    "setConfiguredNetworksLockdownState",
    "removeNonCallerConfiguredNetworks",
    "getWifiMacAddress",
    "setMinimumRequiredWifiSecurityLevel",
    "setWifiSsidPolicy",
    "WifiSsidPolicy",
    "WifiSsid.fromBytes",
]:
    assert token in gateway, token
for forbidden in ["Settings.Global", "java.lang.reflect", "getDeclaredMethod", "Class.forName"]:
    assert forbidden not in gateway, forbidden

# Router must reuse typed network gateway plus existing controllers, not call DPM directly.
for token in [
    "AndroidNetworkParityGateway",
    "AlwaysOnVpnController",
    '"network.always_on_vpn"',
    '"network.preferential"',
    '"network.set_global_proxy"',
    '"network.clear_global_proxy"',
    '"network.wifi_lockdown"',
    '"network.wifi_remove_non_caller"',
    '"network.wifi_mac"',
    '"network.wifi_min_security"',
    '"network.wifi_ssid_policy"',
]:
    assert token in router, token
assert "DevicePolicyManager" not in router

# Networking classifications from the pinned parity design.
def entry_line(key: str) -> str:
    matches = [line for line in catalog.splitlines() if f'testDpcKey = "{key}"' in line]
    assert len(matches) == 1, (key, len(matches))
    return matches[0]

assert "MODERN_EQUIVALENT" in entry_line("network_stats")
assert 'handlerId = "network.always_on_vpn"' in entry_line("set_always_on_vpn")
assert 'handlerId = "network.preferential"' in entry_line("set_get_preferential_network_service_status")
assert "MODERN_EQUIVALENT" in entry_line("enterprise_slice")
assert 'handlerId = "network.set_global_proxy"' in entry_line("set_global_http_proxy")
assert 'handlerId = "network.clear_global_proxy"' in entry_line("clear_global_http_proxy")
assert "ParityDestination.NETWORK_CONTROL" in entry_line("set_private_dns_mode")

wifi_keys = [
    "create_wifi_configuration",
    "create_eap_tls_wifi_configuration",
    "enable_wifi_config_lockdown",
    "modify_wifi_configuration",
    "modify_owned_wifi_configuration",
    "remove_not_owned_wifi_configurations",
    "show_wifi_mac_address",
    "set_wifi_min_security_level",
    "set_wifi_ssid_restriction",
]
for key in wifi_keys:
    line = entry_line(key)
    assert "PlatformFeature.WIFI" in line, key

# Legacy mutable Wi-Fi config actions stay discoverable as modern equivalents.
for key in [
    "create_wifi_configuration",
    "create_eap_tls_wifi_configuration",
    "modify_wifi_configuration",
    "modify_owned_wifi_configuration",
]:
    line = entry_line(key)
    assert "MODERN_EQUIVALENT" in line, key
    assert "replacementGuidance" in line, key

# Lockdown now uses the supported API introduced in API 30, not legacy Settings.Global.
lockdown = entry_line("enable_wifi_config_lockdown")
assert 'handlerId = "network.wifi_lockdown"' in lockdown
assert "minSdk = 30" in lockdown
assert "MODERN_EQUIVALENT" in lockdown

assert 'handlerId = "network.wifi_remove_non_caller"' in entry_line("remove_not_owned_wifi_configurations")
assert "minSdk = 31" in entry_line("remove_not_owned_wifi_configurations")
assert 'handlerId = "network.wifi_mac"' in entry_line("show_wifi_mac_address")
assert "minSdk = 24" in entry_line("show_wifi_mac_address")
assert 'handlerId = "network.wifi_min_security"' in entry_line("set_wifi_min_security_level")
assert "minSdk = 33" in entry_line("set_wifi_min_security_level")
assert 'handlerId = "network.wifi_ssid_policy"' in entry_line("set_wifi_ssid_restriction")
assert "minSdk = 33" in entry_line("set_wifi_ssid_restriction")

# Input metadata must be explicit for executable actions.
for key, expected in {
    "set_get_preferential_network_service_status": ["enabled"],
    "set_global_http_proxy": ["host", "port", "exclusion_list"],
    "enable_wifi_config_lockdown": ["lockdown"],
    "set_wifi_min_security_level": ["level"],
    "set_wifi_ssid_restriction": ["policy_type", "ssids"],
}.items():
    line = entry_line(key)
    for field in expected:
        assert f'key = "{field}"' in line, (key, field)

print("NETWORK_WIFI_PARITY_CONTRACT_131_OK")
