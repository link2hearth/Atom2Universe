package com.Atom2Universe.app.games.trebuchet.gears

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Le dessin du canon à pression : la manivelle qui pompe, les pistons qui montent et
 * descendent en rythme, le réservoir qu'ils remplissent, et le tube — gros, planté
 * sur son tourillon à distance fixe, qui seul pivote avec l'angle de tir.
 *
 * Rien ici n'est un corps physique — comme [GearMotorArt], c'est un décor accroché à
 * la manivelle qui compte vraiment. La manivelle elle-même (la roue dentée, ses
 * rayons, sa jante) reste dessinée par [GearMachineView.drawGear]. La disposition
 * suit le même repère que la physique du tir ([GearMachineRules.CANNON_PIVOT_DISTANCE]
 * / [GearMachineRules.CANNON_BARREL_LENGTH]) : le boulet doit partir exactement d'où
 * l'œil voit la bouche.
 */
class GearCannonArt(private val dp: Float) {

    private val path = Path()
    private val oval = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private companion object {
        val IRON = Color.rgb(96, 106, 122)
        val IRON_DARK = Color.rgb(60, 68, 82)
        val BRASS = Color.rgb(196, 158, 84)
        val BRASS_DARK = Color.rgb(148, 116, 58)
        val TANK = Color.rgb(126, 138, 148)
        val TANK_DARK = Color.rgb(88, 98, 108)
        val NEEDLE = Color.rgb(214, 68, 58)
        val FACE = Color.rgb(232, 227, 214)
        val STONE = Color.rgb(70, 76, 88)

        /**
         * Le haut de l'échelle du manomètre, en pascals -- un choix d'affichage, pas
         * une limite physique : la pression réelle plafonne toute seule, bien avant
         * ou bien après selon la machine et son réservoir.
         */
        const val GAUGE_MAX_PRESSURE = 4_000_000f

        /** Deux pistons, un quart de tour d'écart : jamais les deux en même temps en haut. */
        const val PISTON_COUNT = 2
        const val PISTON_PHASE = PI.toFloat() / 2f
    }

    /** Dessine le canon monté sur [gear], si c'est bien lui la pièce lanceur. */
    fun draw(canvas: Canvas, game: GearMachineGame, gear: GearMachineGame.GearState, alpha: Int) {
        if (gear.wheel.kind != GearWheelKind.PUMP) return
        val cx = gear.body.x
        val cy = gear.body.y
        val s = gear.wheel.outerRadius.coerceAtLeast(0.6f)
        val pivotDistance = GearMachineRules.CANNON_PIVOT_DISTANCE

        pistons(canvas, gear, cx, cy, s, pivotDistance, alpha)
        val tankScale = tankSizeFactor(gear.wheel.reservoirVolume)
        val tankTopY = tank(canvas, cx, cy, pivotDistance, tankScale, alpha)
        gauge(canvas, cx + pivotDistance * 0.62f, tankTopY, s * tankScale, game.launcherPressure(), alpha)
        cannon(canvas, game, gear, cx, cy, pivotDistance, alpha)
    }

    /**
     * Le facteur d'échelle du réservoir dessiné, tiré de son volume réel.
     *
     * En racine cubique, comme un vrai volume qui grandirait en gardant ses
     * proportions : le réservoir par défaut sert de référence, à l'échelle 1.
     */
    private fun tankSizeFactor(reservoirVolume: Float): Float =
        (reservoirVolume / GearMachineRules.DEFAULT_RESERVOIR_VOLUME).pow(1f / 3f).coerceIn(0.45f, 3f)

    /**
     * Les deux pistons : chacun monte et descend au bout d'une bielle accrochée à un
     * maneton sur la jante de la manivelle -- exactement ce qui la fait tourner en
     * pompe plutôt qu'en volant.
     *
     * Ils sont **postés à demeure**, jamais avec le train qu'ils habillent : seule
     * leur tige suit la manivelle, le bâti reste d'aplomb.
     */
    private fun pistons(
        canvas: Canvas, gear: GearMachineGame.GearState,
        cx: Float, cy: Float, s: Float, pivotDistance: Float, alpha: Int
    ) {
        val baseX = cx + pivotDistance * 0.20f
        val spacing = pivotDistance * 0.17f
        val cylHeight = cy * 0.55f
        val cylWidth = pivotDistance * 0.075f
        val amplitude = cylHeight * 0.26f
        val restY = cylHeight + amplitude * 1.15f
        val crankPinR = gear.wheel.pitchRadius * 0.72f

        for (i in 0 until PISTON_COUNT) {
            val px = baseX + i * spacing
            val phase = gear.body.angle + i * PISTON_PHASE
            val crossheadY = restY + sin(phase) * amplitude

            // Le cylindre : un bâti fixe, planté sur le sol comme la tour d'un moulin.
            fill.color = IRON_DARK
            fill.alpha = alpha
            canvas.drawRect(px - cylWidth / 2f, 0f, px + cylWidth / 2f, cylHeight, fill)
            fill.color = IRON
            fill.alpha = alpha
            canvas.drawRect(px - cylWidth / 2f, 0f, px - cylWidth * 0.1f, cylHeight, fill)
            line.strokeWidth = cylWidth * 0.16f
            line.color = BRASS_DARK
            line.alpha = alpha
            // Le presse-étoupe, en haut du cylindre : c'est de là que sort la tige.
            canvas.drawLine(px - cylWidth * 0.65f, cylHeight, px + cylWidth * 0.65f, cylHeight, line)

            // La bielle : du maneton de la manivelle jusqu'à la tête de la tige.
            val pinX = cx + cos(phase) * crankPinR
            val pinY = cy + sin(phase) * crankPinR
            line.strokeWidth = cylWidth * 0.30f
            line.color = IRON
            line.alpha = alpha
            canvas.drawLine(pinX, pinY, px, crossheadY, line)
            fill.color = BRASS
            fill.alpha = alpha
            canvas.drawCircle(pinX, pinY, cylWidth * 0.28f, fill)

            // La tige et sa tête, seules à bouger : c'est elles qui « montent et
            // descendent en rythme » d'un piston à l'autre, un quart de tour d'écart.
            line.strokeWidth = cylWidth * 0.22f
            line.color = IRON_DARK
            line.alpha = alpha
            canvas.drawLine(px, cylHeight, px, crossheadY, line)
            fill.color = IRON_DARK
            fill.alpha = alpha
            canvas.drawRect(
                px - cylWidth * 0.55f, crossheadY - cylWidth * 0.18f,
                px + cylWidth * 0.55f, crossheadY + cylWidth * 0.18f, fill
            )
        }
    }

    /**
     * Le réservoir, planté entre les pistons et le canon, avec sa coupole.
     *
     * Renvoie l'altitude du sommet de la coupole : c'est là que [gauge] vient se
     * poser, « sur sa tête » comme demandé.
     */
    private fun tank(
        canvas: Canvas, cx: Float, cy: Float, pivotDistance: Float, scale: Float, alpha: Int
    ): Float {
        val tx = cx + pivotDistance * 0.62f
        val radius = pivotDistance * 0.20f * scale
        val bodyTop = (cy * 0.92f * scale).coerceAtLeast(radius * 1.6f)

        // Le socle : une margelle de pierre, pour que la chaudière ait l'air posée
        // et non flottante.
        fill.color = STONE
        fill.alpha = alpha
        canvas.drawRect(tx - radius * 1.15f, 0f, tx + radius * 1.15f, radius * 0.22f, fill)

        // Le corps : un cylindre debout, ombré d'un côté pour qu'il se lise en volume.
        fill.color = TANK
        fill.alpha = alpha
        canvas.drawRect(tx - radius, radius * 0.22f, tx + radius, bodyTop, fill)
        fill.color = TANK_DARK
        fill.alpha = alpha
        canvas.drawRect(tx - radius, radius * 0.22f, tx - radius * 0.35f, bodyTop, fill)
        line.strokeWidth = radius * 0.10f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawLine(tx - radius, radius * 0.22f, tx - radius, bodyTop, line)
        canvas.drawLine(tx + radius, radius * 0.22f, tx + radius, bodyTop, line)

        // Deux cerclages, pour lire une chaudière plutôt qu'un tonneau.
        line.strokeWidth = radius * 0.12f
        line.color = IRON
        line.alpha = alpha
        for (t in floatArrayOf(0.35f, 0.72f)) {
            val y = radius * 0.22f + (bodyTop - radius * 0.22f) * t
            canvas.drawLine(tx - radius, y, tx + radius, y, line)
        }

        // La coupole, en dôme : le point d'accroche du manomètre.
        oval.set(tx - radius, bodyTop - radius, tx + radius, bodyTop + radius)
        fill.color = TANK
        fill.alpha = alpha
        canvas.drawArc(oval, 180f, 180f, true, fill)
        line.strokeWidth = radius * 0.08f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawArc(oval, 180f, 180f, false, line)

        return bodyTop + radius
    }

    /** Le manomètre, posé sur la coupole : une aiguille qui suit la pression. */
    private fun gauge(canvas: Canvas, gx: Float, gy: Float, s: Float, pressure: Float, alpha: Int) {
        val r = (s * 0.16f).coerceIn(0.12f, 0.55f)
        val cy = gy + r * 0.7f

        line.strokeWidth = r * 0.35f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawLine(gx, gy, gx, cy - r * 0.7f, line)

        fill.color = FACE
        fill.alpha = alpha
        canvas.drawCircle(gx, cy, r, fill)
        line.strokeWidth = r * 0.16f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawCircle(gx, cy, r, line)

        val fraction = (pressure / GAUGE_MAX_PRESSURE).coerceIn(0f, 1f)
        // Zéro à gauche-bas, plein à droite-bas : un balayage de deux cents degrés,
        // comme un vrai cadran de manomètre.
        val needleDeg = Math.toRadians((200f - 200f * fraction).toDouble()).toFloat()
        line.strokeWidth = r * 0.14f
        line.color = NEEDLE
        line.alpha = alpha
        canvas.drawLine(gx, cy, gx + cos(needleDeg) * r * 0.78f, cy + sin(needleDeg) * r * 0.78f, line)
        fill.color = IRON_DARK
        fill.alpha = alpha
        canvas.drawCircle(gx, cy, r * 0.14f, fill)
    }

    /**
     * Le canon : un tourillon fixe, planté après le réservoir, et un tube qui seul
     * s'élève avec l'angle de tir. Le calibre suit le poids du boulet chargé : un
     * caillou d'un kilo et un bloc de quatre-vingt-dix-neuf ne sortent pas du même tube.
     */
    private fun cannon(
        canvas: Canvas, game: GearMachineGame, gear: GearMachineGame.GearState,
        cx: Float, cy: Float, pivotDistance: Float, alpha: Int
    ) {
        val pivotX = cx + pivotDistance
        val pivotY = cy
        val aim = Math.toRadians(gear.wheel.launchAngle.toDouble()).toFloat()
        val nx = cos(aim)
        val ny = sin(aim)
        val length = GearMachineRules.CANNON_BARREL_LENGTH
        val ballRadius = game.config.projectileKind.radiusFor(game.config.shotMass)
        // Un « beau gros canon » ne doit jamais paraître fin, même pour le plus petit
        // caillou -- seul l'écart au calibre max se voit vraiment.
        val bore = 0.30f + ballRadius * 0.9f
        val wall = bore * 0.55f

        // L'affût : une jambe qui va du tourillon au sol, deux roues de carriage.
        line.strokeWidth = bore * 0.5f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawLine(pivotX, pivotY, pivotX - bore * 1.2f, 0f, line)
        canvas.drawLine(pivotX, pivotY, pivotX + bore * 1.6f, 0f, line)
        fill.color = IRON
        fill.alpha = alpha
        val wheelR = bore * 1.3f
        canvas.drawCircle(pivotX - bore * 0.6f, wheelR * 0.9f, wheelR, fill)
        canvas.drawCircle(pivotX + bore * 1.1f, wheelR * 0.9f, wheelR, fill)
        line.strokeWidth = wheelR * 0.16f
        line.color = IRON_DARK
        line.alpha = alpha
        canvas.drawCircle(pivotX - bore * 0.6f, wheelR * 0.9f, wheelR, line)
        canvas.drawCircle(pivotX + bore * 1.1f, wheelR * 0.9f, wheelR, line)

        // La culasse : un bloc large, planté sur le tourillon, plus épais que le tube.
        val breechLen = length * 0.16f
        val breechHalf = bore + wall * 1.6f
        path.reset()
        path.moveTo(pivotX - nx * breechLen * 0.4f - ny * breechHalf, pivotY - ny * breechLen * 0.4f + nx * breechHalf)
        path.lineTo(pivotX + nx * breechLen - ny * (bore + wall), pivotY + ny * breechLen + nx * (bore + wall))
        path.lineTo(pivotX + nx * breechLen + ny * (bore + wall), pivotY + ny * breechLen - nx * (bore + wall))
        path.lineTo(pivotX - nx * breechLen * 0.4f + ny * breechHalf, pivotY - ny * breechLen * 0.4f - nx * breechHalf)
        path.close()
        fill.color = BRASS_DARK
        fill.alpha = alpha
        canvas.drawPath(path, fill)

        // Le tube, en trapèze : plus large à la culasse, effilé vers la bouche.
        val tipHalf = bore + wall * 0.9f
        path.reset()
        path.moveTo(pivotX - ny * (bore + wall), pivotY + nx * (bore + wall))
        path.lineTo(pivotX + nx * length - ny * tipHalf, pivotY + ny * length + nx * tipHalf)
        path.lineTo(pivotX + nx * length + ny * tipHalf, pivotY + ny * length - nx * tipHalf)
        path.lineTo(pivotX + ny * (bore + wall), pivotY - nx * (bore + wall))
        path.close()
        fill.color = IRON
        fill.alpha = alpha
        canvas.drawPath(path, fill)
        // Le flanc éclairé, pour donner du volume au tube.
        path.reset()
        path.moveTo(pivotX - ny * (bore + wall) * 0.15f, pivotY + nx * (bore + wall) * 0.15f)
        path.lineTo(pivotX + nx * length - ny * tipHalf * 0.15f, pivotY + ny * length + nx * tipHalf * 0.15f)
        path.lineTo(pivotX + nx * length + ny * tipHalf, pivotY + ny * length - nx * tipHalf)
        path.lineTo(pivotX + ny * (bore + wall), pivotY - nx * (bore + wall))
        path.close()
        fill.color = IRON_DARK
        fill.alpha = alpha
        canvas.drawPath(path, fill)

        // Trois frettes de renfort, régulièrement espacées le long du tube.
        line.color = BRASS
        line.alpha = alpha
        for (t in floatArrayOf(0.30f, 0.52f, 0.74f)) {
            val bx = pivotX + nx * length * t
            val by = pivotY + ny * length * t
            val half = (bore + wall) * (1f - t * 0.25f)
            line.strokeWidth = half * 0.45f
            canvas.drawLine(bx - ny * half, by + nx * half, bx + ny * half, by - nx * half, line)
        }

        // La bouche : une frette large, pour finir le tube d'un trait net.
        val muzzleX = pivotX + nx * length
        val muzzleY = pivotY + ny * length
        line.strokeWidth = tipHalf * 0.7f
        line.color = BRASS
        line.alpha = alpha
        canvas.drawLine(
            muzzleX - nx * bore * 0.4f, muzzleY - ny * bore * 0.4f, muzzleX, muzzleY, line
        )
    }
}
