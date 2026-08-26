package io.dpcaio.app

import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.core.model.RiskClass
import io.dpcaio.core.model.VisibilityClass

enum class EnterprisePolicyGroup(val label: String) {
    DEVICE("Device"),
    APPLICATIONS("Applications"),
    NETWORK("Network"),
}

data class EnterprisePolicyDescriptor(
    val id: String,
    val title: String,
    val group: EnterprisePolicyGroup,
    val requirements: CapabilityRequirements,
)

object EnterprisePolicyCatalog {
    val entries = listOf(
        EnterprisePolicyDescriptor(
            id = "usb_data",
            title = "USB data signaling",
            group = EnterprisePolicyGroup.DEVICE,
            requirements = CapabilityRequirements(
                minApi = 31,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.HIGH,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "auto_time",
            title = "Automatic time",
            group = EnterprisePolicyGroup.DEVICE,
            requirements = CapabilityRequirements(
                minApi = 36,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "auto_timezone",
            title = "Automatic timezone",
            group = EnterprisePolicyGroup.DEVICE,
            requirements = CapabilityRequirements(
                minApi = 36,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "thread_network",
            title = "Thread network restriction",
            group = EnterprisePolicyGroup.NETWORK,
            requirements = CapabilityRequirements(
                minApi = 36,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "nfc_radio",
            title = "NFC radio restriction",
            group = EnterprisePolicyGroup.DEVICE,
            requirements = CapabilityRequirements(
                minApi = 35,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "nfc_changes",
            title = "Lock NFC setting changes",
            group = EnterprisePolicyGroup.DEVICE,
            requirements = CapabilityRequirements(
                minApi = 36,
                ownership = OwnershipRequirement.DEVICE_OR_ORG_OWNED_PROFILE,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "app_functions",
            title = "App Functions policy",
            group = EnterprisePolicyGroup.APPLICATIONS,
            requirements = CapabilityRequirements(
                minApi = 36,
                ownership = OwnershipRequirement.DEVICE_OR_PROFILE_OWNER,
                visibility = VisibilityClass.EXPERIMENTAL,
                risk = RiskClass.MEDIUM,
            ),
        ),
        EnterprisePolicyDescriptor(
            id = "local_network_permission",
            title = "Local network runtime permission",
            group = EnterprisePolicyGroup.APPLICATIONS,
            requirements = CapabilityRequirements(
                minApi = 37,
                ownership = OwnershipRequirement.DEVICE_OR_PROFILE_OWNER,
                risk = RiskClass.MEDIUM,
            ),
        ),
    )
}
