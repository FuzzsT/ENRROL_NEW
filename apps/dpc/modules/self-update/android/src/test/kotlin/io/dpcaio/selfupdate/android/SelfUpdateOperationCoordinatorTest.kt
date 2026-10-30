package io.dpcaio.selfupdate.android

fun main() {
    while (SelfUpdateOperationCoordinator.isBusy()) {
        SelfUpdateOperationCoordinator.finishCheck()
        SelfUpdateOperationCoordinator.finishDownload()
        SelfUpdateOperationCoordinator.finishInstall()
    }

    check(SelfUpdateOperationCoordinator.tryStartDownload())
    check(!SelfUpdateOperationCoordinator.tryStartCheck())
    check(SelfUpdateOperationCoordinator.handoffDownloadToInstall())
    check(SelfUpdateOperationCoordinator.isBusy())
    check(!SelfUpdateOperationCoordinator.tryStartCheck())
    check(!SelfUpdateOperationCoordinator.tryStartDownload())

    SelfUpdateOperationCoordinator.finishInstall()
    check(!SelfUpdateOperationCoordinator.isBusy())
    check(SelfUpdateOperationCoordinator.tryStartCheck())
    SelfUpdateOperationCoordinator.finishCheck()
    check(!SelfUpdateOperationCoordinator.isBusy())

    println("SelfUpdateOperationCoordinatorTest: PASS")
}
