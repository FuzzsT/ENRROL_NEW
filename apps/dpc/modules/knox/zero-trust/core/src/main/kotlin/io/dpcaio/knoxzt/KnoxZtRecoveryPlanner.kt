package io.dpcaio.knoxzt

const val KNOXZT_PACKAGE = "com.samsung.android.knox.zt.framework"

enum class KnoxZtRecoveryRoute {
    NONE,
    ENABLE_SYSTEM_APP,
    INSTALL_EXISTING_PACKAGE,
    DOWNLOAD_VERIFY_INSTALL
}

data class KnoxZtProbe(
    val installedForUser: Boolean,
    val knownToSystem: Boolean = installedForUser,
    val enabled: Boolean = false,
    val systemApp: Boolean = false,
    val trusted: Boolean = false
)

data class KnoxZtRecoveryPlan(
    val routes: List<KnoxZtRecoveryRoute>,
    val blockers: List<String>
) {
    val selected: KnoxZtRecoveryRoute? get() = routes.firstOrNull()
}

class KnoxZtRecoveryPlanner {
    fun plan(probe: KnoxZtProbe, trustedInstallSourceConfigured: Boolean): KnoxZtRecoveryPlan {
        if (probe.installedForUser) {
            if (!probe.systemApp || !probe.trusted) {
                return KnoxZtRecoveryPlan(emptyList(), listOf("SIGNATURE_OR_SYSTEM_TRUST_REQUIRED"))
            }
            return if (probe.enabled) {
                KnoxZtRecoveryPlan(listOf(KnoxZtRecoveryRoute.NONE), emptyList())
            } else {
                KnoxZtRecoveryPlan(listOf(KnoxZtRecoveryRoute.ENABLE_SYSTEM_APP), emptyList())
            }
        }

        if (probe.knownToSystem) {
            return KnoxZtRecoveryPlan(
                routes = listOf(KnoxZtRecoveryRoute.INSTALL_EXISTING_PACKAGE, KnoxZtRecoveryRoute.ENABLE_SYSTEM_APP),
                blockers = emptyList()
            )
        }

        return if (trustedInstallSourceConfigured) {
            KnoxZtRecoveryPlan(listOf(KnoxZtRecoveryRoute.DOWNLOAD_VERIFY_INSTALL), emptyList())
        } else {
            KnoxZtRecoveryPlan(emptyList(), listOf("TRUSTED_INSTALL_SOURCE_REQUIRED"))
        }
    }
}
