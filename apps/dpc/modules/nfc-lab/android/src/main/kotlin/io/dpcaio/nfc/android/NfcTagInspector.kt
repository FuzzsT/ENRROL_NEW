package io.dpcaio.nfc.android

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcBarcode
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

data class NfcTagSnapshot(
    val idHex: String,
    val techList: List<String>,
    val nfcA: Boolean,
    val nfcB: Boolean,
    val nfcF: Boolean,
    val nfcV: Boolean,
    val isoDep: Boolean,
    val ndef: Boolean,
    val ndefFormatable: Boolean,
    val nfcBarcode: Boolean,
    val mifareClassic: Boolean,
    val mifareUltralight: Boolean
)

class NfcTagInspector {
    fun inspect(tag: Tag): NfcTagSnapshot = NfcTagSnapshot(
        idHex = tag.id.joinToString("") { "%02X".format(it) },
        techList = tag.techList.toList(),
        nfcA = NfcA.get(tag) != null,
        nfcB = NfcB.get(tag) != null,
        nfcF = NfcF.get(tag) != null,
        nfcV = NfcV.get(tag) != null,
        isoDep = IsoDep.get(tag) != null,
        ndef = Ndef.get(tag) != null,
        ndefFormatable = NdefFormatable.get(tag) != null,
        nfcBarcode = NfcBarcode.get(tag) != null,
        mifareClassic = MifareClassic.get(tag) != null,
        mifareUltralight = MifareUltralight.get(tag) != null
    )
}
