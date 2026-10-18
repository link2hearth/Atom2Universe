package com.Atom2Universe.app.games.roguelike

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le dessin des gestes de reliques pendant qu'on les joue, et leur pictogramme sur les boutons.
 *
 * Un même vocabulaire pour tous : **un viseur sur la cible porte la jauge** (un arc qui se
 * remplit depuis midi, zone verte = Bien, zone dorée = Parfait, un trait blanc au milieu) ; le
 * viseur s'allume quand c'est le moment ou quand le doigt est dessus ; **un cercle qui bat**
 * montre où poser le doigt. La couleur de ce qu'on fabrique suit la jauge : pâle (pas encore),
 * jaune (Bien), blanc (Parfait), rouge (trop). Aucune consigne écrite.
 *
 * Rien n'est alloué pendant le dessin. [d] est la densité de l'écran (1 dp en pixels).
 */
internal class RelicGestureArt {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val segments = FloatArray(DrawGesture.MAX_POINTS * 4)
    private val glyph = FloatArray(16)
    private val shape = Path()

    fun draw(canvas: Canvas, gesture: TouchGesture, t: Float, d: Float) {
        when (gesture) {
            is DrawGesture -> drawBolt(canvas, gesture, t, d)
            is ChargeGesture -> drawCharge(canvas, gesture, t, d)
            is FreezeGesture -> drawFreeze(canvas, gesture, t, d)
            is DoseGesture -> drawDose(canvas, gesture, t, d)
            is DialGesture -> drawDial(canvas, gesture, t, d)
            is WaveGesture -> drawWave(canvas, gesture, t, d)
            is HourglassGesture -> drawHourglass(canvas, gesture, t, d)
        }
    }

    // ── Le vocabulaire commun ───────────────────────────────────────────────────

    /** La couleur de ce qu'on fabrique, selon la jauge : [weak] tant qu'on n'est pas dans la zone. */
    private fun levelColor(level: Float, center: Float, good: Float, perfect: Float, weak: Int): Int = when {
        level > center + good -> 0xFFEF5350.toInt()
        perfect > 0f && level >= center - perfect -> Color.WHITE
        level >= center - good -> 0xFFFFE27A.toInt()
        else -> weak
    }

    /**
     * La jauge autour d'un viseur de rayon [r] : piste sombre, zone verte, zone dorée (si
     * [perfect] > 0), le remplissage depuis midi, et le trait blanc au milieu de la zone.
     */
    private fun gaugeRing(canvas: Canvas, x: Float, y: Float, r: Float, level: Float,
        center: Float, good: Float, perfect: Float, color: Int, d: Float) {
        val gr = r + 9f * d
        oval.set(x - gr, y - gr, x + gr, y + gr)
        fun angle(f: Float) = -90f + 360f * f
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = 11f * d
        stroke.color = 0xCC111729.toInt()
        canvas.drawCircle(x, y, gr, stroke)
        stroke.strokeWidth = 7f * d
        stroke.color = 0xFF7CB342.toInt()
        canvas.drawArc(oval, angle(center - good), 720f * good, false, stroke)
        if (perfect > 0f) {
            stroke.color = 0xFFFFD54F.toInt()
            canvas.drawArc(oval, angle(center - perfect), 720f * perfect, false, stroke)
        }
        if (level > 0f) {
            stroke.strokeWidth = 4f * d
            stroke.color = color
            canvas.drawArc(oval, -90f, 360f * level.coerceAtMost(1f), false, stroke)
        }
        val mid = Math.toRadians(angle(center).toDouble())
        val mx = cos(mid).toFloat(); val my = sin(mid).toFloat()
        stroke.color = Color.WHITE; stroke.strokeWidth = 3f * d
        canvas.drawLine(x + mx * (gr - 8f * d), y + my * (gr - 8f * d), x + mx * (gr + 8f * d), y + my * (gr + 8f * d), stroke)
    }

    /** Le cercle du viseur : blanc, ou allumé (jaune, plus épais). */
    private fun reticle(canvas: Canvas, x: Float, y: Float, r: Float, lit: Boolean, d: Float) {
        stroke.color = Color.BLACK; stroke.strokeWidth = (if (lit) 7f else 5f) * d
        canvas.drawCircle(x, y, r, stroke)
        stroke.color = if (lit) 0xFFFFE27A.toInt() else Color.WHITE; stroke.strokeWidth = (if (lit) 4f else 2.5f) * d
        canvas.drawCircle(x, y, r, stroke)
    }

    /** Là où poser le doigt : un cercle blanc qui bat, un point au milieu. */
    private fun beacon(canvas: Canvas, x: Float, y: Float, t: Float, d: Float, dot: Int = 0xFFFFE27A.toInt()) {
        val beat = 18f * d + 3f * d * sin(t / 120f)
        stroke.color = Color.BLACK; stroke.strokeWidth = 5f * d
        canvas.drawCircle(x, y, beat, stroke)
        stroke.color = Color.WHITE; stroke.strokeWidth = 3f * d
        canvas.drawCircle(x, y, beat, stroke)
        fill.color = dot
        canvas.drawCircle(x, y, 6f * d, fill)
    }

    // ── Foudre : dessiner l'éclair ──────────────────────────────────────────────

    /** L'éclair dessiné crépite (chaque point bouge un peu à chaque instant) et change de couleur avec la jauge. */
    private fun drawBolt(canvas: Canvas, g: DrawGesture, t: Float, d: Float) {
        val color = levelColor(g.gauge, g.center, g.good, g.perfect, 0xFF90CAF9.toInt())
        gaugeRing(canvas, g.targetX, g.targetY, g.targetRadius, g.gauge, g.center, g.good, g.perfect, color, d)
        reticle(canvas, g.targetX, g.targetY, g.targetRadius, g.onTarget, d)
        if (!g.started) { beacon(canvas, g.startX, g.startY, t, d); return }
        val frame = (t / 55f).toInt()
        val jitter = 1.6f * d
        val count = g.tracedCount
        fun x(i: Int) = g.traced[i * 2] + if (i == 0) 0f else sin(i * 12.9898f + frame * 7.13f) * jitter
        fun y(i: Int) = g.traced[i * 2 + 1] + if (i == 0) 0f else cos(i * 4.1414f + frame * 3.71f) * jitter
        var n = 0
        for (i in 1 until count) {
            segments[n++] = x(i - 1); segments[n++] = y(i - 1); segments[n++] = x(i); segments[n++] = y(i)
        }
        // Le dernier bout rejoint le doigt, qui a pu bouger depuis le dernier point gardé
        segments[n++] = x(count - 1); segments[n++] = y(count - 1)
        segments[n++] = g.fingerX; segments[n++] = g.fingerY
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        stroke.color = 0xFF111729.toInt(); stroke.strokeWidth = 9f * d
        canvas.drawLines(segments, 0, n, stroke)
        stroke.color = color; stroke.strokeWidth = 5f * d
        canvas.drawLines(segments, 0, n, stroke)
        stroke.color = Color.WHITE; stroke.strokeWidth = 2f * d
        canvas.drawLines(segments, 0, n, stroke)
        stroke.strokeCap = Paint.Cap.BUTT; stroke.strokeJoin = Paint.Join.MITER
        fill.color = color
        canvas.drawCircle(g.fingerX, g.fingerY, 7f * d, fill)
    }

    // ── Feu : attiser à deux doigts ─────────────────────────────────────────────

    /**
     * La flamme monte sur le héros tant que le doigt tient ; chaque cible porte la jauge, et son
     * viseur s'allume dès que la jauge entre dans la zone. Une flèche part alors du héros vers la
     * cible : c'est le moment de lancer, d'un coup de doigt (dans n'importe quelle direction).
     */
    private fun drawCharge(canvas: Canvas, g: ChargeGesture, t: Float, d: Float) {
        val level = g.gauge(t)
        val color = levelColor(level, g.center, g.good, g.perfect, 0xFFFFAB66.toInt())
        val ready = level >= g.center - g.good && level <= g.center + g.good
        for (i in 0 until g.targets.size / 3) {
            val x = g.targets[i * 3]; val y = g.targets[i * 3 + 1]; val r = g.targets[i * 3 + 2]
            gaugeRing(canvas, x, y, r, level, g.center, g.good, g.perfect, color, d)
            reticle(canvas, x, y, r, ready, d)
        }
        if (!g.holding) { beacon(canvas, g.heroX, g.heroY, t, d, 0xFFF39A33.toInt()); return }
        // La flamme : trois disques qui grandissent avec la jauge et vacillent ; rouge quand elle s'emballe
        val wild = level > g.center + g.good
        val flicker = sin(t / (if (wild) 35f else 70f)) * (if (wild) 4f else 2f) * d
        val size = (10f + 30f * level.coerceAtMost(1f)) * d + flicker
        fill.color = if (wild) 0xCCE53935.toInt() else 0xCCF39A33.toInt()
        canvas.drawCircle(g.heroX, g.heroY, size, fill)
        fill.color = if (wild) 0xFFFF8A65.toInt() else 0xFFFFC04D.toInt()
        canvas.drawCircle(g.heroX, g.heroY - size * .15f, size * .66f, fill)
        fill.color = 0xFFFFF3C4.toInt()
        canvas.drawCircle(g.heroX, g.heroY - size * .25f, size * .33f, fill)
        if (ready && g.targets.size >= 2) {
            // La flèche du lancer, du héros vers la (première) cible
            val dx = g.targets[0] - g.heroX; val dy = g.targets[1] - g.heroY
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / len; val uy = dy / len
            val from = size + 6f * d; val to = from + 34f * d; val head = 10f * d
            val tx = g.heroX + ux * to; val ty = g.heroY + uy * to
            glyph[0] = g.heroX + ux * from; glyph[1] = g.heroY + uy * from; glyph[2] = tx; glyph[3] = ty
            glyph[4] = tx; glyph[5] = ty; glyph[6] = tx - ux * head - uy * head * .7f; glyph[7] = ty - uy * head + ux * head * .7f
            glyph[8] = tx; glyph[9] = ty; glyph[10] = tx - ux * head + uy * head * .7f; glyph[11] = ty - uy * head - ux * head * .7f
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.color = 0xFF111729.toInt(); stroke.strokeWidth = 8f * d
            canvas.drawLines(glyph, 0, 12, stroke)
            stroke.color = 0xFFFFE27A.toInt(); stroke.strokeWidth = 4f * d
            canvas.drawLines(glyph, 0, 12, stroke)
            stroke.strokeCap = Paint.Cap.BUTT
        }
    }

    // ── Glace : figer ───────────────────────────────────────────────────────────

    /**
     * Le givre pousse autour du doigt immobile : un disque de glace et des aiguilles, d'autant
     * plus nombreuses que la jauge monte. Quand le doigt bouge, un anneau bleu s'échappe : ça a fondu.
     */
    private fun drawFreeze(canvas: Canvas, g: FreezeGesture, t: Float, d: Float) {
        val level = g.gauge(t)
        val color = levelColor(level, g.center, g.good, g.perfect, 0xFF90CAF9.toInt())
        gaugeRing(canvas, g.targetX, g.targetY, g.targetRadius, level, g.center, g.good, g.perfect, color, d)
        reticle(canvas, g.targetX, g.targetY, g.targetRadius, g.pressed, d)
        if (!g.pressed) { beacon(canvas, g.targetX, g.targetY, t, d, 0xFF90CAF9.toInt()); return }
        val melt = t - g.meltedAt
        if (g.meltedAt >= 0f && melt in 0f..300f) {
            stroke.color = 0xFF4FC3F7.toInt(); stroke.alpha = ((1f - melt / 300f) * 255).toInt()
            stroke.strokeWidth = 3f * d
            canvas.drawCircle(g.anchorX, g.anchorY, (14f + melt / 6f) * d, stroke)
            stroke.alpha = 255
        }
        if (level <= 0f) return
        val x = if (g.onHero) g.targetX else g.anchorX
        val y = if (g.onHero) g.targetY else g.anchorY
        fill.color = 0x55CDF2FB
        canvas.drawCircle(x, y, (14f + 30f * level.coerceAtMost(1f)) * d, fill)
        val needles = (level.coerceAtMost(1f) * 12f).toInt()
        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until needles) {
            val a = i * 2.39996f   // l'angle d'or : les aiguilles se répartissent sans se chevaucher
            val inner = 12f * d
            val outer = (18f + 26f * level.coerceAtMost(1f) + (i % 3) * 4f) * d
            stroke.color = 0xFF111729.toInt(); stroke.strokeWidth = 4f * d
            canvas.drawLine(x + cos(a) * inner, y + sin(a) * inner, x + cos(a) * outer, y + sin(a) * outer, stroke)
            stroke.color = color; stroke.strokeWidth = 2f * d
            canvas.drawLine(x + cos(a) * inner, y + sin(a) * inner, x + cos(a) * outer, y + sin(a) * outer, stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    // ── Poison : doser ──────────────────────────────────────────────────────────

    /**
     * La jauge monte à chaque dose et redescend seule ; seule la zone verte compte. À
     * l'intérieur du viseur, un arc blanc fond pendant le temps compté. Chaque touche lâche une
     * bulle verte qui monte de la cible.
     */
    private fun drawDose(canvas: Canvas, g: DoseGesture, t: Float, d: Float) {
        val level = g.gauge(t)
        val inZone = level >= g.center - g.good && level <= g.center + g.good
        val color = if (level > g.center + g.good) 0xFFEF5350.toInt() else if (inZone) Color.WHITE else 0xFF8D9C7A.toInt()
        gaugeRing(canvas, g.targetX, g.targetY, g.targetRadius, level, g.center, g.good, 0f, color, d)
        reticle(canvas, g.targetX, g.targetY, g.targetRadius, inZone, d)
        if (g.onWeapon) {
            // Une lame visible dans le viseur du héros ; le venin la recouvre à mesure qu'on dose.
            val x = g.targetX; val y = g.targetY
            val size = g.targetRadius * .65f
            canvas.save()
            canvas.rotate(35f, x, y)
            fill.color = 0xFFCFD8DC.toInt()
            shape.reset()
            shape.moveTo(x, y - size)
            shape.lineTo(x + 6f * d, y - size * .65f)
            shape.lineTo(x + 6f * d, y + size * .4f)
            shape.lineTo(x - 6f * d, y + size * .4f)
            shape.lineTo(x - 6f * d, y - size * .65f)
            shape.close()
            canvas.drawPath(shape, fill)
            fill.color = 0xFF72B83E.toInt()
            canvas.drawRect(x - 5f * d, y + size * .4f - size * 1.1f * level.coerceIn(0f, 1f),
                x + 5f * d, y + size * .4f, fill)
            stroke.strokeWidth = 4f * d; stroke.color = 0xFFC9A227.toInt()
            canvas.drawLine(x - 12f * d, y + size * .45f, x + 12f * d, y + size * .45f, stroke)
            stroke.color = 0xFF795548.toInt()
            canvas.drawLine(x, y + size * .45f, x, y + size * .85f, stroke)
            canvas.restore()
        }
        if (g.firstAt < 0f) { beacon(canvas, g.targetX, g.targetY, t, d, 0xFF9EDC57.toInt()); return }
        // Le temps compté, qui fond à l'intérieur du viseur
        val counting = (t - g.firstAt - g.warmupMs) / (g.durationMs - g.warmupMs)
        val inner = g.targetRadius - 7f * d
        oval.set(g.targetX - inner, g.targetY - inner, g.targetX + inner, g.targetY + inner)
        stroke.strokeWidth = 3f * d
        stroke.color = if (counting < 0f) 0x66FFFFFF else Color.WHITE
        canvas.drawArc(oval, -90f, 360f * (1f - counting.coerceIn(0f, 1f)), false, stroke)
        // Les bulles des dernières doses
        for (i in g.doseTimes.indices) {
            val age = t - g.doseTimes[i]
            if (g.doseTimes[i] < 0f || age !in 0f..600f) continue
            val k = age / 600f
            fill.color = 0xFF9EDC57.toInt(); fill.alpha = ((1f - k) * 230).toInt()
            canvas.drawCircle(g.targetX + sin(i * 2.3f) * g.targetRadius * .5f,
                g.targetY - k * 46f * d, (4f + 3f * k) * d, fill)
        }
        fill.alpha = 255
    }

    // ── Les éclairs errants : le cadran ─────────────────────────────────────────

    /**
     * Le cadran autour de la cible : la zone à toucher (verte, cœur doré) et l'aiguille qui
     * tourne vers elle, une traînée derrière elle pour dire dans quel sens. Les zones déjà jouées
     * laissent une marque sur le cadran ; le viseur s'allume quand l'aiguille est dans la zone.
     */
    private fun drawDial(canvas: Canvas, g: DialGesture, t: Float, d: Float) {
        val x = g.targetX; val y = g.targetY
        val gr = g.targetRadius + 9f * d
        oval.set(x - gr, y - gr, x + gr, y + gr)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = 11f * d
        stroke.color = 0xCC111729.toInt()
        canvas.drawCircle(x, y, gr, stroke)
        val needle = g.needle(t)
        val playing = g.result == null && g.zone < g.grades.size
        // Deux ou trois petits repères annoncent le nombre de touches, même avant la première.
        for (i in g.grades.indices) {
            fill.color = when (g.grades[i]) {
                Timing.PERFECT -> 0xFFFFD54F.toInt()
                Timing.GOOD -> 0xFFAED581.toInt()
                Timing.MISS -> 0xFFEF5350.toInt()
                null -> 0xFFCFD8DC.toInt()
            }
            canvas.drawCircle(x + (i - (g.grades.size - 1) / 2f) * 13f * d, y + gr + 18f * d, 3.5f * d, fill)
        }
        if (playing) {
            stroke.strokeWidth = 7f * d
            stroke.color = 0xFF7CB342.toInt()
            canvas.drawArc(oval, g.zoneAngle - g.goodDeg, 2f * g.goodDeg, false, stroke)
            stroke.color = 0xFFFFD54F.toInt()
            canvas.drawArc(oval, g.zoneAngle - g.perfectDeg, 2f * g.perfectDeg, false, stroke)
        }
        // Les marques des zones jouées
        for (i in 0 until g.zone) {
            val a = Math.toRadians(g.playedAt[i].toDouble())
            fill.color = when (g.grades[i]) { Timing.PERFECT -> 0xFFFFD54F.toInt(); Timing.GOOD -> 0xFFAED581.toInt(); else -> 0xFF757575.toInt() }
            canvas.drawCircle(x + cos(a).toFloat() * gr, y + sin(a).toFloat() * gr, 4.5f * d, fill)
        }
        val inZone = playing && kotlin.math.abs(needle - g.zoneAngle) <= g.goodDeg
        reticle(canvas, x, y, g.targetRadius, inZone, d)
        if (!playing) return
        // La traînée, derrière l'aiguille
        stroke.strokeWidth = 4f * d
        stroke.color = 0x99FFE27A.toInt()
        canvas.drawArc(oval, if (g.direction > 0f) needle - 28f else needle, 28f, false, stroke)
        val a = Math.toRadians(needle.toDouble())
        val nx = x + cos(a).toFloat() * gr; val ny = y + sin(a).toFloat() * gr
        fill.color = 0xFF111729.toInt(); canvas.drawCircle(nx, ny, 9f * d, fill)
        fill.color = if (inZone) Color.WHITE else 0xFFFFE27A.toInt(); canvas.drawCircle(nx, ny, 6.5f * d, fill)
    }

    /** Une onde s'élargit sur le héros ; relâcher dans la zone verte/dorée du cercle. */
    private fun drawWave(canvas: Canvas, g: WaveGesture, t: Float, d: Float) {
        val level = g.gauge(t)
        val color = levelColor(level, g.center, g.good, g.perfect, 0xFFFFAB91.toInt())
        gaugeRing(canvas, g.heroX, g.heroY, g.radius, level, g.center, g.good, g.perfect, color, d)
        if (!g.holding) { beacon(canvas, g.heroX, g.heroY, t, d); return }
        stroke.strokeWidth = 4f * d; stroke.color = color
        canvas.drawCircle(g.heroX, g.heroY, g.radius * (level / g.center).coerceAtMost(1.4f), stroke)
        stroke.strokeWidth = 2f * d; stroke.alpha = 140
        canvas.drawCircle(g.heroX, g.heroY, g.radius * (level / g.center).coerceAtMost(1.4f) * .65f, stroke)
        stroke.alpha = 255
    }

    /** Grand sablier rotatif : verre, armature et sable dans la chambre initialement en bas. */
    private fun drawHourglass(canvas: Canvas, g: HourglassGesture, t: Float, d: Float) {
        canvas.drawColor(0xBB111729.toInt())
        val x = g.centerX; val y = g.centerY; val r = g.radius
        canvas.save()
        canvas.rotate(g.displayedRotation(t), x, y)
        val w = r * .55f; val h = r * .85f
        shape.reset()
        shape.moveTo(x - w, y - h)
        shape.lineTo(x + w, y - h)
        shape.cubicTo(x + w, y - h * .4f, x + r * .09f, y - h * .2f, x + r * .06f, y)
        shape.cubicTo(x + r * .09f, y + h * .2f, x + w, y + h * .4f, x + w, y + h)
        shape.lineTo(x - w, y + h)
        shape.cubicTo(x - w, y + h * .4f, x - r * .09f, y + h * .2f, x - r * .06f, y)
        shape.cubicTo(x - r * .09f, y - h * .2f, x - w, y - h * .4f, x - w, y - h)
        shape.close()
        fill.color = 0x447ACBDD
        canvas.drawPath(shape, fill)
        stroke.strokeWidth = 3f * d; stroke.color = 0xFFC5EAF2.toInt()
        canvas.drawPath(shape, stroke)
        // Le sable suit le récipient pendant le retournement ; une fois retourné il coule.
        canvas.save()
        canvas.clipPath(shape)
        val drained = (g.sandTime(t) / 5000f).coerceIn(0f, 1f)
        fill.color = 0xFFFFD16A.toInt()
        canvas.drawRect(x - w, y + h * (.4f + .6f * drained), x + w, y + h, fill)
        if (g.sandTime(t) > 0f) {
            stroke.color = 0xFFFFD16A.toInt(); stroke.strokeWidth = 2f * d
            canvas.drawLine(x, y + h * .4f, x, y - h, stroke)
            fill.color = 0xFFFFE6A1.toInt()
            for (i in 0 until 8) {
                val fall = ((g.sandTime(t) / 600f + i / 8f) % 1f)
                canvas.drawCircle(x + sin(i * 3f) * 2f * d, y - fall * h, 1.5f * d, fill)
            }
            canvas.drawRect(x - w, y - h, x + w, y - h + h * .6f * drained, fill)
        }
        canvas.restore()
        stroke.strokeWidth = 7f * d; stroke.color = 0xFFB88A4A.toInt()
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - w - 8f * d, y - h - 5f * d, x + w + 8f * d, y - h - 5f * d, stroke)
        canvas.drawLine(x - w - 8f * d, y + h + 5f * d, x + w + 8f * d, y + h + 5f * d, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        canvas.restore()
        if (!g.flipped) {
            // Flèche courbe autour du sablier : tourner, dans l'un ou l'autre sens.
            oval.set(x - r * 1.15f, y - r * 1.15f, x + r * 1.15f, y + r * 1.15f)
            stroke.color = 0xFFFFE27A.toInt(); stroke.strokeWidth = 4f * d
            canvas.drawArc(oval, -55f, 230f, false, stroke)
            val ax = x - r * 1.15f; val ay = y + r * .1f
            canvas.drawLine(ax, ay, ax - 7f * d, ay - 11f * d, stroke)
            canvas.drawLine(ax, ay, ax + 10f * d, ay - 8f * d, stroke)
            beacon(canvas, x + r * 1.15f, y, t, d)
        }
    }

    // ── Les pictogrammes des boutons ────────────────────────────────────────────

    /** Le geste d'une relique, en petit dans le coin haut droit de son bouton. */
    fun drawGlyph(canvas: Canvas, kind: RelicGesture, r: RectF, enabled: Boolean, d: Float) {
        val w = 16f * d; val h = 9f * d
        val x = r.right - 7f * d - w; val y = r.top + 6f * d
        val cy = y + h / 2f
        val ink = if (enabled) 0xFFFFE27A.toInt() else 0xFF8A8A8A.toInt()
        val dark = 0xCC111729.toInt()
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        fun lines(n: Int) {
            stroke.color = dark; stroke.strokeWidth = 4.5f * d
            canvas.drawLines(glyph, 0, n, stroke)
            stroke.color = ink; stroke.strokeWidth = 2f * d
            canvas.drawLines(glyph, 0, n, stroke)
        }
        fun dot(cx: Float, radius: Float) {
            fill.color = dark; canvas.drawCircle(cx, cy, radius + 1.3f * d, fill)
            fill.color = ink; canvas.drawCircle(cx, cy, radius, fill)
        }
        fun ring(cx: Float, radius: Float) {
            stroke.color = dark; stroke.strokeWidth = 3.5f * d; canvas.drawCircle(cx, cy, radius, stroke)
            stroke.color = ink; stroke.strokeWidth = 1.5f * d; canvas.drawCircle(cx, cy, radius, stroke)
        }
        when (kind) {
            // Un zigzag : l'éclair qu'on dessine
            RelicGesture.DRAW_BOLT -> {
                glyph[0] = x;             glyph[1] = y + h
                glyph[2] = x + w * .32f;  glyph[3] = y
                glyph[4] = glyph[2];      glyph[5] = glyph[3]
                glyph[6] = x + w * .58f;  glyph[7] = y + h
                glyph[8] = glyph[6];      glyph[9] = glyph[7]
                glyph[10] = x + w * .82f; glyph[11] = y
                glyph[12] = glyph[10];    glyph[13] = glyph[11]
                glyph[14] = x + w;        glyph[15] = y + h * .55f
                lines(16)
            }
            // Un doigt qui tient (cerclé), puis un coup de doigt : une flèche
            RelicGesture.CHARGE -> {
                ring(x + 4.5f * d, 4.5f * d); dot(x + 4.5f * d, 2f * d)
                glyph[0] = x + 10f * d; glyph[1] = cy; glyph[2] = x + w; glyph[3] = cy
                glyph[4] = x + w; glyph[5] = cy; glyph[6] = x + w - 3.5f * d; glyph[7] = cy - 3f * d
                glyph[8] = x + w; glyph[9] = cy; glyph[10] = x + w - 3.5f * d; glyph[11] = cy + 3f * d
                lines(12)
            }
            // Un flocon : ne plus bouger
            RelicGesture.FREEZE -> {
                val cx = x + w / 2f; val radius = h / 2f + 1f * d
                for (k in 0 until 3) {
                    val a = (k * 60f + 90f) * (Math.PI / 180.0).toFloat()
                    glyph[k * 4] = cx + cos(a) * radius; glyph[k * 4 + 1] = cy + sin(a) * radius
                    glyph[k * 4 + 2] = cx - cos(a) * radius; glyph[k * 4 + 3] = cy - sin(a) * radius
                }
                lines(12)
            }
            // Trois points : tapoter
            RelicGesture.DOSE -> for (k in 0 until 3) dot(x + 2f * d + k * 6f * d, 1.8f * d)
            // Un cadran et son aiguille
            RelicGesture.DIAL -> {
                val cx = x + w / 2f
                ring(cx, 4.5f * d)
                glyph[0] = cx; glyph[1] = cy; glyph[2] = cx + 3f * d; glyph[3] = cy - 3f * d
                lines(4)
            }
            RelicGesture.WAVE -> {
                ring(x + w / 2f, 4.5f * d)
                ring(x + w / 2f, 2f * d)
            }
            RelicGesture.HOURGLASS -> {
                glyph[0] = x + 3f * d; glyph[1] = y
                glyph[2] = x + w - 3f * d; glyph[3] = y + h
                glyph[4] = x + w - 3f * d; glyph[5] = y
                glyph[6] = x + 3f * d; glyph[7] = y + h
                glyph[8] = x + 3f * d; glyph[9] = y
                glyph[10] = x + w - 3f * d; glyph[11] = y
                glyph[12] = x + 3f * d; glyph[13] = y + h
                glyph[14] = x + w - 3f * d; glyph[15] = y + h
                lines(16)
            }
            RelicGesture.SWIPE -> {}
        }
        stroke.strokeCap = Paint.Cap.BUTT; stroke.strokeJoin = Paint.Join.MITER
    }
}
