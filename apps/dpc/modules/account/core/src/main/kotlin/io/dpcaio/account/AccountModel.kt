package io.dpcaio.account

data class AccountRecord(
    val name: String,
    val type: String,
    val userId: Int
)

enum class AccountPriorityStatus {
    ALREADY_FIRST,
    REORDER_REQUIRED,
    TARGET_NOT_FOUND
}

data class AccountPriorityPlan(
    val target: AccountRecord?,
    val status: AccountPriorityStatus,
    val accountsToTemporarilyRemove: List<AccountRecord>,
    val accountsToReAdd: List<AccountRecord>
)
