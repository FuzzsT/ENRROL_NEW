package io.dpcaio.offline

private fun checkReceipt(v:Boolean,m:String){ if(!v) error(m) }
fun main(){
    val r=OfflineSyncReceipt("bundle-v3",3,"enroll-1","policy-digest","packages-digest","result-digest",1700000000000L)
    val text=r.toRedactedJson()
    for (required in listOf("bundle-v3","policy-digest","packages-digest","result-digest")) checkReceipt(required in text, required)
    for (forbidden in listOf("token","password","authorization","privateKey","imei")) checkReceipt(forbidden !in text.lowercase(), forbidden)
    println("OfflineSyncReceiptTest: PASS")
}
