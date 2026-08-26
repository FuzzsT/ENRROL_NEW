package io.dpcaio.samsung.settings.android

import io.dpcaio.samsung.settings.SettingDelay

object AndroidThreadSettingDelay : SettingDelay {
    override fun await(ms: Long) { if (ms > 0) Thread.sleep(ms) }
}
