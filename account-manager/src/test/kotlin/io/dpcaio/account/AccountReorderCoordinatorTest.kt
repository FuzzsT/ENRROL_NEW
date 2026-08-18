package io.dpcaio.account

private fun assertEq(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val plan = AccountPriorityPlan(
        target = AccountRecord("target@gmail.com", "com.google", 0),
        status = AccountPriorityStatus.REORDER_REQUIRED,
        accountsToTemporarilyRemove = listOf(
            AccountRecord("first@gmail.com", "com.google", 0),
            AccountRecord("second@gmail.com", "com.google", 0)
        ),
        accountsToReAdd = listOf(
            AccountRecord("first@gmail.com", "com.google", 0),
            AccountRecord("second@gmail.com", "com.google", 0)
        )
    )

    val coordinator = AccountReorderCoordinator()
    val unconfirmed = coordinator.buildWorkflow(plan, confirmed = false)
    assertEq(AccountReorderWorkflowStatus.NEEDS_CONFIRMATION, unconfirmed.status, "must require confirmation")
    assertEq(emptyList<AccountReorderStep>(), unconfirmed.steps, "unconfirmed workflow has no destructive steps")

    val confirmed = coordinator.buildWorkflow(plan, confirmed = true)
    assertEq(AccountReorderWorkflowStatus.READY, confirmed.status, "confirmed workflow is ready")
    assertEq(
        listOf(
            AccountReorderStepKind.REMOVE_ACCOUNT,
            AccountReorderStepKind.REMOVE_ACCOUNT,
            AccountReorderStepKind.RE_ADD_ACCOUNT,
            AccountReorderStepKind.RE_ADD_ACCOUNT,
            AccountReorderStepKind.VERIFY_ORDER
        ),
        confirmed.steps.map { it.kind },
        "step order"
    )
    assertEq("first@gmail.com", confirmed.steps[0].accountName, "remove first")
    assertEq("target@gmail.com", confirmed.targetName, "target preserved")

    val alreadyFirst = coordinator.buildWorkflow(
        AccountPriorityPlan(
            target = AccountRecord("target@gmail.com", "com.google", 0),
            status = AccountPriorityStatus.ALREADY_FIRST,
            accountsToTemporarilyRemove = emptyList(),
            accountsToReAdd = emptyList()
        ),
        confirmed = false
    )
    assertEq(AccountReorderWorkflowStatus.COMPLETE, alreadyFirst.status, "already first needs no workflow")

    println("AccountReorderCoordinatorTest: PASS")
}
