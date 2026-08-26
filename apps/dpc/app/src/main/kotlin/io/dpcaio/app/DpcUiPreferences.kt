package io.dpcaio.app

import android.content.Context

object DpcUiPreferences {
    private const val PREFS = "dpc_ui"
    private const val KEY_SHOW_HIDDEN = "show_hidden"
    private const val KEY_DEVELOPER_MODE = "developer_mode"
    private const val KEY_SELECTED_FILTER = "selected_filter"
    private const val KEY_SHOW_EXPERIMENTAL = "show_experimental"

    data class State(
        val showHidden: Boolean = false,
        val developerMode: Boolean = false,
        val selectedFilter: String = "all",
        val showExperimental: Boolean = false,
    )

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): State {
        val p = prefs(context)
        return State(
            showHidden = p.getBoolean(KEY_SHOW_HIDDEN, false),
            developerMode = p.getBoolean(KEY_DEVELOPER_MODE, false),
            selectedFilter = p.getString(KEY_SELECTED_FILTER, "all") ?: "all",
            showExperimental = p.getBoolean(KEY_SHOW_EXPERIMENTAL, false),
        )
    }

    fun setShowHidden(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_HIDDEN, enabled).apply()
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    fun setSelectedFilter(context: Context, filter: String) {
        prefs(context).edit().putString(KEY_SELECTED_FILTER, filter).apply()
    }

    fun setShowExperimental(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_EXPERIMENTAL, enabled).apply()
    }
}
