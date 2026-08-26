package io.dpcaio.knox.official

import io.dpcaio.model.CapabilityState
import io.dpcaio.model.EnterpriseRoute

fun main() {
    val descriptor = KnoxApiDescriptor(
        capabilityId = "knox.application.prevent_start",
        className = "com.samsung.android.knox.application.ApplicationPolicy",
        minimumKnoxApi = 30,
        maximumTestedKnoxApi = 40,
        requiredPermission = "com.samsung.android.knox.permission.KNOX_APP_MGMT",
        requiredOwner = "DEVICE_OR_PROFILE_OWNER",
    )
    val base = KnoxPlatformContext(
        samsungDevice = true,
        knoxApi = 35,
        ownerSatisfied = true,
        licenseSatisfied = true,
        permissionSatisfied = true,
    )
    val ready = KnoxCapabilityReducer.reduce(descriptor, base, classPresent = true)
    check(ready.route == EnterpriseRoute.KNOX_OFFICIAL)
    check(ready.state == CapabilityState.AVAILABLE)
    check(!ready.operational) // prerequisites are not a verified call/readback

    check(KnoxCapabilityReducer.reduce(descriptor, base.copy(samsungDevice = false), true).state == CapabilityState.UNSUPPORTED_FIRMWARE)
    check(KnoxCapabilityReducer.reduce(descriptor, base.copy(licenseSatisfied = false), true).state == CapabilityState.LICENSE_REQUIRED)
    check(KnoxCapabilityReducer.reduce(descriptor, base.copy(knoxApi = 41), true).state == CapabilityState.UNVERIFIED_PLATFORM_MAPPING)
    check(KnoxCapabilityReducer.reduce(descriptor, base, false).state == CapabilityState.CLASS_MISSING)
    println("KnoxCapabilityReducerTest: PASS")
}
