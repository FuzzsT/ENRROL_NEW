package io.dpcaio.offline

private fun op(v:Boolean,m:String){if(!v) error(m)}
fun main(){
    val spec=OfflinePolicySpec(
        defaultPermissionPolicy=OfflineDefaultPermissionPolicy.PROMPT,
        permissions=listOf(OfflinePermissionRule("com.example","android.permission.CAMERA",OfflinePermissionDesiredState.DEFAULT,0,true)),
        components=listOf(OfflineComponentRule("com.example",".SetupActivity",OfflineComponentDesiredState.DISABLED,0,true))
    )
    op(spec.permissions.single().required,"permission required")
    op(spec.components.single().normalizedClassName=="com.example.SetupActivity","normalize class")
    println("OfflinePolicyModelsTest: PASS")
}
