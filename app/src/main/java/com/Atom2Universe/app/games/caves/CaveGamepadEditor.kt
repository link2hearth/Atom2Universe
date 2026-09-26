package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.input.GamepadAction
import com.Atom2Universe.app.games.caves.input.GamepadBindings
import com.Atom2Universe.app.games.caves.input.XboxButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Liste d'actions et sélecteur de boutons Xbox, sans schéma complet de manette. */
internal class CaveGamepadEditor(
    private val context: Context,
    private val container: ScrollView,
    val bindings: MutableMap<GamepadAction, XboxButton>
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun refresh() {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        list.addView(TextView(context).apply {
            setText(R.string.cave_pad_hint); setTextColor(0xFFB0C3C9.toInt()); textSize = 14f
            setPadding(0, 0, 0, dp(12))
        })
        for (action in GamepadAction.entries) {
            list.addView(row(context.getString(action.label), bindings.getValue(action)) { choose(action) },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val scroll = container.scrollY
        container.removeAllViews()
        container.addView(list)
        container.post { container.scrollTo(0, scroll) }
    }

    private fun row(label: String, button: XboxButton, click: () -> Unit): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(16), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(0xFF21343E.toInt()); cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0xFF526C79.toInt())
        }
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = attrs.getDrawable(0); attrs.recycle()
        isFocusable = true
        contentDescription = context.getString(R.string.cave_pad_binding, label, context.getString(button.label))
        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(0xFFF0F5F2.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(XboxBadge(context, button), LinearLayout.LayoutParams(dp(68), dp(48)))
        setOnClickListener { click() }
    }

    private fun choose(action: GamepadAction) {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val dialog = MaterialAlertDialogBuilder(context, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(action.label)
            .setMessage(R.string.cave_pad_swap_hint)
            .setView(ScrollView(context).apply { addView(list) })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        for (button in XboxButton.entries) {
            val assigned = bindings.entries.firstOrNull { it.value == button }?.key
            val label = if (assigned == null) context.getString(R.string.cave_pad_unused)
                else context.getString(assigned.label)
            list.addView(row(label, button) {
                GamepadBindings.assign(bindings, action, button)
                refresh()
                dialog.dismiss()
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        dialog.show()
    }
}

/** Cercles ABXY colorés, épaules allongées, gâchettes et croix directionnelle. */
private class XboxBadge(context: Context, private val button: XboxButton) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val label = context.getString(button.label)

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = minOf(width / 68f, height / 48f)
        canvas.translate((width - 68f * scale) / 2f, (height - 48f * scale) / 2f)
        canvas.scale(scale, scale)
        path.reset()
        when (button) {
            XboxButton.A, XboxButton.B, XboxButton.X, XboxButton.Y, XboxButton.LS, XboxButton.RS ->
                path.addCircle(34f, 24f, 20f, Path.Direction.CW)
            XboxButton.LB, XboxButton.RB -> path.addRoundRect(4f, 9f, 64f, 39f, 8f, 8f, Path.Direction.CW)
            XboxButton.LT, XboxButton.RT -> {
                path.moveTo(17f, 4f); path.lineTo(51f, 4f); path.lineTo(47f, 38f)
                path.quadTo(34f, 50f, 21f, 38f); path.close()
            }
            else -> {
                path.moveTo(26f, 4f); path.lineTo(42f, 4f); path.lineTo(42f, 16f)
                path.lineTo(54f, 16f); path.lineTo(54f, 32f); path.lineTo(42f, 32f)
                path.lineTo(42f, 44f); path.lineTo(26f, 44f); path.lineTo(26f, 32f)
                path.lineTo(14f, 32f); path.lineTo(14f, 16f); path.lineTo(26f, 16f); path.close()
            }
        }
        paint.style = Paint.Style.FILL; paint.color = 0xFF111B21.toInt()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = button.color
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, 34f, 24f - (paint.ascent() + paint.descent()) / 2f, paint)
        canvas.restore()
    }
}
