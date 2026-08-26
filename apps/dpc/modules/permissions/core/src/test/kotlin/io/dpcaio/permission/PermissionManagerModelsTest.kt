package io.dpcaio.permission

import io.dpcaio.policy.ManagedPermissionState

private fun pmAssert(v:Boolean,m:String){ if(!v) error(m) }
fun main(){
    val record=PermissionManagerRecord(
        packageName="com.example.camera",
        permission="android.permission.CAMERA",
        requestedInManifest=true,
        actualGranted=false,
        dpcState=ManagedPermissionState.GRANTED,
        appOpState=AppOpState.IGNORED,
        userId=10,
        targetSdk=36,
        group="android.permission-group.CAMERA",
        protection=PermissionProtection.DANGEROUS,
        route=PermissionControlRoute.DPC,
        capability=PermissionControlCapability.CAN_GRANT_AND_DENY
    )
    pmAssert(!record.actualGranted && record.dpcState==ManagedPermissionState.GRANTED, "actual and DPC state must be independent")
    pmAssert(record.appOpState==AppOpState.IGNORED, "AppOps independent")

    val router=PermissionExecutionRouter()
    val deviceOwner=router.resolve(PermissionControlRequest(
        desiredState=ManagedPermissionState.GRANTED,
        isRuntimePermission=true,
        isSensorPermission=false,
        isDeviceOwner=true,
        isProfileOwner=false,
        delegatedPermissionGrant=false,
        sensorGrantOptOut=false,
        shizukuAvailable=true,
        systemPrivilegedAvailable=false,
        userActionAvailable=true
    ))
    pmAssert(deviceOwner.route==PermissionControlRoute.DPC, "DPC must win over Shizuku")

    val profileSensor=router.resolve(PermissionControlRequest(
        desiredState=ManagedPermissionState.GRANTED,
        isRuntimePermission=true,
        isSensorPermission=true,
        isDeviceOwner=false,
        isProfileOwner=true,
        delegatedPermissionGrant=false,
        sensorGrantOptOut=false,
        shizukuAvailable=true,
        systemPrivilegedAvailable=false,
        userActionAvailable=true
    ))
    pmAssert(profileSensor.capability==PermissionControlCapability.SENSOR_GRANT_RESTRICTED, "profile-owner sensor grant must be gated")
    pmAssert(profileSensor.route!=PermissionControlRoute.DPC, "must not issue unsupported DPC grant")

    val optOut=router.resolve(PermissionControlRequest(
        desiredState=ManagedPermissionState.GRANTED,
        isRuntimePermission=true,
        isSensorPermission=true,
        isDeviceOwner=true,
        isProfileOwner=false,
        delegatedPermissionGrant=false,
        sensorGrantOptOut=true,
        shizukuAvailable=false,
        systemPrivilegedAvailable=false,
        userActionAvailable=false
    ))
    pmAssert(optOut.capability==PermissionControlCapability.PROVISIONING_SENSOR_OPT_OUT, "sensor opt-out")

    val delegated=router.resolve(PermissionControlRequest(
        desiredState=ManagedPermissionState.DENIED,
        isRuntimePermission=true,
        isSensorPermission=false,
        isDeviceOwner=false,
        isProfileOwner=false,
        delegatedPermissionGrant=true,
        sensorGrantOptOut=false,
        shizukuAvailable=true,
        systemPrivilegedAvailable=false,
        userActionAvailable=true
    ))
    pmAssert(delegated.route==PermissionControlRoute.DELEGATED_DPC, "delegation before shizuku")

    val shizukuDefault=router.resolve(PermissionControlRequest(
        desiredState=ManagedPermissionState.DEFAULT,
        isRuntimePermission=true,
        isSensorPermission=false,
        isDeviceOwner=false,
        isProfileOwner=false,
        delegatedPermissionGrant=false,
        sensorGrantOptOut=false,
        shizukuAvailable=true,
        systemPrivilegedAvailable=false,
        userActionAvailable=true
    ))
    pmAssert(shizukuDefault.route==PermissionControlRoute.UNAVAILABLE, "DPC DEFAULT must not be faked by Shizuku")
    pmAssert(shizukuDefault.reason=="DPC_DEFAULT_REQUIRES_DPC", "default route reason")

    println("PermissionManagerModelsTest: PASS")
}
