package io.dpcaio.app

import android.content.Context

/** Production enterprise variant never accepts local LAB licenses. */
object KnoxFlavorLicenseProvider {
    fun isLabSimulatedActive(context: Context): Boolean = false
}
