package io.dpcaio.account

class AccountPriorityPlanner {
    fun plan(accounts: List<AccountRecord>, targetName: String): AccountPriorityPlan {
        val index = accounts.indexOfFirst { it.name == targetName }
        if (index < 0) {
            return AccountPriorityPlan(
                target = null,
                status = AccountPriorityStatus.TARGET_NOT_FOUND,
                accountsToTemporarilyRemove = emptyList(),
                accountsToReAdd = emptyList()
            )
        }

        val target = accounts[index]
        if (index == 0) {
            return AccountPriorityPlan(
                target = target,
                status = AccountPriorityStatus.ALREADY_FIRST,
                accountsToTemporarilyRemove = emptyList(),
                accountsToReAdd = emptyList()
            )
        }

        val beforeTarget = accounts.take(index)
        return AccountPriorityPlan(
            target = target,
            status = AccountPriorityStatus.REORDER_REQUIRED,
            accountsToTemporarilyRemove = beforeTarget,
            accountsToReAdd = beforeTarget
        )
    }
}
