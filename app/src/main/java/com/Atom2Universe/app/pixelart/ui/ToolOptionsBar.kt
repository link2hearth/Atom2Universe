package com.Atom2Universe.app.pixelart.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.SelectMode
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.pixelart.core.Tool

/** Icône de chaque forme, dans l'ordre où la feuille de formes les propose. */
val SHAPE_ICONS: List<Pair<ShapeKind, Int>> = listOf(
    ShapeKind.LINE to R.drawable.ic_px_sh_line,
    ShapeKind.RECT to R.drawable.ic_px_sh_rect,
    ShapeKind.ROUNDED_RECT to R.drawable.ic_px_sh_rounded,
    ShapeKind.ELLIPSE to R.drawable.ic_px_sh_ellipse,
    ShapeKind.TRIANGLE to R.drawable.ic_px_sh_triangle,
    ShapeKind.DIAMOND to R.drawable.ic_px_sh_diamond,
    ShapeKind.PENTAGON to R.drawable.ic_px_sh_pentagon,
    ShapeKind.HEXAGON to R.drawable.ic_px_sh_hexagon,
    ShapeKind.STAR to R.drawable.ic_px_sh_star,
    ShapeKind.ARROW to R.drawable.ic_px_sh_arrow,
)

fun shapeIcon(kind: ShapeKind): Int = SHAPE_ICONS.first { it.first == kind }.second

fun shapeLabel(kind: ShapeKind): Int = when (kind) {
    ShapeKind.LINE -> R.string.px_shape_line
    ShapeKind.RECT -> R.string.px_shape_rect
    ShapeKind.ROUNDED_RECT -> R.string.px_shape_rounded
    ShapeKind.ELLIPSE -> R.string.px_shape_ellipse
    ShapeKind.TRIANGLE -> R.string.px_shape_triangle
    ShapeKind.DIAMOND -> R.string.px_shape_diamond
    ShapeKind.PENTAGON -> R.string.px_shape_pentagon
    ShapeKind.HEXAGON -> R.string.px_shape_hexagon
    ShapeKind.STAR -> R.string.px_shape_star
    ShapeKind.ARROW -> R.string.px_shape_arrow
}

/**
 * La barre flottante des réglages de l'outil courant. Elle se reconstruit quand l'outil change
 * (chaque outil n'affiche que ce qui le concerne) et quand la sélection apparaît ou disparaît.
 */
class ToolOptionsBar(
    private val context: Context,
    private val container: LinearLayout,
    private val session: () -> EditorSession,
    private val listener: Listener,
) {
    interface Listener {
        /** Un réglage a changé : enregistrer les préférences, redessiner les repères. */
        fun onOptionsChanged()
        fun onPickShape()
        /** Une action sur le contenu (sélection, presse-papiers, retournement…) a été lancée. */
        fun onContentAction(action: ContentAction)
    }

    enum class ContentAction {
        SELECT_ALL, DESELECT, INVERT, COPY, CUT, PASTE, DELETE, FILL_SELECTION,
        FLIP_H, FLIP_V, ROTATE_CW, ROTATE_CCW, COMMIT, CANCEL,
    }

    private val parentScroll: View get() = container.parent as View

    private var builtWithSelection = false

    /** La sélection est apparue ou a disparu : la barre change (bouton « Désélectionner »). Sinon on la laisse telle quelle. */
    fun selectionChanged() {
        if ((session().selection != null) != builtWithSelection) rebuild()
    }

    fun rebuild() {
        container.removeAllViews()
        val s = session()
        builtWithSelection = s.selection != null
        val o = s.options
        val c = context
        fun add(v: View) = container.addView(v)

        fun mirrors() {
            add(c.divider())
            add(c.chip(c.getString(R.string.px_mirror_h_short), R.drawable.ic_px_mirror_h) { o.mirrorX = !o.mirrorX; it.isSelected = o.mirrorX; listener.onOptionsChanged() }
                .apply { isSelected = o.mirrorX; contentDescription = c.getString(R.string.px_mirror_h) })
            add(c.chip(c.getString(R.string.px_mirror_v_short), R.drawable.ic_px_mirror_v) { o.mirrorY = !o.mirrorY; it.isSelected = o.mirrorY; listener.onOptionsChanged() }
                .apply { isSelected = o.mirrorY; contentDescription = c.getString(R.string.px_mirror_v) })
        }

        fun sizeSlider(max: Int = 32) {
            add(LabeledSlider(c, c.getString(R.string.px_opt_size), 1, max, o.size.coerceIn(1, max), { c.getString(R.string.px_opt_size_value, it) }) {
                o.size = it; listener.onOptionsChanged()
            })
        }

        fun brushShape() {
            fun roundLabel() = c.getString(if (o.round) R.string.px_brush_round else R.string.px_brush_square)
            add(c.chip(roundLabel(), if (o.round) R.drawable.ic_px_round else R.drawable.ic_px_square) {
                o.round = !o.round
                it.text = roundLabel()
                it.setCompoundDrawablesRelativeWithIntrinsicBounds(if (o.round) R.drawable.ic_px_round else R.drawable.ic_px_square, 0, 0, 0)
                it.compoundDrawablesRelative[0]?.setBounds(0, 0, c.dp(18), c.dp(18))
                listener.onOptionsChanged()
            }.apply { contentDescription = c.getString(R.string.px_opt_round) })
        }

        fun tolerance() {
            add(LabeledSlider(c, c.getString(R.string.px_opt_tolerance), 0, 128, o.tolerance, { c.getString(R.string.px_opt_tolerance_value, it) }) {
                o.tolerance = it; listener.onOptionsChanged()
            })
            add(c.chip(c.getString(R.string.px_opt_contiguous), R.drawable.ic_px_contiguous) { o.contiguous = !o.contiguous; it.isSelected = o.contiguous; listener.onOptionsChanged() }
                .apply { isSelected = o.contiguous; contentDescription = c.getString(R.string.px_opt_contiguous) })
            add(c.chip(c.getString(R.string.px_opt_all_layers), R.drawable.ic_px_layers) { o.sampleAllLayers = !o.sampleAllLayers; it.isSelected = o.sampleAllLayers; listener.onOptionsChanged() }
                .apply { isSelected = o.sampleAllLayers; contentDescription = c.getString(R.string.px_opt_all_layers) })
        }

        fun action(icon: Int, desc: Int, a: ContentAction) =
            c.iconButton(icon, desc, size = 36, pad = 8) { listener.onContentAction(a) }.also { add(it) }

        fun selectModes() {
            val modes = listOf(
                Triple(SelectMode.REPLACE, R.drawable.ic_px_mode_replace, R.string.px_mode_new),
                Triple(SelectMode.ADD, R.drawable.ic_px_mode_add, R.string.px_mode_add),
                Triple(SelectMode.SUBTRACT, R.drawable.ic_px_mode_sub, R.string.px_mode_subtract),
            )
            val chips = ArrayList<TextView>()
            for ((mode, icon, desc) in modes) {
                val ch = c.chip(c.getString(desc), icon) {
                    o.selectMode = mode
                    chips.forEachIndexed { i, x -> x.isSelected = modes[i].first == mode }
                    listener.onOptionsChanged()
                }.apply { isSelected = o.selectMode == mode; contentDescription = c.getString(desc) }
                chips.add(ch)
                add(ch)
            }
        }

        fun selectionActions() {
            add(c.divider())
            add(c.chip(c.getString(R.string.px_sel_all), R.drawable.ic_px_crop) { listener.onContentAction(ContentAction.SELECT_ALL) })
            add(c.chip(c.getString(R.string.px_sel_invert), R.drawable.ic_px_invert) { listener.onContentAction(ContentAction.INVERT) })
            add(c.divider())
            action(R.drawable.ic_px_copy, R.string.px_copy, ContentAction.COPY)
            action(R.drawable.ic_px_cut, R.string.px_cut, ContentAction.CUT)
            action(R.drawable.ic_px_paste, R.string.px_paste, ContentAction.PASTE)
            add(c.divider())
            action(R.drawable.ic_px_delete, R.string.px_delete_selection, ContentAction.DELETE)
            action(R.drawable.ic_px_fill, R.string.px_fill_selection, ContentAction.FILL_SELECTION)
            add(c.divider())
            action(R.drawable.ic_px_flip_h, R.string.px_flip_h, ContentAction.FLIP_H)
            action(R.drawable.ic_px_flip_v, R.string.px_flip_v, ContentAction.FLIP_V)
            action(R.drawable.ic_px_rot_ccw, R.string.px_rotate_ccw, ContentAction.ROTATE_CCW)
            action(R.drawable.ic_px_rot_cw, R.string.px_rotate_cw, ContentAction.ROTATE_CW)
        }

        // Une sélection limite tout ce qu'on dessine : on doit pouvoir la lever d'un geste, quel que soit l'outil.
        if (s.selection != null && s.tool != Tool.MOVE) {
            add(c.chip(c.getString(R.string.px_sel_none), R.drawable.ic_px_close) { listener.onContentAction(ContentAction.DESELECT) }
                .apply { isSelected = true })
            add(c.divider())
        }

        when (s.tool) {
            Tool.PENCIL -> {
                sizeSlider(); brushShape()
                add(c.chip(c.getString(R.string.px_opt_pixel_perfect), R.drawable.ic_px_pixel_perfect) { o.pixelPerfect = !o.pixelPerfect; it.isSelected = o.pixelPerfect; listener.onOptionsChanged() }
                    .apply { isSelected = o.pixelPerfect; contentDescription = c.getString(R.string.px_opt_pixel_perfect) })
                mirrors()
            }
            Tool.ERASER -> { sizeSlider(); brushShape(); mirrors() }
            Tool.BRUSH -> {
                sizeSlider(48)
                add(LabeledSlider(c, c.getString(R.string.px_opt_opacity), 1, 100, o.opacity, { c.getString(R.string.px_opt_percent, it) }) {
                    o.opacity = it; listener.onOptionsChanged()
                })
                mirrors()
            }
            Tool.FILL -> tolerance()
            Tool.PICKER, Tool.HAND -> Unit
            Tool.SHAPE -> {
                add(c.chip(c.getString(shapeLabel(o.shape)), shapeIcon(o.shape)) { listener.onPickShape() })
                val fillIcon = { f: ShapeFill -> when (f) { ShapeFill.OUTLINE -> R.drawable.ic_px_fill_outline; ShapeFill.FILL -> R.drawable.ic_px_fill_solid; ShapeFill.BOTH -> R.drawable.ic_px_fill_both } }
                val fillLabel = { f: ShapeFill -> c.getString(when (f) { ShapeFill.OUTLINE -> R.string.px_fill_outline; ShapeFill.FILL -> R.string.px_fill_solid; ShapeFill.BOTH -> R.string.px_fill_both }) }
                if (o.shape != ShapeKind.LINE) {
                    add(c.chip(fillLabel(o.shapeFill), fillIcon(o.shapeFill)) {
                        o.shapeFill = ShapeFill.values()[(o.shapeFill.ordinal + 1) % ShapeFill.values().size]
                        it.text = fillLabel(o.shapeFill)
                        it.setCompoundDrawablesRelativeWithIntrinsicBounds(fillIcon(o.shapeFill), 0, 0, 0)
                        it.compoundDrawablesRelative[0]?.setBounds(0, 0, c.dp(18), c.dp(18))
                        listener.onOptionsChanged()
                    }.apply { contentDescription = c.getString(R.string.px_opt_fill_mode) })
                }
                if (o.shape != ShapeKind.LINE && o.shape != ShapeKind.ARROW) {
                    add(c.chip(c.getString(R.string.px_opt_square), R.drawable.ic_px_square_lock) { o.shapeSquare = !o.shapeSquare; it.isSelected = o.shapeSquare; listener.onOptionsChanged() }
                        .apply { isSelected = o.shapeSquare; contentDescription = c.getString(R.string.px_opt_square) })
                }
                sizeSlider(16)
                mirrors()
            }
            Tool.SELECT_RECT, Tool.SELECT_LASSO -> { selectModes(); selectionActions() }
            Tool.SELECT_WAND -> { selectModes(); tolerance(); selectionActions() }
            Tool.MOVE -> {
                action(R.drawable.ic_px_flip_h, R.string.px_flip_h, ContentAction.FLIP_H)
                action(R.drawable.ic_px_flip_v, R.string.px_flip_v, ContentAction.FLIP_V)
                action(R.drawable.ic_px_rot_ccw, R.string.px_rotate_ccw, ContentAction.ROTATE_CCW)
                action(R.drawable.ic_px_rot_cw, R.string.px_rotate_cw, ContentAction.ROTATE_CW)
                add(c.divider())
                action(R.drawable.ic_px_check, R.string.px_commit, ContentAction.COMMIT)
                action(R.drawable.ic_px_close, R.string.px_cancel, ContentAction.CANCEL)
            }
        }
        parentScroll.visibility = if (container.childCount == 0) View.GONE else View.VISIBLE
        (parentScroll as? android.widget.HorizontalScrollView)?.scrollTo(0, 0)
    }
}
