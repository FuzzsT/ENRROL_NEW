package io.dpcaio.offline

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

private fun cAssert(v:Boolean,m:String){ if(!v) error(m) }

fun main(){
    val kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val bytes="manifest".toByteArray()
    val signer=Signature.getInstance("Ed25519").apply { initSign(kp.private); update(bytes) }
    val sig=signer.sign()
    val pub=Base64.getEncoder().encodeToString(kp.public.encoded)
    val vr=OfflineBundleVerifier().verifyManifest(bytes,sig,pub)
    cAssert(vr.verified && vr.detail=="VERIFIED","Base64 X509 verifier compatibility")

    val manifest=OfflineBundleManifest(
        schemaVersion=1,
        bundleId="offline",
        minimumDpcVersion="1.0.0",
        minimumAndroidApi=33,
        allowedModes=setOf("FULLY_MANAGED"),
        packages=listOf(OfflinePackageEntry("com.example",1,"cert",listOf(OfflinePackageFile("packages/example/base.apk","",true)))),
        requiredCapabilities=setOf(OfflineCapability.PACKAGE_INSTALL)
    )
    val readiness=OfflineReadinessPlanner().evaluate(manifest, OfflineReadinessInput(
        signatureVerified=true,
        schemaSupported=true,
        currentAndroidApi=36,
        provisioningMode="FULLY_MANAGED",
        currentDpcVersion="1.0.0",
        availablePackageFiles=setOf("packages/example/base.apk"),
        availableCapabilities=setOf("PACKAGE_INSTALL")
    ))
    cAssert(readiness.status==OfflineReadinessStatus.FULL_OFFLINE_READY,"Android-facing readiness input")
    cAssert(readiness.details.isEmpty(),"ready details empty")
    cAssert(OfflineReadinessStatus.valueOf("OFFLINE_BUNDLE_INVALID")==OfflineReadinessStatus.OFFLINE_BUNDLE_INVALID,"UI status exists")
    println("OfflineAndroidCompatibilityTest: PASS")
}
