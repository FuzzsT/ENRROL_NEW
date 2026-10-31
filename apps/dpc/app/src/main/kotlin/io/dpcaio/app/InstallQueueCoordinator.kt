package io.dpcaio.app

import android.content.pm.PackageInstaller

class InstallQueueCoordinator(
    private val store: InstallQueueStore,
    private val authorization: InstallAuthorizationCoordinator = InstallAuthorizationCoordinator(store.hostContext),
) {
    fun enqueue(items: List<InstallQueueItem>): List<InstallQueueItem> {
        val merged = store.loadQueue() + items
        store.replaceQueue(merged)
        return merged
    }

    fun startOrResume(): InstallQueueItem? {
        val next = store.loadQueue().firstOrNull { it.state == InstallQueueState.QUEUED } ?: return null
        if (!authorization.consumeAuthorization()) {
            authorization.requestInstallAuthorization(store.hostContext)
            return null
        }
        val updated = next.copy(state = InstallQueueState.PREFLIGHT, updatedAt = System.currentTimeMillis())
        store.updateItem(updated)
        return updated
    }

    fun markInstalling(itemId: String, sessionId: Int, packageName: String?, versionCode: Long?): InstallQueueItem? {
        val item = store.loadQueue().firstOrNull { it.id == itemId } ?: return null
        val updated = item.copy(
            state = InstallQueueState.INSTALLING,
            sessionId = sessionId,
            resolvedPackage = packageName ?: item.resolvedPackage,
            versionCode = versionCode ?: item.versionCode,
            updatedAt = System.currentTimeMillis(),
        )
        store.updateItem(updated)
        return updated
    }

    fun failPreflight(itemId: String, detail: String): InstallQueueItem? = finish(itemId, InstallQueueState.FAILED, detail)

    fun skip(itemId: String, detail: String): InstallQueueItem? = finish(itemId, InstallQueueState.SKIPPED, detail)

    fun onPackageInstallerResult(itemId: String, sessionId: Int?, status: Int, packageName: String?, detail: String?): InstallQueueItem? {
        val current = store.loadQueue().firstOrNull { it.id == itemId } ?: return null
        if (sessionId != null && current.sessionId != null && current.sessionId != sessionId) return null
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val waiting = current.copy(
                state = InstallQueueState.WAITING_USER,
                resolvedPackage = packageName ?: current.resolvedPackage,
                detail = detail,
                updatedAt = System.currentTimeMillis(),
            )
            store.updateItem(waiting)
            return waiting
        }
        return if (status == PackageInstaller.STATUS_SUCCESS) {
            finish(itemId, InstallQueueState.SUCCESS, detail, packageName)
        } else {
            finish(itemId, InstallQueueState.FAILED, detail ?: "PackageInstaller status=$status", packageName)
        }
    }

    fun retryFailed(): List<InstallQueueItem> {
        val next = store.loadQueue().map {
            if (it.state == InstallQueueState.FAILED) {
                it.copy(state = InstallQueueState.QUEUED, sessionId = null, detail = null, updatedAt = System.currentTimeMillis())
            } else it
        }
        store.replaceQueue(next)
        return next
    }

    fun clearCompleted(): List<InstallQueueItem> = store.clearCompleted()

    fun recoverInterrupted(): List<InstallQueueItem> {
        val interrupted = setOf(InstallQueueState.PREFLIGHT, InstallQueueState.INSTALLING, InstallQueueState.WAITING_USER)
        val next = store.loadQueue().map {
            if (it.state in interrupted) {
                it.copy(
                    state = InstallQueueState.FAILED,
                    detail = "Interrupted install requires retry",
                    updatedAt = System.currentTimeMillis(),
                )
            } else it
        }
        store.replaceQueue(next)
        return next
    }

    private fun finish(itemId: String, state: InstallQueueState, detail: String?, packageName: String? = null): InstallQueueItem? {
        val item = store.loadQueue().firstOrNull { it.id == itemId } ?: return null
        val updated = item.copy(
            state = state,
            resolvedPackage = packageName ?: item.resolvedPackage,
            detail = detail,
            updatedAt = System.currentTimeMillis(),
        )
        store.updateItem(updated)
        store.appendHistory(
            InstallHistoryEntry(
                itemId = updated.id,
                timestamp = updated.updatedAt,
                packageName = updated.resolvedPackage,
                versionCode = updated.versionCode,
                displayName = updated.displayName,
                result = updated.state,
                detail = updated.detail,
            )
        )
        return updated
    }
}
