package io.dpcaio.app

import io.dpcaio.knox.license.lab.KnoxLabLicenseResult
import io.dpcaio.knox.license.lab.KnoxLabLicenseStatus
import io.dpcaio.knox.license.lab.KnoxLabLicenseVerifier
import java.util.Base64

/**
 * Offline test-license provider for the lab product flavor only.
 * This token is NOT a Samsung KLM/KPE license and must never be treated as one.
 */
object KnoxLabLicenseProvider {
    private const val AUDIENCE = "io.dpcaio.app"
    private const val PUBLIC_KEY_X509_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbaQWjnYa1eqcgIntn95WLQMK1O7EHhUQMOplzDKQdykgPQBMK6UGJ+cIyAOGOKKm1PsVtkapbg/6cy83bQZRCw=="

    private val verifier by lazy {
        KnoxLabLicenseVerifier(Base64.getDecoder().decode(PUBLIC_KEY_X509_BASE64))
    }

    fun verify(token: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): KnoxLabLicenseResult =
        verifier.verify(
            token = token.trim(),
            expectedAudience = AUDIENCE,
            buildTrack = "lab",
            nowEpochSeconds = nowEpochSeconds
        )

    fun isSimulatedActive(token: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        verify(token, nowEpochSeconds).status == KnoxLabLicenseStatus.SIMULATED_ACTIVE
}
