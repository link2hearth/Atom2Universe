package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.followImmersiveMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.Atom2Universe.app.util.ImmersiveMaterialAlertDialogBuilder as MaterialAlertDialogBuilder

/** Petits outils de construction d'interface en code (les feuilles et options sont trop dynamiques pour du XML). */

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.themeColor(attr: Int, fallback: Int = Color.WHITE): Int {
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) {
        if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    } else fallback
}

fun Context.accentColor(): Int = themeColor(androidx.appcompat.R.attr.colorAccent)
fun Context.onAccentColor(): Int = themeColor(R.attr.a2uMidiAccentOnText)

/** Un damier gris clair/foncé : le fond de tout aperçu de pixel art transparent. */
fun Context.checkerDrawable(cell: Int = dp(6)): Drawable {
    val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(tile)
    val p = Paint()
    p.color = ContextCompat.getColor(this, R.color.px_checker_a)
    c.drawRect(0f, 0f, cell * 2f, cell * 2f, p)
    p.color = ContextCompat.getColor(this, R.color.px_checker_b)
    c.drawRect(cell.toFloat(), 0f, cell * 2f, cell.toFloat(), p)
    c.drawRect(0f, cell.toFloat(), cell.toFloat(), cell * 2f, p)
    return BitmapDrawable(resources, tile).apply {
        setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    }
}

/** Un bitmap de pixel art à afficher net (agrandi au plus proche voisin). */
fun Context.pixelDrawable(bmp: Bitmap): Drawable = BitmapDrawable(resources, bmp).apply {
    isFilterBitmap = false
    setAntiAlias(false)
}

fun Context.iconButton(@DrawableRes icon: Int, @StringRes description: Int, size: Int = 40, pad: Int = 9, onClick: () -> Unit): ImageButton =
    ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = getString(description)
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_px_tool, theme)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        imageTintList = ContextCompat.getColorStateList(context, R.color.px_tool_tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(1), 0, dp(1), 0) }
        setOnClickListener { onClick() }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(this, contentDescription)
    }

fun Context.label(text: CharSequence, size: Float = 13f, primary: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    setTextColor(ContextCompat.getColor(context, if (primary) R.color.audio_text_primary else R.color.audio_text_secondary))
}

/** Puce à bascule (pastille arrondie) : [selected] se lit au liseré de couleur d'accent. */
fun Context.chip(text: CharSequence?, @DrawableRes icon: Int = 0, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
    gravity = Gravity.CENTER
    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_px_chip, theme)
    minHeight = dp(34)
    minWidth = dp(34)
    val sidePad = dp(if (text.isNullOrEmpty()) 8 else 12)
    setPadding(sidePad, 0, sidePad, 0)
    if (icon != 0) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawablePadding = if (text.isNullOrEmpty()) 0 else dp(6)
        compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.px_icon_tint)
        // Les icônes sont dessinées en 24 dp : on les ramène à 18 dp.
        val d = compoundDrawablesRelative[0]
        d?.setBounds(0, 0, dp(18), dp(18))
        setCompoundDrawablesRelative(d, null, null, null)
    }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
        setMargins(dp(3), 0, dp(3), 0)
    }
    setOnClickListener { onClick(this) }
}

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(ContextCompat.getColor(context, R.color.px_divider))
    layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).apply { setMargins(dp(6), 0, dp(6), 0) }
}

/** Curseur avec sa valeur affichée : `[nom] ───●─── 12`. */
class LabeledSlider(context: Context, name: String, min: Int, max: Int, value: Int, private val format: (Int) -> String, onChange: (Int) -> Unit) :
    LinearLayout(context) {
    /** Un curseur dans une zone qui défile : il interdit au parent de lui voler le geste. */
    val bar = object : SeekBar(context) {
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            return super.onTouchEvent(event)
        }
    }
    private val valueText = context.label(format(value), 12f, true)
    var value: Int
        get() = bar.progress + minValue
        set(v) { bar.progress = v - minValue; valueText.text = format(v) }
    private val minValue = min

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(context.label(name, 12f).apply { setPadding(0, 0, context.dp(6), 0) })
        bar.max = max - min
        bar.progress = value - min
        bar.progressTintList = ColorStateList.valueOf(context.accentColor())
        bar.thumbTintList = ColorStateList.valueOf(context.accentColor())
        addView(bar, LayoutParams(context.dp(150), context.dp(40)))
        addView(valueText.apply { minWidth = context.dp(36); gravity = Gravity.END })
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                valueText.text = format(p + minValue)
                if (fromUser) onChange(p + minValue)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
}

/** Feuille du bas : titre puis contenu, prête à montrer. */
fun Context.bottomSheet(title: CharSequence?, build: (LinearLayout, BottomSheetDialog) -> Unit): BottomSheetDialog {
    val dialog = BottomSheetDialog(this, R.style.PxBottomSheetDialog)
    dialog.followImmersiveMode()
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(20))
    }
    if (title != null) {
        root.addView(label(title, 17f, true).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(12))
        })
    }
    build(root, dialog)
    val scroll = android.widget.ScrollView(this).apply { addView(root) }
    dialog.setContentView(scroll)
    dialog.setOnShowListener {
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }
    return dialog
}

class SheetItem(@DrawableRes val icon: Int, val label: String, val checked: Boolean = false, val destructive: Boolean = false, val action: () -> Unit)

/** Liste d'actions dans une feuille du bas. */
fun Context.actionSheet(title: CharSequence?, items: List<SheetItem>): BottomSheetDialog =
    bottomSheet(title) { root, dialog ->
        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, dp(4), 0)
                minimumHeight = dp(48)
                background = ResourcesCompat.getDrawable(resources, R.drawable.bg_px_tool, theme)
                isSelected = false
            }
            row.addView(ImageView(this).apply {
                setImageResource(item.icon)
                imageTintList = ColorStateList.valueOf(
                    if (item.destructive) 0xFFFF6B6B.toInt() else ContextCompat.getColor(context, R.color.audio_text_secondary),
                )
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) }
            })
            row.addView(TextView(this).apply {
                text = item.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (item.destructive) 0xFFFF6B6B.toInt() else ContextCompat.getColor(context, R.color.audio_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (item.checked) row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_px_check)
                imageTintList = ColorStateList.valueOf(accentColor())
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            row.setOnClickListener { dialog.dismiss(); item.action() }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

/** Boîte de dialogue avec un champ de texte. */
fun Context.promptText(
    @StringRes title: Int,
    initial: String,
    @StringRes positive: Int,
    number: Boolean = false,
    onOk: (String) -> Unit,
) {
    val input = EditText(this).apply {
        setText(initial)
        setSelection(initial.length)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine()
        setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
    }
    val box = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(positive) { _, _ -> onOk(input.text.toString().trim()) }
        .setNegativeButton(R.string.px_cancel, null)
        .create()
        .also { it.followImmersiveMode(); it.show(); input.requestFocus() }
}

fun Context.confirm(@StringRes title: Int, message: CharSequence?, @StringRes positive: Int, destructive: Boolean = false, onOk: () -> Unit): AlertDialog =
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .apply { if (message != null) setMessage(message) }
        .setPositiveButton(positive) { _, _ -> onOk() }
        .setNegativeButton(R.string.px_cancel, null)
        .create()
        .also { it.followImmersiveMode(); it.show() }

/** Une ligne horizontale défilante de puces. */
fun Context.scrollRow(vararg views: View): HorizontalScrollView = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (v in views) addView(v)
    })
}
