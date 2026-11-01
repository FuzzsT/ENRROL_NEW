package io.dpcaio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.dpcaio.core.model.OwnershipMode
import io.dpcaio.knox.license.KnoxPublicCapability
import java.text.DateFormat
import java.util.Date

class KnoxEnterpriseCenterActivity : Activity() {
    private var historyFilter = KnoxLicenseHistoryFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Samsung Enterprise Center"
        render()
    }

    private fun formatHistoryTime(timestampEpochMs: Long?): String =
        timestampEpochMs?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(it))
        } ?: "NONE"

    private fun render() {
        val management = ManagementContextFactory.create(this)
        val ownerSatisfied = management.ownership != OwnershipMode.NONE
        val licenseSnapshot = KnoxLicenseRuntimeStore(this).read()
        val retryScheduled = KnoxLicenseRetryCoordinator(this).isScheduled()
        val activationHistory = KnoxLicenseHistoryStore(this).readRecent(10, historyFilter)
        val historySummary = KnoxLicenseHistoryStore(this).summary()
        val statusCounts = historySummary.statusCounts.entries.joinToString(", ") { "${it.key.name}=${it.value}" }
        val sourceCounts = historySummary.sourceCounts.entries.joinToString(", ") { "${it.key.name}=${it.value}" }
        val root = ModernUi.page(this)

        root.addView(
            ModernUi.hero(
                this,
                title = "Samsung Enterprise Center",
                subtitle = "Capability Matrix across Android Enterprise, Knox, SEM and bounded OEM research surfaces.",
                eyebrow = "SAMSUNG / KNOX",
            )
        )

        root.addView(ModernUi.section(this, "Runtime state"))
        root.addView(
            ModernUi.card(this, emphasized = true).apply {
                addView(ModernUi.title(this@KnoxEnterpriseCenterActivity, "Capability Matrix"))
                addView(
                    ModernUi.body(
                        this@KnoxEnterpriseCenterActivity,
                        "Samsung=${management.samsungDevice} • Knox=${management.knoxAvailable} • License=${management.knoxLicenseActive}",
                    )
                )
                addView(
                    ModernUi.chip(
                        this@KnoxEnterpriseCenterActivity,
                        if (management.knoxLicenseActive) "KNOX ACTIVE" else "KNOX LIMITED",
                        if (management.knoxLicenseActive) ModernUi.Tone.SUCCESS else ModernUi.Tone.WARNING,
                    )
                )
            }
        )

        root.addView(ModernUi.section(this, "KPE license diagnostics"))
        root.addView(
            ModernUi.card(this, emphasized = true).apply {
                val status = licenseSnapshot?.status?.name ?: "UNKNOWN"
                val detail = licenseSnapshot?.detail ?: "NO_RUNTIME_STATE"
                val errorCode = licenseSnapshot?.errorCode?.toString() ?: "NONE"
                addView(ModernUi.title(this@KnoxEnterpriseCenterActivity, "KPE license diagnostics"))
                addView(
                    ModernUi.body(
                        this@KnoxEnterpriseCenterActivity,
                        "Status=$status\nDetail=$detail\nErrorCode=$errorCode\nRetryScheduled=$retryScheduled\nAttempts=${historySummary.totalAttempts}\nLastSuccess=${formatHistoryTime(historySummary.lastSuccessEpochMs)}\nLastFailure=${formatHistoryTime(historySummary.lastFailureEpochMs)}\nStatusCounts=$statusCounts\nSourceCounts=$sourceCounts\nSuccessRate=${"%.1f".format(historySummary.successRate)}%\nConsecutiveProblems=${historySummary.consecutiveProblemAttempts}\nLastStableActive=${formatHistoryTime(historySummary.lastStableActiveEpochMs)}\nHealth=${historySummary.healthVerdict.name}",
                    )
                )
                addView(ModernUi.spacer(this@KnoxEnterpriseCenterActivity, 8))
                addView(
                    ModernUi.action(this@KnoxEnterpriseCenterActivity, "Retry now") {
                        Thread {
                            KnoxLicenseController(this@KnoxEnterpriseCenterActivity)
                                .activateIfRequested(KnoxLicenseAttemptSource.MANUAL)
                            runOnUiThread { render() }
                        }.start()
                    }
                )
            }
        )

        root.addView(ModernUi.section(this, "KPE activation history"))
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.title(this@KnoxEnterpriseCenterActivity, "History controls"))
                fun filterAction(label: String, filter: KnoxLicenseHistoryFilter) {
                    addView(
                        ModernUi.action(this@KnoxEnterpriseCenterActivity, label) {
                            historyFilter = filter
                            render()
                        }
                    )
                }
                filterAction("Filter: ALL", KnoxLicenseHistoryFilter.ALL)
                filterAction("Filter: FAILED", KnoxLicenseHistoryFilter.FAILED)
                filterAction("Filter: RETRYABLE", KnoxLicenseHistoryFilter.RETRYABLE)
                filterAction("Filter: MANUAL", KnoxLicenseHistoryFilter.MANUAL)
                addView(ModernUi.spacer(this@KnoxEnterpriseCenterActivity, 8))
                addView(
                    ModernUi.action(this@KnoxEnterpriseCenterActivity, "Export history JSON") {
                        val json = KnoxLicenseHistoryStore(this@KnoxEnterpriseCenterActivity).exportJson()
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        startActivity(Intent.createChooser(share, "Export KPE history JSON"))
                    }
                )
                addView(
                    ModernUi.action(this@KnoxEnterpriseCenterActivity, "Clear history") {
                        KnoxLicenseHistoryStore(this@KnoxEnterpriseCenterActivity).clear()
                        render()
                    }
                )
            }
        )
        if (activationHistory.isEmpty()) {
            root.addView(
                ModernUi.card(this).apply {
                    addView(ModernUi.body(this@KnoxEnterpriseCenterActivity, "No activation attempts recorded for ${historyFilter.name}."))
                }
            )
        } else {
            activationHistory.forEach { entry ->
                root.addView(
                    ModernUi.card(this).apply {
                        val formattedTime = DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.MEDIUM,
                        ).format(Date(entry.timestampEpochMs))
                        addView(
                            ModernUi.body(
                                this@KnoxEnterpriseCenterActivity,
                                "Time=$formattedTime\nSource=${entry.source.name}\nPrevious=${entry.previousStatus?.name ?: "NONE"} • New=${entry.newStatus.name}\nDetail=${entry.detail} • ErrorCode=${entry.errorCode ?: "NONE"}",
                            )
                        )
                    }
                )
            }
        }

        root.addView(ModernUi.section(this, "Capabilities"))

        fun row(
            name: String,
            route: String,
            owner: String,
            license: String,
            permission: String,
            readback: String,
            state: String,
        ) {
            val tone = when {
                state.startsWith("UNSUPPORTED") -> ModernUi.Tone.DANGER
                state == "ACTIVE" || state == "AVAILABLE" || state == "SUPPORTED" -> ModernUi.Tone.SUCCESS
                state.contains("REQUIRED") || state.contains("LIMITED") || state.contains("DEPRECATED") -> ModernUi.Tone.WARNING
                else -> ModernUi.Tone.NEUTRAL
            }
            root.addView(
                ModernUi.card(this@KnoxEnterpriseCenterActivity).apply {
                    addView(ModernUi.title(this@KnoxEnterpriseCenterActivity, name))
                    addView(
                        ModernUi.body(
                            this@KnoxEnterpriseCenterActivity,
                            "Route=$route\nOwner=$owner • License=$license\nPermission=$permission • Readback=$readback",
                        )
                    )
                    addView(ModernUi.chip(this@KnoxEnterpriseCenterActivity, state, tone))
                }
            )
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

        root.addView(ModernUi.section(this, "Mutation policy"))
        root.addView(
            ModernUi.card(this).apply {
                addView(ModernUi.title(this@KnoxEnterpriseCenterActivity, "High-impact mutation flow"))
                addView(
                    ModernUi.body(
                        this@KnoxEnterpriseCenterActivity,
                        "Preview → ALLOW_WITH_CONFIRMATION → Apply → Readback → Commit/Rollback",
                    )
                )
                addView(ModernUi.spacer(this@KnoxEnterpriseCenterActivity, 8))
                addView(
                    ModernUi.body(
                        this@KnoxEnterpriseCenterActivity,
                        "No hidden-API exemptions, guessed Binder transactions, signature-permission bypass, root/su fallback, forged Knox state, or private KPE material.",
                    )
                )
            }
        )

        setContentView(ModernUi.scrollPage(this, root))
    }
}
