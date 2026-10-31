package io.dpcaio.engineering

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal data class BinaryXmlAttribute(
    val name: String,
    val value: String?,
)

internal sealed interface BinaryXmlEvent {
    data class StartElement(
        val name: String,
        val attributes: List<BinaryXmlAttribute>,
    ) : BinaryXmlEvent

    data class EndElement(val name: String) : BinaryXmlEvent
}

internal sealed interface BinaryManifestReadResult {
    data class Success(val events: List<BinaryXmlEvent>) : BinaryManifestReadResult
    data class Failure(
        val error: SecretCodeExtractionError,
        val message: String,
    ) : BinaryManifestReadResult
}

object BinaryAndroidManifestReader {
    const val STRING_POOL = 0x0001
    const val START_ELEMENT = 0x0102
    const val END_ELEMENT = 0x0103

    private const val XML_TYPE = 0x0003
    private const val RESOURCE_MAP = 0x0180
    private const val START_NAMESPACE = 0x0100
    private const val END_NAMESPACE = 0x0101
    private const val CDATA = 0x0104
    private const val UTF8_FLAG = 0x00000100
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val NO_INDEX = -1

    internal fun read(
        bytes: ByteArray,
        limits: SecretCodeExtractionLimits = SecretCodeExtractionLimits(),
    ): BinaryManifestReadResult {
        if (bytes.size > limits.maxManifestBytes) {
            return fail(SecretCodeExtractionError.LIMIT_EXCEEDED, "manifest exceeds maxManifestBytes")
        }
        if (bytes.size < 8) return malformed("manifest header truncated")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = u16(buffer, 0)
        val rootHeaderSize = u16(buffer, 2)
        val rootSize = u32(buffer, 4)
        if (rootType != XML_TYPE || rootHeaderSize < 8 || rootSize < rootHeaderSize || rootSize > bytes.size) {
            return malformed("invalid XML root chunk")
        }

        var strings: List<String>? = null
        val events = mutableListOf<BinaryXmlEvent>()
        var nodeCount = 0
        var offset = rootHeaderSize

        while (offset < rootSize) {
            if (offset + 8 > rootSize) return malformed("chunk header truncated at $offset")
            val type = u16(buffer, offset)
            val headerSize = u16(buffer, offset + 2)
            val chunkSize = u32(buffer, offset + 4)
            if (headerSize < 8 || chunkSize < headerSize || offset + chunkSize > rootSize) {
                return malformed("invalid chunk size at $offset")
            }

            when (type) {
                STRING_POOL -> {
                    if (chunkSize > limits.maxStringPoolBytes) {
                        return fail(SecretCodeExtractionError.LIMIT_EXCEEDED, "string pool exceeds maxStringPoolBytes")
                    }
                    strings = parseStringPool(buffer, offset, headerSize, chunkSize)
                        ?: return malformed("invalid string pool")
                }
                START_ELEMENT -> {
                    val pool = strings ?: return malformed("start element before string pool")
                    nodeCount++
                    if (nodeCount > limits.maxNodes) {
                        return fail(SecretCodeExtractionError.LIMIT_EXCEEDED, "XML node count exceeds maxNodes")
                    }
                    parseStartElement(buffer, offset, headerSize, chunkSize, pool)?.let(events::add)
                        ?: return malformed("invalid start element")
                }
                END_ELEMENT -> {
                    val pool = strings ?: return malformed("end element before string pool")
                    nodeCount++
                    if (nodeCount > limits.maxNodes) {
                        return fail(SecretCodeExtractionError.LIMIT_EXCEEDED, "XML node count exceeds maxNodes")
                    }
                    parseEndElement(buffer, offset, headerSize, pool)?.let(events::add)
                        ?: return malformed("invalid end element")
                }
                RESOURCE_MAP, START_NAMESPACE, END_NAMESPACE, CDATA -> Unit
                else -> Unit
            }
            offset += chunkSize
        }

        if (offset != rootSize) return malformed("chunk walk did not end at root size")
        return BinaryManifestReadResult.Success(events)
    }

    private fun parseStringPool(
        buffer: ByteBuffer,
        chunkStart: Int,
        headerSize: Int,
        chunkSize: Int,
    ): List<String>? {
        if (headerSize < 28 || chunkStart + headerSize > buffer.limit()) return null
        val stringCount = u32(buffer, chunkStart + 8)
        val flags = u32(buffer, chunkStart + 16)
        val stringsStart = u32(buffer, chunkStart + 20)
        if (stringCount < 0 || stringCount > 200_000) return null
        val offsetsStart = chunkStart + headerSize
        val offsetsBytes = stringCount.toLong() * 4L
        if (offsetsStart.toLong() + offsetsBytes > chunkStart.toLong() + chunkSize.toLong()) return null
        val dataStart = chunkStart + stringsStart
        val chunkEnd = chunkStart + chunkSize
        if (dataStart < chunkStart || dataStart > chunkEnd) return null

        val utf8 = (flags and UTF8_FLAG) != 0
        val result = ArrayList<String>(stringCount)
        for (index in 0 until stringCount) {
            val rel = u32(buffer, offsetsStart + index * 4)
            val start = dataStart + rel
            if (start < dataStart || start >= chunkEnd) return null
            val decoded = if (utf8) decodeUtf8(buffer, start, chunkEnd) else decodeUtf16(buffer, start, chunkEnd)
            result += decoded ?: return null
        }
        return result
    }

    private fun parseStartElement(
        buffer: ByteBuffer,
        chunkStart: Int,
        headerSize: Int,
        chunkSize: Int,
        strings: List<String>,
    ): BinaryXmlEvent.StartElement? {
        if (headerSize < 36 || chunkStart + headerSize > buffer.limit()) return null
        val nameIndex = i32(buffer, chunkStart + 20)
        val name = stringAt(strings, nameIndex) ?: return null
        val attributeStart = u16(buffer, chunkStart + 24)
        val attributeSize = u16(buffer, chunkStart + 26)
        val attributeCount = u16(buffer, chunkStart + 28)
        if (attributeSize < 20) return null
        val attrsBase = chunkStart + 16 + attributeStart
        val attrsEnd = attrsBase.toLong() + attributeCount.toLong() * attributeSize.toLong()
        if (attrsBase < chunkStart || attrsEnd > chunkStart.toLong() + chunkSize.toLong()) return null

        val attributes = ArrayList<BinaryXmlAttribute>(attributeCount)
        for (index in 0 until attributeCount) {
            val at = attrsBase + index * attributeSize
            val attrNameIndex = i32(buffer, at + 4)
            val rawValueIndex = i32(buffer, at + 8)
            val dataType = u8(buffer, at + 15)
            val data = i32(buffer, at + 16)
            val attrName = stringAt(strings, attrNameIndex) ?: return null
            val value = when {
                rawValueIndex != NO_INDEX -> stringAt(strings, rawValueIndex)
                dataType == TYPE_STRING -> stringAt(strings, data)
                dataType == TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
                dataType == TYPE_INT_DEC -> data.toString()
                dataType == TYPE_INT_HEX -> "0x" + data.toUInt().toString(16)
                else -> null
            }
            attributes += BinaryXmlAttribute(attrName, value)
        }
        return BinaryXmlEvent.StartElement(name, attributes)
    }

    private fun parseEndElement(
        buffer: ByteBuffer,
        chunkStart: Int,
        headerSize: Int,
        strings: List<String>,
    ): BinaryXmlEvent.EndElement? {
        if (headerSize < 24 || chunkStart + headerSize > buffer.limit()) return null
        val nameIndex = i32(buffer, chunkStart + 20)
        return stringAt(strings, nameIndex)?.let(BinaryXmlEvent::EndElement)
    }

    private fun decodeUtf8(buffer: ByteBuffer, start: Int, end: Int): String? {
        val first = readLength8(buffer, start, end) ?: return null
        val second = readLength8(buffer, first.next, end) ?: return null
        val byteLength = second.length
        val dataStart = second.next
        if (byteLength < 0 || dataStart + byteLength >= end) return null
        if (u8(buffer, dataStart + byteLength) != 0) return null
        val bytes = ByteArray(byteLength)
        for (i in 0 until byteLength) bytes[i] = buffer.get(dataStart + i)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun decodeUtf16(buffer: ByteBuffer, start: Int, end: Int): String? {
        val lengthInfo = readLength16(buffer, start, end) ?: return null
        val byteLength = lengthInfo.length.toLong() * 2L
        val dataStart = lengthInfo.next
        if (byteLength < 0 || dataStart.toLong() + byteLength + 2L > end.toLong()) return null
        val bytes = ByteArray(byteLength.toInt())
        for (i in bytes.indices) bytes[i] = buffer.get(dataStart + i)
        val term = u16(buffer, dataStart + bytes.size)
        if (term != 0) return null
        return String(bytes, StandardCharsets.UTF_16LE)
    }

    private data class LengthInfo(val length: Int, val next: Int)

    private fun readLength8(buffer: ByteBuffer, start: Int, end: Int): LengthInfo? {
        if (start >= end) return null
        val first = u8(buffer, start)
        return if ((first and 0x80) != 0) {
            if (start + 1 >= end) null else LengthInfo(((first and 0x7f) shl 8) or u8(buffer, start + 1), start + 2)
        } else {
            LengthInfo(first, start + 1)
        }
    }

    private fun readLength16(buffer: ByteBuffer, start: Int, end: Int): LengthInfo? {
        if (start + 2 > end) return null
        val first = u16(buffer, start)
        return if ((first and 0x8000) != 0) {
            if (start + 4 > end) null else LengthInfo(((first and 0x7fff) shl 16) or u16(buffer, start + 2), start + 4)
        } else {
            LengthInfo(first, start + 2)
        }
    }

    private fun stringAt(strings: List<String>, index: Int): String? =
        if (index in strings.indices) strings[index] else null

    private fun malformed(message: String) =
        BinaryManifestReadResult.Failure(SecretCodeExtractionError.MALFORMED_AXML, message)

    private fun fail(error: SecretCodeExtractionError, message: String) =
        BinaryManifestReadResult.Failure(error, message)

    private fun u8(buffer: ByteBuffer, offset: Int): Int = buffer.get(offset).toInt() and 0xff
    private fun u16(buffer: ByteBuffer, offset: Int): Int = buffer.getShort(offset).toInt() and 0xffff
    private fun u32(buffer: ByteBuffer, offset: Int): Int = buffer.getInt(offset)
    private fun i32(buffer: ByteBuffer, offset: Int): Int = buffer.getInt(offset)
}
