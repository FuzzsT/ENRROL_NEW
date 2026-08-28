package io.dpcaio.policy.parity

import io.dpcaio.policy.PolicyResult

data class ParityActionRequest(
    val parityId: String,
    val values: Map<String, String>,
)

data class ParityActionResult(
    val success: Boolean,
    val message: String,
)

fun interface ParityActionHandler {
    fun execute(request: ParityActionRequest): PolicyResult<String>
}
