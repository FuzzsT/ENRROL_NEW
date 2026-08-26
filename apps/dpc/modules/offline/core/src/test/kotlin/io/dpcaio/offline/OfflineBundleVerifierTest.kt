package io.dpcaio.offline

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature

private fun bAssert(value: Boolean, message: String) { if (!value) error(message) }
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun main() {
    val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val manifestBytes = "{\"schemaVersion\":1,\"bundleId\":\"enterprise-offline-v1\"}".toByteArray()
    val signer = Signature.getInstance("Ed25519")
    signer.initSign(keyPair.private)
    signer.update(manifestBytes)
    val signature = signer.sign()

    val verifier = OfflineBundleVerifier()
    val valid = verifier.verifyManifest(manifestBytes, signature, keyPair.public)
    bAssert(valid.verified && valid.code == "VERIFIED", "valid Ed25519 signature")

    val tampered = verifier.verifyManifest(manifestBytes + '!'.code.toByte(), signature, keyPair.public)
    bAssert(!tampered.verified && tampered.code == "OFFLINE_BUNDLE_SIGNATURE_INVALID", "tampered manifest rejected")

    val basePath = "packages/agent/base.apk"
    val splitPath = "packages/agent/split_config.arm64_v8a.apk"
    val baseBytes = "base-apk".toByteArray()
    val splitBytes = "split-apk".toByteArray()
    val pkg = OfflinePackageEntry(
        packageName = "com.example.agent",
        versionCode = 42,
        baseFile = basePath,
        requiredSplits = listOf(splitPath),
        sha256 = sha256(baseBytes),
        signingCertificateSha256 = "cert",
        fileSha256 = mapOf(basePath to sha256(baseBytes), splitPath to sha256(splitBytes))
    )
    val manifest = OfflineBundleManifest(
        schemaVersion = 1,
        bundleId = "enterprise-offline-v1",
        minimumDpcVersion = "1.0.0",
        minimumAndroidApi = 33,
        allowedModes = setOf("FULLY_MANAGED"),
        packages = listOf(pkg),
        requiredCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL)
    )

    val plan = OfflinePackagePlanner().plan(manifest, mapOf(basePath to baseBytes, splitPath to splitBytes))
    bAssert(plan.ready && plan.blockingCode == null, "complete package plan")

    val missingSplit = OfflinePackagePlanner().plan(manifest, mapOf(basePath to baseBytes))
    bAssert(!missingSplit.ready && missingSplit.blockingCode == "OFFLINE_SPLIT_MISSING:$splitPath", "missing required split rejected")

    val wrongHash = OfflinePackagePlanner().plan(manifest, mapOf(basePath to "evil".toByteArray(), splitPath to splitBytes))
    bAssert(!wrongHash.ready && wrongHash.blockingCode == "OFFLINE_PACKAGE_HASH_MISMATCH:$basePath", "wrong APK hash rejected")

    println("OfflineBundleVerifierTest: PASS")
}
