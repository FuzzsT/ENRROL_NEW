package io.dpcaio.account

enum class AccountReorderWorkflowStatus {
    NEEDS_CONFIRMATION,
    READY,
    COMPLETE,
    BLOCKED
}

enum class AccountReorderStepKind {
    REMOVE_ACCOUNT,
    RE_ADD_ACCOUNT,
    VERIFY_ORDER
}

data class AccountReorderStep(
    val kind: AccountReorderStepKind,
    val accountName: String? = null
)

data class AccountReorderWorkflow(
    val status: AccountReorderWorkflowStatus,
    val targetName: String?,
    val steps: List<AccountReorderStep>,
    val warning: String?
)

class AccountReorderCoordinator {
    fun buildWorkflow(plan: AccountPriorityPlan, confirmed: Boolean): AccountReorderWorkflow = when (plan.status) {
        AccountPriorityStatus.TARGET_NOT_FOUND -> AccountReorderWorkflow(
            status = AccountReorderWorkflowStatus.BLOCKED,
            targetName = null,
            steps = emptyList(),
            warning = "Target account is not present in the observed Google account list."
        )

        AccountPriorityStatus.ALREADY_FIRST -> AccountReorderWorkflow(
            status = AccountReorderWorkflowStatus.COMPLETE,
            targetName = plan.target?.name,
            steps = emptyList(),
            warning = null
        )

        AccountPriorityStatus.REORDER_REQUIRED -> {
            if (!confirmed) {
                AccountReorderWorkflow(
                    status = AccountReorderWorkflowStatus.NEEDS_CONFIRMATION,
                    targetName = plan.target?.name,
                    steps = emptyList(),
                    warning = "Reordering removes accounts before the target and requires re-authentication when they are added again."
                )
            } else {
                val steps = buildList {
                    plan.accountsToTemporarilyRemove.forEach {
                        add(AccountReorderStep(AccountReorderStepKind.REMOVE_ACCOUNT, it.name))
                    }
                    plan.accountsToReAdd.forEach {
                        add(AccountReorderStep(AccountReorderStepKind.RE_ADD_ACCOUNT, it.name))
                    }
                    add(AccountReorderStep(AccountReorderStepKind.VERIFY_ORDER, plan.target?.name))
                }
                AccountReorderWorkflow(
                    status = AccountReorderWorkflowStatus.READY,
                    targetName = plan.target?.name,
                    steps = steps,
                    warning = "The observed account order will be verified after all system authenticator flows complete."
                )
            }
        }
    }
}
