package io.dpcaio.core.model

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}
private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }
private fun assertFalse(value: Boolean, message: String) { if (value) error(message) }

fun main() {
    val base = ManagementContext(
        apiLevel = 37,
        ownership = OwnershipMode.DEVICE_OWNER,
        organizationOwnedProfile = false,
        samsungDevice = false,
        knoxAvailable = false,
        knoxLicenseActive = false,
        buildTrack = BuildTrack.ENTERPRISE_DEBUG,
        showHidden = false,
        developerMode = false,
        showExperimental = false,
    )

    val usb = CapabilityResolver.resolve(
        CapabilityRequirements(minApi = 31, ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE),
        base.copy(apiLevel = 31),
    )
    assertEquals(CapabilityAvailability.AVAILABLE, usb.availability, "USB availability")
    assertTrue(usb.visible, "USB should be visible")
    assertTrue(usb.executable, "USB should be executable")

    val apiHidden = CapabilityResolver.resolve(
        CapabilityRequirements(minApi = 36, visibility = VisibilityClass.HIDDEN),
        base.copy(apiLevel = 35, showHidden = true),
    )
    assertEquals(CapabilityAvailability.API_UNAVAILABLE, apiHidden.availability, "API floor")
    assertTrue(apiHidden.visible, "showHidden should reveal unavailable hidden feature")
    assertFalse(apiHidden.executable, "unsupported API cannot execute")

    val labReq = CapabilityRequirements(visibility = VisibilityClass.LAB)
    assertFalse(CapabilityResolver.resolve(labReq, base.copy(showHidden = true)).visible,
        "showHidden alone must not reveal LAB")
    val lab = CapabilityResolver.resolve(labReq, base.copy(developerMode = true))
    assertEquals(CapabilityAvailability.LAB_ONLY, lab.availability, "lab availability")
    assertTrue(lab.visible, "developer mode reveals lab")
    assertTrue(lab.executable, "developer mode permits lab action when hard requirements pass")

    val profileOnly = CapabilityResolver.resolve(
        CapabilityRequirements(ownership = OwnershipRequirement.PROFILE_OWNER),
        base,
    )
    assertEquals(CapabilityAvailability.PROFILE_OWNER_ONLY, profileOnly.availability, "profile-only gate")

    val orgOwned = CapabilityResolver.resolve(
        CapabilityRequirements(ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE),
        base.copy(ownership = OwnershipMode.PROFILE_OWNER, organizationOwnedProfile = true),
    )
    assertEquals(CapabilityAvailability.AVAILABLE, orgOwned.availability, "org-owned profile satisfies device-wide gate")

    val samsung = CapabilityResolver.resolve(
        CapabilityRequirements(requiresSamsung = true),
        base,
    )
    assertEquals(CapabilityAvailability.SAMSUNG_ONLY, samsung.availability, "Samsung gate")

    val knox = CapabilityResolver.resolve(
        CapabilityRequirements(requiresKnoxLicense = true),
        base.copy(samsungDevice = true, knoxAvailable = true, knoxLicenseActive = false),
    )
    assertEquals(CapabilityAvailability.KNOX_LICENSE_REQUIRED, knox.availability, "Knox license gate")

    val affiliation = CapabilityResolver.resolve(
        CapabilityRequirements(requiresAffiliation = true, visibility = VisibilityClass.HIDDEN),
        base.copy(showHidden = true, affiliatedUser = false),
    )
    assertEquals(CapabilityAvailability.AFFILIATION_REQUIRED, affiliation.availability, "affiliation gate")
    assertTrue(affiliation.visible, "showHidden reveals affiliation-gated capability")
    assertFalse(affiliation.executable, "unaffiliated capability cannot execute")

    val experimental = CapabilityResolver.resolve(
        CapabilityRequirements(visibility = VisibilityClass.EXPERIMENTAL),
        base.copy(showExperimental = true),
    )
    assertEquals(CapabilityAvailability.EXPERIMENTAL, experimental.availability, "experimental label")
    assertTrue(experimental.visible, "experimental visible when enabled")
    assertTrue(experimental.executable, "experimental may execute when hard requirements pass")

    println("CapabilityResolverTest: PASS")
}
