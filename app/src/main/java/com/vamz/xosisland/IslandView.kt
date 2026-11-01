package com.vamz.xosisland

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class IslandView(ctx: Context) : LinearLayout(ctx) {
    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }
    private val icon = ImageView(ctx)
    private val col = LinearLayout(ctx)
    private val title = TextView(ctx)
    private val body = TextView(ctx)
    private val chip = TextView(ctx)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(dp(10), dp(7), dp(16), dp(7))

        addView(icon, LayoutParams(dp(26), dp(26)))

        col.orientation = VERTICAL
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        body.setTextColor(Color.parseColor("#B8B8B8"))
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        body.maxLines = 2
        body.ellipsize = TextUtils.TruncateAt.END
        col.addView(title)
        col.addView(body)
        val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(10)
        addView(col, lp)

        chip.setTextColor(Color.parseColor("#34C759"))
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        chip.typeface = Typeface.DEFAULT_BOLD
        chip.maxLines = 1
        val clp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        clp.leftMargin = dp(10)
        addView(chip, clp)
    }

    fun bind(e: IslandEvent, expanded: Boolean) {
        if (isAttachedToWindow) TransitionManager.beginDelayedTransition(this)
        icon.setImageDrawable(e.icon)
        val w = resources.displayMetrics.widthPixels
        if (expanded) {
            title.text = e.title.ifEmpty { e.app }
            body.text = e.text
            body.visibility = if (e.text.isEmpty()) View.GONE else View.VISIBLE
            chip.visibility = View.GONE
            minimumWidth = w - dp(32)
            title.maxWidth = w - dp(120)
            bg.cornerRadius = dp(28).toFloat()
        } else {
            body.visibility = View.GONE
            title.text = if (e.kind == Kind.MEDIA) "♪ " + e.title else e.title.ifEmpty { e.text }
            chip.text = e.chip ?: ""
            chip.visibility = if (e.chip.isNullOrEmpty()) View.GONE else View.VISIBLE
            minimumWidth = dp(120)
            title.maxWidth = dp(160)
            bg.cornerRadius = dp(100).toFloat()
        }
    }
}
