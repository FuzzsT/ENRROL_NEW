package io.dpcaio.app

import android.app.Application
import io.dpcaio.shizuku.ShizukuEarlyBootstrap

class DpcAioApplication : Application() {
    companion object {
        init {
            ShizukuEarlyBootstrap.initialize(BuildConfig.APPLICATION_ID)
        }
    }

    override fun onCreate() {
        super.onCreate()
        UiSurfaceController.install(this)
        KnoxStartupController.evaluateAndPersist(this)
        KnoxZtStartupController.enqueue(this)
        EnrollmentCoordinator.scheduleResume(this, "PROCESS_START")
    }
}
