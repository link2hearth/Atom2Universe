package com.Atom2Universe.app.games.hotpotato

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

/**
 * « Patate chaude » — la patate tombe sur la plaque de cuisson et rebondit.
 * On tape sous elle pour la renvoyer en l'air : le décalage horizontal du tap
 * donne l'angle. Il faut la cuire à point (zone verte de l'anneau) puis l'éjecter
 * par un des côtés de l'écran. Trop crue ou trop cuite = strike. 3 strikes = fin.
 */
class HotPotatoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // ── Canvas virtuel (plein écran, paysage ET portrait) ───────────────────────
    // En paysage on fixe la hauteur (REF_H_LANDSCAPE) et la largeur s'étire ;
    // en portrait on fixe la largeur (REF_W_PORTRAIT) et la hauteur s'étire.
    // Le tout est recalculé dans surfaceChanged pour remplir l'écran sans bandes.
    private val REF_H_LANDSCAPE = 460f
    private val REF_W_PORTRAIT = 420f
    private val PLATE_THICKNESS = 64f
    private var VW = 820f
    private var VH = 460f
    private var PLATE_TOP = VH - PLATE_THICKNESS
    private val PR = 30f

    // ── Physique ───────────────────────────────────────────────────────────────
    private val GRAVITY = 950f
    private val RESTITUTION = 0.52f
    private val MAX_VX = 900f

    // ── Rebond « lance-pierre » (on tire à l'opposé ; distance du tir = force) ────
    private val MIN_IMPULSE = 440f           // simple tap : petit rebond
    private val MAX_IMPULSE = 900f           // tir tendu : plafond
    private val POWER_PER_DRAG = 2.0f        // gain de force par px de tir (virtuels)
    private val MAX_DRAG = 230f              // distance de tir au-delà de laquelle c'est plafonné
    private val TAP_THRESHOLD = 22f          // en dessous = simple tap → rebond vertical
    private val MIN_UP_RATIO = 0.30f         // composante verticale minimale (toujours décoller)

    // ── Cuisson ────────────────────────────────────────────────────────────────
    private val COOK_SPEED = 0.185f          // vitesse à pleine chaleur (/s)
    private val HEAT_RANGE = 300f            // hauteur au-dessus de la plaque où la chaleur agit
    private val COOK_MIN = 0.60f             // début de la zone « cuit à point »
    private val COOK_MAX = 0.80f             // fin de la zone
    private val BURN_LIMIT = 1.5f            // au-delà : carbonisée, perdue
    private val HOTSPOT_MULT = 2.0f

    private val MAX_STRIKES = 3

    // ── Progression / apparition des patates ─────────────────────────────────────
    private val SOLO_COUNT = 10              // patates 1..10 : une seule à la fois
    private val PHASE2_COUNT = 10            // patates 11..20 : une nouvelle toutes les SECONDS_1
    private val SECONDS_PER_POTATO_1 = 5f    // cadence patates 11..20
    private val SECONDS_PER_POTATO_2 = 4f    // cadence à partir de la 21e, jusqu'au game over
    private val SOLO_RESPAWN_DELAY = 0.8f    // petit répit entre deux patates en phase solo

    // ── État ───────────────────────────────────────────────────────────────────
    private enum class Phase { READY, RUNNING, PAUSED, GAME_OVER }
    private var phase = Phase.READY

    private class Potato(var x: Float, var y: Float) {
        var vx = 0f
        var vy = 0f
        var cook = 0f
        var spin = 0f
        var rot = 0f
        var squash = 0f          // >0 aplatie (impact), <0 étirée (lancer)
        var eyesClosed = 0f      // timer yeux fermés (rebond)
        var onPlate = false
        val toppings = HashSet<Topping>()
    }

    private enum class Topping { CREAM, BACON, LETTUCE }

    private class FallingTopping(var x: Float, var y: Float, val type: Topping) {
        var vy = 70f
        var wobble = Random.nextFloat() * 6.28f
    }

    private class Ember(var x: Float, var y: Float, var vx: Float, var vy: Float) {
        var life = 0f
    }

    private class HotSpot(var phase: Float, val baseX: Float, val span: Float)

    private class FloatText(var x: Float, var y: Float, val text: String, val color: Int) {
        var life = 1.1f
    }

    /** Tir de lance-pierre en cours : patate visée + point de départ et point courant du doigt. */
    private class Aim(val potato: Potato, val startX: Float, val startY: Float) {
        var curX = startX
        var curY = startY
    }

    private val potatoes = mutableListOf<Potato>()
    private val fallingToppings = mutableListOf<FallingTopping>()
    private val embers = mutableListOf<Ember>()
    private val hotSpots = mutableListOf<HotSpot>()
    private val floatTexts = mutableListOf<FloatText>()

    private var elapsed = 0f
    private var score = 0
    private var bestScore = 0
    private var newBest = false
    private var strikes = 0

    private var potatoesSpawned = 0
    private var spawnTimer = 0f
    private var toppingTimer = 2.5f
    private var emberTimer = 0f

    // ── Letterbox ───────────────────────────────────────────────────────────────
    private var scaleX = 1f
    private var scaleY = 1f
    private var offX = 0f
    private var offY = 0f

    // ── Thread ───────────────────────────────────────────────────────────────────
    private var gameThread: Thread? = null
    @Volatile private var running = false
    private var hasSurface = false
    private var resumed = false

    // ── Entrée tactile (lance-pierre) ───────────────────────────────────────────────
    private val aims = HashMap<Int, Aim>()   // pointerId → tir en cours

    private val prefs by lazy { context.getSharedPreferences("hot_potato_save", Context.MODE_PRIVATE) }

    // ── Paints ────────────────────────────────────────────────────────────────────
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#33000000")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val overlayBg = Paint().apply { color = Color.parseColor("#CC1a0f08") }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 2f, 2f, Color.parseColor("#99000000"))
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0E6D2")
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#99000000"))
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 2f, 2f, Color.parseColor("#AA000000"))
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        bestScore = prefs.getInt("best_score", 0)
    }

    // ── Surface ────────────────────────────────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        hasSurface = true
        ensureThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width >= height) {
            // Paysage : hauteur de référence fixe, largeur étirée à l'écran.
            VH = REF_H_LANDSCAPE
            scaleY = height / VH; scaleX = scaleY
            VW = width / scaleX
        } else {
            // Portrait : largeur de référence fixe, hauteur étirée à l'écran.
            VW = REF_W_PORTRAIT
            scaleX = width / VW; scaleY = scaleX
            VH = height / scaleY
        }
        PLATE_TOP = VH - PLATE_THICKNESS
        offX = 0f
        offY = 0f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
        stopThread()
    }

    /** Démarre l'unique thread de rendu, seulement si surface + reprise actives et pas déjà vivant. */
    private fun ensureThread() {
        if (hasSurface && resumed && gameThread?.isAlive != true) {
            running = true
            gameThread = Thread(this, "HotPotatoThread").apply { start() }
        }
    }

    private fun stopThread() {
        running = false
        try { gameThread?.join(500) } catch (_: InterruptedException) {}
        gameThread = null
    }

    // ── Contrôles publics ─────────────────────────────────────────────────────────
    fun togglePause() {
        phase = when (phase) {
            Phase.RUNNING -> Phase.PAUSED
            Phase.PAUSED -> Phase.RUNNING
            else -> phase
        }
    }

    fun pause() {
        if (phase == Phase.RUNNING) phase = Phase.PAUSED
        resumed = false
        stopThread()
    }

    fun resume() {
        resumed = true
        ensureThread()
    }

    // ── Boucle ──────────────────────────────────────────────────────────────────────
    override fun run() {
        var lastNano = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNano = now

            if (phase == Phase.RUNNING) update(dt)

            val c = holder.lockCanvas() ?: continue
            try { drawFrame(c) } finally { holder.unlockCanvasAndPost(c) }

            val remainMs = 16L - (System.nanoTime() - now) / 1_000_000L
            if (remainMs > 0) try { Thread.sleep(remainMs) } catch (_: InterruptedException) {}
        }
    }

    // ── Mise à jour ───────────────────────────────────────────────────────────────
    private fun update(dt: Float) {
        elapsed += dt
        updateDifficulty(dt)
        updatePotatoes(dt)
        updateToppings(dt)
        updateEmbers(dt)
        updateHotSpots(dt)
        floatTexts.removeAll { it.y -= 34f * dt; it.life -= dt; it.life <= 0f }
    }

    private fun updateDifficulty(dt: Float) {
        spawnTimer -= dt
        if (potatoesSpawned < SOLO_COUNT) {
            // Phase solo : une seule patate à la fois (1..10)
            if (potatoes.isEmpty() && spawnTimer <= 0f) {
                spawnPotato()
                // En spawnant la 10e, amorce la cadence pour que la 11e attende l'intervalle
                // au lieu d'apparaître aussitôt (sinon 10e et 11e tomberaient ensemble).
                if (potatoesSpawned >= SOLO_COUNT) spawnTimer = SECONDS_PER_POTATO_1
            }
        } else {
            // Phase cadencée : une nouvelle patate à intervalle régulier
            // (ou immédiatement si l'écran se vide). 5 s/patate pour 11..20, puis 4 s.
            if (spawnTimer <= 0f || potatoes.isEmpty()) {
                spawnPotato()
                spawnTimer = if (potatoesSpawned < SOLO_COUNT + PHASE2_COUNT)
                    SECONDS_PER_POTATO_1 else SECONDS_PER_POTATO_2
            }
        }

        // Obstacles seulement une fois la phase d'apprentissage passée.
        if (potatoesSpawned >= SOLO_COUNT) {
            val wantHotSpots = if (potatoesSpawned >= SOLO_COUNT + PHASE2_COUNT) 2 else 1
            while (hotSpots.size < wantHotSpots) {
                hotSpots += HotSpot(
                    phase = Random.nextFloat() * 6.28f,
                    baseX = VW * (0.3f + Random.nextFloat() * 0.4f),
                    span = 70f + Random.nextFloat() * 40f
                )
            }
        }
        // Braises : à partir de la phase à 4 s/patate.
        if (potatoesSpawned >= SOLO_COUNT + PHASE2_COUNT) {
            emberTimer -= dt
            if (emberTimer <= 0f) {
                spawnEmber()
                emberTimer = 4.5f
            }
        }
    }

    private fun spawnPotato() {
        val x = Random.nextFloat() * (VW - 160f) + 80f
        synchronized(potatoes) { potatoes += Potato(x, -PR) }
        potatoesSpawned++
    }

    /** Petit répit avant la patate suivante, uniquement en phase solo (1 patate à la fois). */
    private fun scheduleSoloGap() {
        if (potatoesSpawned < SOLO_COUNT) spawnTimer = SOLO_RESPAWN_DELAY
    }

    private fun spawnEmber() {
        val fromLeft = Random.nextBoolean()
        val y = 140f + Random.nextFloat() * (PLATE_TOP - 240f)
        val speed = 180f + Random.nextFloat() * 90f
        embers += Ember(
            x = if (fromLeft) -20f else VW + 20f,
            y = y,
            vx = if (fromLeft) speed else -speed,
            vy = -40f + Random.nextFloat() * 80f
        )
    }

    private fun updatePotatoes(dt: Float) = synchronized(potatoes) {
        val iter = potatoes.iterator()
        while (iter.hasNext()) {
            val p = iter.next()

            p.vy += GRAVITY * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= (1f - dt * 0.35f)          // léger frottement de l'air horizontal

            // Rotation / squash / yeux
            p.rot += p.spin * dt
            p.spin *= (1f - dt * 1.2f)
            p.squash += (0f - p.squash) * (dt * 7f)
            if (p.eyesClosed > 0f) p.eyesClosed -= dt

            // Rebond sur la plaque
            p.onPlate = false
            if (p.y + PR >= PLATE_TOP && p.vy > 0f) {
                p.y = PLATE_TOP - PR
                if (p.vy > 60f) {
                    p.vy = -p.vy * RESTITUTION
                    p.squash = 0.9f
                    p.cook += 0.03f             // saisie au contact
                } else {
                    p.vy = 0f
                    p.onPlate = true
                }
                p.spin *= 0.4f
            }

            // Cuisson par proximité
            val gap = (PLATE_TOP - (p.y + PR)).coerceAtLeast(0f)
            var heat = (1f - gap / HEAT_RANGE).coerceIn(0.12f, 1f)
            if (overHotSpot(p.x) && gap < 90f) heat *= HOTSPOT_MULT
            p.cook += COOK_SPEED * heat * dt

            // Carbonisée : perdue
            if (p.cook >= BURN_LIMIT) {
                addFloat(p.x, p.y, context.getString(R.string.hot_potato_burnt), 0xFFFF5A3C.toInt())
                registerStrike()
                iter.remove()
                scheduleSoloGap()
                continue
            }

            // Sortie par un côté : évaluation
            if (p.x - PR > VW || p.x + PR < 0f) {
                evaluateExit(p)
                iter.remove()
                scheduleSoloGap()
                continue
            }
        }
    }

    private fun evaluateExit(p: Potato) {
        if (p.cook in COOK_MIN..COOK_MAX) {
            val mult = 1f + 0.5f * p.toppings.size
            val pts = (100 * mult).roundToInt()
            score += pts
            val label = context.getString(R.string.hot_potato_perfect) + "  +$pts"
            addFloat((p.x).coerceIn(40f, VW - 40f), p.y, label, 0xFF66E08A.toInt())
        } else {
            val msg = if (p.cook < COOK_MIN) R.string.hot_potato_raw else R.string.hot_potato_burnt
            addFloat((p.x).coerceIn(40f, VW - 40f), p.y, context.getString(msg), 0xFFFFB14A.toInt())
            registerStrike()
        }
    }

    private fun registerStrike() {
        strikes++
        if (strikes >= MAX_STRIKES) triggerGameOver()
    }

    private fun updateToppings(dt: Float) {
        toppingTimer -= dt
        if (toppingTimer <= 0f) {
            fallingToppings += FallingTopping(
                x = 50f + Random.nextFloat() * (VW - 100f),
                y = -20f,
                type = Topping.entries[Random.nextInt(Topping.entries.size)]
            )
            toppingTimer = 2.2f + Random.nextFloat() * 2.5f
        }
        val iter = fallingToppings.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            t.wobble += dt * 3f
            t.x += sin(t.wobble) * 22f * dt
            t.y += t.vy * dt
            if (t.y > VH + 30f) { iter.remove(); continue }
            // Collision patate
            var stuck = false
            for (p in potatoes) {
                if (hypot(p.x - t.x, p.y - t.y) < PR + 12f) {
                    if (p.toppings.add(t.type)) {
                        addFloat(t.x, t.y - 16f, "+", 0xFFFFFFFF.toInt())
                    }
                    stuck = true
                    break
                }
            }
            if (stuck) iter.remove()
        }
    }

    private fun updateEmbers(dt: Float) {
        val iter = embers.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.vy += 60f * dt
            e.x += e.vx * dt
            e.y += e.vy * dt
            e.life += dt
            if (e.x < -40f || e.x > VW + 40f || e.y > VH + 40f) { iter.remove(); continue }
            for (p in potatoes) {
                if (hypot(p.x - e.x, p.y - e.y) < PR + 8f) {
                    p.cook += 0.32f
                    addFloat(p.x, p.y - PR, "🔥", 0xFFFF6A2A.toInt())
                    iter.remove()
                    break
                }
            }
        }
    }

    private fun updateHotSpots(dt: Float) {
        for (h in hotSpots) h.phase += dt * 0.6f
    }

    private fun overHotSpot(x: Float): Boolean {
        for (h in hotSpots) {
            val cx = h.baseX + sin(h.phase) * 90f
            if (abs(x - cx) < h.span * 0.5f) return true
        }
        return false
    }

    private fun addFloat(x: Float, y: Float, text: String, color: Int) {
        floatTexts += FloatText(x, y, text, color)
    }

    private fun triggerGameOver() {
        if (score > bestScore) {
            bestScore = score
            newBest = true
            prefs.edit { putInt("best_score", bestScore) }
        }
        phase = Phase.GAME_OVER
    }

    private fun startGame() {
        potatoes.clear(); fallingToppings.clear(); embers.clear()
        hotSpots.clear(); floatTexts.clear()
        elapsed = 0f; score = 0; strikes = 0; newBest = false
        potatoesSpawned = 0; spawnTimer = 0f; toppingTimer = 2.5f; emberTimer = 0f
        spawnPotato()
        phase = Phase.RUNNING
    }

    // ── Entrée (lance-pierre) ───────────────────────────────────────────────────────
    private fun toVx(sx: Float) = (sx - offX) / scaleX
    private fun toVy(sy: Float) = (sy - offY) / scaleY

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (phase != Phase.RUNNING) {
                    when (phase) {
                        Phase.READY -> startGame()
                        Phase.PAUSED -> phase = Phase.RUNNING
                        Phase.GAME_OVER -> phase = Phase.READY
                        else -> {}
                    }
                    return true
                }
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val tx = toVx(event.getX(idx))
                val ty = toVy(event.getY(idx))
                findPotatoUnder(tx, ty)?.let { p ->
                    synchronized(aims) { aims[pid] = Aim(p, tx, ty) }
                }
            }

            MotionEvent.ACTION_MOVE -> synchronized(aims) {
                if (aims.isNotEmpty()) {
                    for (i in 0 until event.pointerCount) {
                        val a = aims[event.getPointerId(i)] ?: continue
                        a.curX = toVx(event.getX(i)); a.curY = toVy(event.getY(i))
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val a = synchronized(aims) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val r = aims[pid]; aims.clear(); r
                    } else {
                        aims.remove(pid)
                    }
                }
                if (a != null && phase == Phase.RUNNING) {
                    applySling(a.potato, toVx(event.getX(idx)) - a.startX, toVy(event.getY(idx)) - a.startY)
                }
            }

            MotionEvent.ACTION_CANCEL -> synchronized(aims) { aims.clear() }
        }
        return true
    }

    /** Patate la plus proche horizontalement, située au-dessus du point touché. */
    private fun findPotatoUnder(tx: Float, ty: Float): Potato? {
        var best: Potato? = null
        var bestDx = Float.MAX_VALUE
        synchronized(potatoes) {
            for (p in potatoes) {
                val dx = abs(tx - p.x)
                if (ty > p.y - 16f && ty - p.y < 240f && dx < PR + 80f && dx < bestDx) {
                    best = p; bestDx = dx
                }
            }
        }
        return best
    }

    /**
     * Vitesse de lancement pour un tir de lance-pierre : on part à l'opposé du
     * vecteur de tir (drag = end − start), avec une force ∝ longueur du tir
     * (plancher pour un simple tap, plafond au-delà de [MAX_DRAG]) et toujours
     * une composante vers le haut.
     */
    private fun computeLaunch(dragX: Float, dragY: Float): FloatArray {
        val dist = hypot(dragX, dragY)
        val power = (MIN_IMPULSE + POWER_PER_DRAG * dist.coerceAtMost(MAX_DRAG)).coerceAtMost(MAX_IMPULSE)
        var dirX: Float
        var dirY: Float
        if (dist < TAP_THRESHOLD) {
            dirX = 0f; dirY = -1f
        } else {
            dirX = -dragX / dist
            dirY = -dragY / dist
            if (dirY > -MIN_UP_RATIO) dirY = -MIN_UP_RATIO   // jamais vers le bas
            val n = hypot(dirX, dirY); dirX /= n; dirY /= n
        }
        return floatArrayOf((dirX * power).coerceIn(-MAX_VX, MAX_VX), dirY * power)
    }

    private fun applySling(p: Potato, dragX: Float, dragY: Float) {
        val l = computeLaunch(dragX, dragY)
        p.vx = l[0]
        p.vy = l[1]
        p.spin = p.vx * 0.018f
        p.eyesClosed = 0.3f
        p.squash = -0.55f
    }

    // ── Rendu ──────────────────────────────────────────────────────────────────────
    private fun drawFrame(canvas: Canvas) {
        // Efface tout le buffer physique (bandes letterbox comprises) pour éviter
        // les rémanences d'une frame sur l'autre.
        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scaleX, scaleY)

        drawBackground(canvas)
        drawPlate(canvas)
        for (t in fallingToppings) drawFallingTopping(canvas, t)
        for (p in potatoes) drawPotato(canvas, p)
        drawAimPreviews(canvas)
        for (e in embers) drawEmber(canvas, e)
        drawFloatTexts(canvas)
        drawHud(canvas)

        when (phase) {
            Phase.READY -> drawOverlay(
                canvas,
                context.getString(R.string.hot_potato_title),
                context.getString(R.string.hot_potato_tap_to_start) + "\n" +
                    context.getString(R.string.hot_potato_how_to),
                false
            )
            Phase.PAUSED -> drawOverlay(
                canvas,
                context.getString(R.string.hot_potato_paused),
                context.getString(R.string.hot_potato_tap_to_resume),
                false
            )
            Phase.GAME_OVER -> drawOverlay(
                canvas,
                context.getString(R.string.hot_potato_game_over),
                buildGameOverMsg(),
                newBest
            )
            else -> {}
        }
        canvas.restore()
    }

    private fun buildGameOverMsg(): String {
        val retry = context.getString(R.string.hot_potato_tap_to_retry)
        return if (newBest) {
            "★ ${context.getString(R.string.hot_potato_new_best)} ★\n$score\n$retry"
        } else {
            "${context.getString(R.string.hot_potato_score_label)}: $score\n$retry"
        }
    }

    private fun drawBackground(canvas: Canvas) {
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, VH,
            intArrayOf(0xFF2A1A10.toInt(), 0xFF4A2C18.toInt(), 0xFF6E3E1E.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, VW, VH, fillPaint)
        fillPaint.shader = null
    }

    private fun drawPlate(canvas: Canvas) {
        // Lueur des points chauds (sous la surface)
        for (h in hotSpots) {
            val cx = h.baseX + sin(h.phase) * 90f
            val pulse = 0.6f + 0.4f * sin(h.phase * 3f)
            fillPaint.shader = RadialGradient(
                cx, PLATE_TOP, h.span,
                Color.argb((150 * pulse).toInt(), 255, 140, 40), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, PLATE_TOP, h.span, fillPaint)
            fillPaint.shader = null
        }
        // Corps de la plaque
        fillPaint.color = 0xFF1C1C22.toInt()
        canvas.drawRect(0f, PLATE_TOP, VW, VH, fillPaint)
        // Surface chauffée (dégradé rouge)
        fillPaint.shader = LinearGradient(
            0f, PLATE_TOP, 0f, PLATE_TOP + 14f,
            0xFFFF7A2A.toInt(), 0xFF1C1C22.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, PLATE_TOP, VW, PLATE_TOP + 14f, fillPaint)
        fillPaint.shader = null
        // Liseré incandescent
        strokePaint.color = 0xFFFFA64D.toInt()
        strokePaint.strokeWidth = 3f
        canvas.drawLine(0f, PLATE_TOP, VW, PLATE_TOP, strokePaint)
    }

    private fun drawPotato(canvas: Canvas, p: Potato) {
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
        val sx = 1f + 0.28f * p.squash
        val sy = 1f - 0.28f * p.squash
        canvas.scale(sx, sy)

        // Ombre douce
        fillPaint.color = 0x33000000
        canvas.drawOval(RectF(-PR * 1.02f, -PR * 0.86f + 4f, PR * 1.02f, PR * 0.94f + 4f), fillPaint)

        // Corps
        fillPaint.color = potatoColor(p.cook)
        canvas.drawOval(RectF(-PR * 1.02f, -PR * 0.86f, PR * 1.02f, PR * 0.94f), fillPaint)
        // Reflet
        fillPaint.color = 0x33FFFFFF
        canvas.drawOval(RectF(-PR * 0.6f, -PR * 0.62f, -PR * 0.05f, -PR * 0.12f), fillPaint)
        // Petites taches
        fillPaint.color = (0x55000000.toInt() or (potatoColor(p.cook) and 0x00FFFFFF))
        canvas.drawCircle(PR * 0.45f, PR * 0.2f, 2.6f, fillPaint)
        canvas.drawCircle(-PR * 0.2f, PR * 0.5f, 2.2f, fillPaint)

        drawToppings(canvas, p)
        drawFace(canvas, p)
        canvas.restore()

        // Anneau de cuisson (hors transformation pour rester circulaire)
        drawCookRing(canvas, p)
    }

    private fun drawToppings(canvas: Canvas, p: Potato) {
        if (Topping.BACON in p.toppings) {
            fillPaint.color = 0xFFB5462E.toInt()
            val rect = RectF(-PR * 0.85f, -PR * 0.1f, PR * 0.85f, PR * 0.18f)
            canvas.drawRoundRect(rect, 5f, 5f, fillPaint)
            fillPaint.color = 0xFFE8A38C.toInt()
            canvas.drawRect(-PR * 0.85f, -PR * 0.02f, PR * 0.85f, PR * 0.04f, fillPaint)
        }
        if (Topping.LETTUCE in p.toppings) {
            fillPaint.color = 0xFF6FBF3B.toInt()
            for (i in -1..1) {
                canvas.drawCircle(i * PR * 0.4f, -PR * 0.55f, PR * 0.26f, fillPaint)
            }
        }
        if (Topping.CREAM in p.toppings) {
            fillPaint.color = 0xFFFFFBF0.toInt()
            val path = Path()
            path.moveTo(-PR * 0.5f, -PR * 0.7f)
            path.cubicTo(-PR * 0.3f, -PR * 1.15f, PR * 0.3f, -PR * 1.15f, PR * 0.5f, -PR * 0.7f)
            path.cubicTo(PR * 0.2f, -PR * 0.78f, -PR * 0.2f, -PR * 0.78f, -PR * 0.5f, -PR * 0.7f)
            path.close()
            canvas.drawPath(path, fillPaint)
            canvas.drawCircle(0f, -PR * 0.95f, PR * 0.14f, fillPaint)
        }
    }

    private fun drawFace(canvas: Canvas, p: Potato) {
        val eyeY = -PR * 0.12f
        val eyeDx = PR * 0.36f
        val overcook = ((p.cook - COOK_MAX) / (BURN_LIMIT - COOK_MAX)).coerceIn(0f, 1f)

        // Joues
        fillPaint.color = 0x44FF6E6E
        canvas.drawCircle(-eyeDx - PR * 0.12f, eyeY + PR * 0.28f, PR * 0.16f, fillPaint)
        canvas.drawCircle(eyeDx + PR * 0.12f, eyeY + PR * 0.28f, PR * 0.16f, fillPaint)

        if (p.eyesClosed > 0f) {
            // Yeux fermés (arcs joyeux)
            strokePaint.color = Color.BLACK
            strokePaint.strokeWidth = 2.4f
            strokePaint.strokeCap = Paint.Cap.ROUND
            for (s in intArrayOf(-1, 1)) {
                val cx = s * eyeDx
                canvas.drawArc(cx - 6f, eyeY - 6f, cx + 6f, eyeY + 6f, 200f, 140f, false, strokePaint)
            }
        } else {
            // Yeux ouverts
            fillPaint.color = Color.WHITE
            canvas.drawCircle(-eyeDx, eyeY, PR * 0.2f, fillPaint)
            canvas.drawCircle(eyeDx, eyeY, PR * 0.2f, fillPaint)
            fillPaint.color = Color.BLACK
            val look = (overcook * 1.5f)
            canvas.drawCircle(-eyeDx, eyeY + look, PR * 0.1f, fillPaint)
            canvas.drawCircle(eyeDx, eyeY + look, PR * 0.1f, fillPaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(-eyeDx + 1.5f, eyeY - 1.5f, PR * 0.03f, fillPaint)
            canvas.drawCircle(eyeDx + 1.5f, eyeY - 1.5f, PR * 0.03f, fillPaint)
        }

        // Bouche
        strokePaint.color = Color.BLACK
        strokePaint.strokeWidth = 2.4f
        strokePaint.strokeCap = Paint.Cap.ROUND
        val mouthY = PR * 0.42f
        val inBand = p.cook in COOK_MIN..COOK_MAX
        when {
            p.eyesClosed > 0f -> {            // surprise au rebond : petit « o »
                fillPaint.color = 0xFF7A2E2E.toInt()
                canvas.drawCircle(0f, mouthY, PR * 0.1f, fillPaint)
            }
            overcook > 0.35f -> {             // inquiète : bouche ouverte qui grandit
                fillPaint.color = 0xFF5A1E1E.toInt()
                val w = PR * (0.16f + 0.18f * overcook)
                canvas.drawOval(RectF(-w, mouthY - w * 0.7f, w, mouthY + w), fillPaint)
            }
            inBand -> {                       // cuit à point : grand sourire
                canvas.drawArc(-PR * 0.32f, mouthY - PR * 0.3f, PR * 0.32f, mouthY + PR * 0.18f,
                    10f, 160f, false, strokePaint)
            }
            else -> {                         // pas encore prête : petit sourire
                canvas.drawArc(-PR * 0.2f, mouthY - PR * 0.18f, PR * 0.2f, mouthY + PR * 0.1f,
                    20f, 140f, false, strokePaint)
            }
        }
    }

    private fun drawCookRing(canvas: Canvas, p: Potato) {
        val rr = PR * 1.5f
        val rect = RectF(p.x - rr, p.y - rr, p.x + rr, p.y + rr)
        ringBgPaint.strokeWidth = 5f
        canvas.drawArc(rect, 0f, 360f, false, ringBgPaint)
        // Zone cible (verte)
        bandPaint.color = 0x886FE08A.toInt()
        bandPaint.strokeWidth = 5f
        canvas.drawArc(rect, -90f + COOK_MIN * 360f, (COOK_MAX - COOK_MIN) * 360f, false, bandPaint)
        // Progression
        val frac = p.cook.coerceIn(0f, 1f)
        ringPaint.strokeWidth = 5f
        ringPaint.color = when {
            p.cook in COOK_MIN..COOK_MAX -> 0xFF55E07A.toInt()
            p.cook < COOK_MIN -> 0xFFE8C25A.toInt()
            else -> 0xFFFF5A3C.toInt()
        }
        canvas.drawArc(rect, -90f, frac * 360f, false, ringPaint)
    }

    /** Aperçu de la trajectoire pendant qu'un tir de lance-pierre est en cours. */
    private fun drawAimPreviews(canvas: Canvas) {
        synchronized(aims) {
            if (aims.isEmpty()) return
            for (a in aims.values) {
                val p = a.potato
                if (p !in potatoes) continue
                val l = computeLaunch(a.curX - a.startX, a.curY - a.startY)
                drawTrajectory(canvas, p.x, p.y, l[0], l[1])
            }
        }
    }

    private fun drawTrajectory(canvas: Canvas, x0: Float, y0: Float, vx: Float, vy: Float) {
        fillPaint.color = 0x99FFFFFF.toInt()
        var t = 0.05f
        for (i in 0 until 16) {
            val x = x0 + vx * t
            val y = y0 + vy * t + 0.5f * GRAVITY * t * t
            if (y > PLATE_TOP - PR || x < 0f || x > VW) break
            val r = (4.5f - i * 0.2f).coerceAtLeast(1.5f)
            canvas.drawCircle(x, y, r, fillPaint)
            t += 0.07f
        }
    }

    private fun drawFallingTopping(canvas: Canvas, t: FallingTopping) {
        when (t.type) {
            Topping.CREAM -> {
                fillPaint.color = 0xFFFFFBF0.toInt()
                canvas.drawCircle(t.x, t.y, 9f, fillPaint)
                canvas.drawCircle(t.x - 4f, t.y + 4f, 6f, fillPaint)
            }
            Topping.BACON -> {
                fillPaint.color = 0xFFB5462E.toInt()
                canvas.drawRoundRect(RectF(t.x - 11f, t.y - 5f, t.x + 11f, t.y + 5f), 4f, 4f, fillPaint)
                fillPaint.color = 0xFFE8A38C.toInt()
                canvas.drawRect(t.x - 11f, t.y - 1f, t.x + 11f, t.y + 1.5f, fillPaint)
            }
            Topping.LETTUCE -> {
                fillPaint.color = 0xFF6FBF3B.toInt()
                canvas.drawCircle(t.x - 5f, t.y, 7f, fillPaint)
                canvas.drawCircle(t.x + 5f, t.y, 7f, fillPaint)
                canvas.drawCircle(t.x, t.y - 4f, 6f, fillPaint)
            }
        }
    }

    private fun drawEmber(canvas: Canvas, e: Ember) {
        fillPaint.shader = RadialGradient(
            e.x, e.y, 16f,
            0xFFFFE08A.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(e.x, e.y, 16f, fillPaint)
        fillPaint.shader = null
        fillPaint.color = 0xFFFF6A2A.toInt()
        canvas.drawCircle(e.x, e.y, 5f, fillPaint)
    }

    private fun drawFloatTexts(canvas: Canvas) {
        for (f in floatTexts) {
            val alpha = (f.life.coerceIn(0f, 1f) * 255).toInt()
            bodyPaint.color = (f.color and 0x00FFFFFF) or (alpha shl 24)
            bodyPaint.textSize = 18f
            canvas.drawText(f.text, f.x, f.y, bodyPaint)
        }
        bodyPaint.color = Color.parseColor("#F0E6D2")
    }

    private fun drawHud(canvas: Canvas) {
        hudPaint.textSize = 24f
        canvas.drawText("$score", 16f, 34f, hudPaint)
        hudPaint.textSize = 13f
        canvas.drawText("${context.getString(R.string.hot_potato_best_label)}: $bestScore", 16f, 52f, hudPaint)
        // Strikes (patates restantes)
        val left = MAX_STRIKES - strikes
        for (i in 0 until MAX_STRIKES) {
            val cx = VW - 24f - i * 28f
            fillPaint.color = if (i < left) 0xFFE0B05A.toInt() else 0x33FFFFFF
            canvas.drawOval(RectF(cx - 11f, 16f, cx + 11f, 38f), fillPaint)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, message: String, highlight: Boolean) {
        canvas.drawRect(0f, 0f, VW, VH, overlayBg)
        titlePaint.textSize = 40f
        titlePaint.color = if (highlight) 0xFFFFD700.toInt() else Color.WHITE
        canvas.drawText(title, VW / 2f, VH * 0.36f, titlePaint)
        titlePaint.color = Color.WHITE
        bodyPaint.textSize = 17f
        bodyPaint.color = Color.parseColor("#F0E6D2")
        var y = VH * 0.46f
        for (line in message.split("\n")) {
            canvas.drawText(line, VW / 2f, y, bodyPaint)
            y += 27f
        }
    }

    // Couleur de la patate selon la cuisson : crue → dorée → cramée
    private fun potatoColor(cook: Float): Int {
        val stops = floatArrayOf(0f, 0.6f, 0.72f, 1f, 1.5f)
        val colors = intArrayOf(
            0xFFF3E2B0.toInt(),  // crue, pâle
            0xFFE0B05A.toInt(),  // début de coloration
            0xFFC8893C.toInt(),  // cuit à point, doré
            0xFF7A4A28.toInt(),  // trop cuit
            0xFF35241A.toInt()   // carbonisé
        )
        val c = cook.coerceIn(0f, 1.5f)
        var i = 0
        while (i < stops.size - 1 && c > stops[i + 1]) i++
        val t = ((c - stops[i]) / (stops[i + 1] - stops[i])).coerceIn(0f, 1f)
        return lerpColor(colors[i], colors[i + 1], t)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt()
        val g = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
