package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.Atom2Universe.app.studio.data.PadSize
import com.Atom2Universe.app.studio.data.PadPlayMode
import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.StudioNames

/**
 * Les pads, à jouer au doigt : une grande case par piste (sa couleur, son dessin), plusieurs doigts à la fois.
 *
 * - Une case de **batterie** frappe : [onDown] quand un doigt s'y pose, [onUp] quand il la quitte (le moteur fait le roulement
 *   entre les deux).
 * - Une piste mélodique présente un clavier polyphonique ou un thérémine monodique.
 *   Les notes de la gamme montent de gauche à droite ; le geste vertical module le son selon le mode choisi.
 *
 * Les cases remplissent toute la place, en carrés autant que possible.
 */
class PadsView(context: Context) : View(context) {

    /** Une case : la piste, sa couleur, son dessin, et pour une piste à notes ses niveaux (hauteurs MIDI, du grave à l'aigu). */
    class Tile(
        val track: Int, val color: Int, val icon: Int, val levels: IntArray? = null,
        val padSize: PadSize = PadSize.NORMAL, val name: String = "", val mode: PadPlayMode = PadPlayMode.DRIVE,
    )

    var tiles: List<Tile> = emptyList()
        set(v) {
            val sameLayout = field.size == v.size && field.indices.all { i ->
                field[i].track == v[i].track && field[i].padSize == v[i].padSize && (field[i].levels == null) == (v[i].levels == null)
            }
            if (!sameLayout) {
                releaseAll()
                glow.clear()
            } else if (held.isNotEmpty()) {
                // Un rafraîchissement du mixeur ou de la prise peut reconstruire les objets sans changer les touches.
                for (pointer in held.keys.toList()) {
                    val h = held[pointer] ?: continue
                    val levels = v[h.tile].levels
                    if (field[h.tile].mode != v[h.tile].mode || levels != null && h.pitch !in levels) {
                        mutedPointers.add(pointer)
                        lift(pointer)
                    }
                }
            }
            field = v
            if (!sameLayout) rebuildLayout()
            invalidate()
        }

    var editing = false
        set(value) {
            if (field == value) return
            releaseAll()
            field = value
            invalidate()
        }
    var onReorder: ((fromTrack: Int, toTrack: Int) -> Unit)? = null
    var onResize: ((track: Int) -> Unit)? = null
    var onToggleHalf: ((track: Int) -> Unit)? = null

    /** La tonique (0 = do) et les noms des notes : les niveaux de la tonique portent leur nom. */
    var keyPc: Int = 0
    var noteNames: List<String> = emptyList()

    var onDown: ((track: Int) -> Unit)? = null
    var onUp: ((track: Int) -> Unit)? = null
    var onNoteDown: ((track: Int, pitch: Int) -> Unit)? = null
    var onNoteUp: ((track: Int, pitch: Int) -> Unit)? = null
    var onNoteEffect: ((track: Int, amount: Float) -> Unit)? = null
    var onChooseMode: ((track: Int, x: Int, y: Int) -> Unit)? = null
    var onGlide: ((track: Int, from: Int, to: Int) -> Unit)? = null
    var onThereminExpression: ((track: Int, amount: Float) -> Unit)? = null

    /**
     * Ce que tient chaque doigt : la case, sur une piste à notes la note (-1 : un coup de batterie), la hauteur où il s'est posé et
     * l'effet du moment (0 posé, jusqu'à 1 quand il s'est éloigné).
     */
    private class Hold(val tile: Int, var pitch: Int, val startY: Float, var effect: Float = 0f, var x: Float = 0f)

    private val held = HashMap<Int, Hold>()
    private val modePresses = HashMap<Int, Int>()
    private val mutedPointers = HashSet<Int>()
    internal val hasHeldDrum get() = held.values.any { it.pitch < 0 }

    /** Éclat de chaque case (1 juste après un coup, puis s'éteint). */
    private val glow = HashMap<Int, Float>()

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val base = if (dark) 0xFF202027.toInt() else 0xFFE4E4EA.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val halfLabel = context.getString(R.string.studio_pad_half_size)
    private val halfPaint = Paint(label).apply { textAlign = Paint.Align.CENTER }
    private val halfLabelWidth = halfPaint.measureText(halfLabel)
    private val modeNames = PadPlayMode.entries.associateWith { context.getString(StudioNames.padMode(it)) }
    private val modePaint = Paint(label).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        isFakeBoldText = false
    }
    private val modeWidths = modeNames.mapValues { modePaint.measureText(it.value) }
    private val thereminShade = LinearGradient(0f, 0f, 0f, 1f, 0x30FFFFFF, 0x72000000, Shader.TileMode.CLAMP)
    private val shadeMatrix = Matrix()
    private val rect = RectF()
    private val band = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var areas = emptyArray<RectF>()
    private var editPointer = -1
    private var source = -1
    private var target = -1
    private var startX = 0f
    private var startY = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var dragging = false
    private var interrupted = false
    private var preview: Array<RectF>? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Le placement suit l'ordre des pistes ; une grande case occupe deux colonnes sur la même ligne. */
    private fun layoutOf(order: List<Int>): Array<RectF> {
        if (order.isEmpty() || width <= 0 || height <= 0) return emptyArray()
        val units = order.size + order.count { tiles[it].padSize == PadSize.WIDE }
        val minColumns = if (units > order.size) 2 else 1
        var bestColumns = minColumns
        var bestRows = 1
        var bestScore = Float.MAX_VALUE
        for (cols in minColumns..units) {
            var rows = 1
            var used = 0
            for (i in order) {
                val span = if (tiles[i].padSize == PadSize.WIDE) 2 else 1
                if (used + span > cols) {
                    rows++
                    used = 0
                }
                used += span
            }
            val w = width / cols.toFloat()
            val h = height / rows.toFloat()
            val score = maxOf(w / h, h / w) + (cols * rows - units) * 0.15f
            if (score < bestScore) {
                bestScore = score
                bestColumns = cols
                bestRows = rows
            }
        }
        // La grille normale sert de référence stable : réduire une percussion ne rétrécit pas les autres.
        // Les formats consécutifs partagent une rangée ; une rangée compacte rend sa hauteur aux grandes.
        val rows = mutableListOf<PadRow>()
        var first = 0
        while (first < order.size) {
            val compact = tiles[order[first]].padSize == PadSize.HALF
            var end = first + 1
            while (end < order.size && (tiles[order[end]].padSize == PadSize.HALF) == compact) end++
            val standardSlots = bestColumns * 2
            val count = end - first
            // Évite de rejeter une percussion seule sur la ligne suivante si toutes restent faciles à toucher.
            val slots = when {
                !compact -> bestColumns
                count == standardSlots + 1 && width.toFloat() / count >= dp(48f) -> count
                else -> standardSlots
            }
            var row = PadRow(compact, slots)
            rows.add(row)
            for (pos in first until end) {
                val index = order[pos]
                val span = if (tiles[index].padSize == PadSize.WIDE) 2 else 1
                if (row.used + span > slots) {
                    row = PadRow(compact, slots)
                    rows.add(row)
                }
                row.indices.add(index)
                row.used += span
            }
            first = end
        }

        val smallRows = rows.count { it.compact }
        val largeRows = rows.size - smallRows
        val halfHeight = height / maxOf(2 * bestRows, smallRows + 2 * largeRows).toFloat()
        val largeHeight = (height - smallRows * halfHeight) / largeRows.coerceAtLeast(1)
        val result = Array(order.size) { RectF() }
        var top = 0f
        for (row in rows) {
            val bottom = top + if (row.compact) halfHeight else largeHeight
            val cellWidth = width.toFloat() / row.slots
            // Les claviers se partagent la largeur libre de leur rangée quand des pads sont réduits.
            val keys = if (!row.compact && smallRows > 0) row.indices.count { tiles[it].levels != null } else 0
            val extra = if (keys > 0) (row.slots - row.used) * cellWidth / keys else 0f
            var left = 0f
            for (i in row.indices) {
                val tile = tiles[i]
                val right = left + cellWidth * (if (tile.padSize == PadSize.WIDE) 2 else 1) +
                    (if (tile.levels != null) extra else 0f)
                result[i].set(left, top, right, bottom)
                left = right
            }
            top = bottom
        }
        return result
    }

    private class PadRow(val compact: Boolean, val slots: Int) {
        val indices = mutableListOf<Int>()
        var used = 0
    }

    private fun rebuildLayout() {
        areas = layoutOf(tiles.indices.toList())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseAll()
        rebuildLayout()
    }

    private fun tileRect(i: Int, out: RectF) {
        out.set((preview ?: areas)[i])
        val gap = minOf(dp(3f), out.width() / 4f, out.height() / 4f)
        out.inset(gap, gap)
    }

    private fun indexAt(x: Float, y: Float) = areas.indexOfFirst { it.contains(x, y) }

    private fun pitchAt(i: Int, x: Float): Int? {
        val levels = tiles[i].levels ?: return -1
        if (levels.isEmpty()) return null
        tileRect(i, rect)
        // Le grave à gauche, l'aigu à droite
        val k = ((x - rect.left) / rect.width() * levels.size).toInt().coerceIn(0, levels.size - 1)
        return levels[k]
    }

    /** L'effet d'un doigt sur une touche : la distance parcourue depuis l'endroit où il s'est posé (à fond au tiers de la touche). */
    private fun effectOf(h: Hold, y: Float): Float {
        tileRect(h.tile, rect)
        val span = (rect.bottom - keysTop(rect)) / 3f
        return if (span <= 0f) 0f else (kotlin.math.abs(y - h.startY) / span).coerceIn(0f, 1f)
    }

    /** Le haut des touches d'une case à notes : une petite bande au-dessus porte le dessin de l'instrument. */
    private fun keysTop(r: RectF): Float = r.top + minOf(dp(if (editing) 30f else 38f), r.height() * 0.35f)

    private fun modeLeft(r: RectF, mode: PadPlayMode): Float =
        maxOf(r.left, r.right - maxOf(dp(48f), modeWidths.getValue(mode) + dp(26f)))

    private fun modeAt(i: Int, x: Float, y: Float): Boolean {
        if (i < 0 || tiles[i].levels == null) return false
        tileRect(i, rect)
        return x >= modeLeft(rect, tiles[i].mode) && y <= keysTop(rect)
    }

    override fun onDraw(canvas: Canvas) {
        val n = areas.size
        if (n == 0) return
        var animating = false
        for (i in 0 until n) {
            val t = tiles[i]
            tileRect(i, rect)
            val g = glow[i] ?: 0f
            val levels = t.levels
            if (levels == null) {
                val pressed = held.values.any { it.tile == i }
                // Au repos, la couleur un peu éteinte ; frappée, elle s'allume puis retombe
                paint.color = blend(base, t.color, if (pressed) 1f else 0.55f + 0.45f * g)
                canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
                InstrumentIcons.draw(canvas, t.icon, rect.centerX(), rect.centerY(), minOf(rect.width(), rect.height()) * 0.38f, Color.WHITE)
            } else {
                drawMelodic(canvas, i, t, levels)
            }
            if (levels == null && rect.height() >= dp(58f)) {
                drawTileName(canvas, t, rect.left + dp(9f), rect.bottom - dp(8f), rect.right - dp(6f))
            }
            if (editing) drawEditControls(canvas, i, t)
            if (g > 0f) {
                glow[i] = (g - 0.08f).coerceAtLeast(0f)
                animating = true
            }
        }
        if (dragging) drawDragIcon(canvas)
        if (animating) postInvalidateOnAnimation()
    }

    /** La poignée montre le sens du prochain changement de largeur. Le contour marque la place du pad déplacé. */
    private fun drawEditControls(canvas: Canvas, i: Int, tile: Tile) {
        if (dragging && i == source) {
            paint.color = Color.argb(145, 15, 16, 24)
            canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color = if (target >= 0) Color.WHITE else Color.GRAY
            canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
            paint.style = Paint.Style.FILL
        }
        val margin = minOf(dp(4f), rect.width() / 20f, rect.height() / 20f)
        val radius = minOf(dp(14f), rect.width() / 4f - margin, rect.height() / 3f)
        val x = rect.right - radius - margin
        val y = rect.top + radius + margin
        val darkInk = Color.rgb(25, 26, 34)
        val resting = Color.argb(175, 20, 20, 28)
        val left = rect.left + margin
        val badgeWidth = minOf(dp(40f), rect.width() / 2f - 2f * margin)
        val compact = tile.padSize == PadSize.HALF
        paint.color = if (compact) Color.WHITE else resting
        canvas.drawRoundRect(left, y - radius, left + badgeWidth, y + radius, radius, radius, paint)
        halfPaint.color = if (compact) darkInk else Color.WHITE
        halfPaint.textSize = label.textSize * minOf(1f, badgeWidth * 0.85f / halfLabelWidth, radius * 1.3f / label.textSize)
        canvas.drawText(halfLabel, left + badgeWidth / 2f, y - (halfPaint.ascent() + halfPaint.descent()) / 2f, halfPaint)

        val wide = tile.padSize == PadSize.WIDE
        paint.color = if (wide) Color.WHITE else resting
        canvas.drawCircle(x, y, radius, paint)
        paint.color = if (wide) darkInk else Color.WHITE
        paint.strokeWidth = dp(1.5f)
        for (side in -1..1 step 2) {
            val outer = x + side * radius * 0.58f
            val inner = x + side * radius * 0.10f
            val tip = if (wide) inner else outer
            val tail = if (wide) outer else inner
            val wing = tip + (tail - tip) * 0.60f
            canvas.drawLine(tail, y, tip, y, paint)
            canvas.drawLine(wing, y - radius * 0.24f, tip, y, paint)
            canvas.drawLine(wing, y + radius * 0.24f, tip, y, paint)
        }
    }

    private fun drawTileName(canvas: Canvas, tile: Tile, x: Float, y: Float, right: Float) {
        if (tile.name.isEmpty() || label.measureText(tile.name) > right - x) return
        label.textAlign = Paint.Align.LEFT
        canvas.drawText(tile.name, x, y, label)
    }

    private fun drawDragIcon(canvas: Canvas) {
        val tile = tiles.getOrNull(source) ?: return
        val radius = minOf(dp(20f), width / 4f, height / 4f)
        val x = fingerX.coerceIn(radius, width - radius)
        val y = (fingerY - dp(28f)).coerceIn(radius, height - radius)
        paint.color = blend(base, tile.color, 0.85f)
        canvas.drawCircle(x, y, radius, paint)
        InstrumentIcons.draw(canvas, tile.icon, x, y, radius, Color.WHITE)
    }

    /** Une case à notes : une touche debout par note, la tonique un peu plus claire (avec son nom), la touche tenue allumée. */
    private fun drawMelodic(canvas: Canvas, i: Int, t: Tile, levels: IntArray) {
        paint.color = blend(base, t.color, 0.25f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        // Le dessin de l'instrument, petit, dans la bande du haut : on reconnaît la piste sans gêner les touches
        val top = keysTop(rect)
        val s = (top - rect.top) * 0.36f
        val titleLeft = rect.left + dp(if (editing) 50f else 10f)
        val titleRight = if (editing) rect.right - dp(36f) else modeLeft(rect, t.mode)
        if (titleLeft + 2f * s < titleRight) {
            InstrumentIcons.draw(canvas, t.icon, titleLeft + s, (rect.top + top) / 2f, s, Color.argb(220, 255, 255, 255))
        }
        if (top - rect.top >= label.textSize + dp(2f)) {
            val titleY = (top + rect.top - label.ascent() - label.descent()) * 0.5f
            drawTileName(canvas, t, titleLeft + dp(4f) + 2f * s, titleY, titleRight)
        }
        if (!editing) drawModeButton(canvas, t.mode, titleRight, top)
        val count = levels.size
        if (count == 0) return
        if (t.mode == PadPlayMode.THEREMIN) {
            drawTheremin(canvas, i, t, levels, top)
            return
        }
        val w = rect.width() / count
        val gap = dp(3f)
        for (k in 0 until count) {
            val p = levels[k]
            band.set(rect.left + k * w + gap, top + gap, rect.left + (k + 1) * w - gap, rect.bottom - gap)
            val tonic = ((p % 12) + 12) % 12 == keyPc
            val finger = held.values.firstOrNull { it.tile == i && it.pitch == p }
            val light = when {
                finger != null -> 1f
                tonic -> 0.62f
                else -> 0.45f
            }
            paint.color = blend(base, t.color, light)
            canvas.drawRoundRect(band, dp(10f), dp(10f), paint)
            if (tonic && noteNames.size == 12 && w > label.textSize * 1.6f) {
                label.textAlign = Paint.Align.CENTER
                canvas.drawText(noteNames[((p % 12) + 12) % 12] + (p / 12 - 1), band.centerX(), band.bottom - dp(10f), label)
            }
            // La teinte suit la modulation choisie.
            if (finger != null && finger.effect > 0f) {
                val alpha = (finger.effect * 150).toInt()
                paint.color = if (t.mode == PadPlayMode.FILTER) Color.argb(alpha, 65, 170, 255) else Color.argb(alpha, 255, 90, 40)
                canvas.drawRoundRect(band, dp(10f), dp(10f), paint)
            }
        }
    }

    private fun drawModeButton(canvas: Canvas, mode: PadPlayMode, left: Float, bottom: Float) {
        val cy = (rect.top + bottom) / 2f
        val radius = minOf(dp(12f), (bottom - rect.top) * 0.44f)
        band.set(left + dp(2f), cy - radius, rect.right - dp(3f), cy + radius)
        paint.color = Color.argb(130, 20, 20, 28)
        canvas.drawRoundRect(band, radius, radius, paint)
        val showName = modeWidths.getValue(mode) + dp(19f) <= band.width() && radius * 2f >= modePaint.textSize
        if (showName) {
            canvas.drawText(modeNames.getValue(mode), band.left + dp(7f), cy - (modePaint.ascent() + modePaint.descent()) / 2f, modePaint)
        }
        val cx = if (showName) band.right - dp(7f) else band.centerX()
        val arrow = minOf(dp(3f), radius / 2f, band.width().coerceAtLeast(0f) / 4f)
        paint.color = Color.WHITE
        paint.strokeWidth = dp(1.5f)
        canvas.drawLine(cx - arrow, cy - arrow / 2f, cx, cy + arrow / 2f, paint)
        canvas.drawLine(cx, cy + arrow / 2f, cx + arrow, cy - arrow / 2f, paint)
    }

    private fun drawTheremin(canvas: Canvas, i: Int, tile: Tile, levels: IntArray, top: Float) {
        val h = rect.bottom - top
        band.set(rect.left, top, rect.right, rect.bottom)
        paint.color = blend(base, tile.color, 0.58f)
        canvas.drawRoundRect(band, dp(12f), dp(12f), paint)
        shadeMatrix.setScale(1f, h)
        shadeMatrix.postTranslate(0f, top)
        thereminShade.setLocalMatrix(shadeMatrix)
        paint.shader = thereminShade
        canvas.drawRoundRect(band, dp(12f), dp(12f), paint)
        paint.shader = null
        val w = rect.width() / levels.size
        for (k in levels.indices) {
            val x = rect.left + k * w
            val p = levels[k]
            val tonic = p % 12 == keyPc
            if (k > 0) {
                paint.color = if (tonic) 0x77FFFFFF else 0x33FFFFFF
                paint.strokeWidth = dp(1f)
                canvas.drawLine(x, top + dp(5f), x, rect.bottom - dp(5f), paint)
            }
            if (tonic && noteNames.size == 12 && w > label.textSize * 1.6f && h > label.textSize * 2) {
                label.textAlign = Paint.Align.CENTER
                canvas.drawText(noteNames[keyPc] + (p / 12 - 1), x + w / 2f, rect.bottom - dp(10f), label)
            }
        }
        val finger = held.values.firstOrNull { it.tile == i } ?: return
        val radius = minOf(dp(10f), h / 2f, rect.width() / 2f)
        val cx = finger.x.coerceIn(rect.left + radius, (rect.right - radius).coerceAtLeast(rect.left + radius))
        val cy = (rect.bottom - h * (finger.effect - 0.15f) / 0.85f)
            .coerceIn(top + radius, (rect.bottom - radius).coerceAtLeast(top + radius))
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** Fait briller la case de la piste [track] (un coup du roulement, par exemple). */
    fun flash(track: Int) {
        val i = tiles.indexOfFirst { it.track == track }
        if (i < 0 || tiles[i].levels != null) return
        glow[i] = 1f
        postInvalidateOnAnimation()
    }

    private fun expressionAt(i: Int, y: Float): Float {
        tileRect(i, rect)
        val height = (rect.bottom - keysTop(rect)).coerceAtLeast(1f)
        return 0.15f + 0.85f * ((rect.bottom - y) / height).coerceIn(0f, 1f)
    }

    private fun press(pointer: Int, h: Hold) {
        val t = tiles[h.tile]
        if (t.mode == PadPlayMode.THEREMIN && t.levels != null) {
            if (held.values.any { it.tile == h.tile }) {
                mutedPointers.add(pointer)
                return
            }
            h.effect = expressionAt(h.tile, h.startY)
            onThereminExpression?.invoke(t.track, h.effect)
        }
        held[pointer] = h
        if (h.pitch < 0) {
            glow[h.tile] = 1f
            onDown?.invoke(t.track)
        } else {
            onNoteDown?.invoke(t.track, h.pitch)
        }
    }

    private fun lift(pointer: Int) {
        val h = held.remove(pointer) ?: return
        val t = tiles.getOrNull(h.tile) ?: return
        if (h.pitch < 0) onUp?.invoke(t.track) else onNoteUp?.invoke(t.track, h.pitch)
    }

    private fun sizeHandleAt(i: Int, x: Float, y: Float): PadSize? {
        val area = areas[i]
        if (!area.contains(x, y) || y >= area.top + dp(48f)) return null
        val hitWidth = minOf(dp(48f), area.width() / 2f)
        return when {
            x < area.left + hitWidth -> PadSize.HALF
            x >= area.right - hitWidth -> PadSize.WIDE
            else -> null
        }
    }

    private fun moveEdit(x: Float, y: Float) {
        fingerX = x
        fingerY = y
        if (!dragging) {
            val dx = x - startX
            val dy = y - startY
            if (dx * dx + dy * dy <= touchSlop * touchSlop) return
            dragging = true
        }
        // Les zones de dépôt restent stables sous le doigt pendant que les autres pads montrent le résultat.
        var next = indexAt(x, y)
        val last = areas.last()
        if (next < 0 && x >= last.right && x < width && y >= last.top && y < height) next = areas.lastIndex
        if (next != target) {
            target = next
            preview = if (next < 0 || next == source) null else {
                val order = tiles.indices.toMutableList()
                order.add(next, order.removeAt(source))
                layoutOf(order)
            }
        }
        invalidate()
    }

    private fun clearEdit() {
        editPointer = -1
        source = -1
        target = -1
        dragging = false
        preview = null
    }

    private fun touchEdit(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startX = event.x
                startY = event.y
                source = indexAt(startX, startY)
                target = source
                if (source >= 0) editPointer = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (editPointer < 0) return
                val i = event.findPointerIndex(editPointer)
                if (i >= 0) moveEdit(event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = event.actionIndex
                if (event.getPointerId(i) != editPointer) return
                val x = event.getX(i)
                val y = event.getY(i)
                moveEdit(x, y)
                val track = tiles[source].track
                val destination = if (dragging && target >= 0 && target != source) tiles[target].track else null
                val handle = if (dragging) null else sizeHandleAt(source, x, y)?.takeIf { it == sizeHandleAt(source, startX, startY) }
                clearEdit()
                if (destination != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onReorder?.invoke(track, destination)
                } else if (handle != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (handle == PadSize.HALF) onToggleHalf?.invoke(track) else onResize?.invoke(track)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) interrupted = false
        if (interrupted) return true
        if (editing) {
            touchEdit(event)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val id = event.getPointerId(idx)
                val tile = indexAt(x, y)
                if (modeAt(tile, x, y)) {
                    modePresses[id] = tile
                } else if (tile >= 0) {
                    pitchAt(tile, x)?.let { pitch ->
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        press(id, Hold(tile, pitch, y, x = x))
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // Un doigt qui glisse vers une autre case (ou un autre niveau) lâche l'ancienne et joue la nouvelle
                for (k in 0 until event.pointerCount) {
                    val id = event.getPointerId(k)
                    val x = event.getX(k)
                    val y = event.getY(k)
                    val tile = indexAt(x, y)
                    if (id in mutedPointers) continue
                    val button = modePresses[id]
                    if (button != null) {
                        if (tile != button || !modeAt(button, x, y)) {
                            modePresses.remove(id)
                            mutedPointers.add(id)
                        }
                        continue
                    }
                    val pitch = if (tile >= 0) pitchAt(tile, x) else null
                    val was = held[id]
                    if (was != null && tile == was.tile && pitch != null && pitch >= 0 && tiles[tile].mode == PadPlayMode.THEREMIN) {
                        val track = tiles[tile].track
                        val expression = expressionAt(tile, y)
                        if (expression != was.effect) {
                            was.effect = expression
                            onThereminExpression?.invoke(track, expression)
                        }
                        was.x = x
                        if (pitch != was.pitch) {
                            val from = was.pitch
                            was.pitch = pitch
                            onGlide?.invoke(track, from, pitch)
                        }
                        invalidate()
                        continue
                    }
                    if (was != null && tile == was.tile && pitch == was.pitch) {
                        if (was.pitch >= 0 && tiles[tile].mode != PadPlayMode.CLEAN) {
                            val e = effectOf(was, y)
                            if (e != was.effect) {
                                was.effect = e
                                onNoteEffect?.invoke(tiles[tile].track, e)
                                invalidate()
                            }
                        }
                        continue
                    }
                    if (pitch == null && was == null) continue
                    lift(id)
                    if (pitch != null) {
                        val previous = was?.takeIf { it.tile == tile && pitch >= 0 && tiles[tile].mode != PadPlayMode.CLEAN }
                        val h = Hold(tile, pitch, previous?.startY ?: y, x = x)
                        if (previous != null) h.effect = effectOf(h, y)
                        press(id, h)
                        if (previous != null && h.effect > 0f) onNoteEffect?.invoke(tiles[tile].track, h.effect)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val button = modePresses.remove(id)
                mutedPointers.remove(id)
                if (button != null && indexAt(event.getX(idx), event.getY(idx)) == button && modeAt(button, event.getX(idx), event.getY(idx))) {
                    onChooseMode?.invoke(tiles[button].track, modeLeft(rect, tiles[button].mode).toInt(), keysTop(rect).toInt())
                } else {
                    lift(id)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    /** Lâche tout (doigts perdus, page quittée) : aucun roulement ni aucune note ne reste coincé. */
    fun releaseAll() {
        interrupted = true
        clearEdit()
        modePresses.clear()
        mutedPointers.clear()
        for (id in held.keys.toList()) lift(id)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }
}
