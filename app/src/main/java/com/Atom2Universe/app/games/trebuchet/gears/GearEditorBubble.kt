package com.Atom2Universe.app.games.trebuchet.gears

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.trebuchet.Projectile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Éditeur flottant d'une roue sélectionnée.
 *
 * Il se manœuvre **exactement comme la bulle du trébuchet** : chaque réglage est une
 * ligne, chaque chiffre une roulette qu'on touche, glisse ou pousse aux flèches, et
 * la colonne désignée décide de ce que valent les flèches. Un appui long change de
 * colonne, et ce choix survit au passage d'une pièce à l'autre. C'était le seul
 * endroit du jeu où les nombres se réglaient autrement, et il n'y avait aucune raison
 * à ça : le joueur a déjà appris le geste sur la machine.
 *
 * La denture est la dimension maîtresse : le module reste constant, donc changer le
 * nombre de dents recalcule automatiquement rayons, masse, inertie et engrènements.
 */
class GearEditorBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gearView: GearMachineView? = null
    var onEdited: (() -> Unit)? = null

    /**
     * Un réglage vu par les roulettes.
     *
     * [intDigits] fixe le nombre de colonnes ; [step] le **cran naturel**, celui sur
     * lequel les flèches démarrent — on désigne la colonne dont le poids lui ressemble
     * le plus. [signed] ajoute une case de signe devant les chiffres : c'est ce qui
     * permet de régler un étage négatif ou le sens d'un attelage sans une ligne de
     * plus. [choice] fait défiler des mots au lieu de chiffres, du même geste.
     */
    private enum class Dial(
        val label: Int,
        val unit: Int,
        val intDigits: Int,
        val step: Int,
        val signed: Boolean = false,
        val choice: Boolean = false
    ) {
        TEETH(R.string.trebuchet_gear_edit_teeth, R.string.trebuchet_gear_unit_teeth, 3, 1),
        LAYER(R.string.trebuchet_gear_edit_layer, R.string.trebuchet_gear_unit_layer, 2, 1, signed = true),

        /**
         * Le gabarit de la machine motrice, en mètres de rayon.
         *
         * C'est **le** levier d'un moteur, et il ne se lit pas sur l'engrenage : les
         * ailes d'un moulin balaient une surface qui grandit comme le carré de leur
         * envergure, une bête tire au bout d'un bras dont la longueur fait le couple.
         */
        SPAN(R.string.trebuchet_gear_edit_span, R.string.trebuchet_gear_unit_m, 2, 1),

        /**
         * L'attelage, et son signe pour le sens. Une seule colonne : on n'attelle pas
         * douze bêtes, et le signe dit de quel côté elles tirent.
         */
        UNITS(R.string.trebuchet_gear_edit_units, R.string.trebuchet_gear_unit_none, 1, 1, signed = true),

        /**
         * La durée d'une charge, en secondes. Quatre colonnes parce qu'on va jusqu'à
         * vingt minutes, et un cran de trente : personne ne cherche 301 secondes.
         */
        DURATION(R.string.trebuchet_gear_edit_duration, R.string.trebuchet_gear_unit_s, 4, 30),
        LAUNCH(R.string.trebuchet_gear_edit_launch, R.string.trebuchet_gear_unit_deg, 2, 1),

        /**
         * Ce qu'on charge — le même catalogue qu'au trébuchet, [Projectile] : boulet,
         * fragmentation ou bombe. Une seule roulette de mots, comme au trébuchet.
         */
        SHOT(R.string.trebuchet_dial_shot, R.string.trebuchet_unit_none, 0, 1, choice = true),

        /**
         * Le boulet, en kilos. Quatre colonnes : le bloc de siège pèse une tonne.
         * Sans objet pour la fragmentation ou la bombe, qui ne se pèsent pas — voir
         * [dialsFor].
         */
        BALL(R.string.trebuchet_gear_edit_ball, R.string.trebuchet_gear_unit_kg, 4, 10),

        /**
         * Combien de bâtons de poudre dans la bombe. Sans objet pour les deux autres
         * projectiles, qui n'ont pas de charge — voir [dialsFor].
         */
        STICKS(R.string.trebuchet_dial_charge, R.string.trebuchet_unit_sticks, 3, 5),

        /**
         * Le réservoir d'un canon, en litres. Le deuxième levier de puissance, à côté
         * de la denture de la manivelle — voir [GearWheelConfig.reservoirVolume].
         */
        TANK(R.string.trebuchet_gear_edit_tank, R.string.trebuchet_gear_unit_l, 4, 10),

        /**
         * Le régime, qui ne se règle pas : il se subit. Sa ligne emprunte la place des
         * deux flèches pour y poser le frein et le stop — ce sont les seules commandes
         * qu'une vitesse accepte.
         */
        SPEED(R.string.trebuchet_gear_edit_speed, R.string.trebuchet_gear_unit_rpm, 0, 1);

        val digits: Int get() = intDigits + if (signed) 1 else 0
    }

    private enum class Hit { NONE, HEADER, WHEEL, ARROW, ACTION }

    /**
     * Ce que la bulle montre.
     *
     * [WHEEL] est le panneau historique : il suit la piece tenue et disparait avec elle.
     * [TIME] est **independant de la selection** — c'est l'avance rapide, qui ne
     * concerne pas une roue mais toute la machine. Elle vivait dans la bulle du moteur,
     * ou elle n'avait rien a faire : on chargeait « le moulin » alors qu'on charge le
     * mecanisme entier, et il fallait selectionner une piece precise pour atteindre une
     * commande qui ne lui appartenait pas.
     */
    private enum class Panel { WHEEL, TIME }

    private var panel = Panel.WHEEL

    /** La piece tenue au dernier rafraichissement : sert a voir la selection changer. */
    private var lastWheelId: Int? = null

    /** Les boutons du bas. Ils dependent entierement de la piece tenue. */
    private enum class Action { MOTOR, LAUNCHER_KIND, CHARGE, COUPLE, CLUTCH, DUPLICATE, DELETE }

    private val dp = resources.displayMetrics.density

    private companion object {
        const val HEADER_DP = 26f
        const val PAD_DP = 12f
        const val ROW_DP = 54f

        /**
         * En dessous, les roulettes deviendraient trop plates pour être visées au
         * doigt. Une bulle de moteur ouvre six lignes plus le régime et les boutons ;
         * en paysage sur un téléphone il faut bien qu'elles se resserrent, mais une
         * ligne dessinée hors du panneau serait pire — invisible et intouchable.
         */
        const val MIN_ROW_DP = 38f
        const val CELL_W_DP = 28f
        const val GAP_DP = 3f
        const val ARROW_W_DP = 36f
        const val ACTION_DP = 46f
        const val CHOICE_PAD_DP = 8f
        const val STEP_DP = 24f
        const val TAP_SLOP_DP = 8f
        const val LONG_PRESS_MS = 400L
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_EVERY_MS = 80L

        /** Une seconde par tour vaut soixante tours par minute. */
        const val RAD_PER_S_TO_RPM = 60f / (2f * PI.toFloat())
    }

    // ── Peintures ─────────────────────────────────────────────────────────────

    private val pPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 16, 25, 50) }
    private val pPanelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 209, 102)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 143, 166, 200) }
    private val pGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 209, 102) }
    private val pCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 143, 166, 200) }
    private val pCellOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 209, 102) }
    private val pPicked = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102) }
    private val pArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102) }
    private val pArrowBed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 143, 166, 200) }
    private val pArrowBedOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 209, 102) }
    private val pDanger = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 230, 112, 82) }
    private val pDigit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pDigitDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 209, 102)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pChoice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pChoiceDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 209, 102)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 166, 200)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(143, 166, 200) }
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pButton = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 216, 224)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rect = RectF()
    private val arrowPath = Path()
    private val clipPath = Path()

    init {
        pDigit.textSize = 22f * dp
        pDigitDim.textSize = 22f * dp
        pChoice.textSize = 15f * dp
        pChoiceDim.textSize = 15f * dp
        pLabel.textSize = 13f * dp
        pUnit.textSize = 12f * dp
        pTitle.textSize = 12f * dp
        pButton.textSize = 12f * dp
    }

    // ── Contenu ───────────────────────────────────────────────────────────────

    private var dials = emptyList<Dial>()
    private var deeds = emptyList<Action>()
    private var labelWidth = 0f
    private var unitWidth = 0f
    private var choiceWidth = 0f
    private var cellsSpan = 0f
    private var rowDp = ROW_DP

    // ── Saisie ────────────────────────────────────────────────────────────────

    private var hit = Hit.NONE
    private var turnRow = -1
    private var turnCol = -1
    private var arrowDir = 0
    private var pressedAction: Action? = null

    /** Vrai quand le doigt tient le frein : il se relâche au lever, jamais avant. */
    private var braking = false

    /**
     * La colonne désignée de chaque réglage, et non de chaque ligne à l'écran : elle
     * survit au fait de lâcher une roue pour en prendre une autre et d'y revenir.
     */
    private val pickedCol = IntArray(Dial.entries.size) { -1 }

    private var consumed = false
    private var turnOffset = 0f
    private var travel = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val restX = FloatArray(2) { Float.NaN }
    private val restY = FloatArray(2) { Float.NaN }
    private val slot: Int
        get() = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 1 else 0

    private val longPress = Runnable {
        if (hit == Hit.WHEEL && turnRow in dials.indices && !dials[turnRow].choice) {
            pickedCol[dials[turnRow].ordinal] = turnCol
            consumed = true
            invalidate()
        }
    }

    private val repeater = object : Runnable {
        override fun run() {
            if (hit != Hit.ARROW) return
            nudge(arrowDir)
            postDelayed(this, REPEAT_EVERY_MS)
        }
    }

    // ── Ce que la pièce tenue propose ─────────────────────────────────────────

    /**
     * Les lignes offertes pour la pièce tenue.
     *
     * Un volant porte toujours son bras de lancement : son élévation et son boulet se
     * règlent donc **ici**, sur la pièce elle-même. Un engrenage, lui, peut recevoir un
     * moteur — jamais un volant, dont le métier est de garder l'élan, pas de le
     * produire — et son gabarit, son attelage et la durée n'existent qu'avec lui.
     */
    private fun dialsFor(wheel: GearWheelConfig?): List<Dial> {
        if (wheel == null) return emptyList()
        val out = ArrayList<Dial>(7)
        out += Dial.TEETH
        out += Dial.LAYER
        if (wheel.kind in GearMachineRules.LAUNCHER_KINDS) {
            out += Dial.LAUNCH
            // Le réservoir n'existe que pour un canon : un volant n'a rien à mettre
            // sous pression.
            if (wheel.kind == GearWheelKind.PUMP) out += Dial.TANK
            // Le catalogue est le même pour les deux lanceurs : ce qu'on charge ne
            // dépend pas de la façon dont on l'envoie.
            out += Dial.SHOT
            val kind = gearView?.game?.config?.projectileKind
            if (kind?.weighable == true) out += Dial.BALL
            if (kind?.explosive == true) out += Dial.STICKS
        } else if (wheel.motor != null) {
            // Le genre du moteur ne se regle plus ici : il se choisit en posant la
            // piece, comme on choisit un engrenage plutot qu'un volant. Une ligne qui
            // ne servait qu'a defaire ce qu'on venait de poser n'apprenait rien.
            out += Dial.SPAN
            out += Dial.UNITS
        }
        out += Dial.SPEED
        return out
    }

    /**
     * Les boutons du bas, selon la piece tenue.
     *
     * Le lanceur ne se supprime pas, ne se duplique pas, et il n'y a rien a lui
     * accoupler qu'on ne puisse amorcer depuis l'autre roue — mais il gagne le
     * bouton qui bascule sa forme, volant ou canon : deux lanceurs possibles sur la
     * meme piece epinglee, le meme geste que le moteur qu'on remplace sans l'ajouter.
     *
     * La roue motrice, elle, gagne le bouton qui fait defiler les trois machines : il
     * n'y a qu'un moteur dans l'atelier, donc on ne l'ajoute pas, on le remplace.
     */
    private fun actionsFor(wheel: GearWheelConfig?): List<Action> {
        if (wheel == null) return emptyList()
        val view = gearView ?: return emptyList()
        if (view.game.config.isPinned(wheel.id)) return listOf(Action.LAUNCHER_KIND, Action.CLUTCH)
        val out = ArrayList<Action>(3)
        if (wheel.motor != null) {
            out += Action.MOTOR
            out += Action.COUPLE
            return out
        }
        out += Action.COUPLE
        out += Action.DUPLICATE
        out += Action.DELETE
        return out
    }

    /** Appelé avec chaque rafraîchissement de l'activité : la sélection pilote la bulle. */
    fun showForSelection() {
        val wheel = gearView?.selectedWheel()
        if (panel == Panel.TIME) {
            // Le panneau du temps ne repond pas a la selection — il s'ouvre et se ferme
            // au bouton, pas au gre des pieces qu'on touche. Une seule exception :
            // **prendre une nouvelle piece** le referme, parce qu'il n'y a qu'une bulle
            // et qu'un joueur qui vient de designer un engrenage attend de le voir.
            if (wheel == null || wheel.id == lastWheelId) {
                lastWheelId = wheel?.id
                invalidate()
                return
            }
            panel = Panel.WHEEL
        }
        lastWheelId = wheel?.id
        val wanted = dialsFor(wheel)
        val wantedDeeds = actionsFor(wheel)
        if (wanted != dials || wantedDeeds != deeds) {
            dials = wanted
            deeds = wantedDeeds
            measureLabels()
            requestLayout()
        }
        val show = wheel != null
        visibility = if (show) VISIBLE else GONE
        if (!show) release()
        invalidate()
    }

    /** Vrai quand la bulle montre l'avance rapide. */
    val showingTime: Boolean get() = panel == Panel.TIME && visibility == VISIBLE

    /**
     * Ouvre ou referme le panneau d'avance rapide.
     *
     * Une duree, un bouton, rien d'autre : c'est tout ce que la commande demande, et
     * c'est pour ca qu'elle merite sa propre bulle plutot qu'une ligne perdue au milieu
     * des reglages d'un moulin.
     */
    fun toggleTime() {
        if (panel == Panel.TIME) {
            panel = Panel.WHEEL
            release()
            showForSelection()
            return
        }
        panel = Panel.TIME
        dials = listOf(Dial.DURATION)
        deeds = listOf(Action.CHARGE)
        measureLabels()
        requestLayout()
        visibility = VISIBLE
        invalidate()
    }

    /** Referme le panneau du temps sans rien decider d'autre. */
    fun closeTime() {
        if (panel == Panel.TIME) toggleTime()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── La grille ─────────────────────────────────────────────────────────────
    //
    // Les colonnes se calculent ici, une fois pour toutes, et le dessin comme la saisie
    // les relisent : deux calculs séparés finissent toujours par diverger d'un dp, et
    // une flèche qui ne se touche pas là où elle se voit est indéfendable.

    private fun measureLabels() {
        labelWidth = 0f
        unitWidth = 0f
        choiceWidth = pChoice.measureText(context.getString(R.string.trebuchet_gear_brake)) * 1.4f
        for (d in dials) {
            labelWidth = maxOf(labelWidth, pLabel.measureText(context.getString(d.label)))
            if (d.unit != R.string.trebuchet_gear_unit_none) {
                unitWidth = maxOf(unitWidth, pUnit.measureText(context.getString(d.unit)))
            }
            if (!d.choice) continue
            // La cellule des mots tient le plus long d'entre eux, sinon la bulle
            // changerait de largeur à chaque cran tourné.
            for (word in wordsOf(d)) {
                choiceWidth = maxOf(choiceWidth, pChoice.measureText(word))
            }
        }
        choiceWidth += 2f * CHOICE_PAD_DP * dp
        cellsSpan = 0f
        for (d in dials) cellsSpan = maxOf(cellsSpan, cellsWidth(d))
    }

    private fun cellsWidth(d: Dial): Float =
        if (d.digits == 0) choiceWidth else d.digits * (CELL_W_DP + GAP_DP) * dp

    private val xLeftArrow: Float get() = PAD_DP * dp + labelWidth + 8f * dp
    private val xCells: Float get() = xLeftArrow + ARROW_W_DP * dp
    private val xRightArrow: Float get() = xCells + cellsSpan
    private val xUnit: Float get() = xRightArrow + ARROW_W_DP * dp + 4f * dp

    private fun cellsLeft(d: Dial): Float = xCells + (cellsSpan - cellsWidth(d)) / 2f
    private fun rowTop(row: Int): Float = (HEADER_DP + PAD_DP) * dp + row * rowDp * dp
    private fun actionTop(): Float = rowTop(dials.size) + 4f * dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (dials.isEmpty()) {
            setMeasuredDimension(0, 0)
            return
        }
        val actionBand = if (deeds.isEmpty()) 0f else (ACTION_DP + 4f) * dp
        val fixed = (HEADER_DP + PAD_DP * 2f) * dp + actionBand
        val available = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        rowDp = if (available > fixed) {
            ((available - fixed) / dp / dials.size).coerceIn(MIN_ROW_DP, ROW_DP)
        } else {
            ROW_DP
        }
        val w = xUnit + unitWidth + PAD_DP * dp
        val h = fixed + dials.size * rowDp * dp
        setMeasuredDimension(w.roundToInt(), h.roundToInt())
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val wheel = gearView?.selectedWheel()
        // Le panneau du temps se passe de roue : il parle de la machine entiere.
        if (panel == Panel.WHEEL && wheel == null) return
        if (dials.isEmpty()) return
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanel)
        canvas.drawRoundRect(rect, 12f * dp, 12f * dp, pPanelEdge)
        drawHeader(canvas, wheel)

        val cellH = (rowDp - 8f) * dp
        for (row in dials.indices) {
            val d = dials[row]
            val mid = rowTop(row) + rowDp * dp / 2f
            canvas.drawText(
                context.getString(d.label), PAD_DP * dp,
                mid + pLabel.textSize * 0.36f, pLabel
            )
            if (d == Dial.SPEED) {
                drawTextButton(
                    canvas, xLeftArrow, mid, cellH,
                    context.getString(R.string.trebuchet_gear_brake),
                    hit == Hit.ARROW && row == turnRow && arrowDir > 0
                )
                drawTextButton(
                    canvas, xRightArrow, mid, cellH,
                    context.getString(R.string.trebuchet_gear_stop_short),
                    hit == Hit.ARROW && row == turnRow && arrowDir < 0
                )
                drawWordCell(canvas, cellsLeft(d), mid, cellH, speedText(), rolling = false)
            } else {
                drawArrow(canvas, xLeftArrow, mid, cellH, up = true, row = row)
                drawArrow(canvas, xRightArrow, mid, cellH, up = false, row = row)
                if (d.choice) {
                    drawChoice(canvas, cellsLeft(d), mid, cellH, d, row)
                } else {
                    var x = cellsLeft(d)
                    val digits = digitsOf(d)
                    for (col in 0 until d.digits) {
                        drawWheel(canvas, x, mid, cellH, digits[col], d, row, col)
                        x += (CELL_W_DP + GAP_DP) * dp
                    }
                }
            }
            if (d.unit != R.string.trebuchet_gear_unit_none) {
                canvas.drawText(
                    context.getString(d.unit), xUnit, mid + pUnit.textSize * 0.36f, pUnit
                )
            }
        }
        drawActions(canvas)
    }

    private fun drawHeader(canvas: Canvas, wheel: GearWheelConfig?) {
        val h = HEADER_DP * dp
        canvas.save()
        clipPath.reset()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(rect, 12f * dp, 12f * dp, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, width.toFloat(), h, pHeader)
        canvas.restore()
        val titre = if (wheel == null || panel == Panel.TIME) {
            context.getString(R.string.trebuchet_gear_time_header)
        } else {
            val diameterCm = (wheel.outerRadius * 200f).roundToInt()
            val circumferenceCm = (2f * PI.toFloat() * wheel.outerRadius * 100f).roundToInt()
            context.getString(R.string.trebuchet_gear_size_header, diameterCm, circumferenceCm)
        }
        canvas.drawText(
            titre, width / 2f, h / 2f - (pTitle.ascent() + pTitle.descent()) / 2f, pTitle
        )
        canvas.drawCircle(12f * dp, h / 2f, 2f * dp, pGrip)
        canvas.drawCircle(width - 12f * dp, h / 2f, 2f * dp, pGrip)
    }

    private fun drawArrow(
        canvas: Canvas, x: Float, mid: Float, cellH: Float, up: Boolean, row: Int
    ) {
        val w = ARROW_W_DP * dp
        val on = hit == Hit.ARROW && row == turnRow && (arrowDir > 0) == up
        rect.set(x + 3f * dp, mid - cellH / 2f, x + w - 3f * dp, mid + cellH / 2f)
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

    /** Le frein et le stop prennent la place des flèches : une vitesse ne se règle pas. */
    private fun drawTextButton(
        canvas: Canvas, x: Float, mid: Float, cellH: Float, text: String, pressed: Boolean
    ) {
        val w = ARROW_W_DP * dp
        rect.set(x + 3f * dp, mid - cellH / 2f, x + w - 3f * dp, mid + cellH / 2f)
        canvas.drawRoundRect(rect, 6f * dp, 6f * dp, if (pressed) pArrowBedOn else pArrowBed)
        canvas.drawText(text, rect.centerX(), mid + pButton.textSize * 0.36f, pButton)
    }

    private fun drawWordCell(
        canvas: Canvas, x: Float, mid: Float, cellH: Float, text: String, rolling: Boolean
    ) {
        rect.set(x, mid - cellH / 2f, x + choiceWidth, mid + cellH / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, if (rolling) pCellOn else pCell)
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawText(text, rect.centerX(), mid + pChoice.textSize * 0.36f, pChoice)
        canvas.restore()
    }

    /**
     * La roulette des mots : la valeur choisie et ses voisines qui arrivent.
     *
     * Elle est dessinée exactement comme une roulette de chiffres — même cellule, même
     * défilement, même estompage — parce que c'en est une. Seul le contenu change.
     */
    private fun drawChoice(canvas: Canvas, x: Float, mid: Float, cellH: Float, d: Dial, row: Int) {
        rect.set(x, mid - cellH / 2f, x + choiceWidth, mid + cellH / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, pCell)
        val words = wordsOf(d)
        if (words.isEmpty()) return
        val cur = choiceIndex(d)
        val rolling = hit == Hit.WHEEL && row == turnRow
        val offset = if (rolling) turnOffset else 0f
        val step = STEP_DP * dp
        val base = mid + pChoice.textSize * 0.36f
        canvas.save()
        canvas.clipRect(rect)
        for (k in -1..1) {
            val i = ((cur + k) % words.size + words.size) % words.size
            canvas.drawText(
                words[i], rect.centerX(), base + k * step + offset,
                if (k == 0 && abs(offset) < step / 2f) pChoice else pChoiceDim
            )
        }
        canvas.restore()
    }

    /** Une roulette : le chiffre tenu et ceux qui l'encadrent, coupés par la cellule. */
    private fun drawWheel(
        canvas: Canvas, x: Float, mid: Float, cellH: Float,
        digit: Int, d: Dial, row: Int, col: Int
    ) {
        val w = CELL_W_DP * dp
        val picked = col == columnOf(d)
        rect.set(x, mid - cellH / 2f, x + w, mid + cellH / 2f)
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, if (picked) pCellOn else pCell)
        if (picked) {
            // Un trait sous la colonne en plus du fond : le fond seul se perd au soleil,
            // et c'est ce que les flèches vont changer, donc ça doit se voir.
            canvas.drawRect(x, mid + cellH / 2f - 2.5f * dp, x + w, mid + cellH / 2f, pPicked)
        }
        val rolling = hit == Hit.WHEEL && row == turnRow && col == turnCol
        val offset = if (rolling) turnOffset else 0f
        val step = STEP_DP * dp
        val base = mid + pDigit.textSize * 0.36f
        val signCell = d.signed && col == 0
        canvas.save()
        canvas.clipRect(rect)
        for (k in -1..1) {
            val shown = if (signCell) {
                // La case de signe ne compte que jusqu'à deux : elle bascule.
                if ((digit + k) % 2 == 0) "+" else "−"
            } else {
                (((digit + k) % 10 + 10) % 10).toString()
            }
            canvas.drawText(
                shown, rect.centerX(), base + k * step + offset,
                if (k == 0 && abs(offset) < step / 2f) pDigit else pDigitDim
            )
        }
        canvas.restore()
    }

    private fun drawActions(canvas: Canvas) {
        if (deeds.isEmpty()) return
        val top = actionTop()
        val gap = 5f * dp
        val left = PAD_DP * dp
        val span = width - left * 2f
        val buttonWidth = (span - gap * (deeds.size - 1)) / deeds.size
        for ((index, action) in deeds.withIndex()) {
            val x = left + (buttonWidth + gap) * index
            rect.set(x, top, x + buttonWidth, top + ACTION_DP * dp - 4f * dp)
            val background = when {
                pressedAction == action -> pCellOn
                action == Action.DELETE -> pDanger
                else -> pCell
            }
            canvas.drawRoundRect(rect, 7f * dp, 7f * dp, background)
            canvas.drawText(
                actionLabel(action), rect.centerX(),
                rect.centerY() - (pButton.ascent() + pButton.descent()) / 2f, pButton
            )
        }
    }

    private fun actionLabel(action: Action): String = when (action) {
        // Le bouton dit **quel** moteur est monte : c'est ce qu'on lit avant de le
        // changer, et une etiquette « moteur » ne l'aurait pas dit.
        Action.MOTOR -> motorName(gearView?.selectedWheel()?.motor?.kind ?: GearMotorKind.NONE)
        // Meme principe que le moteur : le bouton dit quelle forme le lanceur a
        // aujourd'hui, avant qu'on la fasse basculer.
        Action.LAUNCHER_KIND -> launcherKindName(
            gearView?.selectedWheel()?.kind ?: GearWheelKind.FLYWHEEL
        )
        Action.CHARGE -> context.getString(
            if (gearView?.game?.charging == true) R.string.trebuchet_gear_charge_stop
            else R.string.trebuchet_gear_charge
        )
        Action.COUPLE -> context.getString(R.string.trebuchet_gear_couple)
        // Le bouton dit ce qu'il fera au prochain appui, exactement comme CHARGE :
        // « Débrayer » tant que c'est relié, « Embrayer » une fois coupé.
        Action.CLUTCH -> context.getString(
            if (gearView?.game?.config?.launcherEngaged != false) R.string.trebuchet_gear_clutch_disengage
            else R.string.trebuchet_gear_clutch_engage
        )
        Action.DUPLICATE -> context.getString(R.string.trebuchet_gear_duplicate)
        Action.DELETE -> context.getString(R.string.trebuchet_gear_delete_short)
    }

    private fun launcherKindName(kind: GearWheelKind): String = context.getString(
        if (kind == GearWheelKind.PUMP) R.string.trebuchet_gear_launcher_cannon
        else R.string.trebuchet_gear_launcher_flywheel
    )

    private fun motorName(kind: GearMotorKind): String = context.getString(
        when (kind) {
            GearMotorKind.NONE -> R.string.trebuchet_gear_motor_none
            GearMotorKind.CAROUSEL -> R.string.trebuchet_gear_motor_carousel
            GearMotorKind.WINDMILL -> R.string.trebuchet_gear_motor_windmill
            GearMotorKind.WATERWHEEL -> R.string.trebuchet_gear_motor_waterwheel
        }
    )

    // ── Saisie ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                travel = 0f
                turnOffset = 0f
                consumed = false
                hitTest(event.x, event.y)
                if (hit == Hit.WHEEL) postDelayed(longPress, LONG_PRESS_MS)
                if (hit == Hit.ARROW) {
                    if (dials.getOrNull(turnRow) == Dial.SPEED) {
                        // Le frein se **tient** — pas de répétition à cadence, c'est la
                        // pression du doigt qui dure. Le stop, lui, agit d'un coup.
                        if (arrowDir > 0) {
                            braking = true
                            gearView?.setBrake(true)
                        } else {
                            gearView?.stopSelected()
                        }
                    } else {
                        // Le premier cran part à l'appui : une flèche doit répondre.
                        nudge(arrowDir)
                        postDelayed(repeater, REPEAT_DELAY_MS)
                    }
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
                        if (travel > TAP_SLOP_DP * dp) removeCallbacks(longPress)
                        // Glisser vers le haut fait monter : on pousse la roulette.
                        turnOffset -= dy
                        val step = STEP_DP * dp
                        while (turnOffset >= step) { turn(+1); turnOffset -= step }
                        while (turnOffset <= -step) { turn(-1); turnOffset += step }
                        invalidate()
                    }
                    // Le doigt qui quitte la flèche arrête la répétition : c'est le seul
                    // moyen d'annuler un appui maintenu par mégarde.
                    Hit.ARROW -> if (travel > 2f * TAP_SLOP_DP * dp) release()
                    Hit.ACTION -> if (travel > 2f * TAP_SLOP_DP * dp) {
                        pressedAction = null
                        invalidate()
                    }
                    Hit.NONE -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (travel < TAP_SLOP_DP * dp && !consumed) {
                    if (hit == Hit.WHEEL) {
                        // Un appui franc vaut un cran : moitié haute pour monter.
                        turn(if (event.y < rowTop(turnRow) + rowDp * dp / 2f) +1 else -1)
                    }
                    if (hit == Hit.ACTION && event.actionMasked == MotionEvent.ACTION_UP) {
                        pressedAction?.let { runAction(it) }
                    }
                }
                onEdited?.invoke()
                release()
            }
        }
        return true
    }

    private fun runAction(action: Action) {
        val view = gearView ?: return
        when (action) {
            Action.MOTOR -> view.cycleSelectedMotor()
            Action.LAUNCHER_KIND -> view.cycleSelectedLauncherKind()
            Action.CHARGE -> view.toggleCharge()
            Action.COUPLE -> view.armLink(GearLinkKind.SHAFT_CLUTCH)
            Action.CLUTCH -> view.toggleLauncherClutch()
            Action.DUPLICATE -> view.duplicateSelected()
            Action.DELETE -> view.deleteSelected()
        }
    }

    private fun release() {
        removeCallbacks(repeater)
        removeCallbacks(longPress)
        // Lever le doigt desserre toujours le frein : un frein resté serré parce qu'un
        // geste s'est terminé ailleurs bloquerait la machine sans rien dire.
        if (braking) {
            braking = false
            gearView?.setBrake(false)
        }
        hit = Hit.NONE
        turnRow = -1
        turnCol = -1
        arrowDir = 0
        turnOffset = 0f
        pressedAction = null
        invalidate()
    }

    /**
     * Range le doigt dans une case : bandeau, flèche, roulette, bouton, ou rien.
     *
     * « Rien » est un résultat à part entière : un appui à côté d'une flèche ne déplace
     * pas la fenêtre, il ne fait rien. Rater sa cible ne doit jamais coûter plus cher
     * que de recommencer.
     */
    private fun hitTest(x: Float, y: Float) {
        hit = Hit.NONE
        turnRow = -1
        turnCol = -1
        arrowDir = 0
        pressedAction = null
        if (y <= HEADER_DP * dp) {
            hit = Hit.HEADER
            return
        }
        for (row in dials.indices) {
            val d = dials[row]
            val top = rowTop(row)
            if (y < top || y > top + rowDp * dp) continue
            turnRow = row
            val aw = ARROW_W_DP * dp
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
            // Le régime n'a pas de roulette : entre ses deux commandes il n'y a qu'un
            // afficheur, et le toucher ne doit rien faire.
            if (d == Dial.SPEED) {
                turnRow = -1
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
        val top = actionTop()
        if (deeds.isNotEmpty() && y >= top) {
            val index = (x / width * deeds.size).toInt().coerceIn(0, deeds.size - 1)
            hit = Hit.ACTION
            pressedAction = deeds[index]
        }
    }

    // ── Les crans ─────────────────────────────────────────────────────────────

    /**
     * La colonne sur laquelle un réglage travaille : celle qu'on a désignée, ou celle
     * de son cran naturel.
     *
     * Le défaut se **calcule** : on cherche la colonne dont le poids ressemble le plus
     * au cran du réglage, en comparant les logarithmes plutôt que les écarts. Trente
     * est aussi loin de cent que de dix, et pas de soixante-dix — c'est le rapport qui
     * compte, pas la différence.
     */
    private fun columnOf(d: Dial): Int {
        if (d.intDigits <= 0) return 0
        val known = pickedCol[d.ordinal]
        if (known in 0 until d.digits) return known
        var best = d.digits - 1
        var bestGap = Float.MAX_VALUE
        for (col in 0 until d.digits) {
            if (d.signed && col == 0) continue
            var weight = 1f
            repeat(d.digits - 1 - col) { weight *= 10f }
            val gap = abs(kotlin.math.ln(weight / d.step))
            if (gap < bestGap) {
                bestGap = gap
                best = col
            }
        }
        return best
    }

    /** Une flèche : un cran sur la colonne désignée, avec la retenue d'un compteur. */
    private fun nudge(delta: Int) {
        if (turnRow !in dials.indices) return
        applyTurn(dials[turnRow], columnOf(dials[turnRow]), delta)
    }

    /**
     * Fait tourner d'un cran la roulette tenue, avec la retenue d'un compteur
     * kilométrique : passer de 9 à 0 pousse la roulette de gauche.
     *
     * La retenue ne descend jamais : bouger les dizaines laisse les unités où elles
     * sont, et c'est tout l'intérêt — on traverse les centaines sans perdre le réglage
     * fin qu'on venait de trouver.
     */
    private fun turn(delta: Int) {
        if (turnRow !in dials.indices) return
        applyTurn(dials[turnRow], turnCol, delta)
    }

    private fun applyTurn(d: Dial, col: Int, delta: Int) {
        val view = gearView ?: return
        if (d.choice) {
            cycleChoice(d, delta)
            onEdited?.invoke()
            invalidate()
            return
        }
        if (d == Dial.SPEED || col < 0 || col >= d.digits) return
        val current = valueOf(d)
        val next = if (d.signed && col == 0) {
            // La case de signe bascule : elle ne s'additionne pas.
            -current
        } else {
            var weight = 1
            repeat(d.digits - 1 - col) { weight *= 10 }
            val magnitude = abs(current) + delta * weight
            if (current < 0) -magnitude else magnitude
        }
        apply(d, next)
        onEdited?.invoke()
        invalidate()
    }

    // ── La machine ────────────────────────────────────────────────────────────

    private fun valueOf(d: Dial): Int {
        val view = gearView ?: return 0
        // La duree appartient a la machine, pas a une piece : elle se lit meme quand
        // rien n'est tenu, ce qui est precisement le cas dans le panneau du temps.
        if (d == Dial.DURATION) return view.game.config.chargeSeconds.roundToInt()
        val wheel = view.selectedWheel() ?: return 0
        return when (d) {
            Dial.TEETH -> wheel.teeth
            Dial.LAYER -> wheel.layer
            Dial.SPAN -> (wheel.motor?.span ?: 0f).roundToInt()
            Dial.UNITS -> wheel.motor?.let {
                it.units * if (it.direction < 0) -1 else 1
            } ?: 0
            Dial.DURATION -> view.game.config.chargeSeconds.roundToInt()
            Dial.LAUNCH -> wheel.launchAngle.roundToInt()
            Dial.BALL -> view.game.config.projectileMass.roundToInt()
            Dial.STICKS -> view.game.config.bombSticks
            // Le modele garde le reservoir en m3 ; la roulette le montre en litres,
            // plus lisible sur une piece qui va de dix a deux mille.
            Dial.TANK -> (wheel.reservoirVolume * 1_000f).roundToInt()
            Dial.SHOT, Dial.SPEED -> 0
        }
    }

    private fun apply(d: Dial, value: Int) {
        val view = gearView ?: return
        when (d) {
            Dial.TEETH -> view.setSelectedTeeth(value)
            Dial.LAYER -> view.setSelectedLayer(value)
            Dial.SPAN -> view.setSelectedMotorSpan(value.toFloat())
            Dial.UNITS -> view.setSelectedMotorUnits(value)
            Dial.DURATION -> view.setChargeSeconds(value.toFloat())
            Dial.LAUNCH -> view.setSelectedLaunchAngle(value.toFloat())
            Dial.BALL -> view.setProjectileMass(value.toFloat())
            Dial.STICKS -> view.setBombSticks(value)
            Dial.TANK -> view.setSelectedReservoirVolume(value / 1_000f)
            Dial.SHOT, Dial.SPEED -> Unit
        }
    }

    /** Les chiffres affichés, du plus fort au plus faible ; la case de signe d'abord. */
    private fun digitsOf(d: Dial): IntArray {
        val out = IntArray(d.digits)
        val value = valueOf(d)
        var magnitude = abs(value)
        for (i in d.digits - 1 downTo if (d.signed) 1 else 0) {
            out[i] = magnitude % 10
            magnitude /= 10
        }
        // Zéro sur la case de signe vaut « + », un vaut « − » : voir [drawWheel].
        if (d.signed) out[0] = if (value < 0) 1 else 0
        return out
    }

    private fun wordsOf(d: Dial): List<String> = when (d) {
        Dial.SHOT -> Projectile.entries.map { shotName(it) }
        else -> emptyList()
    }

    private fun choiceIndex(d: Dial): Int = when (d) {
        Dial.SHOT -> gearView?.game?.config?.projectileKind?.ordinal ?: 0
        else -> 0
    }

    private fun cycleChoice(d: Dial, delta: Int) {
        when (d) {
            Dial.SHOT -> gearView?.cycleProjectileKind(delta)
            else -> Unit
        }
    }

    /** Le nom d'un projectile — les mêmes chaînes qu'au trébuchet, même catalogue. */
    private fun shotName(kind: Projectile): String = context.getString(
        when (kind) {
            Projectile.BOULET -> R.string.trebuchet_shot_ball
            Projectile.FRAGMENTATION -> R.string.trebuchet_shot_cluster
            Projectile.BOMBE -> R.string.trebuchet_shot_bomb
        }
    )

    private fun speedText(): String =
        ((gearView?.selectedOmega() ?: 0f) * RAD_PER_S_TO_RPM).roundToInt().toString()

    // ── La place de la bulle ──────────────────────────────────────────────────

    private fun dragPanel(dx: Float, dy: Float) {
        translationX = clampX(translationX + dx)
        translationY = clampY(translationY + dy)
        restX[slot] = translationX
        restY[slot] = translationY
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        translationX = restX[slot].takeUnless { it.isNaN() }?.let(::clampX) ?: 0f
        translationY = restY[slot].takeUnless { it.isNaN() }?.let(::clampY) ?: clampY(restingY())
    }

    /**
     * Où la bulle se pose quand personne ne l'a encore déplacée.
     *
     * Elle est centrée en haut, et elle est large : sur un téléphone elle recouvrait le
     * sélecteur d'étage et le bouton d'outil, qui vivent dans le coin haut gauche de la
     * scène. Quand elle passe devant, elle descend juste en dessous d'eux — sur une
     * tablette, où elle démarre à leur droite, elle reste tout en haut.
     */
    private fun restingY(): Float {
        if (left >= GearMachineView.HUD_RIGHT_DP * dp) return 0f
        return (GearMachineView.HUD_BOTTOM_DP * dp + 10f * dp - top).coerceAtLeast(0f)
    }

    private fun clampX(value: Float): Float {
        val host = parent as? View ?: return value
        val min = -left.toFloat()
        val max = (host.width - width - left).toFloat()
        return if (max <= min) min else value.coerceIn(min, max)
    }

    private fun clampY(value: Float): Float {
        val host = parent as? View ?: return value
        val min = -top.toFloat()
        val max = (host.height - height - top).toFloat()
        return if (max <= min) min else value.coerceIn(min, max)
    }
}
