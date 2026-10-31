package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.os.Process
import android.widget.LinearLayout
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.policy.CopePolicyValidator
import io.dpcaio.policy.ManagedProfilePackagePolicySpec
import io.dpcaio.policy.PackageAccessPolicyType
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class WorkProfileCopeActivity : Activity() {
    private lateinit var gateway: AndroidDevicePolicyGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Work Profile / COPE"
        gateway = AndroidDevicePolicyGateway(this, ComponentName(this, AioDeviceAdminReceiver::class.java))
        render()
    }

    private fun render() {
        val root = ModernUi.page(this)
        val cap = CapabilityResolver.resolve(
            CapabilityRequirements(minApi = 30, ownership = OwnershipRequirement.PROFILE_OWNER),
            ManagementContextFactory.create(this),
        )

        root.addView(
            ModernUi.hero(
                this,
                title = "Work Profile / COPE",
                subtitle = "Cross-profile access, personal apps and organization identity",
                eyebrow = cap.availability.name,
            )
        )

        root.addView(ModernUi.section(this, "Current state"))
        root.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.body(this@WorkProfileCopeActivity, "Current: ${gateway.getCopePolicySnapshot()}"))
                cap.reason?.let { addView(ModernUi.chip(this@WorkProfileCopeActivity, it, ModernUi.Tone.WARNING)) }
            }
        )

        root.addView(ModernUi.section(this, "Cross-profile packages"))
        val packages = ModernUi.field(this, "Cross-profile packages, comma-separated")
        root.addView(
            ModernUi.card(this).apply {
                addView(packages)
                addView(
                    ModernUi.action(this@WorkProfileCopeActivity, "Choose cross-profile packages") {
                        RuntimeTargetPicker.showPackageMultiPicker(
                            this@WorkProfileCopeActivity,
                            userId = AndroidUserId.fromUid(Process.myUid()),
                            currentPackages = csv(packages.text.toString()),
                            title = "Choose cross-profile packages",
                        ) { selected -> packages.setText(selected.joinToString(", ")) }
                    }
                )
                addView(
                    action("Apply Cross-profile packages", cap.executable) {
                        show(gateway.setCrossProfilePackagesPolicy(csv(packages.text.toString())).toString())
                    }
                )
            }
        )

        root.addView(ModernUi.section(this, "Contacts access"))
        val contactsPackages = ModernUi.field(this, "Contacts/caller-ID package list, comma-separated")
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@WorkProfileCopeActivity, "Android 14+ PackagePolicy controls for managed-profile contacts access."))
                addView(contactsPackages)
                addView(
                    ModernUi.action(this@WorkProfileCopeActivity, "Choose contacts/caller-ID packages") {
                        RuntimeTargetPicker.showPackageMultiPicker(
                            this@WorkProfileCopeActivity,
                            userId = AndroidUserId.fromUid(Process.myUid()),
                            currentPackages = csv(contactsPackages.text.toString()),
                            title = "Choose contacts/caller-ID packages",
                        ) { selected -> contactsPackages.setText(selected.joinToString(", ")) }
                    }
                )
                addView(action("Contacts access: Allowlist + system", cap.executable) {
                    show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM, csv(contactsPackages.text.toString()))).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 7))
                addView(action("Contacts access: Blocklist", cap.executable) {
                    show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.BLOCKLIST, csv(contactsPackages.text.toString()))).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 7))
                addView(action("Contacts access: Unrestricted", cap.executable) {
                    show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.UNRESTRICTED)).toString())
                })
            }
        )

        root.addView(ModernUi.section(this, "Caller ID access"))
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@WorkProfileCopeActivity, "Android 14+ PackagePolicy controls for caller ID access."))
                addView(action("Caller ID access: Allowlist + system", cap.executable) {
                    show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM, csv(contactsPackages.text.toString()))).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 7))
                addView(action("Caller ID access: Blocklist", cap.executable) {
                    show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.BLOCKLIST, csv(contactsPackages.text.toString()))).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 7))
                addView(action("Caller ID access: Unrestricted", cap.executable) {
                    show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.UNRESTRICTED)).toString())
                })
            }
        )

        root.addView(ModernUi.section(this, "Profile availability"))
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.body(this@WorkProfileCopeActivity, "Maximum time off: minimum non-zero 72 hours"))
                addView(action("Set Maximum time off = 72 h", cap.executable) {
                    show(gateway.setManagedProfileMaximumTimeOffPolicy(CopePolicyValidator.MIN_TIME_OFF_MILLIS).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 7))
                addView(
                    ModernUi.actionRow(
                        this@WorkProfileCopeActivity,
                        action("Personal apps: Suspend", cap.executable) { show(gateway.setPersonalAppsSuspendedPolicy(true).toString()) },
                        action("Personal apps: Unsuspend", cap.executable) { show(gateway.setPersonalAppsSuspendedPolicy(false).toString()) },
                    )
                )
            }
        )

        root.addView(ModernUi.section(this, "Organization identity"))
        val org = ModernUi.field(this, "Organization ID (6..64)")
        val name = ModernUi.field(this, "Organization name")
        val aff = ModernUi.field(this, "Affiliation IDs, comma-separated")
        root.addView(
            ModernUi.card(this).apply {
                addView(org)
                addView(name)
                addView(action("Apply Organization identity", cap.executable) {
                    show(gateway.setOrganizationIdentity(org.text.toString(), name.text.toString()).toString())
                })
                addView(ModernUi.spacer(this@WorkProfileCopeActivity, 10))
                addView(aff)
                addView(action("Apply Affiliation IDs", cap.executable) {
                    show(gateway.setAffiliationIdsPolicy(csv(aff.text.toString())).toString())
                })
            }
        )

        setContentView(ModernUi.scrollPage(this, root))
    }

    private fun csv(s: String) = s.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun action(label: String, enabled: Boolean, action: () -> Unit) =
        ModernUi.action(this, label, onClick = action).apply { isEnabled = enabled }

    private fun show(msg: String) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()
    }
}
