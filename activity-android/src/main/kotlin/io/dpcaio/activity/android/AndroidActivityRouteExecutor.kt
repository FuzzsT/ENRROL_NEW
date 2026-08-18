package io.dpcaio.activity.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import io.dpcaio.activity.ActivityAccessInput
import io.dpcaio.activity.ActivityRoute
import io.dpcaio.activity.ActivityRouteExecutor
import io.dpcaio.activity.ActivityRouteResult

class AndroidActivityRouteExecutor(
    context: Context,
    private val user: UserHandle,
    private val deepLinkResolver: (ActivityAccessInput) -> Uri? = { null }
) : ActivityRouteExecutor {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    override fun execute(route: ActivityRoute, input: ActivityAccessInput): ActivityRouteResult {
        return try {
            when (route) {
                ActivityRoute.LAUNCHER_APPS -> {
                    val component = ComponentName(input.packageName, normalizeClassName(input.packageName, input.className))
                    launcherApps.startMainActivity(component, user, null, null)
                    ActivityRouteResult(route, true)
                }
                ActivityRoute.FRAMEWORK_EXPLICIT, ActivityRoute.SAME_UID -> {
                    val intent = Intent().apply {
                        component = ComponentName(input.packageName, normalizeClassName(input.packageName, input.className))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    ActivityRouteResult(route, true)
                }
                ActivityRoute.DEEP_LINK -> {
                    val uri = deepLinkResolver(input)
                        ?: return ActivityRouteResult(route, false, "No deep-link URI registered")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(input.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    ActivityRouteResult(route, true)
                }
                else -> ActivityRouteResult(route, false, "Route requires another executor")
            }
        } catch (e: SecurityException) {
            ActivityRouteResult(route, false, "SecurityException: ${e.message ?: "denied"}")
        } catch (e: RuntimeException) {
            ActivityRouteResult(route, false, "${e.javaClass.simpleName}: ${e.message ?: "failed"}")
        }
    }

    private fun normalizeClassName(packageName: String, className: String): String = when {
        className.startsWith('.') -> packageName + className
        '.' !in className -> "$packageName.$className"
        else -> className
    }
}
