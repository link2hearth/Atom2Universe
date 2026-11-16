package com.Atom2Universe.app.games.balance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * La réserve de Gros tas quand l'écran est couché : les quatre places en carré et les ronds de
 * vie, dans la bulle de l'écran. Le dessin vient de [pile] ; les gestes lui sont transmis en
 * coordonnées du monde, si bien qu'une pièce prise ici se tire jusque sur la grille.
 */
@SuppressLint("ViewConstructor")
class BigPileReserveView(context: Context, private val pile: BigPileView) : View(context) {

    private val dp = resources.displayMetrics.density
    private val gap = 10f * dp
    private val livesHeight = 34f * dp
    private val places = Array(BigPileRules.SLOTS) { RectF() }

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var holding = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cell = (w - gap) / 2f
        setMeasuredDimension(w, (2 * cell + gap + livesHeight).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cell = (w - gap) / 2f
        for (i in places.indices) {
            val col = i % 2
            val row = i / 2
            val x = col * (cell + gap)
            val y = row * (cell + gap)
            places[i].set(x, y, x + cell, y + cell)
        }
    }

    override fun onDraw(canvas: Canvas) {
        synchronized(pile.game) {
            for (i in places.indices) pile.drawSlot(canvas, i, places[i])
            pile.drawLivesRow(canvas, width / 2f, height - livesHeight / 2f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val game = pile.game
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slot = places.indexOfFirst { it.contains(event.x, event.y) }
                if (slot < 0) return false
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                moved = false
                // La bulle défile : elle ne doit pas prendre le geste qui emporte la pièce.
                parent?.requestDisallowInterceptTouchEvent(true)
                val (wx, wy) = world(event)
                holding = synchronized(game) { game.touchDown(wx, wy, slot) }
                if (!holding) return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!holding) return false
                if (!moved && hypot(event.x - downX, event.y - downY) > 10f * dp) moved = true
                if (moved) {
                    val (wx, wy) = world(event)
                    synchronized(game) { game.touchMove(wx, wy) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!holding) return false
                holding = false
                val tap = event.actionMasked == MotionEvent.ACTION_UP && !moved && event.eventTime - downTime < TAP_MS
                synchronized(game) { game.touchUp(tap) }
            }
        }
        pile.wake()
        invalidate()
        return true
    }

    /** Le doigt, en coordonnées du monde de la grille (il peut être hors de la bulle). */
    private fun world(event: MotionEvent): Pair<Float, Float> {
        val loc = IntArray(2)
        pile.getLocationOnScreen(loc)
        return pile.worldX(event.rawX - loc[0]) to pile.worldY(event.rawY - loc[1])
    }

    private companion object {
        const val TAP_MS = 300L
    }
}
