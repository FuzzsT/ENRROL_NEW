package io.dpcaio.account

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val planner = AccountPriorityPlanner()
    val accounts = listOf(
        AccountRecord("first@gmail.com", "com.google", 0),
        AccountRecord("target@gmail.com", "com.google", 0),
        AccountRecord("third@gmail.com", "com.google", 0)
    )

    val alreadyFirst = planner.plan(accounts, "first@gmail.com")
    assertEquals(AccountPriorityStatus.ALREADY_FIRST, alreadyFirst.status, "first account status")
    assertEquals(emptyList<String>(), alreadyFirst.accountsToTemporarilyRemove.map { it.name }, "no removals for first account")

    val reorder = planner.plan(accounts, "target@gmail.com")
    assertEquals(AccountPriorityStatus.REORDER_REQUIRED, reorder.status, "target requires reorder")
    assertEquals(listOf("first@gmail.com"), reorder.accountsToTemporarilyRemove.map { it.name }, "accounts before target are removed")
    assertEquals(listOf("first@gmail.com"), reorder.accountsToReAdd.map { it.name }, "removed accounts are re-added after target")

    val missing = planner.plan(accounts, "missing@gmail.com")
    assertEquals(AccountPriorityStatus.TARGET_NOT_FOUND, missing.status, "missing account status")

    println("AccountPriorityPlannerTest: PASS")
}
