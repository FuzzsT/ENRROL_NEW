package io.dpcaio.app.engineering

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

sealed interface BroadcastExtra {
    val key: String

    data class StringValue(override val key: String, val value: String) : BroadcastExtra
    data class IntValue(override val key: String, val value: Int) : BroadcastExtra
    data class BooleanValue(override val key: String, val value: Boolean) : BroadcastExtra
    data class UriValue(override val key: String, val value: Uri) : BroadcastExtra
}

data class BroadcastRequest(
    val action: String,
    val packageName: String? = null,
    val component: ComponentName? = null,
    val data: Uri? = null,
    val extras: List<BroadcastExtra> = emptyList(),
)

object BroadcastIntentBuilder {
    fun build(request: BroadcastRequest): Intent {
        require(request.action.isNotBlank()) { "Broadcast action is required" }
        val intent = Intent(request.action)
        request.packageName?.trim()?.takeIf { it.isNotEmpty() }?.let(intent::setPackage)
        request.component?.let(intent::setComponent)
        request.data?.let(intent::setData)
        request.extras.forEach { extra ->
            require(extra.key.isNotBlank()) { "Broadcast extra key is required" }
            when (extra) {
                is BroadcastExtra.StringValue -> intent.putExtra(extra.key, extra.value)
                is BroadcastExtra.IntValue -> intent.putExtra(extra.key, extra.value)
                is BroadcastExtra.BooleanValue -> intent.putExtra(extra.key, extra.value)
                is BroadcastExtra.UriValue -> intent.putExtra(extra.key, extra.value)
            }
        }
        return intent
    }
}
