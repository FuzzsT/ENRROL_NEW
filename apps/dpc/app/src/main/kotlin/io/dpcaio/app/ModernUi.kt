package io.dpcaio.app

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Lightweight design system for the classic View-based DPC-AIO UI. */
object ModernUi {
    enum class Tone { NEUTRAL, SUCCESS, WARNING, DANGER }

    data class Palette(
        val page: Int,
        val surface: Int,
        val surfaceRaised: Int,
        val border: Int,
        val text: Int,
        val secondaryText: Int,
        val accent: Int,
        val success: Int,
        val warning: Int,
        val danger: Int,
    )

    fun palette(activity: Activity): Palette {
        val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val accent = themeColor(activity, android.R.attr.colorAccent, Color.rgb(78, 121, 255))
        return if (dark) {
            Palette(
                page = Color.rgb(13, 16, 21),
                surface = Color.rgb(26, 30, 38),
                surfaceRaised = Color.rgb(33, 38, 48),
                border = Color.rgb(55, 62, 75),
                text = Color.rgb(245, 247, 252),
                secondaryText = Color.rgb(178, 187, 204),
                accent = accent,
                success = Color.rgb(72, 196, 126),
                warning = Color.rgb(245, 181, 67),
                danger = Color.rgb(239, 94, 94),
            )
        } else {
            Palette(
                page = Color.rgb(244, 247, 251),
                surface = Color.WHITE,
                surfaceRaised = Color.rgb(250, 251, 253),
                border = Color.rgb(222, 227, 235),
                text = Color.rgb(26, 31, 40),
                secondaryText = Color.rgb(87, 97, 115),
                accent = accent,
                success = Color.rgb(30, 153, 90),
                warning = Color.rgb(196, 126, 24),
                danger = Color.rgb(202, 63, 63),
            )
        }
    }

    fun page(activity: Activity): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun page(activity: Activity, content: LinearLayout): ScrollView = scrollPage(activity, content)

    fun scrollPage(activity: Activity, content: LinearLayout): ScrollView {
        val colors = palette(activity)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 30))
        content.setBackgroundColor(colors.page)
        return ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(colors.page)
            addView(content)
        }
    }

    fun hero(activity: Activity, title: String, subtitle: String, eyebrow: String? = null): LinearLayout {
        val colors = palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 18))
            background = rounded(colors.surface, colors.border, dp(activity, 22), dp(activity, 1))
            elevation = dp(activity, 2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(activity, 18) }

            if (!eyebrow.isNullOrBlank()) {
                addView(TextView(activity).apply {
                    text = eyebrow.uppercase()
                    setTextColor(colors.accent)
                    textSize = 11.5f
                    letterSpacing = 0.08f
                    setTypeface(typeface, Typeface.BOLD)
                })
            }
            addView(TextView(activity).apply {
                text = title
                setTextColor(colors.text)
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                if (!eyebrow.isNullOrBlank()) setPadding(0, dp(activity, 7), 0, 0)
            })
            addView(TextView(activity).apply {
                text = subtitle
                setTextColor(colors.secondaryText)
                textSize = 13.5f
                setPadding(0, dp(activity, 6), 0, 0)
            })
        }
    }

    fun section(activity: Activity, label: String): TextView {
        val colors = palette(activity)
        return TextView(activity).apply {
            text = label.uppercase()
            setTextColor(colors.accent)
            textSize = 11.5f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(activity, 4), dp(activity, 13), dp(activity, 4), dp(activity, 9))
        }
    }

    fun card(activity: Activity, emphasized: Boolean = false): LinearLayout {
        val colors = palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15))
            background = rounded(
                if (emphasized) colors.surfaceRaised else colors.surface,
                if (emphasized) withAlpha(colors.accent, 85) else colors.border,
                dp(activity, 18),
                dp(activity, 1),
            )
            elevation = dp(activity, if (emphasized) 2 else 1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(activity, 10) }
        }
    }

    fun compactCard(activity: Activity, tone: Tone = Tone.NEUTRAL): LinearLayout {
        val colors = palette(activity)
        val accent = toneColor(colors, tone)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13))
            background = rounded(
                colors.surface,
                if (tone == Tone.NEUTRAL) colors.border else withAlpha(accent, 105),
                dp(activity, 20),
                dp(activity, 1),
            )
            elevation = dp(activity, 1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(activity, 9) }
        }
    }

    fun title(activity: Activity, text: String): TextView {
        val colors = palette(activity)
        return TextView(activity).apply {
            this.text = text
            setTextColor(colors.text)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    fun body(activity: Activity, text: String): TextView {
        val colors = palette(activity)
        return TextView(activity).apply {
            this.text = text
            setTextColor(colors.secondaryText)
            textSize = 13f
            setLineSpacing(0f, 1.12f)
        }
    }

    fun chip(activity: Activity, text: String, tone: String = "neutral"): TextView =
        chip(activity, text, toneFromString(tone))

    fun chip(activity: Activity, text: String, tone: Tone): TextView {
        val colors = palette(activity)
        val foreground = toneColor(colors, tone)
        return TextView(activity).apply {
            this.text = text
            setTextColor(foreground)
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5))
            background = rounded(withAlpha(foreground, 24), withAlpha(foreground, 92), dp(activity, 999), dp(activity, 1))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(activity, 9) }
        }
    }

    fun pill(
        activity: Activity,
        text: String,
        selected: Boolean = false,
        tone: Tone = Tone.NEUTRAL,
        onClick: () -> Unit,
    ): TextView {
        val colors = palette(activity)
        val accent = if (tone == Tone.NEUTRAL) colors.accent else toneColor(colors, tone)
        val fill = if (selected) withAlpha(accent, 42) else colors.surfaceRaised
        val stroke = if (selected) withAlpha(accent, 175) else colors.border
        return TextView(activity).apply {
            this.text = text
            setTextColor(if (selected) accent else colors.secondaryText)
            textSize = 12f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            minHeight = dp(activity, 44)
            minimumHeight = dp(activity, 44)
            setPadding(dp(activity, 14), 0, dp(activity, 14), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(withAlpha(accent, 38)),
                rounded(fill, stroke, dp(activity, 999), dp(activity, 1)),
                null,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun monogramTile(activity: Activity, text: String, tone: Tone = Tone.NEUTRAL): TextView {
        val colors = palette(activity)
        val accent = toneColor(colors, tone)
        val glyph = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        return TextView(activity).apply {
            this.text = glyph
            setTextColor(accent)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(withAlpha(accent, 28), withAlpha(accent, 90), dp(activity, 14), dp(activity, 1))
            layoutParams = LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply {
                marginEnd = dp(activity, 12)
            }
        }
    }

    fun metricTile(activity: Activity, value: String, label: String, tone: Tone = Tone.NEUTRAL): LinearLayout {
        val colors = palette(activity)
        val accent = toneColor(colors, tone)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            background = rounded(colors.surfaceRaised, withAlpha(accent, 72), dp(activity, 15), dp(activity, 1))
            addView(TextView(activity).apply {
                text = value
                setTextColor(colors.text)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = label.uppercase()
                setTextColor(accent)
                textSize = 10.5f
                letterSpacing = 0.06f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(activity, 2), 0, 0)
            })
        }
    }

    fun divider(activity: Activity): View {
        val colors = palette(activity)
        return View(activity).apply {
            setBackgroundColor(withAlpha(colors.border, 180))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 1),
            ).apply {
                topMargin = dp(activity, 11)
                bottomMargin = dp(activity, 11)
            }
        }
    }

    fun field(activity: Activity, hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val colors = palette(activity)
        return EditText(activity).apply {
            this.hint = hint
            setSingleLine(true)
            this.inputType = inputType
            setTextColor(colors.text)
            setHintTextColor(withAlpha(colors.secondaryText, 180))
            setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11))
            background = rounded(colors.surfaceRaised, colors.border, dp(activity, 14), dp(activity, 1))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(activity, 9) }
        }
    }

    fun action(activity: Activity, label: String, destructive: Boolean = false, onClick: () -> Unit): Button =
        buildAction(activity, label, destructive = destructive, primary = false, onClick = onClick)

    fun action(
        activity: Activity,
        label: String,
        primary: Boolean,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ): Button = buildAction(activity, label, destructive = destructive, primary = primary, onClick = onClick)

    private fun buildAction(
        activity: Activity,
        label: String,
        destructive: Boolean,
        primary: Boolean,
        onClick: () -> Unit,
    ): Button {
        val colors = palette(activity)
        val accent = if (destructive) colors.danger else colors.accent
        val fill = if (primary) withAlpha(accent, 44) else colors.surfaceRaised
        val stroke = if (primary) withAlpha(accent, 170) else withAlpha(accent, 90)
        return Button(activity).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(colors.text)
            textSize = 13.5f
            minHeight = dp(activity, 48)
            minimumHeight = dp(activity, 48)
            setPadding(dp(activity, 12), 0, dp(activity, 12), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(withAlpha(accent, 45)),
                rounded(fill, stroke, dp(activity, 14), dp(activity, 1)),
                null,
            )
            setOnClickListener {
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(65L).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                    onClick()
                }.start()
            }
        }
    }

    fun actionRow(activity: Activity, vararg buttons: Button): LinearLayout = LinearLayout(activity).apply {
        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val stacked = screenWidthDp < 420 || buttons.size >= 3
        orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        buttons.forEachIndexed { index, button ->
            val params = if (stacked) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = dp(activity, 8)
                }
            } else {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(activity, 8)
                }
            }
            addView(button, params)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(activity, 8) }
    }

    fun outputCard(activity: Activity, text: String): TextView = TextView(activity).apply {
        this.text = text
        setTextIsSelectable(true)
        setTextColor(palette(activity).text)
        textSize = 12.5f
        setLineSpacing(0f, 1.08f)
        setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15))
        background = rounded(
            palette(activity).surfaceRaised,
            withAlpha(palette(activity).accent, 85),
            dp(activity, 18),
            dp(activity, 1),
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(activity, 10) }
    }

    fun outputCard(activity: Activity, output: TextView): LinearLayout {
        val colors = palette(activity)
        output.setTextColor(colors.text)
        output.textSize = 12.5f
        output.setTextIsSelectable(true)
        output.setLineSpacing(0f, 1.08f)
        return card(activity, emphasized = true).apply {
            addView(TextView(activity).apply {
                text = "OUTPUT"
                setTextColor(colors.accent)
                textSize = 11f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(activity, 9))
            })
            addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun spacer(activity: Activity, heightDp: Int = 4): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(activity, heightDp))
    }

    fun dp(activity: Activity, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        activity.resources.displayMetrics,
    ).toInt()

    private fun toneFromString(tone: String): Tone = when (tone.lowercase()) {
        "success" -> Tone.SUCCESS
        "warning" -> Tone.WARNING
        "danger" -> Tone.DANGER
        else -> Tone.NEUTRAL
    }

    private fun toneColor(colors: Palette, tone: Tone): Int = when (tone) {
        Tone.SUCCESS -> colors.success
        Tone.WARNING -> colors.warning
        Tone.DANGER -> colors.danger
        Tone.NEUTRAL -> colors.accent
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }

    private fun themeColor(activity: Activity, attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (activity.theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) activity.getColor(value.resourceId) else value.data
        } else {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
