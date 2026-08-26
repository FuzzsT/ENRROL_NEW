package io.dpcaio.policy

enum class CrossProfileDirection {
    MANAGED_TO_PARENT,
    PARENT_TO_MANAGED,
    BIDIRECTIONAL,
}

data class CrossProfileIntentRule(
    val id: String,
    val action: String,
    val categories: Set<String> = emptySet(),
    val mimeType: String? = null,
    val scheme: String? = null,
    val direction: CrossProfileDirection,
) {
    fun valid(): Boolean {
        if (id.isBlank() || action.isBlank()) return false
        if (id.length > 96 || action.length > 256) return false
        if (categories.any { it.isBlank() || it.length > 256 }) return false
        if (mimeType?.isBlank() == true || scheme?.isBlank() == true) return false
        return true
    }
}

data class DesiredCrossProfileInventory(
    val rules: List<CrossProfileIntentRule>,
) {
    fun upsert(rule: CrossProfileIntentRule): DesiredCrossProfileInventory {
        require(rule.valid()) { "Invalid cross-profile rule" }
        return DesiredCrossProfileInventory((rules.filterNot { it.id == rule.id } + rule).sortedBy { it.id })
    }

    fun remove(id: String): DesiredCrossProfileInventory = DesiredCrossProfileInventory(rules.filterNot { it.id == id })
    fun clear(): DesiredCrossProfileInventory = DesiredCrossProfileInventory(emptyList())
}

enum class ManagedProfileLifecycleState {
    NOT_MANAGED_PROFILE,
    ACTIVE,
    QUIET_MODE,
    LOCKED,
    UNKNOWN,
}

data class WorkProfileSnapshot(
    val managedProfile: Boolean,
    val profileName: String?,
    val lifecycleState: ManagedProfileLifecycleState,
    val desiredCrossProfileRules: List<CrossProfileIntentRule>,
)

object ProfileNamePolicy {
    fun normalize(value: String): String? = value.trim().takeIf { it.isNotEmpty() }?.take(100)
}
