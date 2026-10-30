package io.dpcaio.selfupdate.android

import java.util.concurrent.atomic.AtomicReference

object SelfUpdateOperationCoordinator {
    private enum class Operation {
        CHECK,
        DOWNLOAD,
        INSTALL,
    }

    private val activeOperation = AtomicReference<Operation?>(null)

    fun tryStartCheck(): Boolean = activeOperation.compareAndSet(null, Operation.CHECK)

    fun finishCheck() {
        activeOperation.compareAndSet(Operation.CHECK, null)
    }

    fun tryStartDownload(): Boolean = activeOperation.compareAndSet(null, Operation.DOWNLOAD)

    fun handoffDownloadToInstall(): Boolean =
        activeOperation.compareAndSet(Operation.DOWNLOAD, Operation.INSTALL)

    fun finishDownload() {
        activeOperation.compareAndSet(Operation.DOWNLOAD, null)
    }

    fun tryStartInstall(): Boolean = activeOperation.compareAndSet(null, Operation.INSTALL)

    fun finishInstall() {
        activeOperation.compareAndSet(Operation.INSTALL, null)
    }

    fun isBusy(): Boolean = activeOperation.get() != null
}
