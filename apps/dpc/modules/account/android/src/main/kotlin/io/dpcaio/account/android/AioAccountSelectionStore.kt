package io.dpcaio.account.android

import android.content.Context

class AioAccountSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedAccountName(): String? = preferences.getString(KEY_SELECTED, null)

    fun setSelectedAccountName(accountName: String?) {
        preferences.edit().apply {
            if (accountName == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, accountName)
        }.apply()
    }

    fun savePendingReAdd(targetName: String, accountNames: List<String>, restoreManagementDisabled: Boolean = false) {
        preferences.edit()
            .putString(KEY_REORDER_TARGET, targetName)
            .putString(KEY_PENDING_READD, accountNames.joinToString("\n"))
            .putBoolean(KEY_RESTORE_MANAGEMENT_DISABLED, restoreManagementDisabled)
            .apply()
    }

    fun pendingTarget(): String? = preferences.getString(KEY_REORDER_TARGET, null)

    fun shouldRestoreManagementDisabled(): Boolean = preferences.getBoolean(KEY_RESTORE_MANAGEMENT_DISABLED, false)

    fun pendingReAdd(): List<String> = preferences.getString(KEY_PENDING_READD, null)
        ?.lineSequence()
        ?.filter { it.isNotBlank() }
        ?.toList()
        .orEmpty()

    fun markReAdded(accountName: String) {
        val remaining = pendingReAdd().toMutableList().apply { remove(accountName) }
        if (remaining.isEmpty()) clearPendingReorder()
        else preferences.edit().putString(KEY_PENDING_READD, remaining.joinToString("\n")).apply()
    }

    fun clearPendingReorder() {
        preferences.edit().remove(KEY_REORDER_TARGET).remove(KEY_PENDING_READD).remove(KEY_RESTORE_MANAGEMENT_DISABLED).apply()
    }

    private companion object {
        const val PREFS = "google_account_manager"
        const val KEY_SELECTED = "selected_account"
        const val KEY_REORDER_TARGET = "reorder_target"
        const val KEY_PENDING_READD = "pending_readd"
        const val KEY_RESTORE_MANAGEMENT_DISABLED = "restore_management_disabled"
    }
}
