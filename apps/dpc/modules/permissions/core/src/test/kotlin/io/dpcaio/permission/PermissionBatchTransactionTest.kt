package io.dpcaio.permission

import io.dpcaio.policy.ManagedPermissionState

private fun batchAssert(v:Boolean,m:String){if(!v) error(m)}
fun main(){
    val tx=PermissionBatchTransaction()
    val changes=listOf(
        PermissionBatchChange("pkg","CAMERA",0,ManagedPermissionState.DEFAULT,ManagedPermissionState.GRANTED,PermissionControlDecision(PermissionControlRoute.DPC,PermissionControlCapability.CAN_GRANT_AND_DENY,"ok")),
        PermissionBatchChange("pkg","BODY",0,ManagedPermissionState.DENIED,ManagedPermissionState.GRANTED,PermissionControlDecision(PermissionControlRoute.UNAVAILABLE,PermissionControlCapability.SENSOR_GRANT_RESTRICTED,"blocked"))
    )
    val plan=tx.plan(changes)
    batchAssert(plan.supported.size==1,"one supported")
    batchAssert(plan.skipped.size==1 && plan.skipped.first().status==PermissionBatchItemStatus.SKIPPED,"one skipped")

    val final=tx.finalize(plan, mapOf("CAMERA" to ManagedPermissionState.GRANTED))
    batchAssert(final.results.first{it.permission=="CAMERA"}.status==PermissionBatchItemStatus.VERIFIED,"verified readback")
    val restore=tx.restorePlan(changes)
    batchAssert(restore.size==1,"only previous DPC-managed states are restorable")
    batchAssert(restore.first{it.permission=="CAMERA"}.requestedState==ManagedPermissionState.DEFAULT,"restore default")
    println("PermissionBatchTransactionTest: PASS")
}
