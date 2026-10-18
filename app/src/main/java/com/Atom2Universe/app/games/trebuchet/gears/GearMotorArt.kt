package com.Atom2Universe.app.games.trebuchet.gears

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Le dessin des trois machines motrices.
 *
 * **Une machine se tient à gauche, son rouet à droite, et un arbre les relie.** C'est
 * la disposition réelle d'un moulin — les ailes dehors, la denture à l'autre bout de
 * l'arbre — et c'est aussi ce qui rend l'engrenage abordable : posé sur le côté, hors
 * de la carcasse, il reste libre pour qu'on vienne s'y engrener.
 *
 * Tout est tracé en mètres, dans le repère du monde, avec l'axe Y vers le haut. Rien
 * n'est un corps physique : la machine est un décor accroché au seul corps qui compte,
 * la roue dentée, et elle tourne avec elle.
 */
class GearMotorArt(private val dp: Float) {

    private val path = Path()
    private val oval = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private companion object {
        // Le bois, la pierre, la toile et l'eau. Une palette par matière et non par
        // machine : le trestle d'une roue à eau et le bâti d'une roue de cage sont la
        // même charpente, et doivent se ressembler.
        val TIMBER = Color.rgb(146, 104, 62)
        val TIMBER_DARK = Color.rgb(104, 71, 40)
        val STONE = Color.rgb(139, 134, 124)
        val STONE_DARK = Color.rgb(104, 99, 92)
        val CANVAS = Color.rgb(236, 229, 208)
        val IRON = Color.rgb(96, 106, 122)
        val WATER = Color.rgb(126, 190, 226)
        val WATER_DARK = Color.rgb(78, 145, 186)
        val SKIN = Color.rgb(232, 206, 178)
        val CLOTH = Color.rgb(168, 84, 74)
        val ROOF = Color.rgb(88, 72, 60)
    }

    /**
     * Dessine la machine qui entraîne [gear], si elle en a une.
     *
     * [alpha] porte l'estompage des couches, comme pour les roues : une machine d'un
     * autre étage doit s'effacer autant que la denture qu'elle fait tourner.
     */
    fun draw(canvas: Canvas, gear: GearMachineGame.GearState, alpha: Int) {
        val motor = gear.wheel.motor ?: return
        if (motor.kind == GearMotorKind.NONE) return
        val gx = gear.body.x
        val gy = gear.body.y
        val span = motor.span
        val mx = gx - GearMotorRules.offsetX(motor, gear.wheel.outerRadius)
        // La machine se dresse a **sa** hauteur, pas a celle du rouet : voir
        // [GearMotorRules.hubHeight]. C'est l'arbre debout qui rattrape l'ecart.
        val hubY = GearMotorRules.hubHeight(motor, gy)
        val phase = gear.body.angle
        when (motor.kind) {
            GearMotorKind.NONE -> Unit
            GearMotorKind.CAROUSEL -> treadwheel(canvas, mx, hubY, span, motor, phase, alpha)
            GearMotorKind.WINDMILL -> windmill(canvas, mx, hubY, span, motor, phase, alpha)
            GearMotorKind.WATERWHEEL -> waterwheel(canvas, mx, hubY, span, motor, phase, alpha)
        }
        uprightShaft(canvas, mx, hubY, gy, span, phase, alpha)
        shaft(canvas, mx, gx, gy, span, phase, alpha)
    }

    /**
     * Le grand arbre debout : il descend le mouvement du haut de la machine jusqu'a la
     * hauteur du rouet.
     *
     * C'est le coeur d'un moulin reel. L'arbre des ailes est tout en haut, sous la
     * calotte ; il porte le rouet d'angle, qui engrene la lanterne calee sur un arbre
     * vertical ; celui-ci traverse les etages et rend le mouvement a l'horizontale en
     * bas. **Aucun rapport n'est introduit** : les deux renvois sont a l'identique, et
     * la roue dentee tourne exactement a la vitesse des ailes.
     */
    private fun uprightShaft(
        canvas: Canvas, mx: Float, hubY: Float, gearY: Float,
        span: Float, phase: Float, alpha: Int
    ) {
        val drop = hubY - gearY
        if (drop < 0.35f) return
        val post = max(0.10f, span * 0.065f)

        // L'arbre lui-meme, avec son flanc eclaire.
        fill.color = TIMBER_DARK
        fill.alpha = alpha
        canvas.drawRect(mx - post / 2f, gearY, mx + post / 2f, hubY, fill)
        fill.color = TIMBER
        fill.alpha = alpha
        canvas.drawRect(mx - post / 2f, gearY, mx - post * 0.16f, hubY, fill)

        // Les colliers de palier, repartis sur la hauteur : ils disent que l'arbre
        // traverse les planchers, et ils donnent l'echelle de la descente.
        line.color = IRON
        line.alpha = alpha
        line.strokeWidth = post * 0.30f
        var y = gearY + drop * 0.28f
        while (y < hubY - drop * 0.12f) {
            canvas.drawLine(mx - post * 0.85f, y, mx + post * 0.85f, y, line)
            y += drop * 0.34f
        }

        bevelPair(canvas, mx, hubY, span, phase, alpha, downward = true)
        bevelPair(canvas, mx, gearY, span, phase, alpha, downward = false)
    }

    /**
     * Un renvoi d'angle : une roue vue de champ, et la lanterne a plat qu'elle engrene.
     *
     * Vus de cote, l'un est un disque debout et l'autre une galette : c'est ainsi que
     * se dessinent les coupes de moulin, et c'est ce qui rend lisible en un coup d'oeil
     * le fait que le mouvement tourne d'un quart.
     */
    private fun bevelPair(
        canvas: Canvas, mx: Float, y: Float, span: Float,
        phase: Float, alpha: Int, downward: Boolean
    ) {
        val r = max(0.22f, span * 0.15f)
        val face = r * 0.26f
        val side = if (downward) -1f else 1f

        // La roue de champ, portee par l'arbre horizontal.
        oval.set(mx - face, y - r, mx + face, y + r)
        fill.color = TIMBER
        fill.alpha = alpha
        canvas.drawOval(oval, fill)
        line.color = TIMBER_DARK
        line.alpha = alpha
        line.strokeWidth = r * 0.10f
        canvas.drawOval(oval, line)

        // La lanterne, a plat sur l'arbre debout, decalee vers l'interieur du renvoi.
        val lanternY = y + side * r * 0.86f
        oval.set(mx - r, lanternY - face, mx + r, lanternY + face)
        fill.color = TIMBER_DARK
        fill.alpha = alpha
        canvas.drawOval(oval, fill)

        // Quelques fuseaux qui tournent : sans eux, la lanterne est une olive inerte.
        line.color = CANVAS
        line.alpha = (alpha * 0.7f).toInt()
        line.strokeWidth = r * 0.075f
        for (i in 0 until 7) {
            val t = cos(phase + i * (2f * PI.toFloat() / 7))
            canvas.drawLine(
                mx + t * r * 0.82f, lanternY - face * 0.75f,
                mx + t * r * 0.82f, lanternY + face * 0.75f, line
            )
        }
    }

    /**
     * L'arbre horizontal du bas : il sort du pied de la machine, traverse le vide, et le
     * rouet est calé dessus. Deux frettes disent qu'il est d'un seul tenant — quelle que
     * soit la descente, la roue dentée tourne exactement à la vitesse de la machine, il
     * n'y a aucun rapport nulle part.
     */
    private fun shaft(
        canvas: Canvas, mx: Float, gx: Float, gy: Float, span: Float, phase: Float, alpha: Int
    ) {
        val thickness = max(0.10f, span * 0.075f)
        fill.color = TIMBER_DARK
        fill.alpha = alpha
        canvas.drawRect(mx, gy - thickness / 2f, gx, gy + thickness / 2f, fill)
        fill.color = TIMBER
        fill.alpha = alpha
        canvas.drawRect(mx, gy - thickness / 2f, gx, gy + thickness * 0.12f, fill)
        // Les frettes tournent avec l'arbre : c'est le seul repère qui dise, quand la
        // machine est loin, qu'il tourne vraiment.
        line.color = IRON
        line.alpha = alpha
        line.strokeWidth = thickness * 0.28f
        for (t in floatArrayOf(0.34f, 0.68f)) {
            val cx = mx + (gx - mx) * t
            val wobble = sin(phase) * thickness * 0.30f
            canvas.drawLine(cx, gy - thickness * 0.62f + wobble, cx, gy + thickness * 0.62f + wobble, line)
        }
    }

    // ── Le moulin à vent ──────────────────────────────────────────────────────

    /**
     * Un moulin-tour : une tour de pierre tronconique, sa calotte, et les ailes.
     *
     * Les proportions sont celles d'un vrai moulin — la tour fait un peu moins du
     * quart de l'envergure en largeur au sol, et les ailes descendent presque jusqu'au
     * pied. C'est pour ça qu'une aile de huit mètres demande un axe à huit mètres de
     * haut : si l'engrenage est posé plus bas, le moulin se tasse, et c'est juste.
     */
    private fun windmill(
        canvas: Canvas, mx: Float, hubY: Float, span: Float,
        motor: GearMotorConfig, phase: Float, alpha: Int
    ) {
        // La tour monte du sol jusque sous la calotte. Sa hauteur est celle de l'axe,
        // qui est desormais garantie superieure a l'envergure : les ailes ne peuvent
        // plus passer sous terre, et le moulin ne peut plus etre plus petit qu'elles.
        val towerTop = hubY - span * 0.10f
        val towerHeight = max(0f, towerTop)
        if (towerHeight > 0.4f) {
            val baseHalf = span * 0.26f
            val topHalf = span * 0.15f
            path.reset()
            path.moveTo(mx - baseHalf, 0f)
            path.lineTo(mx + baseHalf, 0f)
            path.lineTo(mx + topHalf, towerTop)
            path.lineTo(mx - topHalf, towerTop)
            path.close()
            fill.color = STONE
            fill.alpha = alpha
            canvas.drawPath(path, fill)
            // Le flanc au vent reste dans l'ombre : sans lui la tour est un aplat.
            path.reset()
            path.moveTo(mx - baseHalf, 0f)
            path.lineTo(mx - baseHalf * 0.35f, 0f)
            path.lineTo(mx - topHalf * 0.35f, towerTop)
            path.lineTo(mx - topHalf, towerTop)
            path.close()
            fill.color = STONE_DARK
            fill.alpha = alpha
            canvas.drawPath(path, fill)

            // La galerie : la coursive d'où le meunier oriente ses ailes.
            val galleryY = towerHeight * 0.42f
            val galleryHalf = baseHalf * 0.92f
            fill.color = TIMBER_DARK
            fill.alpha = alpha
            canvas.drawRect(
                mx - galleryHalf, galleryY, mx + galleryHalf, galleryY + span * 0.035f, fill
            )
            line.color = TIMBER
            line.alpha = alpha
            line.strokeWidth = span * 0.014f
            var rail = -galleryHalf
            while (rail <= galleryHalf) {
                canvas.drawLine(
                    mx + rail, galleryY, mx + rail, galleryY + span * 0.10f, line
                )
                rail += galleryHalf * 0.28f
            }
            canvas.drawLine(
                mx - galleryHalf, galleryY + span * 0.10f,
                mx + galleryHalf, galleryY + span * 0.10f, line
            )

            // La porte, et deux lucarnes : c'est ce qui donne l'échelle du bâtiment.
            fill.color = ROOF
            fill.alpha = alpha
            val doorHalf = min(span * 0.055f, towerHeight * 0.18f)
            val doorHigh = min(span * 0.20f, towerHeight * 0.30f)
            canvas.drawRect(mx - doorHalf, 0f, mx + doorHalf, doorHigh, fill)
            val windowSize = span * 0.045f
            canvas.drawRect(
                mx + topHalf * 0.25f, towerTop - span * 0.30f,
                mx + topHalf * 0.25f + windowSize, towerTop - span * 0.30f + windowSize, fill
            )
            canvas.drawRect(
                mx - topHalf * 0.55f, galleryY + span * 0.26f,
                mx - topHalf * 0.55f + windowSize, galleryY + span * 0.26f + windowSize, fill
            )
        }

        // La calotte, qui pivote au vent, et le nez d'ou sort l'arbre.
        //
        // Elle est tracee point par point et non par un arc : le canevas de la scene a
        // l'axe Y **inverse**, et les angles d'un `drawArc` s'y lisent a l'envers -- le
        // dome se serait retourne vers le bas sans que rien ne le dise.
        val capR = span * 0.19f
        path.reset()
        path.moveTo(mx - capR, towerTop - capR * 0.15f)
        path.lineTo(mx - capR, towerTop + capR * 0.10f)
        path.quadTo(mx - capR * 0.75f, towerTop + capR * 1.05f, mx, towerTop + capR * 1.05f)
        path.quadTo(mx + capR * 0.75f, towerTop + capR * 1.05f, mx + capR, towerTop + capR * 0.10f)
        path.lineTo(mx + capR, towerTop - capR * 0.15f)
        path.close()
        fill.color = ROOF
        fill.alpha = alpha
        canvas.drawPath(path, fill)

        // Les ailes. Chacune est une verge, ses barreaux, et la toile d'un seul côté :
        // une aile de moulin n'est pas symétrique, c'est ce qui la fait mordre le vent.
        val sails = motor.units.coerceIn(GearMotorRules.MIN_UNITS, GearMotorRules.MAX_UNITS)
        val hubR = span * 0.055f
        for (i in 0 until sails) {
            val angle = phase + i * (2f * PI.toFloat() / sails)
            sail(canvas, mx, hubY, span, angle, alpha)
        }
        fill.color = IRON
        fill.alpha = alpha
        canvas.drawCircle(mx, hubY, hubR, fill)
    }

    /** Une aile : la verge, les barreaux, et la toile tendue sur un bord. */
    private fun sail(
        canvas: Canvas, cx: Float, cy: Float, span: Float, angle: Float, alpha: Int
    ) {
        val ux = cos(angle)
        val uy = sin(angle)
        // La normale : le côté où la toile est tendue.
        val nx = -uy
        val ny = ux
        val root = span * 0.10f
        val width = span * 0.16f

        // La toile, d'abord, pour que les barreaux se posent dessus.
        path.reset()
        path.moveTo(cx + ux * root, cy + uy * root)
        path.lineTo(cx + ux * root + nx * width * 0.55f, cy + uy * root + ny * width * 0.55f)
        path.lineTo(cx + ux * span + nx * width * 0.85f, cy + uy * span + ny * width * 0.85f)
        path.lineTo(cx + ux * span, cy + uy * span)
        path.close()
        fill.color = CANVAS
        fill.alpha = (alpha * 0.88f).toInt()
        canvas.drawPath(path, fill)

        // La verge, en deux traits pour lui donner de l'épaisseur.
        line.color = TIMBER_DARK
        line.alpha = alpha
        line.strokeWidth = span * 0.030f
        canvas.drawLine(
            cx + ux * root, cy + uy * root, cx + ux * span, cy + uy * span, line
        )

        // Les barreaux, resserrés vers le bout comme sur une vraie aile.
        line.color = TIMBER
        line.alpha = alpha
        line.strokeWidth = span * 0.014f
        var t = 0.16f
        while (t < 1f) {
            val bx = cx + ux * span * t
            val by = cy + uy * span * t
            val half = width * (0.55f + t * 0.32f)
            canvas.drawLine(bx, by, bx + nx * half, by + ny * half, line)
            t += 0.105f
        }
        canvas.drawLine(
            cx + ux * root + nx * width * 0.55f, cy + uy * root + ny * width * 0.55f,
            cx + ux * span + nx * width * 0.87f, cy + uy * span + ny * width * 0.87f, line
        )
    }

    // ── La roue à eau ─────────────────────────────────────────────────────────

    /**
     * Une roue par-dessus : l'eau arrive par un coursier, tombe dans les augets du
     * côté qui descend, et c'est son **poids** qui fait tourner — pas son choc. Le jet
     * se pose donc toujours du côté vers lequel la roue part.
     */
    private fun waterwheel(
        canvas: Canvas, mx: Float, hubY: Float, span: Float,
        motor: GearMotorConfig, phase: Float, alpha: Int
    ) {
        val inner = span * 0.76f
        // La pile de pierre, derrière la roue : elle porte l'axe.
        if (hubY > 0.4f) {
            val half = span * 0.20f
            path.reset()
            path.moveTo(mx - half * 1.35f, 0f)
            path.lineTo(mx + half * 1.35f, 0f)
            path.lineTo(mx + half, hubY)
            path.lineTo(mx - half, hubY)
            path.close()
            fill.color = STONE_DARK
            fill.alpha = alpha
            canvas.drawPath(path, fill)
        }

        // Le bief : un coursier de bois qui amène l'eau au-dessus de la roue.
        val side = if (motor.direction < 0) 1f else -1f
        val mouthX = mx + side * span * 0.42f
        val mouthY = hubY + span * 1.06f
        val raceTop = mouthY + span * 0.30f
        val raceOut = mouthX + side * span * 0.95f
        line.color = TIMBER_DARK
        line.alpha = alpha
        line.strokeWidth = span * 0.055f
        canvas.drawLine(raceOut, raceTop, mouthX, mouthY, line)
        line.color = TIMBER
        line.alpha = alpha
        line.strokeWidth = span * 0.030f
        canvas.drawLine(
            raceOut, raceTop + span * 0.10f, mouthX, mouthY + span * 0.10f, line
        )

        // L'eau : dans le coursier, puis en chute libre jusqu'aux augets.
        val flow = motor.units.coerceIn(GearMotorRules.MIN_UNITS, GearMotorRules.MAX_UNITS)
        line.color = WATER
        line.alpha = alpha
        line.strokeWidth = span * (0.020f + 0.0045f * flow)
        canvas.drawLine(
            raceOut, raceTop + span * 0.045f, mouthX, mouthY + span * 0.045f, line
        )
        line.color = WATER_DARK
        line.alpha = alpha
        canvas.drawLine(mouthX, mouthY + span * 0.03f, mouthX, hubY + span * 0.94f, line)

        // La roue elle-même : deux jantes, des rayons, et les augets entre les deux.
        line.color = TIMBER_DARK
        line.alpha = alpha
        line.strokeWidth = span * 0.045f
        canvas.drawCircle(mx, hubY, span, line)
        canvas.drawCircle(mx, hubY, inner, line)

        val buckets = (flow * 3 + 12).coerceAtMost(36)
        for (i in 0 until buckets) {
            val angle = phase + i * (2f * PI.toFloat() / buckets)
            val ux = cos(angle)
            val uy = sin(angle)
            // Un auget n'est pas une palette droite : c'est un godet, donc un fond
            // radial et une lèvre repliée dans le sens de la marche.
            val lip = -side * span * 0.13f
            path.reset()
            path.moveTo(mx + ux * inner, hubY + uy * inner)
            path.lineTo(mx + ux * span, hubY + uy * span)
            path.lineTo(mx + ux * span - uy * lip, hubY + uy * span + ux * lip)
            path.lineTo(mx + ux * inner - uy * lip * 0.55f, hubY + uy * inner + ux * lip * 0.55f)
            path.close()
            fill.color = if (i % 2 == 0) TIMBER else TIMBER_DARK
            fill.alpha = alpha
            canvas.drawPath(path, fill)
        }

        // Les rayons et le moyeu.
        line.color = TIMBER
        line.alpha = alpha
        line.strokeWidth = span * 0.028f
        for (i in 0 until 8) {
            val angle = phase + i * (2f * PI.toFloat() / 8)
            canvas.drawLine(
                mx + cos(angle) * span * 0.10f, hubY + sin(angle) * span * 0.10f,
                mx + cos(angle) * inner, hubY + sin(angle) * inner, line
            )
        }
        fill.color = IRON
        fill.alpha = alpha
        canvas.drawCircle(mx, hubY, span * 0.085f, fill)

        // Le bassin de fuite, si la roue plonge assez bas pour le mériter.
        val tail = hubY - span
        if (tail < 0.6f) {
            fill.color = WATER_DARK
            fill.alpha = (alpha * 0.55f).toInt()
            canvas.drawRect(
                mx - span * 1.15f, max(tail, -0.35f), mx + span * 1.15f, max(tail, -0.35f) + 0.30f, fill
            )
        }
    }

    // ── Le manège ─────────────────────────────────────────────────────────────

    /**
     * Une roue de cage, et les marcheurs dedans.
     *
     * Un carrousel tourne autour d'un axe **vertical**, et l'atelier se dessine dans un
     * plan vertical : on ne le verrait que par-dessus, c'est-à-dire pas du tout. La roue
     * de cage est la même machine — des bêtes qui marchent au bout d'un bras — montée
     * sur un axe horizontal, et c'est ainsi qu'étaient les grues de cathédrale.
     */
    private fun treadwheel(
        canvas: Canvas, mx: Float, hubY: Float, span: Float,
        motor: GearMotorConfig, phase: Float, alpha: Int
    ) {
        val inner = span * 0.80f

        // La charpente : deux jambes de force et une entretoise, derrière la roue.
        if (hubY > 0.4f) {
            line.color = TIMBER_DARK
            line.alpha = alpha
            line.strokeWidth = span * 0.070f
            val foot = span * 1.05f
            canvas.drawLine(mx - foot, 0f, mx, hubY, line)
            canvas.drawLine(mx + foot, 0f, mx, hubY, line)
            line.strokeWidth = span * 0.045f
            canvas.drawLine(
                mx - foot * 0.55f, hubY * 0.48f, mx + foot * 0.55f, hubY * 0.48f, line
            )
            fill.color = TIMBER_DARK
            fill.alpha = alpha
            canvas.drawRect(mx - foot * 1.15f, -0.22f, mx + foot * 1.15f, 0f, fill)
        }

        // Les deux jantes et les traverses : c'est sur elles qu'on marche.
        line.color = TIMBER
        line.alpha = alpha
        line.strokeWidth = span * 0.050f
        canvas.drawCircle(mx, hubY, span, line)
        canvas.drawCircle(mx, hubY, inner, line)
        line.color = TIMBER_DARK
        line.alpha = alpha
        line.strokeWidth = span * 0.028f
        val rungs = 26
        for (i in 0 until rungs) {
            val angle = phase + i * (2f * PI.toFloat() / rungs)
            canvas.drawLine(
                mx + cos(angle) * inner, hubY + sin(angle) * inner,
                mx + cos(angle) * span, hubY + sin(angle) * span, line
            )
        }
        // Les rayons, en croix de Saint-André comme une roue de charron.
        line.color = TIMBER
        line.alpha = alpha
        line.strokeWidth = span * 0.030f
        for (i in 0 until 6) {
            val angle = phase + i * (2f * PI.toFloat() / 6)
            canvas.drawLine(
                mx + cos(angle) * span * 0.10f, hubY + sin(angle) * span * 0.10f,
                mx + cos(angle) * inner, hubY + sin(angle) * inner, line
            )
        }
        fill.color = IRON
        fill.alpha = alpha
        canvas.drawCircle(mx, hubY, span * 0.085f, fill)

        // Les marcheurs. Ils restent en bas, debout, tête vers l'axe : c'est la roue
        // qui défile sous eux.
        //
        // **De quel côté ils marchent n'est pas un détail de dessin.** À la jante
        // basse, le tapis file dans le sens de la rotation ; pour rester sur place il
        // faut donc poser le pied **dans ce sens-là**, pas dans l'autre. Le sens par
        // défaut d'un moteur étant horaire, s'en remettre à une constante les faisait
        // marcher à reculons dans tous les cas.
        val lead = if (motor.direction < 0) -1f else 1f
        val walkers = motor.units.coerceIn(GearMotorRules.MIN_UNITS, GearMotorRules.MAX_UNITS)
        val height = (span * 0.34f).coerceIn(0.5f, 1.85f)
        val spread = (PI.toFloat() * 0.66f) / walkers
        for (i in 0 until walkers) {
            val place = -PI.toFloat() / 2f + (i - (walkers - 1) / 2f) * spread
            walker(canvas, mx, hubY, inner, place, height, phase * 3f + i * 1.7f, lead, alpha)
        }
    }

    /**
     * Une silhouette debout sur la jante, jambes animées par la marche.
     *
     * [lead] vaut +1 ou -1 : le sens dans lequel elle avance le long de la jante, celui
     * de la rotation. Elle penche dedans, le bras y pousse, et la jambe d'appui part
     * devant — trois signes qui disent la même chose, parce qu'un seul se perd à
     * distance.
     */
    private fun walker(
        canvas: Canvas, cx: Float, cy: Float, rim: Float,
        place: Float, height: Float, stride: Float, lead: Float, alpha: Int
    ) {
        val footX = cx + cos(place) * rim
        val footY = cy + sin(place) * rim
        // « Debout » veut dire la tête vers le centre de la roue.
        val ux = -cos(place)
        val uy = -sin(place)
        // Le long de la jante, dans le sens de la marche.
        val sx = -uy * lead
        val sy = ux * lead

        // Le corps penche dans le sens ou il pousse : c'est ce qui se lit de loin.
        val tiltX = sx * height * 0.13f
        val tiltY = sy * height * 0.13f
        val hipX = footX + ux * height * 0.46f + tiltX * 0.5f
        val hipY = footY + uy * height * 0.46f + tiltY * 0.5f
        val neckX = footX + ux * height * 0.80f + tiltX
        val neckY = footY + uy * height * 0.80f + tiltY

        line.alpha = alpha
        line.strokeWidth = max(0.035f, height * 0.115f)

        // Les jambes en ciseaux, l'appui devant : le balancement est decale pour que
        // la jambe avant morde plus loin que l'arriere ne recule.
        line.color = ROOF
        val swing = sin(stride) * height * 0.30f
        val front = height * 0.10f
        canvas.drawLine(
            hipX, hipY, footX + sx * (swing + front), footY + sy * (swing + front), line
        )
        canvas.drawLine(
            hipX, hipY, footX - sx * (swing - front * 0.4f), footY - sy * (swing - front * 0.4f), line
        )

        // Le torse, épais : c'est lui qui donne la silhouette à distance.
        line.color = CLOTH
        line.strokeWidth = max(0.055f, height * 0.20f)
        canvas.drawLine(hipX, hipY, neckX, neckY, line)

        // Le bras qui pousse la traverse devant soi.
        line.color = CLOTH
        line.strokeWidth = max(0.030f, height * 0.09f)
        canvas.drawLine(
            neckX, neckY,
            neckX + sx * height * 0.30f - ux * height * 0.16f,
            neckY + sy * height * 0.30f - uy * height * 0.16f, line
        )

        fill.color = SKIN
        fill.alpha = alpha
        canvas.drawCircle(
            neckX + ux * height * 0.10f, neckY + uy * height * 0.10f, height * 0.115f, fill
        )
    }
}
