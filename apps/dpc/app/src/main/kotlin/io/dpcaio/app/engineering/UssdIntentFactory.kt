package io.dpcaio.app.engineering

import android.content.Intent
import android.net.Uri

object UssdIntentFactory {
    fun dial(raw: String): Intent = Intent(Intent.ACTION_DIAL, telUri(raw))

    fun call(raw: String): Intent = Intent(Intent.ACTION_CALL, telUri(raw))

    private fun telUri(raw: String): Uri {
        val value = raw.trim()
        require(value.isNotEmpty()) { "Dial/USSD value is required" }
        return Uri.fromParts("tel", value, null)
    }
}
