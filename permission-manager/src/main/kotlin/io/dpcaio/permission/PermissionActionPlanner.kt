package io.dpcaio.permission

class PermissionActionPlanner {
    fun plan(inspection: PermissionInspection): PermissionActionPlan {
        val appOpAllowsDirect = inspection.appOpState == null ||
            inspection.appOpState == AppOpState.ALLOWED ||
            inspection.appOpState == AppOpState.DEFAULT ||
            inspection.appOpState == AppOpState.FOREGROUND

        if (inspection.rawPermission == RawPermissionState.GRANTED && appOpAllowsDirect) {
            return result(PermissionAction.DIRECT_ALREADY_GRANTED, inspection)
        }
        if (inspection.dpcManageable && inspection.rawPermission != RawPermissionState.NOT_APPLICABLE) {
            return result(PermissionAction.DPC_GRANT, inspection)
        }
        if (inspection.verifiedAlternative?.verified == true) {
            return result(
                PermissionAction.USE_VERIFIED_ROUTE,
                inspection,
                inspection.verifiedAlternative.route.id
            )
        }
        if (inspection.userActionAvailable) {
            return result(PermissionAction.USER_ACTION, inspection)
        }
        return result(PermissionAction.BLOCKED, inspection)
    }

    private fun result(
        action: PermissionAction,
        inspection: PermissionInspection,
        routeId: String? = null
    ) = PermissionActionPlan(
        primary = action,
        rawPermission = inspection.rawPermission,
        appOpState = inspection.appOpState,
        alternativeRouteId = routeId
    )
}
