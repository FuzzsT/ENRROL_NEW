package io.dpcaio.execution

import io.dpcaio.protection.ProtectionDecision

enum class EnterpriseTransactionState {
    PREVIEWED,
    FROZEN,
    APPLIED,
    READBACK_VERIFIED,
    READBACK_MISMATCH,
    COMMITTED,
    ROLLED_BACK,
    CONFLICT_EXTERNAL_CHANGE,
    FAILED,
}

data class EnterpriseOperation(
    val id: String,
    val targetId: String,
    val preState: String?,
    val desiredState: String?,
    val protectionDecision: ProtectionDecision = ProtectionDecision.ALLOW,
)

data class EnterprisePlan(
    val operation: EnterpriseOperation,
    val planHash: String,
    val idempotencyKey: String,
    val state: EnterpriseTransactionState,
)

data class EnterpriseReceipt(
    val planHash: String,
    val state: EnterpriseTransactionState,
    val preState: String?,
    val postState: String?,
    val readback: String?,
)

enum class RollbackDecision {
    ROLLBACK,
    ALREADY_RESTORED,
    CONFLICT_EXTERNAL_CHANGE,
}
