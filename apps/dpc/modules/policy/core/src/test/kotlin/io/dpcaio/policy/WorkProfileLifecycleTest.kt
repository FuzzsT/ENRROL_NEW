package io.dpcaio.policy

fun main() {
    val rule = CrossProfileIntentRule(
        id = "view-web",
        action = "android.intent.action.VIEW",
        categories = setOf("android.intent.category.BROWSABLE"),
        scheme = "https",
        direction = CrossProfileDirection.BIDIRECTIONAL,
    )
    check(rule.valid())
    check(!rule.copy(action = "").valid())
    check(ProfileNamePolicy.normalize("  Work profile  ") == "Work profile")
    check(ProfileNamePolicy.normalize("   ") == null)
    val inv = DesiredCrossProfileInventory(emptyList()).upsert(rule)
    check(inv.rules.single().id == "view-web")
    check(inv.remove("view-web").rules.isEmpty())
    println("WorkProfileLifecycleTest: PASS")
}
