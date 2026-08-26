package io.dpcaio.offline

data class OfflineSyncReceipt(
    val bundleId: String,
    val bundleVersion: Int,
    val deviceEnrollmentId: String,
    val policyDigest: String,
    val packageSetDigest: String,
    val resultDigest: String,
    val appliedAtEpochMs: Long
) {
    fun toRedactedJson(): String = buildString {
        append('{')
        append("\"bundleId\":\"").append(escape(bundleId)).append("\",")
        append("\"bundleVersion\":").append(bundleVersion).append(',')
        append("\"deviceEnrollmentId\":\"").append(escape(deviceEnrollmentId)).append("\",")
        append("\"policyDigest\":\"").append(escape(policyDigest)).append("\",")
        append("\"packageSetDigest\":\"").append(escape(packageSetDigest)).append("\",")
        append("\"resultDigest\":\"").append(escape(resultDigest)).append("\",")
        append("\"appliedAtEpochMs\":").append(appliedAtEpochMs)
        append('}')
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
