package io.dpcaio.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.dpcaio.core.model.OwnershipMode
import io.dpcaio.knox.license.KnoxPublicCapability

class KnoxEnterpriseCenterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Samsung Enterprise Center"
        render()
    }

    private fun render() {
        val management = ManagementContextFactory.create(this)
        val ownerSatisfied = management.ownership == OwnershipMode.DEVICE_OWNER || management.ownership == OwnershipMode.PROFILE_OWNER
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Capability Matrix"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Samsung=${management.samsungDevice} • Knox=${management.knoxAvailable} • License=${management.knoxLicenseActive}"
        })

        fun row(
            name: String,
            route: String,
            owner: String,
            license: String,
            permission: String,
            readback: String,
            state: String,
        ) {
            root.addView(TextView(this).apply {
                text = buildString {
                    append(name).append('\n')
                    append("Route=").append(route)
                    append(" • Owner=").append(owner)
                    append(" • License=").append(license)
                    append(" • Permission=").append(permission)
                    append(" • Readback=").append(readback)
                    append('\n').append(state).append('\n')
                }
            })
        }

        row(
            name = "Android Enterprise",
            route = "ANDROID_DPM",
            owner = if (ownerSatisfied) "SATISFIED" else "REQUIRED",
            license = "N/A",
            permission = "DPM",
            readback = "REQUIRED",
            state = if (ownerSatisfied) "AVAILABLE" else "OWNER_REQUIRED",
        )

        row(
            name = "Knox KPE / License",
            route = "KNOX_OFFICIAL",
            owner = if (ownerSatisfied) "SATISFIED" else "REQUIRED",
            license = KnoxRuntimeStateStore.realLicenseStateName(this),
            permission = "KPE/KLM_CAPABILITY_DEPENDENT",
            readback = "RUNTIME_STATE_STORE",
            state = when {
                !management.samsungDevice -> "UNSUPPORTED_DEVICE"
                management.knoxLicenseActive -> "ACTIVE"
                else -> "LICENSE_REQUIRED"
            },
        )

        val labels = mapOf(
            KnoxPublicCapability.APPLICATION_POLICY to "ApplicationPolicy",
            KnoxPublicCapability.CERTIFICATE_POLICY to "CertificatePolicy",
            KnoxPublicCapability.KIOSK_MODE to "Kiosk",
            KnoxPublicCapability.FIREWALL_VPN to "Firewall / VPN",
            KnoxPublicCapability.ENHANCED_ATTESTATION to "Enhanced Attestation",
            KnoxPublicCapability.KNOX_AUDIT_LOG to "KNOX_AUDIT_LOG",
        )
        KnoxPublicCapability.entries.forEach { cap ->
            val state = when {
                cap == KnoxPublicCapability.KNOX_AUDIT_LOG -> "DEPRECATED_PLATFORM_API"
                !management.samsungDevice -> "UNSUPPORTED_DEVICE"
                !management.knoxLicenseActive -> "LICENSE_REQUIRED"
                else -> cap.defaultState.name
            }
            row(
                name = labels.getValue(cap),
                route = "KNOX_OFFICIAL",
                owner = if (ownerSatisfied) "SATISFIED" else "REQUIRED",
                license = if (management.knoxLicenseActive) "ACTIVE" else "REQUIRED",
                permission = "CAPABILITY_DEPENDENT",
                readback = if (cap.executable) "REQUIRED" else "UNAVAILABLE",
                state = state,
            )
        }

        row(
            name = "SEM Explorer",
            route = "SAMSUNG_SEM",
            owner = "CAPABILITY_DEPENDENT",
            license = "N/A",
            permission = "PROBED",
            readback = "PROBED",
            state = if (management.samsungDevice) "READ_ONLY_DEFAULT" else "UNSUPPORTED_DEVICE",
        )
        row(
            name = "OEM Internals Lab",
            route = "OEM_INTERNAL",
            owner = "NATURAL_CALLER_CONTEXT",
            license = "N/A",
            permission = "PROBED",
            readback = "BOUNDED",
            state = "LAB_ONLY • Firmware-specific",
        )

        root.addView(TextView(this).apply {
            text = "High-impact mutation flow: Preview → ALLOW_WITH_CONFIRMATION → Apply → Readback → Commit/Rollback"
        })
        root.addView(TextView(this).apply {
            text = "No hidden-API exemptions, guessed Binder transactions, signature-permission bypass, root/su fallback, forged Knox state, or private KPE material."
        })
        setContentView(DpcUiShell.scroll(this, root))
    }
}
