package io.dpcaio.app

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import java.util.Collections
import java.util.WeakHashMap

/**
 * Global UI guard for the View-based app surface.
 *
 * Android 15+ enforces edge-to-edge for modern target SDKs. Most DPC-AIO screens
 * are programmatic Views, so a single lifecycle hook is safer than repeating
 * status/navigation-bar padding logic in every Activity.
 */
object UiSurfaceController : Application.ActivityLifecycleCallbacks {
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private val basePadding = WeakHashMap<View, Padding>()
    private val animatedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        configureSystemBars(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        installSafeAreaInsets(content)
        animateEntranceOnce(activity, content)
    }

    private fun configureSystemBars(activity: Activity) {
        val window = activity.window
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            configureLegacySystemBarColors(activity)
        }
        window.isNavigationBarContrastEnforced = true
    }

    @Suppress("DEPRECATION")
    private fun configureLegacySystemBarColors(activity: Activity) {
        val window = activity.window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun installSafeAreaInsets(content: View) {
        val baseline = synchronized(basePadding) {
            basePadding.getOrPut(content) {
                Padding(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
            }
        }

        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(
                    baseline.left + bars.left,
                    baseline.top + bars.top,
                    baseline.right + bars.right,
                    baseline.bottom + bars.bottom,
                )
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    baseline.left + insets.systemWindowInsetLeft,
                    baseline.top + insets.systemWindowInsetTop,
                    baseline.right + insets.systemWindowInsetRight,
                    baseline.bottom + insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        content.requestApplyInsets()
    }

    private fun animateEntranceOnce(activity: Activity, content: View) {
        if (!animatedActivities.add(activity)) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            content.alpha = 1f
            content.translationY = 0f
            return
        }
        val distance = content.resources.displayMetrics.density * 8f
        content.alpha = 0f
        content.translationY = distance
        content.post {
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        animatedActivities.remove(activity)
    }
}
