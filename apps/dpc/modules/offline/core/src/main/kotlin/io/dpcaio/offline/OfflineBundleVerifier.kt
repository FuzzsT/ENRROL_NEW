package io.dpcaio.offline

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class OfflineBundleVerification(val verified: Boolean, val code: String) { val detail: String get() = code }

class OfflineBundleVerifier {
    fun verifyManifest(bytes: ByteArray, signatureBytes: ByteArray, publicKeyX509Base64: String): OfflineBundleVerification = try {
        val normalized = publicKeyX509Base64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot { it.isWhitespace() }
        val keyBytes = Base64.getDecoder().decode(normalized)
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        verifyManifest(bytes, signatureBytes, publicKey)
    } catch (_: Exception) {
        OfflineBundleVerification(false, "OFFLINE_BUNDLE_SIGNATURE_INVALID")
    }

    fun verifyManifest(bytes: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): OfflineBundleVerification = try {
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(bytes)
        if (verifier.verify(signatureBytes)) OfflineBundleVerification(true, "VERIFIED")
        else OfflineBundleVerification(false, "OFFLINE_BUNDLE_SIGNATURE_INVALID")
    } catch (_: Exception) {
        OfflineBundleVerification(false, "OFFLINE_BUNDLE_SIGNATURE_INVALID")
    }
}
