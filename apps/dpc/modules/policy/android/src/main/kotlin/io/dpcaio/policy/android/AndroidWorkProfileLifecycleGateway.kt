package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Process
import android.os.UserManager
import io.dpcaio.policy.CrossProfileDirection
import io.dpcaio.policy.CrossProfileIntentRule
import io.dpcaio.policy.DesiredCrossProfileInventory
import io.dpcaio.policy.ManagedProfileLifecycleState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.policy.ProfileNamePolicy
import io.dpcaio.policy.WorkProfileSnapshot
import org.json.JSONArray
import org.json.JSONObject

class AndroidWorkProfileLifecycleGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val prefs = appContext.getSharedPreferences("dpc-aio-work-profile-rules", Context.MODE_PRIVATE)

    fun snapshot(): WorkProfileSnapshot {
        val managed = runCatching { userManager.isManagedProfile }.getOrDefault(false)
        val profileName = runCatching { userManager.userName }.getOrNull()
        val state = when {
            !managed -> ManagedProfileLifecycleState.NOT_MANAGED_PROFILE
            runCatching { userManager.isQuietModeEnabled(Process.myUserHandle()) }.getOrDefault(false) -> ManagedProfileLifecycleState.QUIET_MODE
            !runCatching { userManager.isUserUnlocked }.getOrDefault(true) -> ManagedProfileLifecycleState.LOCKED
            else -> ManagedProfileLifecycleState.ACTIVE
        }
        return WorkProfileSnapshot(managed, profileName, state, loadInventory().rules)
    }

    fun setProfileName(name: String): PolicyResult<String> {
        val normalized = ProfileNamePolicy.normalize(name)
            ?: return PolicyResult.failure(PolicyStatus.FAILED, "Profile name cannot be blank")
        return policyCall {
            dpm.setProfileName(admin, normalized)
            val observed = runCatching { userManager.userName }.getOrNull()
            if (observed == normalized) PolicyResult.success(normalized)
            else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Profile name readback mismatch")
        }
    }

    fun setProfileEnabled(): PolicyResult<Unit> = policyCall {
        dpm.setProfileEnabled(admin)
        PolicyResult.success()
    }

    fun upsertCrossProfileRule(rule: CrossProfileIntentRule): PolicyResult<DesiredCrossProfileInventory> {
        if (!rule.valid()) return PolicyResult.failure(PolicyStatus.FAILED, "Invalid cross-profile rule")
        return policyCall {
            val filter = IntentFilter().apply {
                addAction(rule.action)
                rule.categories.sorted().forEach(::addCategory)
                rule.scheme?.let(::addDataScheme)
                rule.mimeType?.let(::addDataType)
            }
            dpm.addCrossProfileIntentFilter(admin, filter, directionFlags(rule.direction))
            val next = loadInventory().upsert(rule)
            saveInventory(next)
            PolicyResult.success(next)
        }
    }

    /** Clears only filters created by this admin, then clears the DPC's desired-rule inventory. */
    fun clearDpcRules(): PolicyResult<DesiredCrossProfileInventory> = policyCall {
        dpm.clearCrossProfileIntentFilters(admin)
        val next = DesiredCrossProfileInventory(emptyList())
        saveInventory(next)
        PolicyResult.success(next)
    }

    fun desiredInventory(): DesiredCrossProfileInventory = loadInventory()

    private fun directionFlags(direction: CrossProfileDirection): Int = when (direction) {
        CrossProfileDirection.MANAGED_TO_PARENT -> DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        CrossProfileDirection.PARENT_TO_MANAGED -> DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        CrossProfileDirection.BIDIRECTIONAL -> DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
    }

    private fun loadInventory(): DesiredCrossProfileInventory {
        val raw = prefs.getString(KEY_RULES, null) ?: return DesiredCrossProfileInventory(emptyList())
        return runCatching {
            val array = JSONArray(raw)
            val rules = buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val cats = buildSet {
                        val a = o.optJSONArray("categories") ?: JSONArray()
                        for (j in 0 until a.length()) add(a.getString(j))
                    }
                    add(
                        CrossProfileIntentRule(
                            id = o.getString("id"),
                            action = o.getString("action"),
                            categories = cats,
                            mimeType = o.optString("mimeType").takeIf { it.isNotBlank() },
                            scheme = o.optString("scheme").takeIf { it.isNotBlank() },
                            direction = CrossProfileDirection.valueOf(o.getString("direction")),
                        )
                    )
                }
            }
            DesiredCrossProfileInventory(rules.filter { it.valid() }.sortedBy { it.id })
        }.getOrElse { DesiredCrossProfileInventory(emptyList()) }
    }

    private fun saveInventory(inventory: DesiredCrossProfileInventory) {
        val array = JSONArray()
        inventory.rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("action", rule.action)
                put("categories", JSONArray(rule.categories.sorted()))
                put("mimeType", rule.mimeType ?: "")
                put("scheme", rule.scheme ?: "")
                put("direction", rule.direction.name)
            })
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (e: SecurityException) {
        PolicyResult.failure(PolicyStatus.SECURITY_EXCEPTION, e.message ?: "SecurityException", e.javaClass.name)
    } catch (e: RuntimeException) {
        PolicyResult.failure(PolicyStatus.FAILED, e.message ?: e.javaClass.simpleName, e.javaClass.name)
    }

    companion object { private const val KEY_RULES = "rules" }
}
