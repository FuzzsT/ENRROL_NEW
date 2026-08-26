package io.dpcaio.knox.mock

enum class KnoxMockLicenseState { ACTIVE_REAL, ACTIVE_LAB, INACTIVE }
enum class KnoxMockOperation { PACKAGE_HIDE, PACKAGE_SUSPEND, KNOX_ONLY_POLICY }
enum class KnoxMockRoute { REAL_KNOX, DPM_FALLBACK, REAL_KNOX_REQUIRED }

class KnoxMockGateway {
    fun licenseState(labGateActive: Boolean, realKnoxActive: Boolean): KnoxMockLicenseState = when {
        realKnoxActive -> KnoxMockLicenseState.ACTIVE_REAL
        labGateActive -> KnoxMockLicenseState.ACTIVE_LAB
        else -> KnoxMockLicenseState.INACTIVE
    }

    fun route(
        operation: KnoxMockOperation,
        dpmEquivalentAvailable: Boolean,
        realKnoxActive: Boolean
    ): KnoxMockRoute = when {
        realKnoxActive -> KnoxMockRoute.REAL_KNOX
        dpmEquivalentAvailable && operation != KnoxMockOperation.KNOX_ONLY_POLICY -> KnoxMockRoute.DPM_FALLBACK
        else -> KnoxMockRoute.REAL_KNOX_REQUIRED
    }
}
