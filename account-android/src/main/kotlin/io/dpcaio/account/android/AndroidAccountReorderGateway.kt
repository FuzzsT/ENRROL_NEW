package io.dpcaio.account.android

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class AndroidAccountReorderGateway(
    context: Context,
    private val admin: ComponentName
) {
    private val appContext = context.applicationContext
    private val accountManager = AccountManager.get(appContext)
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)

    fun isAccountManagementDisabled(accountType: String = AndroidGoogleAccountRepository.GOOGLE_ACCOUNT_TYPE): Boolean =
        devicePolicyManager.getAccountTypesWithManagementDisabled()?.contains(accountType) == true

    fun setGoogleAccountManagementDisabled(disabled: Boolean) {
        devicePolicyManager.setAccountManagementDisabled(
            admin,
            AndroidGoogleAccountRepository.GOOGLE_ACCOUNT_TYPE,
            disabled
        )
    }

    fun canRequestAccountRemoval(): Boolean =
        devicePolicyManager.isProfileOwnerApp(appContext.packageName) ||
            appContext.checkSelfPermission("android.permission.REMOVE_ACCOUNTS") == PackageManager.PERMISSION_GRANTED

    fun requestRemoveGoogleAccount(
        activity: Activity,
        accountName: String,
        callback: AccountManagerCallback
    ) {
        val account = Account(accountName, AndroidGoogleAccountRepository.GOOGLE_ACCOUNT_TYPE)
        accountManager.removeAccount(account, activity, { future ->
            runCatching { future.result }
                .onSuccess { bundle -> callback.onResult(bundle.getBoolean(AccountManager.KEY_BOOLEAN_RESULT, false), null) }
                .onFailure { callback.onResult(false, it) }
        }, null)
    }

    fun interface AccountManagerCallback {
        fun onResult(removed: Boolean, error: Throwable?)
    }
}
