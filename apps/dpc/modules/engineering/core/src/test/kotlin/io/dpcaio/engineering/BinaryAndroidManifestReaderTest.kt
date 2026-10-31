package io.dpcaio.engineering

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

private fun fixture(name: String): ByteArray {
    val path = Path.of("apps/dpc/modules/engineering/core/src/test/resources/fixtures/$name")
    return Base64.getDecoder().decode(Files.readString(path).trim())
}

private fun overflowChunkManifest(): ByteArray = ByteBuffer.allocate(16)
    .order(ByteOrder.LITTLE_ENDIAN)
    .apply {
        putShort(0x0003)
        putShort(8)
        putInt(16)
        putShort(0x7777.toShort())
        putShort(8)
        putInt(Int.MAX_VALUE)
    }
    .array()

fun main() {
    val parsed = BinaryAndroidManifestReader.read(fixture("secret-code-manifest.axml.b64"))
    check(parsed is BinaryManifestReadResult.Success)
    val starts = parsed.events.filterIsInstance<BinaryXmlEvent.StartElement>()
    val receiver = starts.first { it.name == "receiver" }
    check(receiver.attributes.first { it.name == "name" }.value == ".SecretReceiver")
    check(receiver.attributes.first { it.name == "enabled" }.value == "true")
    check(receiver.attributes.first { it.name == "exported" }.value == "false")

    val malformed = BinaryAndroidManifestReader.read(byteArrayOf(0x03, 0x00, 0x08, 0x00))
    check(malformed is BinaryManifestReadResult.Failure)
    check(malformed.error == SecretCodeExtractionError.MALFORMED_AXML)

    val overflow = runCatching { BinaryAndroidManifestReader.read(overflowChunkManifest()) }
    check(overflow.isSuccess) { "overflowing chunk size must return a typed failure, not throw" }
    val overflowResult = overflow.getOrThrow()
    check(overflowResult is BinaryManifestReadResult.Failure)
    check(overflowResult.error == SecretCodeExtractionError.MALFORMED_AXML)

    val limit = BinaryAndroidManifestReader.read(
        fixture("secret-code-manifest.axml.b64"),
        SecretCodeExtractionLimits(maxManifestBytes = 32),
    )
    check(limit is BinaryManifestReadResult.Failure)
    check(limit.error == SecretCodeExtractionError.LIMIT_EXCEEDED)

    println("BinaryAndroidManifestReaderTest: PASS")
}
