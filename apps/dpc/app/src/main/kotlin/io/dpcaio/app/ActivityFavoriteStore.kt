package io.dpcaio.app

import android.content.Context

class ActivityFavoriteStore(context: Context) {
    private val storageContext = context.createDeviceProtectedStorageContext()
    private val prefs = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAppFavorite(packageName: String): Boolean = appKey(packageName) in favoriteItems()

    fun isActivityFavorite(packageName: String, className: String): Boolean =
        activityKey(packageName, className) in favoriteItems()

    fun toggleAppFavorite(packageName: String): Boolean = toggleFavorite(appKey(packageName))

    fun toggleActivityFavorite(packageName: String, className: String): Boolean =
        toggleFavorite(activityKey(packageName, className))

    fun groups(): Set<String> = prefs.getStringSet(KEY_GROUP_NAMES, emptySet()).orEmpty().toSortedSet()

    fun createGroup(name: String): Boolean {
        val normalized = normalizeGroup(name) ?: return false
        val groups = groups().toMutableSet()
        if (!groups.add(normalized)) return false
        prefs.edit().putStringSet(KEY_GROUP_NAMES, groups).apply()
        return true
    }

    fun renameGroup(oldName: String, newName: String): Boolean {
        val old = normalizeGroup(oldName) ?: return false
        val new = normalizeGroup(newName) ?: return false
        if (old == new || old !in groups() || new in groups()) return false
        val members = members(old)
        val groups = groups().toMutableSet().apply { remove(old); add(new) }
        prefs.edit()
            .putStringSet(KEY_GROUP_NAMES, groups)
            .remove(groupKey(old))
            .putStringSet(groupKey(new), members)
            .apply()
        return true
    }

    fun deleteGroup(name: String): Boolean {
        val normalized = normalizeGroup(name) ?: return false
        val groups = groups().toMutableSet()
        if (!groups.remove(normalized)) return false
        prefs.edit().putStringSet(KEY_GROUP_NAMES, groups).remove(groupKey(normalized)).apply()
        return true
    }

    fun setMembership(group: String, itemKey: String, member: Boolean) {
        val normalized = normalizeGroup(group) ?: return
        if (normalized !in groups()) return
        val members = members(normalized).toMutableSet()
        if (member) members.add(itemKey) else members.remove(itemKey)
        prefs.edit().putStringSet(groupKey(normalized), members).apply()
    }

    fun members(group: String): Set<String> {
        val normalized = normalizeGroup(group) ?: return emptySet()
        return prefs.getStringSet(groupKey(normalized), emptySet()).orEmpty().toSet()
    }

    fun groupsFor(itemKey: String): Set<String> = groups().filterTo(linkedSetOf()) { itemKey in members(it) }

    fun appItemKey(packageName: String): String = appKey(packageName)
    fun activityItemKey(packageName: String, className: String): String = activityKey(packageName, className)

    private fun favoriteItems(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    private fun toggleFavorite(itemKey: String): Boolean {
        val items = favoriteItems().toMutableSet()
        val nowFavorite = if (itemKey in items) {
            items.remove(itemKey)
            false
        } else {
            items.add(itemKey)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, items).apply()
        return nowFavorite
    }

    private fun normalizeGroup(name: String): String? = name.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }
    private fun groupKey(name: String): String = "group:$name"
    private fun appKey(packageName: String): String = "app:$packageName"
    private fun activityKey(packageName: String, className: String): String = "activity:$packageName/$className"

    companion object {
        private const val PREFS = "dpc_aio_activity_favorites_v1"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_GROUP_NAMES = "favorite_groups"
    }
}
