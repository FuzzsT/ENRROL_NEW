package io.dpcaio.knox.license.lab

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class KnoxLabLicenseStatus {
    SIMULATED_ACTIVE,
    REJECTED_FORMAT,
    REJECTED_SIGNATURE,
    REJECTED_ISSUER,
    REJECTED_AUDIENCE,
    REJECTED_BUILD_TRACK,
    REJECTED_NOT_YET_VALID,
    REJECTED_EXPIRED
}

data class KnoxLabLicenseClaims(
    val issuer: String,
    val audience: String,
    val track: String,
    val licenseType: String,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val nonce: String,
    val scopes: Set<String>
)

data class KnoxLabLicenseResult(
    val status: KnoxLabLicenseStatus,
    val claims: KnoxLabLicenseClaims? = null,
    val detail: String? = null
)

class KnoxLabLicenseVerifier(publicKeyX509: ByteArray) {
    private val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyX509))
    private val decoder = Base64.getUrlDecoder()

    fun verify(
        token: String,
        expectedAudience: String,
        buildTrack: String,
        nowEpochSeconds: Long
    ): KnoxLabLicenseResult {
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != "DPC-AIO-LAB1") {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_FORMAT)
        }

        val payload = try {
            decoder.decode(parts[1])
        } catch (_: IllegalArgumentException) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_FORMAT)
        }
        val signatureBytes = try {
            decoder.decode(parts[2])
        } catch (_: IllegalArgumentException) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_FORMAT)
        }

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        if (!verifier.verify(signatureBytes)) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_SIGNATURE)
        }

        val claims = parseClaims(payload.toString(Charsets.UTF_8))
            ?: return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_FORMAT)

        if (claims.issuer != "DPC-AIO-LAB") {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_ISSUER, claims)
        }
        if (claims.audience != expectedAudience) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_AUDIENCE, claims)
        }
        if (buildTrack !in setOf("lab", "tst", "eng") || claims.track !in setOf("lab", "tst", "eng")) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_BUILD_TRACK, claims)
        }
        if (nowEpochSeconds < claims.issuedAtEpochSeconds) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_NOT_YET_VALID, claims)
        }
        if (nowEpochSeconds >= claims.expiresAtEpochSeconds) {
            return KnoxLabLicenseResult(KnoxLabLicenseStatus.REJECTED_EXPIRED, claims)
        }
        return KnoxLabLicenseResult(KnoxLabLicenseStatus.SIMULATED_ACTIVE, claims)
    }

    private fun parseClaims(payload: String): KnoxLabLicenseClaims? {
        val map = payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()

        val issuer = map["iss"] ?: return null
        val audience = map["aud"] ?: return null
        val track = map["track"] ?: return null
        val licenseType = map["licenseType"] ?: return null
        val issuedAt = map["iat"]?.toLongOrNull() ?: return null
        val expiresAt = map["exp"]?.toLongOrNull() ?: return null
        if (expiresAt <= issuedAt) return null
        val nonce = map["nonce"] ?: return null
        val scopes = map["scopes"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        return KnoxLabLicenseClaims(
            issuer = issuer,
            audience = audience,
            track = track,
            licenseType = licenseType,
            issuedAtEpochSeconds = issuedAt,
            expiresAtEpochSeconds = expiresAt,
            nonce = nonce,
            scopes = scopes
        )
    }
}
