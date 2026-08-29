#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "apps/dpc/modules/samsung/core/src/main/kotlin/io/dpcaio/samsung/firmware/SamsungFirmwareProfile.kt"
ANDROID = ROOT / "apps/dpc/modules/samsung/android/src/main/kotlin/io/dpcaio/samsung/firmware/android/AndroidSamsungFirmwareProbe.kt"
SNAPSHOT = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsSnapshot.kt"
ACTIVITY = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsActivity.kt"
RUNNER = ROOT / "tools/run_host_tests.sh"

assert CORE.is_file(), "SamsungFirmwareProfile.kt missing"
assert ANDROID.is_file(), "AndroidSamsungFirmwareProbe.kt missing"
core = CORE.read_text("utf-8")
android = ANDROID.read_text("utf-8")
snapshot = SNAPSHOT.read_text("utf-8")
activity = ACTIVITY.read_text("utf-8")
runner = RUNNER.read_text("utf-8")

for token in [
    "data class SamsungFirmwareProfile",
    "data class SamsungFirmwarePackageProbe",
    "object SamsungFirmwareEvidenceCatalog",
    "com.sec.android.usermanual",
    "com.swiftkey.swiftkeyconfigurator",
    "com.touchtype.swiftkey",
    "com.amazon.appmanager",
    '"ro.csc.sales_code"',
    '"ro.boot.sales_code"',
    '"persist.sys.omc_path"',
    '"ro.omc.build.version"',
]:
    assert token in core, token

for token in [
    "class AndroidSamsungFirmwareProbe",
    'Class.forName("android.os.SystemProperties")',
    "SamsungFirmwareEvidenceCatalog.salesCodePropertyKeys",
    "SamsungFirmwareEvidenceCatalog.omcPathPropertyKeys",
    "SamsungFirmwareEvidenceCatalog.packageEvidence",
    "PackageManager.ApplicationInfoFlags",
    "Build.MANUFACTURER",
]:
    assert token in android, token

for forbidden in [
    "IBinder.transact",
    "ServiceManager",
    "TRANSACTION_",
    "setHiddenApiExemptions",
]:
    assert forbidden not in android, f"unsafe/guessed OEM transport in firmware probe: {forbidden}"

for token in [
    "AndroidSamsungFirmwareProbe",
    "samsungFirmware",
    'put("samsungFirmware"',
    'putNullable("salesCode"',
    'putNullable("multiCsc"',
    'put("observedPackageCount"',
]:
    assert token in snapshot, token

for token in [
    "Samsung firmware profile:",
    "Sales code:",
    "Multi-CSC:",
    "OMC path:",
    "Firmware package probes:",
]:
    assert token in activity, token

assert "test_133_samsung_firmware_diagnostics_contract.py" in runner
print("SAMSUNG_FIRMWARE_DIAGNOSTICS_CONTRACT_133_OK")
