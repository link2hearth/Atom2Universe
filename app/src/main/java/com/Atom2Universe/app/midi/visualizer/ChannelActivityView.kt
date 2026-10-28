package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Petit indicateur d'activité d'un canal : cinq barres qui montent avec la force et le nombre
 * de notes jouées, puis retombent en douceur. Comme le clavier, il lit [MidiLiveState] à chaque
 * image au lieu de recevoir des événements.
 */
class ChannelActivityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), MidiFrameClock.Tickable {

    private companion object {
        // Forme de la « cloche » : les barres du milieu montent plus haut
        val PROFILE = floatArrayOf(0.55f, 0.85f, 1f, 0.8f, 0.5f)
        const val FALL_PER_SECOND = 3.2f
        const val IDLE_HEIGHT = 0.12f
    }

    private var channel = 0
    private var noteMin = 0
    private var noteMax = MidiLiveState.NOTES
    private var level = 0f
    // Hauteur visée, recalculée seulement quand les notes changent
    private var target = 0f
    private var seenNotesVersion = -1
    private var needsSync = true

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var lastFrameNanos = 0L

    fun bindChannel(channel: Int, minNote: Int, maxNote: Int, color: Int) {
        this.channel = channel
        noteMin = minNote.coerceIn(0, MidiLiveState.NOTES - 1)
        noteMax = maxNote.coerceIn(noteMin + 1, MidiLiveState.NOTES)
        paint.color = color
        level = 0f
        needsSync = true
        invalidate()
        MidiFrameClock.requestFrame()
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        MidiFrameClock.register(this)
    }

    override fun onDetachedFromWindow() {
        MidiFrameClock.unregister(this)
        super.onDetachedFromWindow()
    }

    // Application en arrière-plan : l'horloge s'arrête si plus personne n'est visible
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            lastFrameNanos = 0L
            MidiFrameClock.register(this)
        } else {
            MidiFrameClock.unregister(this)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            needsSync = true
            lastFrameNanos = 0L
            MidiFrameClock.requestFrame()
        }
    }

    override fun onFrame(frameTimeNanos: Long): Boolean {
        if (!isShown) {
            lastFrameNanos = 0L
            needsSync = true
            return false
        }
        val dt = if (lastFrameNanos == 0L) 0.016f
        else min((frameTimeNanos - lastFrameNanos) / 1_000_000_000f, 0.05f)
        lastFrameNanos = frameTimeNanos

        // Les notes ne sont relues que si elles ont changé
        val version = MidiLiveState.notesVersion()
        if (version != seenNotesVersion || needsSync) {
            seenNotesVersion = version
            needsSync = false
            var count = 0
            var maxVelocity = 0
            for (note in noteMin until noteMax) {
                val v = MidiLiveState.velocity(channel, note)
                if (v > 0) {
                    count++
                    if (v > maxVelocity) maxVelocity = v
                }
            }
            target = if (count == 0) 0f
            else ((0.35f + 0.13f * count).coerceAtMost(1f) * (0.4f + 0.6f * maxVelocity / 127f))
        }

        val next = if (target >= level) target else (level - FALL_PER_SECOND * dt).coerceAtLeast(target)
        if (next != level) {
            level = next
            invalidate()
        }
        // Encore en train de retomber : l'horloge doit continuer
        return level != target
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((22 * density).roundToInt(), widthMeasureSpec),
            resolveSize((16 * density).roundToInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val barWidth = width / (PROFILE.size * 2f - 1f)
        val radius = barWidth / 2f
        for (i in PROFILE.indices) {
            val fraction = IDLE_HEIGHT + (1f - IDLE_HEIGHT) * level * PROFILE[i]
            val barHeight = height * fraction
            val left = i * 2f * barWidth
            rect.set(left, (height - barHeight) / 2f, left + barWidth, (height + barHeight) / 2f)
            paint.alpha = (90 + 165 * level).roundToInt()
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
