package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.CapabilityResolver
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.policy.CopePolicyValidator
import io.dpcaio.policy.ManagedProfilePackagePolicySpec
import io.dpcaio.policy.PackageAccessPolicyType
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

class WorkProfileCopeActivity : Activity() {
    private lateinit var gateway:AndroidDevicePolicyGateway
    override fun onCreate(savedInstanceState: Bundle?){ super.onCreate(savedInstanceState); title="Work Profile / COPE"; gateway=AndroidDevicePolicyGateway(this,ComponentName(this,AioDeviceAdminReceiver::class.java)); render() }
    private fun render(){
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val cap=CapabilityResolver.resolve(CapabilityRequirements(minApi=30,ownership=OwnershipRequirement.PROFILE_OWNER),ManagementContextFactory.create(this))
        root.addView(TextView(this).apply { text="Work Profile / COPE\n${cap.availability}"; textSize=20f })
        root.addView(TextView(this).apply { text="Current: ${gateway.getCopePolicySnapshot()}" })
        val packages=EditText(this).apply { hint="Cross-profile packages, comma-separated" }; root.addView(packages)
        add(root,"Apply Cross-profile packages",cap.executable){ show(gateway.setCrossProfilePackagesPolicy(csv(packages.text.toString())).toString()) }
        val contactsPackages=EditText(this).apply { hint="Contacts/caller-ID package list, comma-separated" }; root.addView(contactsPackages)
        root.addView(TextView(this).apply { text="Contacts access (Android 14+ PackagePolicy)" })
        add(root,"Contacts access: Allowlist + system",cap.executable){ show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM,csv(contactsPackages.text.toString()))).toString()) }
        add(root,"Contacts access: Blocklist",cap.executable){ show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.BLOCKLIST,csv(contactsPackages.text.toString()))).toString()) }
        add(root,"Contacts access: Unrestricted",cap.executable){ show(gateway.setManagedProfileContactsAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.UNRESTRICTED)).toString()) }
        root.addView(TextView(this).apply { text="Caller ID access (Android 14+ PackagePolicy)" })
        add(root,"Caller ID access: Allowlist + system",cap.executable){ show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM,csv(contactsPackages.text.toString()))).toString()) }
        add(root,"Caller ID access: Blocklist",cap.executable){ show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.BLOCKLIST,csv(contactsPackages.text.toString()))).toString()) }
        add(root,"Caller ID access: Unrestricted",cap.executable){ show(gateway.setManagedProfileCallerIdAccessPolicy(ManagedProfilePackagePolicySpec(PackageAccessPolicyType.UNRESTRICTED)).toString()) }
        root.addView(TextView(this).apply { text="Maximum time off: minimum non-zero 72 hours" })
        add(root,"Set Maximum time off = 72 h",cap.executable){ show(gateway.setManagedProfileMaximumTimeOffPolicy(CopePolicyValidator.MIN_TIME_OFF_MILLIS).toString()) }
        add(root,"Personal apps: Suspend",cap.executable){ show(gateway.setPersonalAppsSuspendedPolicy(true).toString()) }
        add(root,"Personal apps: Unsuspend",cap.executable){ show(gateway.setPersonalAppsSuspendedPolicy(false).toString()) }
        val org=EditText(this).apply { hint="Organization ID (6..64)" }; val name=EditText(this).apply { hint="Organization name" }; root.addView(org); root.addView(name)
        add(root,"Apply Organization identity",cap.executable){ show(gateway.setOrganizationIdentity(org.text.toString(),name.text.toString()).toString()) }
        val aff=EditText(this).apply { hint="Affiliation IDs, comma-separated" }; root.addView(aff)
        add(root,"Apply Affiliation IDs",cap.executable){ show(gateway.setAffiliationIdsPolicy(csv(aff.text.toString())).toString()) }
        setContentView(DpcUiShell.scroll(this, root))
    }
    private fun csv(s:String)=s.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    private fun add(root:LinearLayout,label:String,enabled:Boolean,action:()->Unit){ root.addView(Button(this).apply { text=label; isEnabled=enabled; setOnClickListener{action()} }) }
    private fun show(msg:String){ AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK",null).show() }
}
