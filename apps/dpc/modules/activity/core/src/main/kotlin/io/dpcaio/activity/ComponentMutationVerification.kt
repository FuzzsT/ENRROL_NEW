package io.dpcaio.activity

object ComponentMutationVerification {
    fun statusLine(
        route: String,
        userId: Int,
        requested: ComponentOverrideState,
        observed: ComponentOverrideState?,
        verified: Boolean,
        detail: String,
    ): String = buildString {
        append("route=")
        append(route)
        append(" user=")
        append(userId)
        append(" requested=")
        append(requested.name)
        append(" observed=")
        append(observed?.name ?: "UNKNOWN")
        append(" verified=")
        append(verified)
        append(" detail=")
        append(detail)
    }

    fun canLaunchAfterEnable(
        requested: ComponentOverrideState,
        observed: ComponentOverrideState?,
        verified: Boolean,
    ): Boolean =
        requested == ComponentOverrideState.ENABLED &&
            observed == ComponentOverrideState.ENABLED &&
            verified
}
