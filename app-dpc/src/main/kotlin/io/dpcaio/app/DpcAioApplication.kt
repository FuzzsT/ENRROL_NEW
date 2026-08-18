package io.dpcaio.app

import android.app.Application

class DpcAioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KnoxStartupController.evaluateAndPersist(this)
        KnoxZtStartupController.enqueue(this)
    }
}
