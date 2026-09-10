package com.Atom2Universe.app.games.toyboxracers.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Direction tactile : un manche au repos fixe, mais dont le socle suit le doigt.
 *
 * Au repos, la silhouette reste posée en bas à gauche : le joueur sait toujours
 * où poser le pouce sans regarder. Dès que le doigt dépasse le débattement, le
 * SOCLE glisse pour rester à un rayon de lui, au lieu de laisser la commande
 * saturer puis retomber. C'est le point important : à fond dans un virage, un
 * pouce qui dérive ne coupe plus la direction d'un coup.
 *
 * Un appui qui tombe hors du manche le recentre sous le doigt, à zéro : poser le
 * pouce de travers ne doit pas donner un braquage complet qu'on n'a pas demandé.
 *
 * La vue ne connaît que l'écran : elle rend « à quelle distance vers la droite »
 * entre -1 et 1. C'est l'appelant qui traduit dans la convention du jeu.
 */
@SuppressLint("ViewConstructor")
internal class SteeringJoystickView(
    context: Context,
    private val baseRadius: Float,
    private val inset: Float
) : View(context) {

    /** -1 à gauche, +1 à droite, dans la convention de l'écran. */
    var onSteering: (Float) -> Unit = {}

    /** Le manche ignore les touchers quand la conduite n'a pas la main. */
    var isSteeringEnabled: () -> Boolean = { true }

    private val maxRadius = baseRadius * 0.86f
    private val knobRadius = baseRadius * 0.46f

    private var homeX = 0f
    private var homeY = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = baseRadius * 0.05f
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = baseRadius * 0.09f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val chevrons = Path()

    private val held get() = pointerId != MotionEvent.INVALID_POINTER_ID

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        homeX = inset + baseRadius
        homeY = h - inset - baseRadius
        if (!held) recentre()
    }

    /** Ramène la silhouette chez elle et coupe la direction. */
    fun reset() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        recentre()
        onSteering(0f)
        invalidate()
    }

    private fun recentre() {
        baseX = homeX
        baseY = homeY
        knobX = homeX
        knobY = homeY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSteeringEnabled()) {
            if (held) reset()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (held) return true
                val index = event.actionIndex
                pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                // Un appui hors du manche le déplace sous le doigt, à zéro. Le
                // rapprocher d'un coup à pleine butée serait un braquage subi.
                if (hypot(x - baseX, y - baseY) > baseRadius) {
                    baseX = x
                    baseY = y
                }
                moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) moveTo(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == pointerId) reset()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun moveTo(x: Float, y: Float) {
        var dx = x - baseX
        var dy = y - baseY
        val distance = hypot(dx, dy)
        if (distance > maxRadius) {
            // Le socle rattrape le doigt jusqu'à se retrouver à un débattement
            // de lui : c'est ce qui empêche la direction de lâcher quand le
            // pouce sort de la zone sans que le joueur l'ait voulu.
            val overshoot = (distance - maxRadius) / distance
            // Le socle reste dans la vue pour rester dessiné. La borne ne change
            // rien à la commande : elle est déjà en butée quand on l'atteint.
            baseX = (baseX + dx * overshoot).coerceIn(baseRadius, maxOf(baseRadius, width - baseRadius))
            baseY = (baseY + dy * overshoot).coerceIn(baseRadius, maxOf(baseRadius, height - baseRadius))
            dx = x - baseX
            dy = y - baseY
        }
        // La tête ne sort jamais de son cercle : le socle a déjà été poussé, et
        // la butée restante est une vraie butée de commande.
        val reach = hypot(dx, dy)
        val scale = if (reach > maxRadius) maxRadius / reach else 1f
        knobX = baseX + dx * scale
        knobY = baseY + dy * scale
        val value = ((knobX - baseX) / maxRadius).coerceIn(-1f, 1f)
        onSteering(if (abs(value) < DEADZONE) 0f else value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val active = held
        fill.color = if (active) 0x803B4055.toInt() else 0x4D3B4055
        canvas.drawCircle(baseX, baseY, baseRadius, fill)
        stroke.color = if (active) 0xAA8FA6C8.toInt() else 0x664B617A
        canvas.drawCircle(baseX, baseY, baseRadius, stroke)

        // Deux chevrons rappellent les anciens boutons : le manche ne sert qu'à
        // tourner, la vue ne doit pas laisser croire qu'il accélère aussi.
        hint.color = if (active) 0x88FFFFFF.toInt() else 0x55FFFFFF
        val wing = baseRadius * 0.17f
        val reach = baseRadius * 0.66f
        chevrons.reset()
        chevrons.moveTo(baseX - reach + wing, baseY - wing)
        chevrons.lineTo(baseX - reach, baseY)
        chevrons.lineTo(baseX - reach + wing, baseY + wing)
        chevrons.moveTo(baseX + reach - wing, baseY - wing)
        chevrons.lineTo(baseX + reach, baseY)
        chevrons.lineTo(baseX + reach - wing, baseY + wing)
        canvas.drawPath(chevrons, hint)

        fill.color = if (active) 0xE08FA6C8.toInt() else 0xB84B617A.toInt()
        canvas.drawCircle(knobX, knobY, knobRadius, fill)
        stroke.color = 0x66FFFFFF
        canvas.drawCircle(knobX, knobY, knobRadius, stroke)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val DEADZONE = 0.06f
    }
}
