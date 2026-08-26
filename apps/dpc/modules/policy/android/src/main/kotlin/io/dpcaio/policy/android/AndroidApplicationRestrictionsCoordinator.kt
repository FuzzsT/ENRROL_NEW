package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import java.util.concurrent.Executors

data class ApplicationRestrictionsReadback(
    val packageName: String,
    val requested: Map<String, String>,
    val observed: Map<String, String>,
    val canonicalRequested: String,
    val canonicalObserved: String,
    val verified: Boolean,
    val detail: String,
)

class AndroidApplicationRestrictionsCoordinator(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "dpc-app-restrictions-readback").apply { isDaemon = true } }

    fun applyAsync(packageName: String, restrictions: Map<String, String>, callback: (ApplicationRestrictionsReadback) -> Unit) {
        val requested = restrictions.toSortedMap()
        executor.execute {
            val result = runCatching {
                val bundle = Bundle().apply { requested.forEach { (key, value) -> putString(key, value) } }
                dpm.setApplicationRestrictions(admin, packageName, bundle)
                val observedBundle = dpm.getApplicationRestrictions(admin, packageName)
                val observed = canonicalMap(observedBundle)
                val canonicalRequested = canonical(requested)
                val canonicalObserved = canonical(observed)
                ApplicationRestrictionsReadback(
                    packageName,
                    requested,
                    observed,
                    canonicalRequested,
                    canonicalObserved,
                    canonicalRequested == canonicalObserved,
                    if (canonicalRequested == canonicalObserved) "READBACK_VERIFIED" else "READBACK_MISMATCH",
                )
            }.getOrElse {
                ApplicationRestrictionsReadback(packageName, requested, emptyMap(), canonical(requested), "", false, "READBACK_FAILED:${it.javaClass.simpleName}")
            }
            appContext.mainExecutor.execute { callback(result) }
        }
    }

    private fun canonicalMap(bundle: Bundle): Map<String, String> = bundle.keySet().sorted().associateWith { key -> bundle.getString(key).orEmpty() }
    private fun canonical(map: Map<String, String>): String = map.toSortedMap().entries.joinToString("\u001f") { (key, value) -> "$key\u001e$value" }
}
