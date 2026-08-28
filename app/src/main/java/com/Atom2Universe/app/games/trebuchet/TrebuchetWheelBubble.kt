package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
 * **Trois façons de changer une valeur, et c'est voulu.** Une bulle qui ne se
 * manœuvrait qu'au glissement était pénible sur un vrai téléphone : un doigt fait cinq
 * millimètres de large, une cellule de chiffre en faisait six, et rater sa cible ne
 * ratait pas seulement le geste — ça déplaçait la fenêtre entière. On a donc, du plus
 * grossier au plus fin :
 *
 *  - les **flèches**, de part et d'autre des chiffres — celle de gauche monte, celle
 *    de droite descend : elles travaillent **toujours sur une colonne**, laquelle est
 *    allumée en permanence. C'est le geste ordinaire, il ne demande aucune précision,
 *    et maintenu il se répète ;
 *  - un **appui long sur un chiffre** déplace cette colonne. C'est ce qui transforme
 *    deux boutons fixes en un pas réglable — dix kilos ou mille, un centimètre ou un
 *    mètre — sans ajouter le moindre bouton, et le choix se garde : on retrouve sa
 *    colonne en revenant sur la pièce. Chaque réglage démarre sur la colonne qui lui
 *    va, celle de son cran naturel ;
 *  - l'**appui bref sur un chiffre** : il ajoute ou retranche une unité de cette
 *    colonne-là — moitié haute pour monter, basse pour descendre ;
 *  - le **glissement sur un chiffre** : la roulette suit le doigt, cran par cran, pour
 *    balayer une plage.
 *
 * Et la fenêtre ne se déplace plus que par son **bandeau**, en haut. Tout le reste de
 * la bulle est inerte : un appui à côté d'une flèche ne fait rien du tout, ce qui est
 * exactement ce qu'on attend d'un appui raté.
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
         * Le **cran naturel** du réglage : celui sur lequel les flèches démarrent.
         *
         * Il n'a rien à voir avec la précision de l'affichage, et c'est tout l'intérêt.
         * Le contrepoids s'affiche au kilo près mais se règle par cent : personne ne
         * cherche 3 001 kg, et personne ne veut appuyer mille fois pour aller de trois
         * tonnes à quatre.
         *
         * Il ne sert plus à additionner quoi que ce soit — les flèches travaillent sur
         * une colonne de chiffres — mais à **désigner laquelle** au départ : on prend
         * celle dont le poids est le plus proche de ce cran-là. Le joueur déplace
         * ensuite la colonne s'il veut un autre pas, et son choix reste.
         */
        val step: Float,
        /**
         * Vrai pour la roulette qui fait défiler des **mots** au lieu de chiffres.
         *
         * Elle se manœuvre exactement comme les autres — flèches, appui, glissement —
         * et c'est tout l'intérêt : le joueur a déjà appris le geste sur les lignes
         * précédentes, il n'a rien de neuf à comprendre pour changer de projectile.
         */
        val choice: Boolean = false
    ) {
        BEAM(R.string.trebuchet_dial_beam, R.string.trebuchet_unit_m, 2, 1, 0.5f),
        LEVER(R.string.trebuchet_dial_lever, R.string.trebuchet_unit_ratio, 1, 1, 0.1f),
        POST(R.string.trebuchet_dial_post, R.string.trebuchet_unit_m, 2, 1, 0.5f),
        MASS(R.string.trebuchet_dial_mass, R.string.trebuchet_unit_kg, 5, 0, 100f),
        HANG(R.string.trebuchet_dial_hang, R.string.trebuchet_unit_m, 2, 1, 0.1f),
        PIN(R.string.trebuchet_dial_pin, R.string.trebuchet_unit_deg, 2, 0, 1f),
        // La fronde est le seul réglage à deux décimales, et elle les mérite : c'est
        // le plus sensible de la machine avec le crochet, dix centimètres y déplacent
        // un tir de plusieurs dizaines de mètres, et le joueur qui cherche son réglage
        // fin n'avait aucun moyen de descendre plus bas. La flèche, elle, garde son
        // cran de dix centimètres — on ne va pas de six mètres à sept en appuyant cent
        // fois — et c'est la roulette des centièmes qui fait le travail de précision.
        SLING(R.string.trebuchet_dial_sling, R.string.trebuchet_unit_m, 2, 2, 0.1f),
        SHOT(R.string.trebuchet_dial_shot, R.string.trebuchet_unit_none, 0, 0, 1f, choice = true),

        // La charge de la bombe, en bâtons. Trois chiffres parce qu'on va jusqu'à cent
        // vingt, et un cran de cinq : le joueur cherche « un peu plus » ou « beaucoup
        // plus », jamais quarante-six bâtons plutôt que quarante-cinq.
        CHARGE(R.string.trebuchet_dial_charge, R.string.trebuchet_unit_sticks, 3, 0, 5f),

        // Le poids du boulet, en kilos. **Deux colonnes de chiffres et pas trois** : la
        // borne haute est à quatre-vingt-dix-neuf pour cette raison-là, parce qu'une
        // troisième colonne qui n'afficherait qu'un zéro coûterait de la place à l'écran
        // et une seconde de lecture à chaque coup d'œil.
        WEIGHT(R.string.trebuchet_dial_weight, R.string.trebuchet_unit_kg, 2, 0, 1f);

        val digits: Int get() = intDigits + decimals
    }

    /** Ce que le doigt vient de toucher. */
    private enum class Hit { NONE, HEADER, WHEEL, ARROW }

    /** Le nom court d'un projectile, tel qu'il défile dans la roulette. */
    private fun shotName(kind: Projectile): String = context.getString(
        when (kind) {
            Projectile.BOULET -> R.string.trebuchet_shot_ball
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

        const val PAD_DP = 12f
        const val ROW_DP = 56f
        const val CELL_W_DP = 32f
        const val CELL_H_DP = 46f
        const val GAP_DP = 4f
        const val DOT_W_DP = 10f

        /** Hauteur du bandeau par lequel — et seulement par lequel — la bulle se déplace. */
        const val HEADER_DP = 26f

        /** Largeur d'une flèche. Généreuse : c'est la cible la plus visée de la bulle. */
        const val ARROW_W_DP = 40f

        /** Marge de part et d'autre du mot le plus long, dans sa cellule. */
        const val CHOICE_PAD_DP = 10f

        /** Durée d'un appui long, celui qui désigne une colonne, en ms. */
        const val LONG_PRESS_MS = 400L

        /** Attente avant qu'une flèche maintenue ne se mette à répéter, en ms. */
        const val REPEAT_DELAY_MS = 400L

        /** Intervalle entre deux crans d'une flèche maintenue, en ms. */
        const val REPEAT_EVERY_MS = 80L
    }

    // ── Encres ────────────────────────────────────────────────────────────────

    private val pPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 16, 25, 50) }
    private val pPanelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(120, 255, 209, 102)
    }
    private val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 143, 166, 200) }
    private val pGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 209, 102) }
    private val pCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 143, 166, 200) }

    /** La cellule désignée : celle sur laquelle les flèches travaillent. */
    private val pCellOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 209, 102) }
    private val pPicked = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFD166".toColorInt() }
    private val pArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFD166".toColorInt() }
    private val pArrowBed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 143, 166, 200) }
    private val pArrowBedOn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 209, 102)
    }
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
    private val arrowPath = Path()
    private val clipPath = Path()

    // ── Contenu ───────────────────────────────────────────────────────────────

    private var dials = emptyList<Dial>()
    private var labelWidth = 0f
    private var unitWidth = 0f
    private var choiceWidth = 0f

    /** Largeur réservée aux cellules : celle de la ligne la plus large. */
    private var cellsSpan = 0f

    // ── Saisie ────────────────────────────────────────────────────────────────

    private var hit = Hit.NONE

    /** La ligne tenue, et la colonne si c'est une roulette de chiffres. */
    private var turnRow = -1
    private var turnCol = -1

    /** Le sens de la flèche tenue : +1 à gauche, -1 à droite. */
    private var arrowDir = 0

    /**
     * La colonne désignée de **chaque réglage**, et non de chaque ligne à l'écran.
     *
     * La nuance est tout l'intérêt : rangée par réglage, la colonne survit au fait de
     * lâcher la poutre pour prendre le contrepoids et d'y revenir. Le joueur qui a
     * décidé de régler sa masse au kilo la retrouve au kilo, sans avoir à le redire.
     *
     * Elle n'est jamais vide. Les flèches doivent être reliées à quelque chose en
     * permanence, sans quoi on ne sait pas ce qu'un appui va faire — et une colonne
     * allumée en permanence est justement ce qui le dit.
     */
    private val pickedCol = IntArray(Dial.entries.size) { -1 }

    /**
     * La colonne sur laquelle un réglage travaille : celle qu'on a choisie, ou celle de
     * son cran naturel.
     *
     * Le défaut se **calcule** au lieu d'être recopié : on cherche la colonne dont le
     * poids ressemble le plus au cran du réglage, en comparant les logarithmes plutôt
     * que les écarts. Cinquante centimètres est aussi loin d'un mètre que de vingt-cinq
     * centimètres, et pas de dix centimètres — c'est le rapport qui compte, pas la
     * différence.
     */
    private fun columnOf(d: Dial): Int {
        val known = pickedCol[d.ordinal]
        if (known in 0 until d.digits) return known
        var best = d.digits - 1
        var bestGap = Float.MAX_VALUE
        for (c in 0 until d.digits) {
            var poids = 1f
            repeat(d.digits - 1 - c) { poids *= 10f }
            poids /= pow10(d.decimals)
            val gap = kotlin.math.abs(kotlin.math.ln(poids / d.step))
            if (gap < bestGap) {
                bestGap = gap
                best = c
            }
        }
        return best
    }

    /** Vrai quand l'appui en cours a déjà servi à autre chose qu'un appui bref. */
    private var consumed = false

    /** L'appui long qui désigne une colonne. */
    private val longPress = Runnable {
        if (hit == Hit.WHEEL && turnRow in dials.indices && !dials[turnRow].choice) {
            // On déplace la colonne, on ne l'éteint pas : les flèches doivent toujours
            // savoir sur quoi elles tapent.
            pickedCol[dials[turnRow].ordinal] = turnCol
            consumed = true
            invalidate()
        }
    }

    /** Chemin parcouru depuis le dernier cran : sert aussi à faire rouler l'image. */
    private var turnOffset = 0f
    private var travel = 0f
    private var lastX = 0f
    private var lastY = 0f

    /** La répétition d'une flèche maintenue. */
    private val repeater = object : Runnable {
        override fun run() {
            if (hit != Hit.ARROW) return
            nudge(arrowDir)
            postDelayed(this, REPEAT_EVERY_MS)
        }
    }

    init {
        pDigit.textSize = 24f * dp
        pDigitDim.textSize = 24f * dp
        pLabel.textSize = 13f * dp
        pUnit.textSize = 13f * dp
        pChoice.textSize = 15f * dp
        pChoiceDim.textSize = 15f * dp
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
        // Une bulle qui disparaît sous un doigt encore posé laisserait sa flèche en
        // train de se répéter dans le vide.
        if (!show) release()
        if (show) invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    private fun dialsFor(part: TrebuchetView.Part): List<Dial> = when (part) {
        TrebuchetView.Part.BEAM -> listOf(Dial.BEAM, Dial.LEVER)
        TrebuchetView.Part.POST -> listOf(Dial.POST)
        TrebuchetView.Part.WEIGHT -> listOf(Dial.MASS, Dial.HANG)
        TrebuchetView.Part.PIN -> listOf(Dial.PIN)
        // La fronde et ce qu'on met dedans : c'est la même pièce sous le doigt, donc
        // c'est la même fenêtre.
        //
        // La troisième ligne dépend de ce qu'on a chargé, et c'est le seul endroit du jeu
        // où une ligne va et vient. Une bombe se règle en bâtons, un boulet en kilos, un
        // paquet de fragmentation ne se règle pas. Afficher les deux en permanence
        // donnerait une ligne morte les trois quarts du temps ; affichée au moment où
        // elle veut dire quelque chose, elle se remarque et s'explique toute seule.
        TrebuchetView.Part.SLING -> {
            val kind = game?.config?.projectile
            when {
                kind?.explosive == true -> listOf(Dial.SLING, Dial.SHOT, Dial.CHARGE)
                kind?.weighable == true -> listOf(Dial.SLING, Dial.SHOT, Dial.WEIGHT)
                else -> listOf(Dial.SLING, Dial.SHOT)
            }
        }
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
        cellsSpan = 0f
        for (d in dials) cellsSpan = maxOf(cellsSpan, cellsWidth(d))
    }

    /** Largeur, en pixels, des cellules d'une ligne : chiffres ou mots. */
    private fun cellsWidth(d: Dial): Float =
        if (d.choice) choiceWidth
        else d.digits * (CELL_W_DP + GAP_DP) * dp + (if (d.decimals > 0) DOT_W_DP * dp else 0f)

    // ── La grille ─────────────────────────────────────────────────────────────
    //
    // Les colonnes se calculent ici, une fois pour toutes, et le dessin comme la saisie
    // les relisent. C'est la seule façon d'être sûr qu'une flèche se touche là où elle
    // se voit : deux calculs séparés finissent toujours par diverger d'un dp.

    private val xLeftArrow: Float get() = PAD_DP * dp + labelWidth + 8f * dp
    private val xCells: Float get() = xLeftArrow + ARROW_W_DP * dp
    private val xRightArrow: Float get() = xCells + cellsSpan
    private val xUnit: Float get() = xRightArrow + ARROW_W_DP * dp + 4f * dp

    /** Le bord gauche des cellules d'une ligne, centrées dans la place réservée. */
    private fun cellsLeft(d: Dial): Float = xCells + (cellsSpan - cellsWidth(d)) / 2f

    private fun rowTop(row: Int): Float = (HEADER_DP + PAD_DP + row * ROW_DP) * dp

    // ── Mesure ────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (dials.isEmpty()) {
            setMeasuredDimension(0, 0)
            return
        }
        val w = xUnit + unitWidth + PAD_DP * dp
        val h = (HEADER_DP + PAD_DP * 2f + dials.size * ROW_DP) * dp
        setMeasuredDimension(w.roundToInt(), h.roundToInt())
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        if (dials.isEmpty()) return

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanel)
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanelEdge)
        drawHeader(canvas)

        for (row in dials.indices) {
            val d = dials[row]
            val mid = rowTop(row) + ROW_DP * dp / 2f

            canvas.drawText(
                context.getString(d.label), PAD_DP * dp,
                mid + pLabel.textSize * 0.36f, pLabel
            )
            drawArrow(canvas, xLeftArrow, mid, up = true, row = row)
            drawArrow(canvas, xRightArrow, mid, up = false, row = row)

            var x = cellsLeft(d)
            if (d.choice) {
                drawChoice(canvas, x, mid, row)
            } else {
                val digits = digitsOf(d, value(g, d))
                for (col in 0 until d.digits) {
                    // Le point décimal s'insère à sa place, sans roulette : il ne se
                    // règle pas, il se lit.
                    if (d.decimals > 0 && col == d.intDigits) {
                        canvas.drawCircle(x + DOT_W_DP * dp / 2f, mid + 14f * dp, 2.5f * dp, pDot)
                        x += DOT_W_DP * dp
                    }
                    drawWheel(canvas, x, mid, digits[col], row, col, col == columnOf(d))
                    x += (CELL_W_DP + GAP_DP) * dp
                }
            }
            canvas.drawText(
                context.getString(d.unit), xUnit,
                mid + pUnit.textSize * 0.36f, pUnit
            )
        }
    }

    /**
     * Le bandeau de déplacement : la seule prise par laquelle la bulle se pousse.
     *
     * Il porte une poignée dessinée, et c'est tout ce qu'il porte. Il n'y a rien à y
     * lire — le bandeau de l'écran dit déjà quelle pièce est en main — mais il y a
     * quelque chose à y **voir** : sans poignée, personne ne devinerait que la fenêtre
     * se déplace, et encore moins par où.
     */
    private fun drawHeader(canvas: Canvas) {
        val h = HEADER_DP * dp
        canvas.save()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rect, 12f * dp, 12f * dp, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, width.toFloat(), h, pHeader)
        canvas.restore()

        val gw = 34f * dp
        rect.set(
            (width - gw) / 2f, h / 2f - 2f * dp,
            (width + gw) / 2f, h / 2f + 2f * dp
        )
        canvas.drawRoundRect(rect, 2f * dp, 2f * dp, pGrip)
    }

    /**
     * Une flèche : un triangle dans son lit, qui s'allume tant qu'on appuie dessus.
     *
     * Elle occupe toute la hauteur d'une cellule, et pas seulement celle du triangle :
     * ce qu'on voit est petit, ce qu'on peut toucher est grand, et c'est exactement ce
     * qu'il fallait corriger.
     *
     * **Elles montent et descendent, elles ne vont pas à gauche et à droite.** Une
     * valeur n'a pas de gauche ni de droite : elle a un haut et un bas, et c'est déjà
     * ce que disent les roulettes, où l'on pousse les chiffres vers le haut pour les
     * faire croître. Celle de gauche pointe donc vers le haut et fait monter, celle de
     * droite vers le bas et fait descendre.
     */
    private fun drawArrow(canvas: Canvas, x: Float, mid: Float, up: Boolean, row: Int) {
        val w = ARROW_W_DP * dp
        val h = CELL_H_DP * dp
        val on = hit == Hit.ARROW && row == turnRow && (arrowDir > 0) == up
        rect.set(x + 3f * dp, mid - h / 2f, x + w - 3f * dp, mid + h / 2f)
        canvas.drawRoundRect(rect, 6f * dp, 6f * dp, if (on) pArrowBedOn else pArrowBed)

        val cx = x + w / 2f
        val r = 7f * dp
        arrowPath.reset()
        if (up) {
            arrowPath.moveTo(cx - r, mid + r * 0.6f)
            arrowPath.lineTo(cx + r, mid + r * 0.6f)
            arrowPath.lineTo(cx, mid - r * 0.7f)
        } else {
            arrowPath.moveTo(cx - r, mid - r * 0.6f)
            arrowPath.lineTo(cx + r, mid - r * 0.6f)
            arrowPath.lineTo(cx, mid + r * 0.7f)
        }
        arrowPath.close()
        canvas.drawPath(arrowPath, pArrow)
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
        val rolling = hit == Hit.WHEEL && row == turnRow
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
    private fun drawWheel(
        canvas: Canvas,
        x: Float,
        mid: Float,
        digit: Int,
        row: Int,
        col: Int,
        picked: Boolean
    ) {
        val w = CELL_W_DP * dp
        val h = CELL_H_DP * dp
        rect.set(x, mid - h / 2f, x + w, mid + h / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, if (picked) pCellOn else pCell)
        if (picked) {
            // Un trait sous la colonne, en plus du fond : le fond seul se perd sur un
            // écran au soleil, et il fallait que ça se voie d'un coup d'œil puisque
            // c'est ce que les flèches vont changer.
            canvas.drawRect(
                x, mid + h / 2f - 2.5f * dp, x + w, mid + h / 2f, pPicked
            )
        }

        val rolling = hit == Hit.WHEEL && row == turnRow && col == turnCol
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
                hitTest(event.x, event.y)
                consumed = false
                if (hit == Hit.WHEEL) {
                    postDelayed(longPress, LONG_PRESS_MS)
                }
                if (hit == Hit.ARROW) {
                    // Le premier cran part à l'appui, pas au relâchement : une flèche
                    // doit répondre tout de suite.
                    nudge(arrowDir)
                    postDelayed(repeater, REPEAT_DELAY_MS)
                }
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                travel += abs(dx) + abs(dy)
                when (hit) {
                    Hit.HEADER -> dragPanel(dx, dy)
                    Hit.WHEEL -> {
                        // Le doigt qui glisse ne désigne plus : il roule.
                        if (travel > TAP_SLOP_DP * dp) removeCallbacks(longPress)
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
                    Hit.ARROW -> {
                        // Le doigt qui quitte la flèche arrête la répétition : c'est le
                        // seul moyen d'annuler un appui maintenu par mégarde.
                        if (travel > 2f * TAP_SLOP_DP * dp) release()
                    }
                    Hit.NONE -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Un appui franc sur un chiffre vaut un cran : moitié haute pour monter,
                // basse pour descendre.
                if (hit == Hit.WHEEL && travel < TAP_SLOP_DP * dp && !consumed) {
                    turn(if (event.y < rowTop(turnRow) + ROW_DP * dp / 2f) +1 else -1)
                }
                release()
            }
        }
        return true
    }

    /** Le doigt s'en va : plus rien n'est tenu, et plus rien ne se répète. */
    private fun release() {
        removeCallbacks(repeater)
        removeCallbacks(longPress)
        hit = Hit.NONE
        turnRow = -1
        turnCol = -1
        arrowDir = 0
        turnOffset = 0f
        invalidate()
    }

    /**
     * Range le doigt dans une case : bandeau, flèche, roulette, ou rien.
     *
     * « Rien » est un résultat à part entière, et c'est le changement le plus utile de
     * cette version : un appui à côté d'une flèche ne déplace plus la fenêtre, il ne
     * fait rien. Rater sa cible ne doit jamais coûter plus cher que de recommencer.
     */
    private fun hitTest(x: Float, y: Float) {
        hit = Hit.NONE
        turnRow = -1
        turnCol = -1
        arrowDir = 0

        if (y <= HEADER_DP * dp) {
            hit = Hit.HEADER
            return
        }
        for (row in dials.indices) {
            val d = dials[row]
            val top = rowTop(row)
            if (y < top || y > top + ROW_DP * dp) continue
            turnRow = row
            val aw = ARROW_W_DP * dp
            // À gauche on monte, à droite on descend : voir [drawArrow].
            if (x >= xLeftArrow && x <= xLeftArrow + aw) {
                hit = Hit.ARROW
                arrowDir = +1
                return
            }
            if (x >= xRightArrow && x <= xRightArrow + aw) {
                hit = Hit.ARROW
                arrowDir = -1
                return
            }
            var cx = cellsLeft(d)
            if (d.choice) {
                if (x >= cx && x <= cx + choiceWidth) {
                    hit = Hit.WHEEL
                    turnCol = 0
                } else {
                    turnRow = -1
                }
                return
            }
            for (col in 0 until d.digits) {
                if (d.decimals > 0 && col == d.intDigits) cx += DOT_W_DP * dp
                if (x >= cx && x <= cx + CELL_W_DP * dp) {
                    hit = Hit.WHEEL
                    turnCol = col
                    return
                }
                cx += (CELL_W_DP + GAP_DP) * dp
            }
            turnRow = -1
            return
        }
    }

    /** La bulle se pousse où on veut, mais reste entièrement dans la scène. */
    private fun dragPanel(dx: Float, dy: Float) {
        val parentView = parent as? View ?: return
        val maxX = (parentView.width - width).toFloat()
        val maxY = (parentView.height - height).toFloat()
        translationX = (translationX + dx).coerceIn(-left.toFloat(), maxX - left)
        translationY = (translationY + dy).coerceIn(-top.toFloat(), maxY - top)
    }

    /**
     * Le cran d'une flèche : le réglage entier avance ou recule de son pas naturel.
     *
     * Contrairement à la roulette, qui travaille chiffre par chiffre, la flèche ne
     * connaît que la valeur. Elle la ramène sur la grille de l'affichage — un
     * contrepoids poussé de cent kilos depuis 3 050 tombe sur 3 150, jamais sur
     * 3 149,97.
     */
    private fun nudge(delta: Int) {
        val g = game ?: return
        if (turnRow !in dials.indices) return
        val d = dials[turnRow]
        if (d.choice) {
            cycleShot(g, delta)
            return
        }
        // La flèche vaut une unité de la colonne désignée, avec la retenue d'un
        // compteur : passer de 9 à 0 pousse la colonne de gauche.
        val p = pow10(d.decimals)
        var poids = 1
        repeat(d.digits - 1 - columnOf(d)) { poids *= 10 }
        val scaled = (value(g, d) * p).roundToInt() + delta * poids
        apply(g, d, scaled.coerceAtLeast(0) / p)
        onValueChanged?.invoke()
        invalidate()
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
            cycleShot(g, delta)
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

    /** Le projectile suivant ou le précédent : ils tournent en rond. */
    private fun cycleShot(g: TrebuchetGame, delta: Int) {
        val kinds = Projectile.entries
        val i = kinds.indexOf(g.config.projectile)
        val next = ((i + delta) % kinds.size + kinds.size) % kinds.size
        synchronized(g) { g.setProjectile(kinds[next]) }
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
            Dial.CHARGE -> bombSticks.toFloat()
            Dial.WEIGHT -> ballMass
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
                Dial.CHARGE -> g.setBombSticks(v.roundToInt())
                Dial.WEIGHT -> g.setBallMass(v)
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
