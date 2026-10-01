package com.Atom2Universe.app.notes.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.GroupWithCount
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.editor.MdPalette
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.dpf
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.onAccentColor
import com.Atom2Universe.app.pixelart.ui.primaryButton

/**
 * Les couleurs d'une note : des teintes sombres et sourdes, lisibles sous un texte clair, comme
 * le reste de l'appli. Null = la surface ordinaire.
 */
object NoteColors {
    class Swatch(val hex: String, @StringRes val name: Int)

    val ALL = listOf(
        Swatch("#77172E", R.string.notes_color_red),
        Swatch("#692B17", R.string.notes_color_orange),
        Swatch("#7C4A03", R.string.notes_color_yellow),
        Swatch("#264D3B", R.string.notes_color_green),
        Swatch("#0C625D", R.string.notes_color_teal),
        Swatch("#256377", R.string.notes_color_blue),
        Swatch("#284255", R.string.notes_color_navy),
        Swatch("#472E5B", R.string.notes_color_purple),
        Swatch("#6C394F", R.string.notes_color_pink),
        Swatch("#4B443A", R.string.notes_color_brown),
        Swatch("#3A3F47", R.string.notes_color_gray),
    )

    fun parse(hex: String?): Int? = try { hex?.let { Color.parseColor(it) } } catch (_: IllegalArgumentException) { null }
}

/** La surface d'une note : sa couleur, ou la surface de l'appli. */
fun Context.noteSurface(hex: String?): Int = NoteColors.parse(hex) ?: ContextCompat.getColor(this, R.color.audio_surface)

/** Le fond de l'éditeur : la couleur de la note, ou le fond de l'appli. */
fun Context.noteBackground(hex: String?): Int = NoteColors.parse(hex) ?: ContextCompat.getColor(this, R.color.audio_background)

fun Context.notePalette(): MdPalette =
    MdPalette(ContextCompat.getColor(this, R.color.audio_text_primary), accentColor(), onAccentColor())

/** Une pastille ronde, cochée d'un anneau quand elle est la couleur actuelle. */
private fun Context.swatchView(color: Int, selected: Boolean, description: String, onClick: () -> Unit): View =
    FrameLayout(this).apply {
        val size = dp(44)
        layoutParams = GridLayout.LayoutParams().apply { width = size; height = size; setMargins(dp(6), dp(6), dp(6), dp(6)) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(if (selected) 3 else 1), if (selected) accentColor() else ContextCompat.getColor(context, R.color.px_divider))
        }
        contentDescription = description
        if (selected) addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_px_check)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.audio_text_primary))
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        })
        setOnClickListener { onClick() }
    }

/** Feuille des couleurs : « aucune » puis la palette. */
fun Context.showNoteColorSheet(current: String?, onPick: (String?) -> Unit) {
    bottomSheet(getString(R.string.notes_color)) { root, dialog ->
        val grid = GridLayout(this).apply { columnCount = 6 }
        grid.addView(swatchView(ContextCompat.getColor(this, R.color.audio_surface), current == null, getString(R.string.notes_color_none)) {
            dialog.dismiss(); onPick(null)
        })
        for (s in NoteColors.ALL) {
            grid.addView(swatchView(Color.parseColor(s.hex), s.hex.equals(current, ignoreCase = true), getString(s.name)) {
                dialog.dismiss(); onPick(s.hex)
            })
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }.show()
}

/** Feuille « déplacer vers » : sans groupe, chaque groupe, puis un nouveau groupe. */
fun Context.showGroupPicker(groups: List<GroupWithCount>, current: Long?, onPick: (Long?) -> Unit, onNewGroup: (String) -> Unit) {
    val items = ArrayList<SheetItem>()
    items += SheetItem(R.drawable.ic_px_close, getString(R.string.notes_no_group), checked = current == null) { onPick(null) }
    for (g in groups) items += SheetItem(R.drawable.ic_px_folder_open, g.group.name, checked = g.group.id == current) { onPick(g.group.id) }
    items += SheetItem(R.drawable.ic_px_add, getString(R.string.notes_new_group)) {
        askName(R.string.notes_new_group, "", R.string.notes_create) { onNewGroup(it) }
    }
    actionSheet(getString(R.string.notes_move_to), items).show()
}

/** Un champ de nom dans une feuille, validé par le bouton ou par la touche Entrée. */
fun Context.askName(@StringRes title: Int, initial: String, @StringRes action: Int, onOk: (String) -> Unit) {
    bottomSheet(getString(title)) { root, dialog ->
        val field = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
        }
        fun done() {
            val name = field.text.toString().trim()
            dialog.dismiss()
            if (name.isNotEmpty()) onOk(name)
        }
        field.setOnEditorActionListener { _, _, _ -> done(); true }
        root.addView(field)
        root.addView(primaryButton(getString(action)) { done() })
        dialog.setOnShowListener { field.requestFocus() }
    }.show()
}

/**
 * Feuille des tags d'une note : chaque tag est une puce à bascule, et un champ en bas crée un
 * tag et le pose aussitôt. [onDone] reçoit la sélection finale à la fermeture.
 */
fun Context.showTagPicker(all: List<Tag>, selected: Set<Long>, createTag: suspend (String) -> Long, launch: (suspend () -> Unit) -> Unit, onDone: (Set<Long>) -> Unit) {
    val chosen = selected.toMutableSet()
    val dialog = bottomSheet(getString(R.string.notes_tags)) { root, _ ->
        val flow = com.google.android.material.chip.ChipGroup(this).apply { chipSpacingHorizontal = dp(2) }
        fun addChip(id: Long, name: String) {
            val c = chip("#$name") { v ->
                if (!chosen.remove(id)) chosen += id
                v.isSelected = id in chosen
            }
            c.isSelected = id in chosen
            flow.addView(c, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        }
        for (t in all) addChip(t.id, t.name)
        if (all.isEmpty()) root.addView(label(getString(R.string.notes_tags_none), 13f).apply { setPadding(dp(4), 0, 0, dp(8)) })
        root.addView(flow)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val field = EditText(this).apply {
            hint = getString(R.string.notes_tag_new_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
        }
        fun create() {
            val name = field.text.toString().trim().removePrefix("#").trim()
            if (name.isEmpty()) return
            field.setText("")
            launch {
                val id = createTag(name)
                if (all.none { it.id == id } && chosen.add(id)) addChip(id, name) else chosen += id
                for (i in 0 until flow.childCount) {
                    val v = flow.getChildAt(i) as TextView
                    if (v.text.toString().equals("#$name", ignoreCase = true)) v.isSelected = true
                }
            }
        }
        field.setOnEditorActionListener { _, _, _ -> create(); true }
        row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip(null, R.drawable.ic_px_add) { create() }.apply { contentDescription = getString(R.string.notes_tag_add) })
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }
    dialog.setOnDismissListener { onDone(chosen) }
    dialog.show()
}

/** Un petit badge arrondi (date, avancement d'une liste) sur une carte. */
fun Context.pillBackground(alpha: Int = 0x22): GradientDrawable = GradientDrawable().apply {
    setColor(Color.argb(alpha, 255, 255, 255))
    cornerRadius = dpf(10f)
}
