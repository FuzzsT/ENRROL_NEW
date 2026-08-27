package io.dpcaio.app

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.roundToInt

/** Density-safe padding for programmatic UI. Arguments are dp, not raw pixels. */
fun View.setPaddingDp(left: Int, top: Int, right: Int, bottom: Int) {
    val density = resources.displayMetrics.density
    setPadding(
        (left * density).roundToInt(),
        (top * density).roundToInt(),
        (right * density).roundToInt(),
        (bottom * density).roundToInt(),
    )
}

/**
 * Compact row for filters/actions that must remain usable on narrow displays.
 * Children keep their intrinsic width and the row scrolls horizontally instead of
 * forcing long labels into 50/50 columns.
 */
fun horizontalScrollRow(vararg views: View): HorizontalScrollView {
    require(views.isNotEmpty()) { "horizontalScrollRow requires at least one child" }
    val context = views.first().context
    return HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { child ->
                if (child.parent is ViewGroup) {
                    (child.parent as ViewGroup).removeView(child)
                }
                addView(
                    child,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        })
    }
}

object DpcUiShell {
    fun install(
        activity: Activity,
        content: View,
        baseHorizontalDp: Int = 0,
        baseVerticalDp: Int = 0,
    ) {
        val density = activity.resources.displayMetrics.density
        val extraHorizontal = (baseHorizontalDp * density).roundToInt()
        val extraVertical = (baseVerticalDp * density).roundToInt()
        val baseLeft = content.paddingLeft + extraHorizontal
        val baseTop = content.paddingTop + extraVertical
        val baseRight = content.paddingRight + extraHorizontal
        val baseBottom = content.paddingBottom + extraVertical

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            run {
                activity.window.decorView.systemUiVisibility =
                    activity.window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        }

        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val system = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(
                    baseLeft + system.left,
                    baseTop + system.top,
                    baseRight + system.right,
                    baseBottom + max(system.bottom, ime.bottom),
                )
            } else {
                @Suppress("DEPRECATION")
                val cutout = insets.displayCutout
                @Suppress("DEPRECATION")
                view.setPadding(
                    baseLeft + max(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
                    baseTop + max(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
                    baseRight + max(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
                    baseBottom + max(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0),
                )
            }
            insets
        }
        content.requestApplyInsets()
    }

    fun scroll(
        activity: Activity,
        child: View,
        baseHorizontalDp: Int = 0,
        baseVerticalDp: Int = 0,
    ): ScrollView = ScrollView(activity).apply {
        isFillViewport = true
        clipToPadding = false
        addView(child)
        install(activity, this, baseHorizontalDp, baseVerticalDp)
    }
}
