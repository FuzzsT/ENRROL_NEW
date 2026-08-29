package io.dpcaio.policy.android.parity

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.UserHandle
import android.os.UserManager
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

class AndroidUserParityGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    fun createAndManageUser(name: String, flags: Int): PolicyResult<String> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Create and manage user requires API 24+")
        return policyCall {
            val user = dpm.createAndManageUser(admin, name, admin, PersistableBundle(), flags)
                ?: return@policyCall PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "DevicePolicyManager rejected user creation",
                )
            val serial = userManager.getSerialNumberForUser(user)
            PolicyResult.success(serial.toString(), "Managed user created: serial=$serial")
        }
    }

    fun removeUser(userSerial: String): PolicyResult<Unit> {
        val user = resolveUser(userSerial) ?: return userNotFound(userSerial)
        return policyCall {
            if (!dpm.removeUser(admin, user)) {
                PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "DevicePolicyManager rejected user removal")
            } else {
                PolicyResult.success(message = "User removed: serial=${userManager.getSerialNumberForUser(user)}")
            }
        }
    }

    fun switchUser(userSerial: String): PolicyResult<Unit> {
        val user = resolveUser(userSerial) ?: return userNotFound(userSerial)
        return policyCall {
            if (!dpm.switchUser(admin, user)) {
                PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "DevicePolicyManager rejected user switch")
            } else {
                PolicyResult.success(message = "User switch requested")
            }
        }
    }

    fun startUserInBackground(userSerial: String): PolicyResult<Int> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Start user in background requires API 28+")
        val user = resolveUser(userSerial) ?: return userNotFound(userSerial)
        return policyCall {
            val result = dpm.startUserInBackground(admin, user)
            PolicyResult.success(result, "startUserInBackground result=$result")
        }
    }

    fun stopUser(userSerial: String): PolicyResult<Int> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Stop user requires API 28+")
        val user = resolveUser(userSerial) ?: return userNotFound(userSerial)
        return policyCall {
            val result = dpm.stopUser(admin, user)
            PolicyResult.success(result, "stopUser result=$result")
        }
    }

    fun logoutUser(): PolicyResult<Int> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Logout user requires API 28+")
        return policyCall {
            val result = dpm.logoutUser(admin)
            PolicyResult.success(result, "logoutUser result=$result")
        }
    }

    fun setLogoutEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Logout policy requires API 28+")
        return policyCall {
            dpm.setLogoutEnabled(admin, enabled)
            PolicyResult.success(message = "Logout enabled=$enabled")
        }
    }

    fun setUserSessionMessages(start: String?, end: String?): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("User session messages require API 28+")
        return policyCall {
            dpm.setStartUserSessionMessage(admin, start)
            dpm.setEndUserSessionMessage(admin, end)
            PolicyResult.success(message = "User session messages updated")
        }
    }

    fun setUserRestriction(key: String, enabled: Boolean, parent: Boolean): PolicyResult<Unit> {
        if (parent && Build.VERSION.SDK_INT < 30) {
            return unsupported("Parent-profile user restrictions require API 30+")
        }
        return policyCall {
            val target = if (parent) dpm.getParentProfileInstance(admin) else dpm
            if (enabled) target.addUserRestriction(admin, key) else target.clearUserRestriction(admin, key)
            PolicyResult.success(message = "User restriction $key enabled=$enabled parent=$parent")
        }
    }

    fun setShortSupportMessage(message: String?): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Short support message requires API 24+")
        return policyCall {
            dpm.setShortSupportMessage(admin, message)
            PolicyResult.success(message = "Short support message updated")
        }
    }

    fun setLongSupportMessage(message: String?): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Long support message requires API 24+")
        return policyCall {
            dpm.setLongSupportMessage(admin, message)
            PolicyResult.success(message = "Long support message updated")
        }
    }

    fun isAffiliatedUser(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Affiliated-user query requires API 28+")
        return policyCall { PolicyResult.success(dpm.isAffiliatedUser) }
    }

    fun isEphemeralUser(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Ephemeral-user query requires API 28+")
        return policyCall { PolicyResult.success(dpm.isEphemeralUser(admin)) }
    }

    fun requestBugreport(): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Bugreport request requires API 24+")
        return policyCall {
            if (!dpm.requestBugreport(admin)) {
                PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "DevicePolicyManager rejected bugreport request")
            } else {
                PolicyResult.success(message = "Bugreport requested")
            }
        }
    }

    fun setBackupServiceEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 26) return unsupported("Backup service policy requires API 26+")
        return policyCall {
            dpm.setBackupServiceEnabled(admin, enabled)
            PolicyResult.success(message = "Backup service enabled=$enabled")
        }
    }

    fun setCommonCriteriaModeEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Common Criteria mode requires API 30+")
        return policyCall {
            dpm.setCommonCriteriaModeEnabled(admin, enabled)
            PolicyResult.success(message = "Common Criteria mode enabled=$enabled")
        }
    }

    fun reboot(): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Reboot requires API 24+")
        return policyCall {
            dpm.reboot(admin)
            PolicyResult.success(message = "Reboot requested")
        }
    }

    fun wipeManagedProfile(flags: Int = 0): PolicyResult<Unit> = policyCall {
        dpm.wipeData(flags)
        PolicyResult.success(message = "Managed profile wipe requested")
    }

    fun factoryResetDevice(flags: Int = 0): PolicyResult<Unit> = policyCall {
        if (Build.VERSION.SDK_INT >= 34) {
            dpm.wipeDevice(flags)
        } else {
            @Suppress("DEPRECATION")
            dpm.wipeData(flags)
        }
        PolicyResult.success(message = "Factory reset requested")
    }

    fun transferOwnership(target: ComponentName): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Ownership transfer requires API 28+")
        return policyCall {
            dpm.transferOwnership(admin, target, PersistableBundle())
            PolicyResult.success(message = "Ownership transfer requested: ${target.flattenToShortString()}")
        }
    }

    private fun resolveUser(serialText: String): UserHandle? {
        val serial = serialText.trim().removePrefix("serial:").trim().toLongOrNull() ?: return null
        return userManager.getUserForSerialNumber(serial)
    }

    private fun <T> userNotFound(serialText: String): PolicyResult<T> =
        PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "USER_NOT_FOUND: serial=${serialText.trim()}")

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (error: SecurityException) {
        PolicyResult.failure(
            PolicyStatus.SECURITY_EXCEPTION,
            error.message ?: "SECURITY_EXCEPTION",
            error.javaClass.name,
        )
    } catch (error: IllegalArgumentException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: "INVALID_ARGUMENT",
            error.javaClass.name,
        )
    } catch (error: RuntimeException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: error.javaClass.simpleName,
            error.javaClass.name,
        )
    }

    private fun <T> unsupported(message: String): PolicyResult<T> =
        PolicyResult.failure(PolicyStatus.UNSUPPORTED, message)
}
