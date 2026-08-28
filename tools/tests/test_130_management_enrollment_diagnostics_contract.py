#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app"

store = (APP / "EnrollmentSessionStore.kt").read_text("utf-8")
enrollment = (APP / "EnrollmentDiagnosticsSnapshot.kt").read_text("utf-8")
enrollment_ui = (APP / "EnrollmentStatusActivity.kt").read_text("utf-8")
dpc = (APP / "DpcDiagnosticsSnapshot.kt").read_text("utf-8")
dpc_ui = (APP / "DpcDiagnosticsActivity.kt").read_text("utf-8")

# Session persistence must distinguish an absent record from a record that exists but cannot be decoded.
for marker in [
    "sealed interface EnrollmentSessionReadResult",
    "data object Absent",
    "data class Present",
    "data class Corrupt",
    "fun readResult()",
]:
    assert marker in store, f"session store missing explicit read state: {marker}"

# Enrollment diagnostics must explain why the exported snapshot is empty and whether Android ownership exists.
for marker in [
    "sessionState",
    "managementState",
    "dpcVersion",
    "getProvisioningModeHandlerReady",
    "policyComplianceHandlerReady",
    "platformProvisioningHandlersReady",
    "recommendedAction",
    'put("sessionState"',
    'put("managementState"',
    'put("platformProvisioningHandlersReady"',
]:
    assert marker in enrollment, f"enrollment diagnostics missing {marker}"

# Status UI must surface the diagnosis, not only a null session message.
for marker in [
    "Session state:",
    "Management state:",
    "Provisioning handlers:",
    "Recommended action:",
]:
    assert marker in enrollment_ui, f"enrollment status UI missing {marker}"
assert "No enrollment session" not in enrollment_ui, "ambiguous legacy no-session message still present"

# DPC diagnostics must distinguish module-surface executability from managed-policy readiness.
for marker in [
    "managementState",
    "ownerPolicyReady",
    "moduleAvailabilitySemantics",
    'put("managementState"',
    'put("ownerPolicyReady"',
    'put("moduleAvailabilitySemantics"',
]:
    assert marker in dpc, f"DPC diagnostics missing {marker}"
for marker in [
    "Management state:",
    "Owner policy ready:",
    "Module surfaces executable:",
    "Module surfaces blocked:",
]:
    assert marker in dpc_ui, f"DPC diagnostics UI missing {marker}"
assert "Modules available:" not in dpc_ui, "ambiguous module availability label still present"

# Diagnostic export must remain secret-safe.
for text in (enrollment, dpc):
    for forbidden in ["enrollmentToken\")", "password\")", "kpeKey\")", "authHeader\")"]:
        assert forbidden not in text, f"diagnostics export includes secret field marker: {forbidden}"

print("test_130_management_enrollment_diagnostics_contract: PASS")
