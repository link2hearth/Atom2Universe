package com.Atom2Universe.app.games.theline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.random.Random

/**
 * Le décor de Circuit : un circuit imprimé vu de près. Le plateau de jeu, sa tuile du hub
 * et le widget du clicker peignent tous avec ces mêmes pièces.
 *
 * Les tailles sont données en fraction de case (`cell`), pour que le même dessin marche
 * dans une tuile de 180 px comme sur une tablette.
 */
object CircuitPainter {

    const val MASK_TOP = 0xFF07281C.toInt()
    const val MASK_BOTTOM = 0xFF041A12.toInt()
    private const val DECO_TRACE = 0xFF0D3A29.toInt()
    private const val DECO_VIA = 0xFF10432F.toInt()
    private const val HOLE = 0xFF052117.toInt()
    const val PLATE = 0xFF0C4230.toInt()
    private const val PLATE_SHADOW = 0xFF03140D.toInt()
    private const val SILK = 0x59D6ECDE
    private const val VIA_RING = 0xFF2F6B51.toInt()

    const val COPPER_EDGE = 0xFF3B2210.toInt()
    const val COPPER = 0xFFB8703A.toInt()
    const val COPPER_SHINE = 0xFFE9A869.toInt()
    const val POWER = 0xFFFFC95C.toInt()
    const val POWER_CORE = 0xFFFFF4D2.toInt()
    const val POWER_GLOW = 0x2EFFC450
    const val LED = 0xFFFF5A46.toInt()

    private const val PAD_SHADOW = 0xFF5E4210.toInt()
    private const val PAD = 0xFFD4A23A.toInt()
    private const val PAD_LIT = 0xFFFFD66B.toInt()
    private const val PAD_SHINE = 0xFFE8C063.toInt()
    private const val PAD_SHINE_LIT = 0xFFFFF1C2.toInt()
    private const val PAD_TEXT = 0xFF2A1A05.toInt()

    private const val SOCKET = 0xFF101417.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = PAD_TEXT
    }
    private val rect = RectF()
    private val glowShaders = HashMap<Int, Shader>()

    // ── Fond ──────────────────────────────────────────────────────────────────────
    /**
     * Le vernis vert autour du plateau, parcouru de pistes décoratives qui tournent à 45°
     * comme sur une vraie carte, chacune finie par un trou métallisé. [unit] règle leur
     * échelle (une case de jeu environ).
     */
    fun backdrop(canvas: Canvas, w: Float, h: Float, seed: Int, unit: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, h, MASK_TOP, MASK_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        val rnd = Random(seed)
        val dxs = intArrayOf(1, 0, -1, 0); val dys = intArrayOf(0, 1, 0, -1)
        val count = (w * h / (unit * unit * 9f)).toInt().coerceIn(6, 260)
        stroke.color = DECO_TRACE; stroke.strokeWidth = unit * 0.09f
        val path = Path()
        repeat(count) {
            var x = rnd.nextFloat() * w; var y = rnd.nextFloat() * h
            val sx = x; val sy = y
            path.reset(); path.moveTo(x, y)
            var dir = rnd.nextInt(4)
            repeat(3) {
                val len = unit * (0.6f + rnd.nextFloat() * 2.2f)
                x += dxs[dir] * len; y += dys[dir] * len
                path.lineTo(x, y)
                val next = (dir + if (rnd.nextBoolean()) 1 else 3) % 4
                val chamfer = unit * 0.35f
                x += (dxs[dir] + dxs[next]) * chamfer; y += (dys[dir] + dys[next]) * chamfer
                path.lineTo(x, y)
                dir = next
            }
            canvas.drawPath(path, stroke)
            decoVia(canvas, sx, sy, unit); decoVia(canvas, x, y, unit)
        }
    }

    private fun decoVia(canvas: Canvas, x: Float, y: Float, unit: Float) {
        fill.color = DECO_VIA; canvas.drawCircle(x, y, unit * 0.14f, fill)
        fill.color = HOLE; canvas.drawCircle(x, y, unit * 0.06f, fill)
    }

    /** La plaque de jeu : un rectangle de vernis plus clair, son ombre et son liseré sérigraphié. */
    fun plate(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, cell: Float) {
        val r = cell * 0.3f
        fill.color = PLATE_SHADOW
        rect.set(left + cell * 0.05f, top + cell * 0.07f, right + cell * 0.05f, bottom + cell * 0.07f)
        canvas.drawRoundRect(rect, r, r, fill)
        fill.color = PLATE
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, r, r, fill)
        val inset = cell * 0.14f
        stroke.color = SILK; stroke.strokeWidth = cell * 0.025f
        rect.set(left + inset, top + inset, right - inset, bottom - inset)
        canvas.drawRoundRect(rect, r * 0.75f, r * 0.75f, stroke)
    }

    /** Une case libre : un petit trou métallisé, là où la piste devra passer. */
    fun via(canvas: Canvas, cx: Float, cy: Float, cell: Float) {
        fill.color = VIA_RING; canvas.drawCircle(cx, cy, cell * 0.1f, fill)
        fill.color = HOLE; canvas.drawCircle(cx, cy, cell * 0.045f, fill)
    }

    // ── Composants ────────────────────────────────────────────────────────────────
    /**
     * Les cases bloquées, peuplées de composants. Un carré de 2 × 2 cases devient une
     * grosse puce à quatre rangées de pattes ; les autres cases reçoivent chacune un
     * composant tiré de leur position, toujours le même pour une même case.
     */
    fun components(canvas: Canvas, width: Int, height: Int, blocked: Set<Int>, ox: Float, oy: Float, cell: Float) {
        val used = HashSet<Int>()
        for (y in 0 until height - 1) for (x in 0 until width - 1) {
            val a = y * width + x
            val quad = intArrayOf(a, a + 1, a + width, a + width + 1)
            if (quad.all { it in blocked && it !in used }) {
                quad.forEach { used.add(it) }
                chip(canvas, ox + (x + 1) * cell, oy + (y + 1) * cell, cell * 0.62f, 4, true)
            }
        }
        for (i in blocked) {
            if (i in used) continue
            val x = i % width; val y = i / width
            val cx = ox + (x + 0.5f) * cell; val cy = oy + (y + 0.5f) * cell
            val hash = ((x * 73856093) xor (y * 19349663)) and 0x7FFFFFFF
            val vertical = (hash shr 4) and 1 == 1
            when (hash % 5) {
                0 -> chip(canvas, cx, cy, cell * 0.26f, 3, false)
                1 -> capacitor(canvas, cx, cy, cell)
                2 -> resistor(canvas, cx, cy, cell, vertical)
                3 -> ceramic(canvas, cx, cy, cell, vertical)
                else -> crystal(canvas, cx, cy, cell)
            }
        }
    }

    /** Une puce vue de dessus : corps noir, pattes argentées, point de la patte 1. [s] = demi-côté. */
    fun chip(canvas: Canvas, cx: Float, cy: Float, s: Float, pinsPerSide: Int, fourSides: Boolean) {
        fill.color = 0xFFAEB6BA.toInt()
        val pitch = s * 2f / (pinsPerSide * 2 + 1)
        val pw = pitch * 0.9f; val pl = s * 0.28f
        for (i in 0 until pinsPerSide) {
            val t = -s + (i * 2 + 1.5f) * pitch
            canvas.drawRect(cx - s - pl, cy + t - pw / 2, cx - s, cy + t + pw / 2, fill)
            canvas.drawRect(cx + s, cy + t - pw / 2, cx + s + pl, cy + t + pw / 2, fill)
            if (fourSides) {
                canvas.drawRect(cx + t - pw / 2, cy - s - pl, cx + t + pw / 2, cy - s, fill)
                canvas.drawRect(cx + t - pw / 2, cy + s, cx + t + pw / 2, cy + s + pl, fill)
            }
        }
        fill.color = 0xFF1A1E21.toInt()
        rect.set(cx - s, cy - s, cx + s, cy + s)
        canvas.drawRoundRect(rect, s * 0.12f, s * 0.12f, fill)
        stroke.color = 0x12FFFFFF; stroke.strokeWidth = s * 0.05f
        rect.inset(s * 0.14f, s * 0.14f)
        canvas.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke)
        fill.color = 0xFF2C3236.toInt()
        canvas.drawCircle(cx - s * 0.6f, cy - s * 0.6f, s * 0.12f, fill)
    }

    /** Un condensateur chimique vu de dessus : boîtier alu, bande de polarité, évent en croix. */
    fun capacitor(canvas: Canvas, cx: Float, cy: Float, cell: Float) {
        val r = cell * 0.34f
        fill.color = 0xFF3A4247.toInt()
        canvas.drawCircle(cx + cell * 0.03f, cy + cell * 0.04f, r, fill)
        fill.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.2f,
            0xFFE4E9EC.toInt(), 0xFF8F989E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
        fill.color = 0xFF2B4E8C.toInt()
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 117f, 126f, false, fill)
        stroke.color = 0xFF6F787E.toInt(); stroke.strokeWidth = cell * 0.03f
        canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, stroke)
        canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, stroke)
    }

    /** Une résistance à pattes, avec ses anneaux de couleur. */
    fun resistor(canvas: Canvas, cx: Float, cy: Float, cell: Float, vertical: Boolean) {
        canvas.save(); canvas.translate(cx, cy)
        if (vertical) canvas.rotate(90f)
        stroke.color = 0xFFB9C0C4.toInt(); stroke.strokeWidth = cell * 0.05f
        canvas.drawLine(-cell * 0.44f, 0f, cell * 0.44f, 0f, stroke)
        val r = cell * 0.12f
        fill.color = 0xFF8A6A3A.toInt()
        rect.set(-cell * 0.3f, -cell * 0.1f, cell * 0.3f, cell * 0.16f)
        canvas.drawRoundRect(rect, r, r, fill)
        fill.color = 0xFFD8C29A.toInt()
        rect.set(-cell * 0.3f, -cell * 0.13f, cell * 0.3f, cell * 0.13f)
        canvas.drawRoundRect(rect, r, r, fill)
        val bands = intArrayOf(0xFF6B3A1E.toInt(), 0xFF1B1B1B.toInt(), 0xFFD23B2E.toInt(), 0xFFC9A53A.toInt())
        for (i in bands.indices) {
            fill.color = bands[i]
            val x = -cell * 0.19f + i * cell * 0.1f
            canvas.drawRect(x, -cell * 0.13f, x + cell * 0.045f, cell * 0.13f, fill)
        }
        canvas.restore()
    }

    /** Un condensateur céramique en boîtier CMS : un pavé brun et ses deux bouts étamés. */
    fun ceramic(canvas: Canvas, cx: Float, cy: Float, cell: Float, vertical: Boolean) {
        canvas.save(); canvas.translate(cx, cy)
        if (vertical) canvas.rotate(90f)
        fill.color = 0xFFC9CED1.toInt()
        canvas.drawRect(-cell * 0.26f, -cell * 0.14f, -cell * 0.16f, cell * 0.14f, fill)
        canvas.drawRect(cell * 0.16f, -cell * 0.14f, cell * 0.26f, cell * 0.14f, fill)
        fill.color = 0xFFB8743A.toInt()
        canvas.drawRect(-cell * 0.16f, -cell * 0.14f, cell * 0.16f, cell * 0.14f, fill)
        canvas.restore()
    }

    /** Un quartz : un boîtier métallique ovale. */
    fun crystal(canvas: Canvas, cx: Float, cy: Float, cell: Float) {
        fill.color = 0xFF8F989E.toInt()
        rect.set(cx - cell * 0.34f, cy - cell * 0.18f, cx + cell * 0.34f, cy + cell * 0.18f)
        canvas.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, fill)
        fill.color = 0xFFD5DADD.toInt()
        rect.set(cx - cell * 0.3f, cy - cell * 0.15f, cx + cell * 0.3f, cy + cell * 0.13f)
        canvas.drawRoundRect(rect, cell * 0.14f, cell * 0.14f, fill)
    }

    // ── Pistes et bornes ─────────────────────────────────────────────────────────
    /** Une piste de cuivre le long de [path] : liseré sombre, cuivre, reflet. */
    fun copper(canvas: Canvas, path: Path, cell: Float) {
        strokePath(canvas, path, cell * 0.44f, COPPER_EDGE)
        strokePath(canvas, path, cell * 0.34f, COPPER)
        strokePath(canvas, path, cell * 0.13f, COPPER_SHINE)
    }

    /** La même piste, parcourue par le courant. */
    fun powered(canvas: Canvas, path: Path, cell: Float) {
        strokePath(canvas, path, cell * 0.7f, POWER_GLOW)
        strokePath(canvas, path, cell * 0.34f, POWER)
        strokePath(canvas, path, cell * 0.14f, POWER_CORE)
    }

    /** Un fil gainé de couleur : bord sombre, gaine, reflet clair. [lit] l'éclaircit quand le courant passe. */
    fun wire(canvas: Canvas, path: Path, cell: Float, color: Int, lit: Float = 0f) {
        val body = blend(color, Color.WHITE, lit * 0.35f)
        strokePath(canvas, path, cell * 0.4f, blend(color, Color.BLACK, 0.55f))
        strokePath(canvas, path, cell * 0.32f, body)
        strokePath(canvas, path, cell * 0.08f, blend(color, Color.WHITE, 0.5f + lit * 0.3f))
    }

    private fun strokePath(canvas: Canvas, path: Path, width: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = width
        canvas.drawPath(path, stroke)
    }

    /**
     * Une borne numérotée : une pastille dorée, carrée pour la borne 1 comme la patte 1
     * d'un composant, ronde pour les autres. [lit] : la piste l'a déjà traversée.
     */
    fun pad(canvas: Canvas, x: Float, y: Float, cell: Float, number: Int, lit: Boolean) {
        val r = cell * 0.31f
        val square = number == 1
        fill.color = PAD_SHADOW
        drawPadShape(canvas, x - cell * (if (square) 0.02f else 0f), y + cell * 0.03f, r, square)
        fill.color = if (lit) PAD_LIT else PAD
        drawPadShape(canvas, x, y, r, square)
        fill.color = if (lit) PAD_SHINE_LIT else PAD_SHINE
        canvas.drawCircle(x - r * 0.25f, y - r * 0.3f, r * 0.35f, fill)
        text.textSize = cell * (if (number >= 10) 0.28f else 0.34f)
        canvas.drawText(number.toString(), x, y - (text.ascent() + text.descent()) / 2f, text)
    }

    private fun drawPadShape(canvas: Canvas, x: Float, y: Float, r: Float, square: Boolean) {
        if (square) {
            rect.set(x - r, y - r, x + r, y + r)
            canvas.drawRoundRect(rect, r * 0.25f, r * 0.25f, fill)
        } else canvas.drawCircle(x, y, r, fill)
    }

    /** Le liseré sérigraphié autour de la dernière borne : c'est là que s'allumera la LED. */
    fun ledRing(canvas: Canvas, x: Float, y: Float, cell: Float) {
        stroke.color = 0x73D6ECDE; stroke.strokeWidth = cell * 0.03f
        canvas.drawCircle(x, y, cell * 0.42f, stroke)
    }

    /** Un connecteur de fil : une embase noire, l'isolant de couleur, le trou du contact. */
    fun socket(canvas: Canvas, x: Float, y: Float, cell: Float, color: Int, lit: Boolean) {
        val s = cell * 0.33f
        fill.color = SOCKET
        rect.set(x - s, y - s, x + s, y + s)
        canvas.drawRoundRect(rect, s * 0.25f, s * 0.25f, fill)
        fill.color = if (lit) blend(color, Color.WHITE, 0.35f) else color
        rect.inset(s * 0.32f, s * 0.32f)
        canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
        fill.color = SOCKET
        canvas.drawCircle(x, y, s * 0.22f, fill)
    }

    /** Un halo doux de couleur [color] ; les dégradés sont gardés par couleur, à rayon 1. */
    fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0f || radius <= 0f) return
        val shader = glowShaders.getOrPut(color) {
            RadialGradient(0f, 0f, 1f, intArrayOf(color, (color and 0xFFFFFF) or (0x60 shl 24), color and 0xFFFFFF),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        fill.shader = shader
        fill.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.save(); canvas.translate(x, y); canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, fill)
        canvas.restore()
        fill.shader = null
        fill.alpha = 255
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - k) + ((b shr s) and 0xFF) * k).toInt()
        return Color.argb((a ushr 24), ch(16), ch(8), ch(0))
    }
}
