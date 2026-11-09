package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Les pads, à jouer au doigt : une grande case par piste (sa couleur, son dessin), plusieurs doigts à la fois.
 *
 * - Une case de **batterie** frappe : [onDown] quand un doigt s'y pose, [onUp] quand il la quitte (le moteur fait le roulement
 *   entre les deux).
 * - Une case de **piste à notes** est un petit clavier : une touche debout par note de la gamme (le grave à gauche, l'aigu à
 *   droite). La note tient tant que le doigt reste ([onNoteDown], [onNoteUp]) ; glisser vers une autre touche change de note,
 *   deux doigts font un accord. Posé, le doigt joue le son normal ; c'est en bougeant (vers le haut ou vers le bas) qu'il le
 *   distord, d'autant plus qu'il s'éloigne de l'endroit où il s'est posé ([onNoteEffect], de 0 à 1).
 *
 * Les cases remplissent toute la place, en carrés autant que possible.
 */
class PadsView(context: Context) : View(context) {

    /** Une case : la piste, sa couleur, son dessin, et pour une piste à notes ses niveaux (hauteurs MIDI, du grave à l'aigu). */
    class Tile(val track: Int, val color: Int, val icon: Int, val levels: IntArray? = null)

    var tiles: List<Tile> = emptyList()
        set(v) {
            releaseAll()
            field = v
            invalidate()
        }

    /** La tonique (0 = do) et les noms des notes : les niveaux de la tonique portent leur nom. */
    var keyPc: Int = 0
    var noteNames: List<String> = emptyList()

    var onDown: ((track: Int) -> Unit)? = null
    var onUp: ((track: Int) -> Unit)? = null
    var onNoteDown: ((track: Int, pitch: Int) -> Unit)? = null
    var onNoteUp: ((track: Int, pitch: Int) -> Unit)? = null
    var onNoteEffect: ((track: Int, amount: Float) -> Unit)? = null

    /**
     * Ce que tient chaque doigt : la case, sur une piste à notes la note (-1 : un coup de batterie), la hauteur où il s'est posé et
     * l'effet du moment (0 posé, jusqu'à 1 quand il s'est éloigné).
     */
    private class Hold(val tile: Int, val pitch: Int, var startY: Float = 0f, var effect: Float = 0f)

    private val held = HashMap<Int, Hold>()

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
    private val rect = RectF()
    private val band = RectF()



    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Colonnes et lignes : la grille la plus proche du carré pour [n] cases dans la vue. */
    private fun grid(n: Int): Pair<Int, Int> {
        if (n <= 0 || width == 0 || height == 0) return 1 to 1
        var best = 1
        var bestScore = Float.MAX_VALUE
        for (cols in 1..n) {
            val rows = (n + cols - 1) / cols
            val w = width / cols.toFloat()
            val h = height / rows.toFloat()
            // Le meilleur : des cases presque carrées et le moins de cases vides
            val score = maxOf(w / h, h / w) + (cols * rows - n) * 0.15f
            if (score < bestScore) {
                bestScore = score
                best = cols
            }
        }
        return best to (n + best - 1) / best
    }

    private fun tileRect(i: Int, out: RectF) {
        val (cols, rows) = grid(tiles.size)
        val cw = width / cols.toFloat()
        val ch = height / rows.toFloat()
        val gap = dp(6f)
        val c = i % cols
        val r = i / cols
        out.set(c * cw + gap / 2, r * ch + gap / 2, (c + 1) * cw - gap / 2, (r + 1) * ch - gap / 2)
    }

    /** La case et la note sous un point (-1, -1 : aucune ; note -1 : une case de batterie). */
    private fun hit(x: Float, y: Float): Hold? {
        val n = tiles.size
        if (n == 0) return null
        val (cols, rows) = grid(n)
        val c = (x / (width / cols.toFloat())).toInt()
        val r = (y / (height / rows.toFloat())).toInt()
        if (c !in 0 until cols || r !in 0 until rows) return null
        val i = r * cols + c
        if (i >= n) return null
        val levels = tiles[i].levels ?: return Hold(i, -1)
        if (levels.isEmpty()) return null
        tileRect(i, rect)
        // Le grave à gauche, l'aigu à droite
        val k = ((x - rect.left) / rect.width() * levels.size).toInt().coerceIn(0, levels.size - 1)
        return Hold(i, levels[k], y)
    }

    /** L'effet d'un doigt sur une touche : la distance parcourue depuis l'endroit où il s'est posé (à fond au tiers de la touche). */
    private fun effectOf(h: Hold, y: Float): Float {
        tileRect(h.tile, rect)
        val span = (rect.bottom - keysTop(rect)) / 3f
        return if (span <= 0f) 0f else (kotlin.math.abs(y - h.startY) / span).coerceIn(0f, 1f)
    }

    /** Le haut des touches d'une case à notes : une petite bande au-dessus porte le dessin de l'instrument. */
    private fun keysTop(r: RectF): Float = r.top + minOf(dp(30f), r.height() * 0.15f)

    override fun onDraw(canvas: Canvas) {
        val n = tiles.size
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
            if (g > 0f) {
                glow[i] = (g - 0.08f).coerceAtLeast(0f)
                animating = true
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    /** Une case à notes : une touche debout par note, la tonique un peu plus claire (avec son nom), la touche tenue allumée. */
    private fun drawMelodic(canvas: Canvas, i: Int, t: Tile, levels: IntArray) {
        paint.color = blend(base, t.color, 0.25f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        // Le dessin de l'instrument, petit, dans la bande du haut : on reconnaît la piste sans gêner les touches
        val top = keysTop(rect)
        val s = (top - rect.top) * 0.36f
        InstrumentIcons.draw(canvas, t.icon, rect.left + dp(10f) + s, (rect.top + top) / 2f, s, Color.argb(220, 255, 255, 255))
        val count = levels.size
        if (count == 0) return
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
            // La touche tenue rougeoie d'autant plus que le doigt distord le son
            if (finger != null && finger.effect > 0f) {
                paint.color = Color.argb((finger.effect * 150).toInt(), 255, 90, 40)
                canvas.drawRoundRect(band, dp(10f), dp(10f), paint)
            }
        }
    }

    /** Fait briller la case de la piste [track] (un coup du roulement, par exemple). */
    fun flash(track: Int) {
        val i = tiles.indexOfFirst { it.track == track }
        if (i < 0 || tiles[i].levels != null) return
        glow[i] = 1f
        postInvalidateOnAnimation()
    }

    private fun press(pointer: Int, h: Hold) {
        held[pointer] = h
        val t = tiles[h.tile]
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = event.actionIndex
                val h = hit(event.getX(idx), event.getY(idx))
                if (h != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press(event.getPointerId(idx), h)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // Un doigt qui glisse vers une autre case (ou un autre niveau) lâche l'ancienne et joue la nouvelle
                for (k in 0 until event.pointerCount) {
                    val id = event.getPointerId(k)
                    val y = event.getY(k)
                    val h = hit(event.getX(k), y)
                    val was = held[id]
                    if (h != null && was != null && h.tile == was.tile && h.pitch == was.pitch) {
                        // Même touche : le doigt qui bouge distord le son
                        if (h.pitch >= 0) {
                            val e = effectOf(was, y)
                            if (e != was.effect) {
                                was.effect = e
                                onNoteEffect?.invoke(tiles[h.tile].track, e)
                                invalidate()
                            }
                        }
                        continue
                    }
                    if (h == null && was == null) continue
                    lift(id)
                    if (h != null) {
                        // Glisser vers une autre touche de la même case garde l'élan : la distorsion continue
                        if (was != null && was.tile == h.tile && h.pitch >= 0) {
                            h.startY = was.startY
                            h.effect = effectOf(h, y)
                        }
                        press(id, h)
                        if (h.pitch >= 0 && h.effect > 0f) onNoteEffect?.invoke(tiles[h.tile].track, h.effect)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                lift(event.getPointerId(event.actionIndex))
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    /** Lâche tout (doigts perdus, page quittée) : aucun roulement ni aucune note ne reste coincé. */
    fun releaseAll() {
        for (id in held.keys.toList()) lift(id)
        invalidate()
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
