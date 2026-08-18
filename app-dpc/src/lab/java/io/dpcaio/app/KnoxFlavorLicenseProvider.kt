package io.dpcaio.app

import android.content.Context

/** Present only in lab/tst/eng variants. */
object KnoxFlavorLicenseProvider {
    fun isLabSimulatedActive(context: Context): Boolean = runCatching {
        val token = context.assets.open("knox_lab/dpc-aio-lab-klm.token")
            .bufferedReader().use { it.readText().trim() }
        KnoxLabLicenseProvider.isSimulatedActive(token)
    }.getOrDefault(false)
}
