#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "apps/dpc/modules/samsung/core/src/main/kotlin/io/dpcaio/samsung/firmware/SamsungFirmwareProfile.kt"
ANDROID = ROOT / "apps/dpc/modules/samsung/android/src/main/kotlin/io/dpcaio/samsung/firmware/android/AndroidSamsungFirmwareProbe.kt"
SNAPSHOT = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsSnapshot.kt"
ACTIVITY = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsActivity.kt"

core = CORE.read_text("utf-8")
android = ANDROID.read_text("utf-8")
snapshot = SNAPSHOT.read_text("utf-8")
activity = ACTIVITY.read_text("utf-8")

# Typed evidence classes keep carrier/preload/overlay findings distinct.
for token in [
    "enum class SamsungFirmwarePackageClass",
    "CARRIER_PROVISIONING_AGENT",
    "CARRIER_STOREFRONT",
    "SAMSUNG_CONNECTIVITY_OVERLAY",
    "OEM_PRELOAD_MANAGER",
    "val packageClass: SamsungFirmwarePackageClass",
    "val carrierId: String?",
    "val carrierProvisioningPresent: Boolean",
    "val carrierPackageCount: Int",
    "val connectivityOverlayPresent: Boolean",
]:
    assert token in core, token

# Identities are derived from the supplied Samsung/operator APKs.
for package_name in [
    "com.dti.tim",
    "com.dti.telefonica",
    "com.dti.bouyguestelecom",
    "com.dti.aone",
    "de.telekom.tsc",
    "com.sfr.android.sfrjeux.samsung",
    "com.altice.android.myapps.samsung",
    "com.samsung.android.ConnectivityUxOverlay",
]:
    assert package_name in core, package_name

assert '"ro.boot.carrierid"' in core

# Platform packages from the supplied set are deliberately not Samsung/CSC evidence.
for neutral_package in [
    "com.google.mainline.adservices",
    "TrichromeLibrary",
]:
    assert neutral_package not in core, f"neutral platform package leaked into Samsung evidence: {neutral_package}"

for token in [
    "SamsungFirmwareEvidenceCatalog.carrierIdPropertyKeys",
    "SamsungFirmwareEvidenceCatalog.packageEvidence",
    "packageClass = evidence.packageClass",
]:
    assert token in android, token

# Evidence probing stays read-only and must not become an internal-service invocation surface.
for forbidden in [
    "IAppEnablerApi",
    "IBinder.transact",
    "ServiceManager",
    "TRANSACTION_",
    "setHiddenApiExemptions",
]:
    assert forbidden not in android, f"unsafe carrier/OEM invocation in firmware probe: {forbidden}"

for token in [
    'putNullable("carrierId"',
    'put("carrierProvisioningPresent"',
    'put("carrierPackageCount"',
    'put("connectivityOverlayPresent"',
    'put("packageClass"',
]:
    assert token in snapshot, token

for token in [
    "Carrier ID:",
    "Carrier provisioning layer:",
    "Carrier evidence packages:",
    "Samsung connectivity overlay:",
]:
    assert token in activity, token

print("SAMSUNG_CARRIER_EVIDENCE_CONTRACT_134_OK")
