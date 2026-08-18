package io.dpcaio.permission.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SpecialAccessIntentFactory {
    fun overlay(packageName: String) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    fun writeSettings(packageName: String) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
    fun unknownSources(packageName: String) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
    fun usageAccess() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun notificationListener() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
