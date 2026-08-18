package io.dpcaio.installer

class InstallPlanner {
    fun plan(request: InstallRequest, availability: InstallAvailability): InstallPlan {
        val routes = mutableListOf<InstallRoute>()
        val blockers = mutableListOf<String>()

        if (request.preference == InstallPreference.PLAY_COMPAT) {
            if (availability.managedPlay) routes += InstallRoute.MANAGED_PLAY
            if (!request.requireRealPlay && request.allowInstallerRecordFallback && availability.installerRecordCompat) {
                routes += InstallRoute.INSTALLER_RECORD_COMPAT
            }
            if (request.requireRealPlay && !availability.managedPlay) {
                blockers += "REAL_PLAY_UNAVAILABLE"
            }
        }

        if (!request.requireRealPlay) {
            if (availability.dpcPackageInstaller) routes += InstallRoute.DPC_PACKAGE_INSTALLER
            if (availability.systemPrivileged) routes += InstallRoute.SYSTEM_PRIVILEGED
            if (availability.shizuku) routes += InstallRoute.SHIZUKU
            if (availability.userConfirmation) routes += InstallRoute.USER_CONFIRMATION
        }

        if (routes.isEmpty()) blockers += "NO_INSTALL_ROUTE"

        return InstallPlan(
            request = request,
            routes = routes.distinct(),
            blockers = blockers.distinct(),
            realPlayVerified = routes.firstOrNull() == InstallRoute.MANAGED_PLAY && availability.managedPlayVerified
        )
    }
}
