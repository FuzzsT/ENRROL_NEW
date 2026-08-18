package io.dpcaio.knox.license.lab

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

private fun b64(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

private fun signToken(payload: String, privateKey: java.security.PrivateKey): String {
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return "DPC-AIO-LAB1.${b64(payload.toByteArray(Charsets.UTF_8))}.${b64(signature.sign())}"
}

fun main() {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(256)
    val keyPair = keyPairGenerator.generateKeyPair()
    val now = 1_787_046_180L
    val payload = listOf(
        "iss=DPC-AIO-LAB",
        "aud=io.dpcaio.app",
        "track=lab",
        "licenseType=KLM_TEST_ONLY",
        "iat=$now",
        "exp=${now + 3600}",
        "nonce=test",
        "scopes=knox.mock.active,app.manage,policy.test"
    ).joinToString("\n")
    val token = signToken(payload, keyPair.private)
    val verifier = KnoxLabLicenseVerifier(keyPair.public.encoded)

    val ok = verifier.verify(token, expectedAudience = "io.dpcaio.app", buildTrack = "lab", nowEpochSeconds = now + 10)
    check(ok.status == KnoxLabLicenseStatus.SIMULATED_ACTIVE)
    check(ok.claims?.licenseType == "KLM_TEST_ONLY")
    check("knox.mock.active" in (ok.claims?.scopes ?: emptySet()))

    val prod = verifier.verify(token, expectedAudience = "io.dpcaio.app", buildTrack = "prd", nowEpochSeconds = now + 10)
    check(prod.status == KnoxLabLicenseStatus.REJECTED_BUILD_TRACK)

    val wrongAudience = verifier.verify(token, expectedAudience = "com.other", buildTrack = "lab", nowEpochSeconds = now + 10)
    check(wrongAudience.status == KnoxLabLicenseStatus.REJECTED_AUDIENCE)

    val expired = verifier.verify(token, expectedAudience = "io.dpcaio.app", buildTrack = "lab", nowEpochSeconds = now + 7200)
    check(expired.status == KnoxLabLicenseStatus.REJECTED_EXPIRED)

    val tampered = token.replace("DPC-AIO-LAB1.", "DPC-AIO-LAB1.X")
    check(verifier.verify(tampered, "io.dpcaio.app", "lab", now + 10).status != KnoxLabLicenseStatus.SIMULATED_ACTIVE)

    println("KnoxLabLicenseVerifierTest: PASS")
}
