package io.dpcaio.selfupdate.android

import java.util.concurrent.atomic.AtomicBoolean

object SelfUpdateOperationCoordinator {
    private val downloadInFlight = AtomicBoolean(false)
    private val installInFlight = AtomicBoolean(false)

    fun tryStartDownload(): Boolean = downloadInFlight.compareAndSet(false, true)

    fun finishDownload() {
        downloadInFlight.set(false)
    }

    fun tryStartInstall(): Boolean = installInFlight.compareAndSet(false, true)

    fun finishInstall() {
        installInFlight.set(false)
    }

    fun isBusy(): Boolean = downloadInFlight.get() || installInFlight.get()
}
