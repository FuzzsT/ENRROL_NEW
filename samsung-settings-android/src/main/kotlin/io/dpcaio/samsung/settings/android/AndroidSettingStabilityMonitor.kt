package io.dpcaio.samsung.settings.android

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

class AndroidSettingStabilityMonitor(private val resolver: ContentResolver) {
    fun observe(uri: Uri, onChanged: () -> Unit): AutoCloseable {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }
        resolver.registerContentObserver(uri, false, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }
}
