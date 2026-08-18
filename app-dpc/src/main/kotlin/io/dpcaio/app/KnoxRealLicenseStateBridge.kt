package io.dpcaio.app

import android.content.Context
import io.dpcaio.knox.license.KnoxLicenseResultInterpreter

/** Called by a real Samsung Knox license callback adapter when that SDK is present. */
object KnoxRealLicenseStateBridge {
    fun onLicenseResult(context: Context, errorCode: Int): PersistedKnoxRuntimeState {
        val state = KnoxLicenseResultInterpreter().fromErrorCode(errorCode)
        KnoxRuntimeStateStore.setRealLicenseStateName(context.applicationContext, state.name)
        return KnoxStartupController.evaluateAndPersist(context.applicationContext)
    }
}
