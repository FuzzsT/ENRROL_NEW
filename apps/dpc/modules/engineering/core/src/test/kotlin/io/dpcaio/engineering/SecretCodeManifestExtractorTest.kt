package io.dpcaio.engineering

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun fixtureBytes(name: String): ByteArray {
    val path = Path.of("apps/dpc/modules/engineering/core/src/test/resources/fixtures/$name")
    return Base64.getDecoder().decode(Files.readString(path).trim())
}

private fun apkWithManifest(bytes: ByteArray): Path {
    val apk = Files.createTempFile("secret-code-fixture", ".apk")
    ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
        zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
        zip.write(bytes)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("classes.dex"))
        zip.write(byteArrayOf(1, 2, 3, 4))
        zip.closeEntry()
    }
    return apk
}

fun main() {
    val literalApk = apkWithManifest(fixtureBytes("secret-code-manifest.axml.b64"))
    val literal = SecretCodeManifestExtractor.extract(literalApk, SecretCodeSourceKind.BASE_APK)
    check(literal is SecretCodeExtractionResult.Success)
    check(literal.declarations.size == 1)
    val declaration = literal.declarations.single()
    check(declaration.declarationKind == SecretCodeDeclarationKind.LITERAL_HOST)
    check(declaration.rawHost == "4636")
    check(declaration.normalizedCode == "4636")
    check(declaration.receiverClassName == ".SecretReceiver")
    check(declaration.enabled == true)
    check(declaration.exported == false)
    check(declaration.requiredPermission == "com.vendor.SECRET")
    check(SecretCodeManifestExtractor.CURRENT_SECRET_CODE_ACTION in declaration.actions)
    check(SecretCodeManifestExtractor.LEGACY_SECRET_CODE_ACTION in declaration.actions)

    val patternApk = apkWithManifest(fixtureBytes("pattern-only-manifest.axml.b64"))
    val pattern = SecretCodeManifestExtractor.extract(patternApk, SecretCodeSourceKind.SPLIT_APK)
    check(pattern is SecretCodeExtractionResult.Success)
    val patternDeclaration = pattern.declarations.single()
    check(patternDeclaration.declarationKind == SecretCodeDeclarationKind.PATTERN_ONLY)
    check(patternDeclaration.normalizedCode == null)

    val candidateLimit = SecretCodeManifestExtractor.extract(
        literalApk,
        SecretCodeSourceKind.BASE_APK,
        SecretCodeExtractionLimits(maxCandidates = 0),
    )
    check(candidateLimit is SecretCodeExtractionResult.Failure)
    check(candidateLimit.error == SecretCodeExtractionError.LIMIT_EXCEEDED)

    val malformedApk = apkWithManifest(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val malformed = SecretCodeManifestExtractor.extract(malformedApk, SecretCodeSourceKind.BASE_APK)
    check(malformed is SecretCodeExtractionResult.Failure)
    check(malformed.error == SecretCodeExtractionError.MALFORMED_AXML)

    Files.deleteIfExists(literalApk)
    Files.deleteIfExists(patternApk)
    Files.deleteIfExists(malformedApk)

    println("SecretCodeManifestExtractorTest: PASS")
}
