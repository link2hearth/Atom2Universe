package com.Atom2Universe.app.games.flappycat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.edit
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

/** Simulation et rendu sur le fil UI : aucun état partagé entre deux boucles. */
class FlappyCatView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), Choreographer.FrameCallback {
    private enum class Phase { READY, RUNNING, PAUSED, OVER }
    internal data class Hazard(var x: Float, val y: Float, val wire: Boolean, val variant: Int, val seed: Float, var passed: Boolean = false) {
        fun center(time: Float) = y + if (wire) 0f else sin(time * 3f + seed) * 19f
    }
    internal data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    internal data class Cable(val left: Hazard, val right: Hazard) {
        fun x(t: Float) = left.x + (right.x - left.x) * t
        // Même courbe pour le dessin et les collisions, avec un léger affaissement.
        fun y(t: Float, top: Boolean) = left.y + (right.y - left.y) * t +
            (if (top) -94f else 94f) + 48f * t * (1f - t)

        fun touches(cx: Float, cy: Float): Boolean {
            if (cx + 20f < left.x || cx - 20f > right.x) return false
            for (top in listOf(true, false)) {
                for (i in 0 until 16) {
                    // Segment dans le repère de l'ellipse du chat, épaisseur du fil comprise.
                    val ax = (x(i / 16f) - cx) / 20f
                    val ay = (y(i / 16f, top) - cy) / 16f
                    val bx = (x((i + 1) / 16f) - cx) / 20f
                    val by = (y((i + 1) / 16f, top) - cy) / 16f
                    val dx = bx - ax; val dy = by - ay
                    val t = (-(ax * dx + ay * dy) / (dx * dx + dy * dy).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
                    if ((ax + t * dx).pow(2) + (ay + t * dy).pow(2) <= 1f) return true
                }
            }
            return false
        }
    }
    private val art = CatArt(context)
    private val prefs = context.getSharedPreferences("flappy_cat_save", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best_score", 0)
    private var phase = Phase.READY
    private var active = false
    private var scheduled = false
    private var lastFrame = 0L
    private var accumulator = 0f
    private var time = 0f
    private var distance = 0f
    private var elapsed = 0f
    private var catY = 280f
    private var velocity = 0f
    private var jump = 0f
    private var immune = 0f
    private var electric = 0f
    private var shake = 0f
    private var overTime = 0f
    private var hearts = 3
    private var score = 0
    private var distanceToNextWave = 120f
    private var wave = 0
    private var newBest = false
    private val hazards = mutableListOf<Hazard>()
    private val cables = mutableListOf<Cable>()
    private val sparks = mutableListOf<Spark>()
    private val step = 1f / 120f
    // Sommet à ~51 unités en 0,23 s : il faut entretenir le vol, sans effet de flottement.
    // L'impulsion reste fixe pour conserver une commande prévisible pendant l'accélération.
    private val jumpImpulse = 440f
    private val riseGravity = 1900f
    private val fallGravity = 2300f
    private val maxFallSpeed = 760f
    private val initialScrollSpeed = 190f
    private val secondsToMaxSpeed = 600f

    init { isClickable = true; isFocusable = true; contentDescription = context.getString(R.string.cat_controls) }

    fun resume() { active = true; lastFrame = 0L; schedule() }
    fun pause() {
        if (phase == Phase.RUNNING) phase = Phase.PAUSED
        active = false
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false; lastFrame = 0L; accumulator = 0f
    }
    fun togglePause() {
        phase = when (phase) { Phase.RUNNING -> Phase.PAUSED; Phase.PAUSED -> Phase.RUNNING; else -> phase }
        accumulator = 0f; invalidate(); schedule()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
    private fun schedule() {
        if (active && isAttachedToWindow && !scheduled) { scheduled = true; Choreographer.getInstance().postFrameCallback(this) }
    }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!active) return
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceAtMost(.067f)
        lastFrame = frameTimeNanos
        if (phase != Phase.PAUSED) {
            accumulator += dt
            while (accumulator >= step) { update(step); accumulator -= step }
        }
        invalidate(); schedule()
    }
    private fun start() {
        hazards.clear(); cables.clear(); sparks.clear(); catY = 280f; velocity = 0f
        elapsed = 0f; score = 0; hearts = 3; wave = 0; distanceToNextWave = 120f
        immune = 0f; electric = 0f; shake = 0f; newBest = false; overTime = 0f
        phase = Phase.RUNNING; flap()
    }
    private fun flap() { velocity = -jumpImpulse; jump = 1f; burst(98f, catY + 15f, 7, Color.rgb(255, 235, 182)) }
    override fun performClick(): Boolean {
        super.performClick()
        when (phase) {
            Phase.READY -> start()
            Phase.RUNNING -> flap()
            Phase.PAUSED -> { phase = Phase.RUNNING; accumulator = 0f }
            Phase.OVER -> if (overTime > .9f) start()
        }
        return true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { performClick(); return true }
        return true
    }
    private fun update(dt: Float) {
        time += dt
        jump = (jump - dt * 5f).coerceAtLeast(0f)
        immune = (immune - dt).coerceAtLeast(0f)
        electric = (electric - dt).coerceAtLeast(0f)
        shake = (shake - dt).coerceAtLeast(0f)
        sparks.removeAll { p -> p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 95f * dt; p.life <= 0f }
        if (phase == Phase.READY) { catY = 270f + sin(time * 2f) * 9f; distance += 22f * dt; return }
        if (phase == Phase.OVER) { overTime += dt; return }
        if (phase != Phase.RUNNING) return
        elapsed += dt
        // Ease-out quadratique : ×1,36 à 2 min, ×1,75 à 5 min, ×2 à 10 min.
        // Le temps de jeu exclut les pauses ; la pente rejoint zéro au plafond.
        val progress = (elapsed / secondsToMaxSpeed).coerceIn(0f, 1f)
        val speed = initialScrollSpeed * (1f + progress * (2f - progress))
        distance += speed * dt
        val gravity = if (velocity < 0f) riseGravity else fallGravity
        velocity = (velocity + gravity * dt).coerceAtMost(maxFallSpeed)
        catY += velocity * dt
        // Le haut du ciel repousse doucement : aucune collision invisible punitive.
        if (catY < 100f) { catY = 100f; velocity = 70f }
        if (catY > 478f) { catY = 478f; velocity = -jumpImpulse; hurt(false) }
        if (phase != Phase.RUNNING) return
        distanceToNextWave -= speed * dt
        if (distanceToNextWave <= 0f) spawnWave()
        // Déplacer tous les poteaux avant de tester leurs connexions.
        for (h in hazards) h.x -= speed * dt
        for (cable in cables) if (cable.touches(108f, catY)) hurt(true)
        if (phase != Phase.RUNNING) return
        for (h in hazards) {
            val hy = h.center(time)
            val hit = if (h.wire) abs(h.x - 108f) < 25f && (catY - 13f < hy - 94f || catY + 13f > hy + 94f)
                else ((h.x - 108f) / 37f).pow(2) + ((hy - catY) / 29f).pow(2) < 1f
            // Les poteaux provoquent un choc simple ; seuls les câbles électrocutent.
            if (hit) hurt(false)
            if (phase != Phase.RUNNING) break
            if (!h.passed && h.x < 62f) {
                h.passed = true; score++
                burst(110f, catY, 9, Color.rgb(255, 211, 117))
            }
        }
        cables.removeAll { it.right.x < -70f }
        // Garder le poteau gauche hors écran tant que son fil peut encore être visible.
        hazards.removeAll { it.x < -300f }
    }
    private fun spawnWave() {
        val progress = (elapsed / secondsToMaxSpeed).coerceIn(0f, 1f)
        val difficulty = progress * (2f - progress)
        val electrical = wave % 2 == 1
        // Les fils sont l'épreuve principale ; trois oiseaux espacés offrent une respiration.
        val count = if (electrical) 8 + (difficulty * 10f).toInt() else 3
        val spacing = if (electrical) 210f else 260f
        val heights = if (electrical) generateElectricalHeights(count, difficulty) else emptyList()
        val firstLane = Random.nextInt(5)
        var previousPole: Hazard? = null
        repeat(count) { index ->
            val y = if (electrical) {
                heights[index]
            } else {
                // La volée balaie cinq hauteurs, y compris le haut du ciel.
                125f + ((firstLane + index * 2) % 5) * 77f + Random.nextFloat() * 12f
            }
            val hazard = Hazard(475f + index * spacing, y, electrical,
                (wave + index) % 3, Random.nextFloat() * 6f)
            hazards += hazard
            if (electrical) {
                previousPole?.let { cables += Cable(it, hazard) }
                previousPole = hazard
            }
        }
        // Espacement en distance : l'accélération ne peut pas superposer deux séries.
        // Un peu plus d'espace à la transition permet de rejoindre le premier passage.
        distanceToNextWave += (count - 1) * spacing + 250f
        wave++
    }

    private fun generateElectricalHeights(count: Int, difficulty: Float): List<Float> {
        val heights = ArrayList<Float>(count)
        val minY = 195f
        val maxY = 365f
        val maxStep = 65f + difficulty * 80f
        var current = Random.nextFloat() * 110f + 220f
        var previousWasSharp = false
        heights += current
        while (heights.size < count) {
            // Choisir une destination, pas un côté selon la parité du poteau.
            // On peut poursuivre une pente avant de changer de direction.
            val canRise = current - minY >= 40f
            val canFall = maxY - current >= 40f
            val rising = canRise && (!canFall || Random.nextBoolean())
            val available = if (rising) current - minY else maxY - current
            val travel = 40f + Random.nextFloat() * (available - 40f).coerceAtLeast(0f)
            val target = current + if (rising) -travel else travel
            val sharp = !previousWasSharp && Random.nextFloat() < .28f
            val length = if (sharp) 1 else Random.nextInt(2, 5)
            val start = current
            val exponent = .7f + Random.nextFloat() * .9f
            for (i in 1..length) {
                if (heights.size == count) break
                val desired = start + (target - start) * (i.toFloat() / length).pow(exponent)
                current += (desired - current).coerceIn(-maxStep, maxStep)
                heights += current
            }
            previousWasSharp = sharp
            // Quelques replats irréguliers cassent le rythme des bosses et des vallées.
            if (Random.nextFloat() < .25f) {
                repeat(Random.nextInt(1, 3)) {
                    if (heights.size < count) {
                        current = (current + Random.nextFloat() * 12f - 6f).coerceIn(minY, maxY)
                        heights += current
                    }
                }
            }
        }
        return heights
    }

    private fun hurt(wire: Boolean) {
        if (immune > 0f || phase != Phase.RUNNING) return
        hearts--; immune = 1f; shake = .32f
        if (wire) electric = 1.1f
        burst(108f, catY, 25, if (wire) Color.CYAN else Color.rgb(255, 190, 129))
        if (hearts == 0) {
            phase = Phase.OVER; overTime = 0f
            newBest = score > best
            if (newBest) { best = score; prefs.edit { putInt("best_score", best) } }
            announceForAccessibility(context.getString(R.string.cat_result, score))
        }
    }
    private fun burst(x: Float, y: Float, count: Int, color: Int) {
        repeat(min(count, (160 - sparks.size).coerceAtLeast(0))) { val a = Random.nextFloat() * 6.283f; val speed = 30f + Random.nextFloat() * 100f
            sparks += Spark(x, y, cos(a) * speed, sin(a) * speed, .35f + Random.nextFloat() * .4f, color) }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(32, 35, 64))
        val scale = min(width / 420f, height / 560f)
        if (scale <= 0f) return
        canvas.save(); canvas.translate((width - 420f * scale) / 2f, (height - 560f * scale) / 2f); canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 420f, 560f)
        canvas.save()
        if (shake > 0f) canvas.translate(sin(time * 95f) * shake * 10f, cos(time * 77f) * shake * 6f)
        art.background(canvas, distance, time)
        for (cable in cables) if (cable.left.x < 440f && cable.right.x > -20f) art.cable(canvas, cable, time)
        for (h in hazards) if (h.x in -60f..480f) {
            if (h.wire) art.pole(canvas, h.x, h.y) else art.bird(canvas, h.x, h.center(time), time + h.seed, h.variant)
        }
        art.particles(canvas, sparks)
        art.cat(canvas, 108f, catY, time, velocity, jump, electric > 0f, immune > 0f)
        canvas.restore()
        art.hud(canvas, score, best, hearts)
        when (phase) {
            Phase.READY -> art.panel(canvas, R.string.flappy_cat_title, R.string.cat_intro, R.string.flappy_cat_tap_to_start)
            Phase.PAUSED -> art.panel(canvas, R.string.flappy_cat_paused, R.string.cat_controls, R.string.flappy_cat_tap_to_resume)
            Phase.OVER -> if (overTime > .8f) art.panel(canvas, if (newBest) R.string.flappy_cat_new_best else R.string.cat_ouch,
                R.string.cat_retry_hint, R.string.flappy_cat_tap_to_retry, context.getString(R.string.cat_result, score))
            else -> {}
        }
        canvas.restore()
    }
}
