package io.dpcaio.app

import android.app.Activity
import android.os.Bundle

/** Non-critical same-UID component used only by the ADB verification harness. */
class VerificationToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
