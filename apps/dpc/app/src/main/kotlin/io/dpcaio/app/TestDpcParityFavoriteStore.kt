package io.dpcaio.app

import android.content.Context

class TestDpcParityFavoriteStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFavorite(id: String): Boolean = prefs.getBoolean(key(id), false)

    fun setFavorite(id: String, favorite: Boolean) {
        prefs.edit().putBoolean(key(id), favorite).apply()
    }

    fun toggle(id: String): Boolean {
        val favorite = !isFavorite(id)
        setFavorite(id, favorite)
        return favorite
    }

    private fun key(id: String): String = "parity:$id"

    companion object {
        private const val PREFS = "dpc_aio_testdpc_parity_favorites_v1"
    }
}
