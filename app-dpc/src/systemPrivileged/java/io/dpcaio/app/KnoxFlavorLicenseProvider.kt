package io.dpcaio.app

import android.content.Context

/** Privileged production variant still requires real Knox authorization. */
object KnoxFlavorLicenseProvider {
    fun isLabSimulatedActive(context: Context): Boolean = false
}
