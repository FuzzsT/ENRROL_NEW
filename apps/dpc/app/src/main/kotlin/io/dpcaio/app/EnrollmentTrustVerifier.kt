package io.dpcaio.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class EnrollmentTrustVerifier(publicKeyDerBase64: String) {
    private val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyDerBase64))
    )

    fun verify(
        envelope: JSONObject,
        expectedSessionId: String,
        expectedReservationId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): EnrollmentTrustResult {
        val sessionId = envelope.optString("sessionId")
        val reservationId = envelope.optString("reservationId")
        val nonce = envelope.optString("nonce")
        val issuedAt = envelope.optLong("issuedAt", -1L)
        val expiresAt = envelope.optLong("expiresAt", -1L)
        val signatureText = envelope.optString("signature")
        if (sessionId != expectedSessionId) return EnrollmentTrustResult(false, "SESSION_MISMATCH")
        if (reservationId != expectedReservationId) return EnrollmentTrustResult(false, "RESERVATION_MISMATCH")
        if (nonce.isBlank()) return EnrollmentTrustResult(false, "NONCE_MISSING")
        if (issuedAt <= 0L || expiresAt <= issuedAt) return EnrollmentTrustResult(false, "TIME_WINDOW_INVALID")
        if (nowMillis > expiresAt) return EnrollmentTrustResult(false, "BOOTSTRAP_EXPIRED")
        if (issuedAt - nowMillis > MAX_CLOCK_SKEW_MS) return EnrollmentTrustResult(false, "BOOTSTRAP_FROM_FUTURE")
        if (signatureText.isBlank()) return EnrollmentTrustResult(false, "SIGNATURE_MISSING")

        val signed = JSONObject().apply {
            put("payload", envelope.getJSONObject("payload"))
            put("sessionId", sessionId)
            put("reservationId", reservationId)
            put("nonce", nonce)
            put("issuedAt", issuedAt)
            put("expiresAt", expiresAt)
            put("keyId", envelope.optString("keyId"))
        }
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(canonicalJson(signed).toByteArray(Charsets.UTF_8))
        val signature = runCatching { Base64.getUrlDecoder().decode(signatureText) }.getOrNull()
            ?: return EnrollmentTrustResult(false, "SIGNATURE_ENCODING_INVALID")
        return if (verifier.verify(signature)) EnrollmentTrustResult(true, null)
        else EnrollmentTrustResult(false, "SIGNATURE_INVALID")
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index -> canonicalJson(value.get(index)) }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    companion object {
        private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L
    }
}

data class EnrollmentTrustResult(val verified: Boolean, val errorCode: String?)
