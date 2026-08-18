package io.dpcaio.nfc.android

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import io.dpcaio.nfc.NfcReplayValidator
import io.dpcaio.nfc.NfcTrace

object SyntheticNfcReplayRegistry {
    @Volatile private var trace: NfcTrace? = null

    fun install(candidate: NfcTrace) {
        require(NfcReplayValidator().validate(candidate).allowed) { "NFC trace is not eligible for replay" }
        trace = candidate
    }

    fun clear() { trace = null }
    fun current(): NfcTrace? = trace
}

class SyntheticHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val trace = SyntheticNfcReplayRegistry.current() ?: return hex("6A82")
        val command = commandApdu.joinToString("") { "%02X".format(it) }
        val response = NfcReplayValidator().responseFor(trace, command) ?: return hex("6A82")
        return hex(response)
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun hex(value: String): ByteArray {
        val clean = value.replace(" ", "").replace(":", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
