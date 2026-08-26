package io.dpcaio.oem.android

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Non-exported same-UID process anchor used only by the OEM Internals Lab. */
class OemLabProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
