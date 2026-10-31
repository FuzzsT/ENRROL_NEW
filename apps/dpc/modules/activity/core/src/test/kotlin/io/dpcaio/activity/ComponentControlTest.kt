package io.dpcaio.activity

private fun cc(v:Boolean,m:String){if(!v) error(m)}
fun main(){
    cc(ComponentStateResolver.effective(true, ComponentOverrideState.DEFAULT), "manifest enabled default")
    cc(!ComponentStateResolver.effective(true, ComponentOverrideState.DISABLED), "disabled override")
    cc(ComponentStateResolver.effective(false, ComponentOverrideState.ENABLED), "enabled override")

    val router=ComponentControlRouter()
    val own=router.resolve(ComponentControlRequest("io.dpcaio.app","io.dpcaio.app.PermissionManagerActivity",0,true,true,false,false,false,ComponentOverrideState.DISABLED))
    cc(own.route==ComponentControlRoute.OWN_UID && own.allowed,"own uid")

    val protected=router.resolve(ComponentControlRequest("io.dpcaio.app","io.dpcaio.app.AioDeviceAdminReceiver",0,true,true,false,false,false,ComponentOverrideState.DISABLED))
    cc(!protected.allowed && protected.status==ComponentControlStatus.PROTECTED_DPC_COMPONENT,"protect DPC admin")

    val foreign=router.resolve(ComponentControlRequest("com.example","com.example.HiddenActivity",10,false,true,false,false,false,ComponentOverrideState.ENABLED))
    cc(foreign.route==ComponentControlRoute.SHIZUKU,"foreign shizuku")

    val noRoute=router.resolve(ComponentControlRequest("com.example","com.example.HiddenActivity",10,false,false,false,false,false,ComponentOverrideState.ENABLED))
    cc(noRoute.status==ComponentControlStatus.COMPONENT_CONTROL_UNAVAILABLE,"no route")

    val batch=ComponentBatchPlanner().plan(33,listOf(own.copy(packageName="io.dpcaio.app", targetUserId=0),own.copy(packageName="io.dpcaio.app", targetUserId=0)))
    cc(batch.atomic,"same uid API33 batch atomic")
    val mixed=ComponentBatchPlanner().plan(33,listOf(own,foreign))
    cc(!mixed.atomic && mixed.status==ComponentControlStatus.BATCH_NOT_ATOMIC,"mixed route non atomic")

    val verified = ComponentMutationVerification.statusLine(
        route = "SHIZUKU",
        userId = 10,
        requested = ComponentOverrideState.ENABLED,
        observed = ComponentOverrideState.ENABLED,
        verified = true,
        detail = "VERIFIED:ENABLED",
    )
    cc(verified.contains("requested=ENABLED"), "verified requested state")
    cc(verified.contains("observed=ENABLED"), "verified observed state")
    cc(verified.contains("verified=true"), "verified marker")
    cc(ComponentMutationVerification.canLaunchAfterEnable(ComponentOverrideState.ENABLED, ComponentOverrideState.ENABLED, true), "verified enable can launch")

    val mismatch = ComponentMutationVerification.statusLine(
        route = "SHIZUKU",
        userId = 10,
        requested = ComponentOverrideState.ENABLED,
        observed = ComponentOverrideState.DISABLED,
        verified = false,
        detail = "READBACK_MISMATCH:requested=ENABLED observed=DISABLED",
    )
    cc(mismatch.contains("READBACK_MISMATCH"), "mismatch remains visible")
    cc(!ComponentMutationVerification.canLaunchAfterEnable(ComponentOverrideState.ENABLED, ComponentOverrideState.DISABLED, false), "mismatch blocks launch")

    val unknown = ComponentMutationVerification.statusLine(
        route = "SHIZUKU",
        userId = 10,
        requested = ComponentOverrideState.DEFAULT,
        observed = null,
        verified = false,
        detail = "READBACK_MISMATCH:requested=DEFAULT observed=UNKNOWN",
    )
    cc(unknown.contains("observed=UNKNOWN"), "unknown readback remains explicit")
    cc(!ComponentMutationVerification.canLaunchAfterEnable(ComponentOverrideState.ENABLED, null, false), "unknown blocks launch")

    println("ComponentControlTest: PASS")
}
