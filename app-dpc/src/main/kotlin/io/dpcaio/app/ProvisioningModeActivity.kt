package io.dpcaio.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val allowed = intent.getIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
        ).orEmpty()

        val mode = when {
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE in allowed ->
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE in allowed ->
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            else -> {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
        }

        val result = Intent().apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode)
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
