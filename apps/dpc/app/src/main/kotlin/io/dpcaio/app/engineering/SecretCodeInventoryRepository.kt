package io.dpcaio.app.engineering

import android.content.Context

class SecretCodeInventoryRepository(context: Context) {
    private val scanner = InstalledPackageSecretCodeScanner(context)
    private val verifier = SecretCodeRuntimeVerifier(context)
    @Volatile private var cached: List<SecretCodeInventoryItem>? = null

    fun scan(
        cancelled: () -> Boolean = { false },
        onEvent: (SecretCodeScanEvent) -> Unit = {},
    ): List<SecretCodeInventoryItem> {
        cached?.let { cachedItems ->
            cachedItems.forEach { onEvent(SecretCodeScanEvent.Candidate(it)) }
            return cachedItems
        }

        val verified = mutableListOf<SecretCodeInventoryItem>()
        for (event in scanner.scan(cancelled = cancelled, onProgress = { onEvent(SecretCodeScanEvent.Progress(it)) })) {
            if (cancelled()) {
                onEvent(SecretCodeScanEvent.Cancelled)
                return verified.distinctBy { dedupeKey(it) }
            }
            when (event) {
                is SecretCodeScanEvent.Candidate -> {
                    val item = verifier.verify(event.item)
                    verified += item
                    onEvent(SecretCodeScanEvent.Candidate(item))
                }
                is SecretCodeScanEvent.Progress -> Unit
                is SecretCodeScanEvent.Diagnostic -> onEvent(event)
                SecretCodeScanEvent.Cancelled -> {
                    onEvent(event)
                    return verified.distinctBy { dedupeKey(it) }
                }
            }
        }

        return verified
            .distinctBy { dedupeKey(it) }
            .sortedWith(
                compareBy<SecretCodeInventoryItem> { it.packageName }
                    .thenBy { it.normalizedCode ?: it.rawHost ?: "" }
                    .thenBy { it.receiverClassName }
                    .thenBy { it.sourceKind.name }
            )
            .also { cached = it }
    }

    fun invalidate() {
        cached = null
    }

    private fun dedupeKey(item: SecretCodeInventoryItem): String = buildString {
        append(item.packageName)
        append('|')
        append(item.receiverClassName)
        append('|')
        append(item.normalizedCode ?: item.rawHost.orEmpty())
        append('|')
        append(item.actions.sorted().joinToString(","))
        append('|')
        append(item.sourceKind.name)
        append('|')
        append(item.sourceApkName)
    }
}
