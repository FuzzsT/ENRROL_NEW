package io.dpcaio.shizuku

import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui

object ShizukuEarlyBootstrap {
    private var initialized = false
    private var suiAvailable = false

    @Synchronized
    fun initialize(packageName: String): Boolean {
        if (initialized) return suiAvailable
        ShizukuProvider.disableAutomaticSuiInitialization()
        suiAvailable = runCatching { Sui.init(packageName) }.getOrDefault(false)
        initialized = true
        return suiAvailable
    }
}
