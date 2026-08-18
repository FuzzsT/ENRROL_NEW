package io.dpcaio.nfc.android

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

class OwnedNfcTagIo {
    fun readNdef(tag: Tag): NdefMessage? {
        val tech = Ndef.get(tag) ?: return null
        tech.connect()
        return try { tech.ndefMessage } finally { tech.close() }
    }

    fun writeNdef(tag: Tag, message: NdefMessage, ownershipConfirmed: Boolean) {
        require(ownershipConfirmed) { "owned/test tag confirmation required" }
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect(); try { ndef.writeNdefMessage(message) } finally { ndef.close() }; return
        }
        val formatable = NdefFormatable.get(tag) ?: error("tag is not NDEF writable")
        formatable.connect(); try { formatable.format(message) } finally { formatable.close() }
    }

    fun mifareClassicReadBlock(tag: Tag, sector: Int, block: Int, key: ByteArray, useKeyB: Boolean): ByteArray {
        val tech = MifareClassic.get(tag) ?: error("MIFARE Classic unavailable")
        tech.connect()
        return try {
            val authenticated = if (useKeyB) tech.authenticateSectorWithKeyB(sector, key) else tech.authenticateSectorWithKeyA(sector, key)
            check(authenticated) { "MIFARE authentication failed" }
            tech.readBlock(block)
        } finally { tech.close() }
    }

    fun mifareClassicWriteBlock(tag: Tag, sector: Int, block: Int, key: ByteArray, useKeyB: Boolean, data: ByteArray, ownershipConfirmed: Boolean) {
        require(ownershipConfirmed) { "owned/test tag confirmation required" }
        val tech = MifareClassic.get(tag) ?: error("MIFARE Classic unavailable")
        tech.connect()
        try {
            val authenticated = if (useKeyB) tech.authenticateSectorWithKeyB(sector, key) else tech.authenticateSectorWithKeyA(sector, key)
            check(authenticated) { "MIFARE authentication failed" }
            tech.writeBlock(block, data)
        } finally { tech.close() }
    }

    fun mifareUltralightReadPages(tag: Tag, pageOffset: Int): ByteArray {
        val tech = MifareUltralight.get(tag) ?: error("MIFARE Ultralight unavailable")
        tech.connect(); return try { tech.readPages(pageOffset) } finally { tech.close() }
    }

    fun mifareUltralightWritePage(tag: Tag, pageOffset: Int, data: ByteArray, ownershipConfirmed: Boolean) {
        require(ownershipConfirmed) { "owned/test tag confirmation required" }
        val tech = MifareUltralight.get(tag) ?: error("MIFARE Ultralight unavailable")
        tech.connect(); try { tech.writePage(pageOffset, data) } finally { tech.close() }
    }

    fun transceiveIsoDep(tag: Tag, data: ByteArray, ownershipConfirmed: Boolean): ByteArray = withOwned(ownershipConfirmed, IsoDep.get(tag)) { it.transceive(data) }
    fun transceiveNfcA(tag: Tag, data: ByteArray, ownershipConfirmed: Boolean): ByteArray = withOwned(ownershipConfirmed, NfcA.get(tag)) { it.transceive(data) }
    fun transceiveNfcB(tag: Tag, data: ByteArray, ownershipConfirmed: Boolean): ByteArray = withOwned(ownershipConfirmed, NfcB.get(tag)) { it.transceive(data) }
    fun transceiveNfcF(tag: Tag, data: ByteArray, ownershipConfirmed: Boolean): ByteArray = withOwned(ownershipConfirmed, NfcF.get(tag)) { it.transceive(data) }
    fun transceiveNfcV(tag: Tag, data: ByteArray, ownershipConfirmed: Boolean): ByteArray = withOwned(ownershipConfirmed, NfcV.get(tag)) { it.transceive(data) }

    private inline fun <T : android.nfc.tech.TagTechnology> withOwned(ownershipConfirmed: Boolean, tech: T?, block: (T) -> ByteArray): ByteArray {
        require(ownershipConfirmed) { "owned/test tag confirmation required" }
        val actual = tech ?: error("NFC technology unavailable")
        actual.connect(); return try { block(actual) } finally { actual.close() }
    }
}
