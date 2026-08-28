package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La bulle à roulettes : l'autre façon de régler la machine.
 *
 * Le doigt sur la pièce est parfait pour chercher — on voit la machine changer de
 * forme sous la main. Il est mauvais pour viser : passer de trois tonnes à sept
 * demande de traverser tout l'écran, et poser une valeur ronde relève de la chance.
 * Les roulettes font l'inverse : une par chiffre, chacune indépendante, donc on
 * change les milliers sans toucher aux unités.
 *
 * Les deux sont disponibles en même temps et lisent la même machine : tourner une
 * roulette déplace la pièce à l'écran, tirer la pièce fait tourner les roulettes.
 *
 * La bulle flotte au-dessus de la scène et se déplace au doigt — sur un téléphone
 * couché, elle finirait sinon par cacher exactement ce qu'on est en train de régler.
 */
class TrebuchetWheelBubble @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    /** La machine réglée. Posée par l'activité juste après l'inflation. */
    var game: TrebuchetGame? = null

    /** Prévenu à chaque cran tourné : l'activité rafraîchit son bandeau. */
    var onValueChanged: (() -> Unit)? = null

    /**
     * Un réglage vu par les roulettes : un nom court, une unité, et un nombre dont on
     * fixe le nombre de chiffres. Les décimales sont des dixièmes de mètre, soit dix
     * centimètres au cran — la précision qu'un trébuchet mérite.
     */
    private enum class Dial(
        val label: Int,
        val unit: Int,
        val intDigits: Int,
        val decimals: Int,
        /**
         * Vrai pour la roulette qui fait défiler des **mots** au lieu de chiffres.
         *
         * Elle se manœuvre exactement comme les autres — on la pousse du doigt, un
         * appui vaut un cran — et c'est tout l'intérêt : le joueur a déjà appris le
         * geste sur les six premières lignes, il n'a rien de neuf à comprendre pour
         * changer de projectile.
         */
        val choice: Boolean = false
    ) {
        BEAM(R.string.trebuchet_dial_beam, R.string.trebuchet_unit_m, 2, 1),
        LEVER(R.string.trebuchet_dial_lever, R.string.trebuchet_unit_ratio, 1, 1),
        POST(R.string.trebuchet_dial_post, R.string.trebuchet_unit_m, 2, 1),
        MASS(R.string.trebuchet_dial_mass, R.string.trebuchet_unit_kg, 5, 0),
        HANG(R.string.trebuchet_dial_hang, R.string.trebuchet_unit_m, 2, 1),
        PIN(R.string.trebuchet_dial_pin, R.string.trebuchet_unit_deg, 2, 0),
        SLING(R.string.trebuchet_dial_sling, R.string.trebuchet_unit_m, 2, 1),
        SHOT(R.string.trebuchet_dial_shot, R.string.trebuchet_unit_none, 0, 0, choice = true);

        val digits: Int get() = intDigits + decimals
    }

    /** Le nom court d'un projectile, tel qu'il défile dans la roulette. */
    private fun shotName(kind: Projectile): String = context.getString(
        when (kind) {
            Projectile.BOULET -> R.string.trebuchet_shot_ball
            Projectile.LOURD -> R.string.trebuchet_shot_heavy
            Projectile.FRAGMENTATION -> R.string.trebuchet_shot_cluster
            Projectile.BOMBE -> R.string.trebuchet_shot_bomb
        }
    )

    private val dp = resources.displayMetrics.density

    private companion object {
        /** Ce qu'il faut glisser pour faire tourner une roulette d'un cran, en dp. */
        const val STEP_DP = 26f

        /** En deçà, le doigt n'a pas glissé : c'est un appui, et il vaut un cran. */
        const val TAP_SLOP_DP = 8f

        const val PAD_DP = 10f
        const val ROW_DP = 46f
        const val CELL_W_DP = 25f
        const val CELL_H_DP = 38f
        const val GAP_DP = 3f
        const val DOT_W_DP = 9f

        /** Marge de part et d'autre du mot le plus long, dans sa cellule. */
        const val CHOICE_PAD_DP = 10f
    }

    // ── Encres ────────────────────────────────────────────────────────────────

    private val pPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 16, 25, 50) }
    private val pPanelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(120, 255, 209, 102)
    }
    private val pCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 143, 166, 200) }
    private val pDigit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#FFD166".toColorInt()
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    /** Les chiffres voisins, ceux qui arrivent : ils font voir que ça tourne. */
    private val pDigitDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(70, 255, 209, 102)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#8FA6C8".toColorInt()
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#8FA6C8".toColorInt() }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFD166".toColorInt() }
    private val pChoice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#FFD166".toColorInt()
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pChoiceDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(70, 255, 209, 102)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private val rect = RectF()

    // ── Contenu ───────────────────────────────────────────────────────────────

    private var dials = emptyList<Dial>()
    private var labelWidth = 0f
    private var unitWidth = 0f
    private var choiceWidth = 0f

    // ── Saisie ────────────────────────────────────────────────────────────────

    /** La roulette tenue, ou (-1, -1) quand c'est la bulle entière qu'on déplace. */
    private var turnRow = -1
    private var turnCol = -1

    /** Chemin parcouru depuis le dernier cran : sert aussi à faire rouler l'image. */
    private var turnOffset = 0f
    private var travel = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var movingPanel = false

    init {
        pDigit.textSize = 20f * dp
        pDigitDim.textSize = 20f * dp
        pLabel.textSize = 12f * dp
        pUnit.textSize = 12f * dp
        pChoice.textSize = 14f * dp
        pChoiceDim.textSize = 14f * dp
    }

    /**
     * Montre les réglages de la pièce tenue en main, ou disparaît. La position, elle,
     * est conservée : une bulle qu'on a poussée de côté doit y rester.
     */
    fun showFor(part: TrebuchetView.Part) {
        val wanted = dialsFor(part)
        if (wanted != dials) {
            dials = wanted
            measureLabels()
            requestLayout()
        }
        val show = wanted.isNotEmpty()
        if ((visibility == VISIBLE) != show) visibility = if (show) VISIBLE else GONE
        if (show) invalidate()
    }

    private fun dialsFor(part: TrebuchetView.Part): List<Dial> = when (part) {
        TrebuchetView.Part.BEAM -> listOf(Dial.BEAM, Dial.LEVER)
        TrebuchetView.Part.POST -> listOf(Dial.POST)
        TrebuchetView.Part.WEIGHT -> listOf(Dial.MASS, Dial.HANG)
        TrebuchetView.Part.PIN -> listOf(Dial.PIN)
        // La fronde et ce qu'on met dedans : c'est la même pièce sous le doigt, donc
        // c'est la même fenêtre.
        TrebuchetView.Part.SLING -> listOf(Dial.SLING, Dial.SHOT)
        TrebuchetView.Part.NONE -> emptyList()
    }

    private fun measureLabels() {
        labelWidth = 0f
        unitWidth = 0f
        choiceWidth = 0f
        for (d in dials) {
            labelWidth = maxOf(labelWidth, pLabel.measureText(context.getString(d.label)))
            unitWidth = maxOf(unitWidth, pUnit.measureText(context.getString(d.unit)))
            if (!d.choice) continue
            // La cellule des mots tient le plus long d'entre eux, sinon la bulle
            // changerait de largeur à chaque cran tourné.
            for (k in Projectile.entries) {
                choiceWidth = maxOf(choiceWidth, pChoice.measureText(shotName(k)))
            }
            choiceWidth += 2f * CHOICE_PAD_DP * dp
        }
    }

    /** Largeur, en pixels, de la cellule d'une roulette : chiffres ou mots. */
    private fun cellsWidth(d: Dial): Float =
        if (d.choice) choiceWidth
        else d.digits * (CELL_W_DP + GAP_DP) * dp + (if (d.decimals > 0) DOT_W_DP * dp else 0f)

    // ── Mesure ────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (dials.isEmpty()) {
            setMeasuredDimension(0, 0)
            return
        }
        var cells = 0f
        for (d in dials) cells = maxOf(cells, cellsWidth(d))
        val w = PAD_DP * 2f * dp + labelWidth + 8f * dp + cells + 8f * dp + unitWidth
        val h = PAD_DP * 2f * dp + dials.size * ROW_DP * dp
        setMeasuredDimension(w.roundToInt(), h.roundToInt())
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        if (dials.isEmpty()) return

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanel)
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanelEdge)

        for (row in dials.indices) {
            val d = dials[row]
            val top = (PAD_DP + row * ROW_DP) * dp
            val mid = top + ROW_DP * dp / 2f

            canvas.drawText(
                context.getString(d.label), PAD_DP * dp,
                mid + pLabel.textSize * 0.36f, pLabel
            )

            var x = PAD_DP * dp + labelWidth + 8f * dp
            if (d.choice) {
                drawChoice(canvas, x, mid, row)
                continue
            }
            val digits = digitsOf(d, value(g, d))
            for (col in 0 until d.digits) {
                // Le point décimal s'insère à sa place, sans roulette : il ne se règle
                // pas, il se lit.
                if (d.decimals > 0 && col == d.intDigits) {
                    canvas.drawCircle(x + DOT_W_DP * dp / 2f, mid + 12f * dp, 2f * dp, pDot)
                    x += DOT_W_DP * dp
                }
                drawWheel(canvas, x, mid, digits[col], row, col)
                x += (CELL_W_DP + GAP_DP) * dp
            }
            canvas.drawText(
                context.getString(d.unit), x + 4f * dp,
                mid + pUnit.textSize * 0.36f, pUnit
            )
        }
    }

    /**
     * La roulette des mots : le projectile choisi, et ses voisins qui arrivent.
     *
     * Elle est dessinée exactement comme une roulette de chiffres — même cellule, même
     * défilement, même estompage des voisins — parce que c'en est une. Seul le contenu
     * change : des noms au lieu de chiffres, et une cellule assez large pour les tenir.
     */
    private fun drawChoice(canvas: Canvas, x: Float, mid: Float, row: Int) {
        val h = CELL_H_DP * dp
        rect.set(x, mid - h / 2f, x + choiceWidth, mid + h / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, pCell)

        val kinds = Projectile.entries
        val cur = kinds.indexOf(game?.config?.projectile ?: Projectile.BOULET)
        val rolling = row == turnRow && turnCol == 0
        val offset = if (rolling) turnOffset else 0f
        val step = STEP_DP * dp
        val cx = x + choiceWidth / 2f
        val base = mid + pChoice.textSize * 0.36f

        canvas.save()
        canvas.clipRect(rect)
        for (k in -1..1) {
            val i = ((cur + k) % kinds.size + kinds.size) % kinds.size
            canvas.drawText(
                shotName(kinds[i]), cx, base + k * step + offset,
                if (k == 0 && abs(offset) < step / 2f) pChoice else pChoiceDim
            )
        }
        canvas.restore()
    }

    /** Une roulette : le chiffre tenu, et ceux qui l'encadrent, coupés par la cellule. */
    private fun drawWheel(canvas: Canvas, x: Float, mid: Float, digit: Int, row: Int, col: Int) {
        val w = CELL_W_DP * dp
        val h = CELL_H_DP * dp
        rect.set(x, mid - h / 2f, x + w, mid + h / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, pCell)

        val rolling = row == turnRow && col == turnCol
        val offset = if (rolling) turnOffset else 0f
        val step = STEP_DP * dp
        val cx = x + w / 2f
        val base = mid + pDigit.textSize * 0.36f

        canvas.save()
        canvas.clipRect(rect)
        for (k in -1..1) {
            // Vers le haut, les chiffres qui montent ; le glissement les accompagne.
            val shown = ((digit + k) % 10 + 10) % 10
            canvas.drawText(
                shown.toString(), cx, base + k * step + offset,
                if (k == 0 && abs(offset) < step / 2f) pDigit else pDigitDim
            )
        }
        canvas.restore()
    }

    // ── Saisie ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                travel = 0f
                turnOffset = 0f
                val hit = wheelAt(event.x, event.y)
                turnRow = hit shr 8
                turnCol = hit and 0xFF
                movingPanel = turnRow < 0
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                travel += abs(dx) + abs(dy)
                if (movingPanel) {
                    dragPanel(dx, dy)
                } else {
                    // Glisser vers le haut fait monter les chiffres : on pousse la
                    // roulette, comme sur un compteur.
                    turnOffset -= dy
                    val step = STEP_DP * dp
                    while (turnOffset >= step) {
                        turn(+1); turnOffset -= step
                    }
                    while (turnOffset <= -step) {
                        turn(-1); turnOffset += step
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Un appui franc vaut un cran : moitié haute pour monter, basse pour
                // descendre. Sans ça, corriger d'une unité demanderait de viser au dp.
                if (!movingPanel && travel < TAP_SLOP_DP * dp) {
                    turn(if (event.y < height / 2f) +1 else -1)
                }
                turnRow = -1
                turnCol = -1
                turnOffset = 0f
                movingPanel = false
                invalidate()
            }
        }
        return true
    }

    /** La bulle se pousse où on veut, mais reste entièrement dans la scène. */
    private fun dragPanel(dx: Float, dy: Float) {
        val parentView = parent as? View ?: return
        val maxX = (parentView.width - width).toFloat()
        val maxY = (parentView.height - height).toFloat()
        translationX = (translationX + dx).coerceIn(-left.toFloat(), maxX - left)
        translationY = (translationY + dy).coerceIn(-top.toFloat(), maxY - top)
    }

    /** La roulette sous le doigt, encodée ligne/colonne, ou -1 si c'est la bulle. */
    private fun wheelAt(x: Float, y: Float): Int {
        for (row in dials.indices) {
            val d = dials[row]
            val top = (PAD_DP + row * ROW_DP) * dp
            if (y < top || y > top + ROW_DP * dp) continue
            var cx = PAD_DP * dp + labelWidth + 8f * dp
            if (d.choice) {
                if (x >= cx && x <= cx + choiceWidth) return row shl 8
                continue
            }
            for (col in 0 until d.digits) {
                if (d.decimals > 0 && col == d.intDigits) cx += DOT_W_DP * dp
                if (x >= cx && x <= cx + CELL_W_DP * dp) return (row shl 8) or col
                cx += (CELL_W_DP + GAP_DP) * dp
            }
        }
        return -1 shl 8
    }

    /**
     * Fait tourner d'un cran la roulette tenue, avec la retenue d'un compteur
     * kilométrique : passer de 9 à 0 pousse la roulette de gauche d'un cran.
     *
     * La retenue ne descend jamais. Bouger les dizaines laisse les unités exactement
     * où elles sont, et c'est tout l'intérêt de la chose : on traverse les milliers
     * sans perdre le réglage fin qu'on venait de trouver.
     */
    private fun turn(delta: Int) {
        val g = game ?: return
        if (turnRow !in dials.indices) return
        val d = dials[turnRow]
        if (d.choice) {
            // Les projectiles tournent en rond : après le dernier vient le premier.
            val kinds = Projectile.entries
            val i = kinds.indexOf(g.config.projectile)
            val next = ((i + delta) % kinds.size + kinds.size) % kinds.size
            synchronized(g) { g.setProjectile(kinds[next]) }
            onValueChanged?.invoke()
            invalidate()
            return
        }
        if (turnCol < 0 || turnCol >= d.digits) return

        // Le poids de la colonne tenue : tourner les dizaines, c'est ajouter dix.
        var weight = 1
        repeat(d.digits - 1 - turnCol) { weight *= 10 }
        val scaled = (value(g, d) * pow10(d.decimals)).roundToInt() + delta * weight

        apply(g, d, scaled.coerceAtLeast(0) / pow10(d.decimals))
        onValueChanged?.invoke()
        invalidate()
    }

    // ── La machine ────────────────────────────────────────────────────────────

    private fun value(g: TrebuchetGame, d: Dial): Float = with(g.config) {
        when (d) {
            Dial.BEAM -> beamLength
            Dial.LEVER -> leverRatio
            Dial.POST -> pivotHeight
            Dial.MASS -> counterweightMass
            Dial.HANG -> hangLength
            Dial.PIN -> pinAngleDeg
            Dial.SLING -> slingLength
            // Le projectile n'est pas un nombre : sa roulette ne passe jamais par ici.
            Dial.SHOT -> 0f
        }
    }

    private fun apply(g: TrebuchetGame, d: Dial, v: Float) {
        synchronized(g) {
            when (d) {
                Dial.BEAM -> g.setBeamLength(v)
                Dial.LEVER -> g.setLeverRatio(v)
                Dial.POST -> g.setPivotHeight(v)
                Dial.MASS -> g.setCounterweightMass(v)
                Dial.HANG -> g.setHangLength(v)
                Dial.PIN -> g.setPinAngle(v)
                Dial.SLING -> g.setSlingLength(v)
                Dial.SHOT -> Unit
            }
        }
    }

    /** Les chiffres affichés, du plus fort au plus faible, décimales comprises. */
    private fun digitsOf(d: Dial, v: Float): IntArray {
        var scaled = (v * pow10(d.decimals)).roundToInt().coerceAtLeast(0)
        val out = IntArray(d.digits)
        for (i in d.digits - 1 downTo 0) {
            out[i] = scaled % 10
            scaled /= 10
        }
        return out
    }

    private fun pow10(n: Int): Float {
        var p = 1f
        repeat(n) { p *= 10f }
        return p
    }
}
