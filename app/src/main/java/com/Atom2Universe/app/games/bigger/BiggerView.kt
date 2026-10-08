package com.Atom2Universe.app.games.bigger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BiggerView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    /** Faux dès qu'une surface a refusé le canevas matériel : voir [lockFrame]. */
    private var hardwareCanvas = true

    /**
     * Attrape l'image à venir — **sur le processeur graphique**.
     *
     * [SurfaceHolder.lockCanvas] rend un canevas logiciel : le processeur écrirait
     * lui-même tous les pixels de l'écran, à chaque image. Le canevas matériel ne demande
     * qu'une chose, **tout redessiner à chaque image**, ce que cette vue fait déjà en
     * commençant par le fond. Une surface peut le refuser : le jeu repasse alors en
     * logiciel, une fois pour toutes.
     */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    val game = BiggerGame()

    var sound: BiggerSoundEngine? = null

    /** Le record enregistré, affiché sous le score. */
    var bestScore = 0

    /** Le plus gros objet jamais atteint, toutes parties confondues : la frise du bas. */
    var discoveredTier = -1

    /** Fin d'une partie, perdue ou abandonnée par « recommencer » : score et plus gros objet. */
    var onRunEnded: ((score: Int, bestTier: Int) -> Unit)? = null

    /** Ce que la bannière affiche. L'icône est nulle tant qu'elle n'est pas cuite. */
    class Hud(val score: Int, val best: Int, val nextIcon: Bitmap?)

    /** Appelé depuis le fil de jeu quand le score ou l'astre suivant changent. */
    var onHud: ((Hud) -> Unit)? = null

    /** Un objet vient d'être atteint pour la première fois. */
    var onDiscovered: ((tier: Int) -> Unit)? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L

    private val dp = resources.displayMetrics.density

    // ── Textes ────────────────────────────────────────────────────────────────
    private val tierNames: Array<String> = resources.getStringArray(R.array.bigger_tier_names)
    private val txtAnnihilation = context.getString(R.string.bigger_annihilation)
    private val txtOverTitle = context.getString(R.string.bigger_over_title)
    private val txtNewRecord = context.getString(R.string.bigger_over_new_record)
    private val txtPlayAgain = context.getString(R.string.bigger_play_again)

    // ── Géométrie : le monde en pixels ───────────────────────────────────────
    private var viewW = 0
    private var viewH = 0
    private var geometryDirty = true
    private var scale = 1f
    private var jarLeft = 0f
    private var floorY = 0f
    private val topPad = 8f * dp
    private val stripH = 44f * dp
    private val margin = 14f * dp

    private fun sx(x: Float) = jarLeft + x * scale
    private fun sy(y: Float) = floorY - y * scale

    // ── Images cuites ────────────────────────────────────────────────────────
    private var background: Bitmap? = null
    private val art = AccretionSprites(context)
    private var iconR = 12f * dp

    /**
     * Les images d'une échelle donnée. Les cuire prend un moment — une étoile, c'est un
     * demi-million de points de photosphère — donc un fil à part s'en charge, des plus
     * petits astres aux plus gros, dans l'ordre où la partie en a besoin. Tant qu'une image
     * manque, l'astre est dessiné comme un simple disque de sa couleur.
     */
    private class Baked {
        val sprites = arrayOfNulls<Bitmap>(Accretion.TIER_COUNT)
        val shades = arrayOfNulls<Bitmap>(Accretion.TIER_COUNT)
        val icons = arrayOfNulls<Bitmap>(Accretion.TIER_COUNT)
        @Volatile var cancelled = false
    }
    @Volatile private var baked = Baked()
    private val halos = arrayOfNulls<RadialGradient>(Accretion.TIER_COUNT)

    // Étoiles qui scintillent, par-dessus le fond cuit.
    private val twinkleX = FloatArray(40)
    private val twinkleY = FloatArray(40)
    private val twinklePhase = FloatArray(40)

    private var time = 0f

    // ── Visée ────────────────────────────────────────────────────────────────
    @Volatile private var holdX = Accretion.WIDTH / 2f
    @Volatile private var pendingDrop = false
    private var warnTimer = 0f

    // ── Effets ───────────────────────────────────────────────────────────────
    private class Spark(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float, val color: Int, val size: Float)
    private class Wave(val x: Float, val y: Float, val r0: Float, val r1: Float, var life: Float, val max: Float, val color: Int)
    private class Popup(val x: Float, var y: Float, val text: String, var life: Float, val max: Float, val color: Int, val size: Float)

    private val sparks = ArrayList<Spark>()
    private val waves = ArrayList<Wave>()
    private val popups = ArrayList<Popup>()
    private var bannerText: String? = null
    private var bannerColor = Color.WHITE
    private var bannerLife = 0f
    private var shake = 0f

    private var bestAtStart = 0
    private var overReward = 0
    private var overRecord = false
    private var overBestTier = -1

    // ── Pinceaux ─────────────────────────────────────────────────────────────
    private val pBitmap = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pAim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp
        pathEffect = DashPathEffect(floatArrayOf(6f * dp, 7f * dp), 0f)
    }
    private val pRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(10f * dp, 8f * dp), 0f)
    }
    private val pJar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; strokeCap = Paint.Cap.ROUND
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val btnRect = RectF()

    // Textes du score, reformatés seulement quand le chiffre change.
    private var shownScore = -1
    private var shownBest = -1
    private var shownNext = -1
    private var shownIcon: Bitmap? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = startThread()

    /** Démarre le fil de jeu, **et un seul** : deux fils feraient avancer la partie à double vitesse. */
    private fun startThread() {
        if (thread?.isAlive == true) {
            if (running) return
            joinThread()
        }
        running = true; lastNanos = System.nanoTime()
        thread = Thread(this, "BiggerPhysics").also { it.start() }
    }

    private fun joinThread() {
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        synchronized(game) {
            viewW = w; viewH = h
            geometryDirty = true
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false; thread?.join(2000); thread = null
    }

    fun pause() { running = false; thread?.join(1500); thread = null }
    fun resume() = startThread()

    /** Repart de zéro. Une partie en cours compte comme terminée : son score est acquis. */
    fun restart() {
        synchronized(game) {
            if (!game.over && game.score > 0) onRunEnded?.invoke(game.score, game.bestTier)
            game.reset()
            clearEffects()
            bestAtStart = bestScore
        }
    }

    /** À appeler une fois la partie restaurée ou créée, pour que « nouveau record » compare au bon chiffre. */
    fun startRun() { bestAtStart = bestScore }

    private fun clearEffects() {
        sparks.clear(); waves.clear(); popups.clear()
        bannerText = null; shake = 0f; pendingDrop = false
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = now
            synchronized(game) {
                if (pendingDrop && game.canDrop) {
                    pendingDrop = false
                    game.drop(holdX)
                }
                game.step(dt)
                handleEvents()
                updateEffects(dt)
                publishHud()
            }
            // Une toile nulle veut dire que la surface n'est pas prête : on attend quand même.
            val canvas = lockFrame()
            if (canvas != null) {
                try { synchronized(game) { drawFrame(canvas) } }
                finally { holder.unlockCanvasAndPost(canvas) }
            }
            Thread.sleep(4)
        }
    }

    // ── Entrées ──────────────────────────────────────────────────────────────

    private fun toWorldX(px: Float) = if (scale > 0f) (px - jarLeft) / scale else Accretion.WIDTH / 2f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!game.over) holdX = toWorldX(event.x).coerceIn(0f, Accretion.WIDTH)
            }
            MotionEvent.ACTION_UP -> {
                if (game.over) {
                    if (btnRect.contains(event.x, event.y)) restart()
                } else {
                    holdX = toWorldX(event.x).coerceIn(0f, Accretion.WIDTH)
                    pendingDrop = true
                }
            }
        }
        return true
    }

    // ── Événements du jeu → effets et sons ───────────────────────────────────

    private fun handleEvents() {
        for (e in game.events) {
            when (e.kind) {
                BiggerEvent.Kind.DROP -> sound?.onDrop()
                BiggerEvent.Kind.MERGE, BiggerEvent.Kind.COLLAPSE -> {
                    burst(e.x, e.y, e.tier, if (e.kind == BiggerEvent.Kind.COLLAPSE) 2.2f else 1f)
                    val color = AccretionPainter.TINT[e.tier]
                    popups.add(Popup(e.x, e.y, context.getString(R.string.bigger_points, e.points), 0.9f, 0.9f,
                        Color.WHITE, (14f + e.tier) * dp))
                    if (e.chain > 1) popups.add(Popup(e.x, e.y - 0.55f, context.getString(R.string.bigger_chain, e.chain),
                        0.9f, 0.9f, color, (13f + e.chain * 1.5f) * dp))
                    if (e.kind == BiggerEvent.Kind.COLLAPSE) { sound?.onCollapse(); shake = 0.35f }
                    else sound?.onMerge(e.tier, e.chain)
                    if (e.discovered) {
                        banner(tierNames.getOrElse(e.tier) { "" }, color)
                        if (e.tier > discoveredTier) {
                            discoveredTier = e.tier
                            sound?.onDiscovery()
                            onDiscovered?.invoke(e.tier)
                        }
                    }
                }
                BiggerEvent.Kind.ANNIHILATION -> {
                    burst(e.x, e.y, Accretion.BLACK_HOLE, 3.5f)
                    waves.add(Wave(e.x, e.y, 0.5f, 7f, 0.9f, 0.9f, Color.WHITE))
                    popups.add(Popup(e.x, e.y, context.getString(R.string.bigger_points, e.points), 1.4f, 1.4f,
                        Color.WHITE, 26f * dp))
                    banner(txtAnnihilation, 0xFFFFD27A.toInt())
                    sound?.onAnnihilation()
                    shake = 0.6f
                }
                BiggerEvent.Kind.GAME_OVER -> {
                    sound?.onGameOver()
                    overReward = NeutrinoRewards.bigger(game.score)
                    overRecord = game.score > bestAtStart
                    overBestTier = game.bestTier
                    onRunEnded?.invoke(game.score, game.bestTier)
                }
            }
        }
        game.events.clear()
    }

    private fun banner(text: String, color: Int) {
        bannerText = text; bannerColor = color; bannerLife = 1.8f
    }

    private fun burst(x: Float, y: Float, tier: Int, power: Float) {
        val color = AccretionPainter.TINT[tier]
        val r = Accretion.RADIUS[tier]
        val rnd = Random.Default
        val count = ((14 + tier * 3) * power).toInt()
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = (2f + rnd.nextFloat() * 5f) * (0.6f + power * 0.4f)
            val life = 0.4f + rnd.nextFloat() * 0.5f
            val c = if (rnd.nextInt(3) == 0) Color.WHITE else color
            sparks.add(Spark(x + cos(a) * r * 0.6f, y + sin(a) * r * 0.6f, cos(a) * speed, sin(a) * speed,
                life, life, c, (1.5f + rnd.nextFloat() * 2.5f) * dp))
        }
        waves.add(Wave(x, y, r, r * (1.8f + power * 0.3f), 0.45f, 0.45f, color))
    }

    private fun updateEffects(dt: Float) {
        time += dt
        var i = sparks.size - 1
        while (i >= 0) {
            val s = sparks[i]
            s.life -= dt
            if (s.life <= 0f) sparks.removeAt(i)
            else { val drag = s.life / s.max; s.x += s.vx * dt * drag; s.y += s.vy * dt * drag }
            i--
        }
        i = waves.size - 1
        while (i >= 0) { waves[i].life -= dt; if (waves[i].life <= 0f) waves.removeAt(i); i-- }
        i = popups.size - 1
        while (i >= 0) {
            val p = popups[i]
            p.life -= dt; p.y += dt * 1.2f
            if (p.life <= 0f) popups.removeAt(i)
            i--
        }
        bannerLife -= dt
        shake = (shake - dt).coerceAtLeast(0f)
        if (!game.over && game.danger > 0.25f) {
            warnTimer -= dt
            if (warnTimer <= 0f) { sound?.onWarning(); warnTimer = 0.9f - game.danger * 0.5f }
        } else warnTimer = 0f
    }

    // ── Géométrie et images cuites ───────────────────────────────────────────

    private fun rebuildGeometry() {
        geometryDirty = false
        val w = viewW.toFloat(); val h = viewH.toFloat()
        floorY = h - stripH - 6f * dp
        val usable = floorY - topPad
        // La largeur fixe l'échelle ; si l'écran est trop court même pour le plus petit
        // bocal, c'est la hauteur qui la fixe.
        scale = min((w - 2f * margin) / Accretion.WIDTH, usable / (Accretion.MIN_HEIGHT + Accretion.DROP_ZONE))
        jarLeft = (w - Accretion.WIDTH * scale) / 2f
        // Le bocal monte jusqu'où l'écran le permet.
        game.jarHeight = usable / scale - Accretion.DROP_ZONE
        iconR = min(12f * dp, (w - 2f * margin) / Accretion.TIER_COUNT * 0.38f)

        startBaking()
        for (t in 0 until Accretion.TIER_COUNT) {
            if (AccretionPainter.isLuminous(t)) {
                val c = AccretionPainter.TINT[t]
                halos[t] = RadialGradient(0f, 0f, 1f,
                    intArrayOf((c and 0xFFFFFF) or (0x48 shl 24), (c and 0xFFFFFF) or (0x18 shl 24), c and 0xFFFFFF),
                    floatArrayOf(0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
            }
        }
        background?.recycle()
        background = bakeBackground(viewW, viewH)
        val rnd = Random(4242)
        for (k in twinkleX.indices) {
            twinkleX[k] = rnd.nextFloat() * w; twinkleY[k] = rnd.nextFloat() * h
            twinklePhase[k] = rnd.nextFloat() * 6.28f
        }
    }

    private fun startBaking() {
        baked.cancelled = true
        val job = Baked()
        baked = job
        val bodyScale = scale
        val iconRadius = iconR
        Thread({
            for (t in 0 until Accretion.TIER_COUNT) {
                if (job.cancelled) return@Thread
                job.icons[t] = art.composite(t, iconRadius)
                val r = Accretion.RADIUS[t] * bodyScale
                job.shades[t] = art.lighting(t, r)
                job.sprites[t] = art.surface(t, r)
            }
        }, "AccretionBake").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /**
     * Un astre : sa surface tourne avec lui, son ombre non — la lumière reste en haut à
     * gauche pendant qu'il roule.
     */
    private fun drawBody(canvas: Canvas, tier: Int, x: Float, y: Float, angleDeg: Float, k: Float = 1f) {
        val sprite = baked.sprites[tier]
        if (sprite == null) {
            pFill.color = AccretionPainter.TINT[tier]
            canvas.drawCircle(x, y, Accretion.RADIUS[tier] * scale * k, pFill)
            return
        }
        val half = sprite.width / 2f
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(k, k)
        canvas.save()
        canvas.rotate(angleDeg)
        canvas.drawBitmap(sprite, -half, -half, pBitmap)
        canvas.restore()
        baked.shades[tier]?.let { canvas.drawBitmap(it, -it.width / 2f, -it.height / 2f, pBitmap) }
        canvas.restore()
    }

    /** Une vignette immobile (frise, hublot, fin de partie), agrandie de [k]. */
    private fun drawIcon(canvas: Canvas, tier: Int, x: Float, y: Float, k: Float = 1f) {
        val icon = baked.icons[tier]
        if (icon == null) {
            pFill.color = AccretionPainter.TINT[tier]
            canvas.drawCircle(x, y, iconR * k, pFill)
            return
        }
        canvas.save(); canvas.translate(x, y); canvas.scale(k, k)
        canvas.drawBitmap(icon, -icon.width / 2f, -icon.height / 2f, pBitmap)
        canvas.restore()
    }

    /** Le fond : dégradé profond, deux nébuleuses et des étoiles. Cuit une fois, en coordonnées écran. */
    private fun bakeBackground(w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF05060F.toInt(), 0xFF120A26.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        fun nebula(x: Float, y: Float, r: Float, color: Int) {
            p.shader = RadialGradient(x, y, r, color, color and 0xFFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        nebula(w * 0.15f, h * 0.3f, w * 0.7f, 0x303A1C6E)
        nebula(w * 0.9f, h * 0.7f, w * 0.6f, 0x28145A6E)
        nebula(w * 0.5f, h * 0.05f, w * 0.5f, 0x206E1C4A)
        p.shader = null
        val rnd = Random(777)
        repeat(260) {
            val big = rnd.nextInt(12) == 0
            p.color = Color.argb(60 + rnd.nextInt(150), 200 + rnd.nextInt(55), 205 + rnd.nextInt(50), 255)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h,
                (if (big) 1.3f else 0.5f + rnd.nextFloat() * 0.6f) * dp, p)
        }
        return bmp
    }

    // ── Rendu ────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        if (viewW == 0) return
        if (geometryDirty) rebuildGeometry()
        background?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: canvas.drawColor(0xFF05060F.toInt())

        pFill.style = Paint.Style.FILL
        for (k in twinkleX.indices) {
            val a = (0.5f + 0.5f * sin(time * 1.7f + twinklePhase[k]))
            pFill.color = Color.argb((a * 200).toInt(), 230, 235, 255)
            canvas.drawCircle(twinkleX[k], twinkleY[k], (0.6f + a * 0.9f) * dp, pFill)
        }

        canvas.save()
        if (shake > 0f) {
            val k = shake * 6f * dp
            canvas.translate(sin(time * 90f) * k, cos(time * 77f) * k)
        }
        drawJar(canvas)
        drawOrbs(canvas)
        drawRim(canvas)
        if (!game.over) drawHeld(canvas)
        drawEffects(canvas)
        canvas.restore()

        drawStrip(canvas)
        drawBanner(canvas)
        if (game.over) drawOverlay(canvas)
    }

    private fun drawJar(canvas: Canvas) {
        val l = sx(0f); val r = sx(Accretion.WIDTH)
        val top = sy(game.jarHeight); val bottom = sy(0f)
        pFill.shader = LinearGradient(0f, top, 0f, bottom, 0x0A8C9CFF, 0x228C9CFF, Shader.TileMode.CLAMP)
        canvas.drawRect(l, top, r, bottom, pFill)
        pFill.shader = null
        pJar.color = 0x806C7BD6.toInt()
        canvas.drawLine(l, top - 6f * dp, l, bottom, pJar)
        canvas.drawLine(r, top - 6f * dp, r, bottom, pJar)
        canvas.drawLine(l, bottom, r, bottom, pJar)
    }

    private fun drawOrbs(canvas: Canvas) {
        val pulse = sin(time * 3f)
        for (o in game.orbs) {
            val b = o.body
            val x = sx(b.x); val y = sy(b.y)
            val shader = halos[o.tier]
            if (shader != null) {
                val rr = o.radius * scale * AccretionPainter.GLOW[o.tier] * (1.3f + 0.08f * pulse)
                pHalo.shader = shader
                pHalo.alpha = (200 + 55 * pulse).toInt()
                canvas.save(); canvas.translate(x, y); canvas.scale(rr, rr)
                canvas.drawCircle(0f, 0f, 1f, pHalo)
                canvas.restore()
            }
            drawBody(canvas, o.tier, x, y, (-b.angle * 180f / PI).toFloat())
            if (o.overflow > 0f) {
                pStroke.strokeWidth = 2f * dp
                pStroke.color = Color.argb((120 + 100 * sin(time * 12f)).toInt().coerceIn(0, 255), 255, 70, 60)
                canvas.drawCircle(x, y, o.radius * scale + 3f * dp, pStroke)
            }
        }
    }

    /** Le bord du bocal : une limite discrète qui rougit et bat quand un objet déborde. */
    private fun drawRim(canvas: Canvas) {
        val y = sy(game.jarHeight)
        val d = game.danger
        val beat = 0.5f + 0.5f * sin(time * (6f + d * 10f))
        val alpha = (70 + d * 185 * beat).toInt().coerceIn(0, 255)
        pRim.color = if (d > 0f) Color.argb(alpha, 255, 80, 60) else Color.argb(70, 255, 120, 110)
        canvas.drawLine(sx(0f), y, sx(Accretion.WIDTH), y, pRim)
        if (d > 0f) {
            pFill.shader = LinearGradient(0f, y, 0f, y + 26f * dp,
                Color.argb((d * 110 * beat).toInt(), 255, 60, 40), 0, Shader.TileMode.CLAMP)
            canvas.drawRect(sx(0f), y, sx(Accretion.WIDTH), y + 26f * dp, pFill)
            pFill.shader = null
        }
    }

    private fun drawHeld(canvas: Canvas) {
        val tier = game.current
        val r = Accretion.RADIUS[tier]
        val x = holdX.coerceIn(r, Accretion.WIDTH - r)
        val grow = 1f - game.cooldown / Accretion.DROP_COOLDOWN
        pAim.color = Color.argb((70 * grow).toInt(), 200, 210, 255)
        canvas.drawLine(sx(x), sy(game.holdY - r), sx(x), sy(0f), pAim)
        pBitmap.alpha = (255 * grow).toInt()
        drawBody(canvas, tier, sx(x), sy(game.holdY), time * 20f, 0.4f + 0.6f * grow)
        pBitmap.alpha = 255
    }

    private fun drawEffects(canvas: Canvas) {
        pStroke.strokeWidth = 3f * dp
        for (w in waves) {
            val t = 1f - w.life / w.max
            pStroke.color = (w.color and 0xFFFFFF) or (((1f - t) * 200).toInt() shl 24)
            canvas.drawCircle(sx(w.x), sy(w.y), (w.r0 + (w.r1 - w.r0) * t) * scale, pStroke)
        }
        pFill.style = Paint.Style.FILL
        for (s in sparks) {
            pFill.color = (s.color and 0xFFFFFF) or (((s.life / s.max) * 255).toInt() shl 24)
            canvas.drawCircle(sx(s.x), sy(s.y), s.size, pFill)
        }
        for (p in popups) {
            pText.textSize = p.size
            val a = ((p.life / p.max) * 1.5f).coerceAtMost(1f)
            pText.color = (p.color and 0xFFFFFF) or ((a * 255).toInt() shl 24)
            pText.setShadowLayer(4f * dp, 0f, 0f, Color.argb((a * 200).toInt(), 0, 0, 0))
            canvas.drawText(p.text, sx(p.x), sy(p.y), pText)
        }
        pText.clearShadowLayer()
    }

    /**
     * Le score et l'astre suivant vivent dans la bannière de l'activité : la place
     * qu'ils prenaient en haut du jeu revient au bocal. On ne prévient l'activité que quand
     * quelque chose change, pas à chaque image.
     */
    private fun publishHud() {
        val best = maxOf(bestScore, game.score)
        val icon = baked.icons[game.next]
        if (game.score == shownScore && best == shownBest && game.next == shownNext && icon === shownIcon) return
        shownScore = game.score; shownBest = best
        shownNext = game.next; shownIcon = icon
        onHud?.invoke(Hud(shownScore, best, icon))
    }

    /** La frise des douze objets : ceux jamais atteints restent des ombres. */
    private fun drawStrip(canvas: Canvas) {
        val n = Accretion.TIER_COUNT
        val slot = (viewW - 2f * margin) / n
        val cy = viewH - stripH / 2f
        for (t in 0 until n) {
            val cx = margin + slot * (t + 0.5f)
            if (t <= discoveredTier) {
                drawIcon(canvas, t, cx, cy)
            } else {
                pStroke.strokeWidth = 1f * dp; pStroke.color = 0x404A5080
                canvas.drawCircle(cx, cy, iconR * 0.8f, pStroke)
            }
            if (t == game.bestTier) {
                pStroke.strokeWidth = 1.5f * dp; pStroke.color = 0xB0FFD27A.toInt()
                canvas.drawCircle(cx, cy, iconR + 4f * dp, pStroke)
            }
        }
    }

    private fun drawBanner(canvas: Canvas) {
        val text = bannerText ?: return
        if (bannerLife <= 0f) { bannerText = null; return }
        val a = (bannerLife / 0.4f).coerceAtMost(1f)
        pText.textSize = 26f * dp
        pText.color = (bannerColor and 0xFFFFFF) or ((a * 255).toInt() shl 24)
        pText.setShadowLayer(10f * dp, 0f, 0f, (bannerColor and 0xFFFFFF) or ((a * 160).toInt() shl 24))
        canvas.drawText(text, viewW / 2f, sy(game.jarHeight * 0.72f), pText)
        pText.clearShadowLayer()
    }

    private fun drawOverlay(canvas: Canvas) {
        val w = viewW.toFloat(); val h = viewH.toFloat()
        pFill.color = Color.argb(205, 5, 6, 15)
        canvas.drawRect(0f, 0f, w, h, pFill)
        val cx = w / 2f
        var y = h * 0.3f
        pText.textSize = 30f * dp; pText.color = 0xFFFF7A5A.toInt()
        canvas.drawText(txtOverTitle, cx, y, pText)
        y += 48f * dp
        pText.textSize = 40f * dp; pText.color = Color.WHITE
        canvas.drawText(context.getString(R.string.bigger_score, game.score), cx, y, pText)
        if (overRecord) {
            y += 28f * dp
            pText.textSize = 15f * dp; pText.color = 0xFFFFD27A.toInt()
            canvas.drawText(txtNewRecord, cx, y, pText)
        }
        if (overBestTier >= 0) {
            y += 44f * dp
            drawIcon(canvas, overBestTier, cx, y)
            y += iconR + 22f * dp
            pText.textSize = 14f * dp; pText.color = 0xFFB8BCE0.toInt()
            canvas.drawText(tierNames.getOrElse(overBestTier) { "" }, cx, y, pText)
        }
        if (overReward > 0) {
            y += 30f * dp
            pText.textSize = 15f * dp; pText.color = 0xFF7FE0FF.toInt()
            canvas.drawText(context.getString(R.string.bigger_over_neutrinos, overReward), cx, y, pText)
        }
        val bw = 170f * dp; val bh = 46f * dp
        y += 34f * dp
        btnRect.set(cx - bw / 2f, y, cx + bw / 2f, y + bh)
        pFill.color = 0xFF1C1E3A.toInt()
        canvas.drawRoundRect(btnRect, 23f * dp, 23f * dp, pFill)
        pStroke.strokeWidth = 1.5f * dp; pStroke.color = 0xFF6C7BD6.toInt()
        canvas.drawRoundRect(btnRect, 23f * dp, 23f * dp, pStroke)
        pText.textSize = 16f * dp; pText.color = Color.WHITE
        canvas.drawText(txtPlayAgain, cx, btnRect.centerY() + 5.5f * dp, pText)
    }
}
