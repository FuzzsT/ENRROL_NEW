package io.dpcaio.app

object NativeJarOperationCoordinator {
    private enum class Operation {
        IMPORT,
        EXECUTION,
    }

    private var activeOperation: Operation? = null
    private var activeReservation: Long = 0L
    private var nextReservation: Long = 1L

    @Synchronized
    fun tryStartImport(): Long? = tryStart(Operation.IMPORT)

    @Synchronized
    fun finishImport(reservation: Long) {
        finish(Operation.IMPORT, reservation)
    }

    @Synchronized
    fun tryStartExecution(): Long? = tryStart(Operation.EXECUTION)

    @Synchronized
    fun isExecutionReservation(reservation: Long): Boolean =
        activeOperation == Operation.EXECUTION && activeReservation == reservation

    @Synchronized
    fun finishExecution(reservation: Long) {
        finish(Operation.EXECUTION, reservation)
    }

    @Synchronized
    fun isBusy(): Boolean = activeOperation != null

    private fun tryStart(operation: Operation): Long? {
        if (activeOperation != null) return null
        val reservation = nextReservation++
        activeOperation = operation
        activeReservation = reservation
        return reservation
    }

    private fun finish(operation: Operation, reservation: Long) {
        if (activeOperation == operation && activeReservation == reservation) {
            activeOperation = null
            activeReservation = 0L
        }
    }
}
