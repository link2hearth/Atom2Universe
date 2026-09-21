package com.Atom2Universe.app.games.roguelike.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.roguelike.EquipSlot
import com.Atom2Universe.app.games.roguelike.Archetype
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Véritable rendu Canvas : décor en cache, sprites composés, poses articulées et particules.
 * Le tampon fait 240 pixels de large ; sa hauteur suit la vue, sans bandes autour du décor.
 * Animation volontairement discrète (~10 poses/s) ; chronologie indépendante du framerate.
 * Cette démo n'utilise ni Combat, ni Hero, ni SpriteLoader, ni les préférences du jeu.
 */
class DungeonArtDemoView(context: Context) : View(context) {
    enum class Action { ATTACK, ICE, SHATTER }

    var onBusyChanged: ((Boolean) -> Unit)? = null
    var paused = false
        set(value) { field = value; lastFrame = SystemClock.uptimeMillis(); invalidate() }
    private var running = false
    private var lastFrame = 0L
    private var clock = 0f
    private var started = 0f
    private var action: Action? = null
    private val gesture = DungeonGesture(context)
    private var queuedAction: Action? = null
    private var enemyGesture = false
    private var parryStarted = false
    private var strikeGrade = DungeonGesture.Grade.GOOD
    private var guardGrade = DungeonGesture.Grade.MISS
    internal var spellGesture = DungeonGesture.Kind.CIRCLE
    internal var doubleParry = false
    val inputLocked get() = action != null || queuedAction != null || gesture.active
    private var heroHp = 86
    var onHeroHealthChanged: ((Int) -> Unit)? = null
    private var heroImpactApplied = false

    fun gestureTouch(event: MotionEvent) {
        if (running && !paused) {
            val now = clock + (SystemClock.uptimeMillis() - lastFrame).coerceAtLeast(0)
            gesture.touch(event, now)
            invalidate()
        }
    }
    private val frozenTargets = BooleanArray(3)
    private val health = IntArray(3) { 42 }
    private var selectedRat = 0
    // Ancrages avant miroir : à l'écran, bas gauche, centre gauche, puis bas centre.
    // Ces ancrages servent aussi aux touches, aux PV et à toutes les trajectoires.
    private val ratXs = floatArrayOf(178f, 178f, 99f)
    private val ratYs = floatArrayOf(198f, 142f, 198f)
    private var worldHeight = 288f
    private var floorY = 84f
    private val heroX = 38f
    private var heroY = 100f
    var mobCount = 1
        private set
    private var frozen: Boolean
        get() = frozenTargets[selectedRat]
        set(value) { frozenTargets[selectedRat] = value }
    private val equipment = DungeonDemoSprites.slots.associateWith { DemoGear(it) }.toMutableMap()
    private val gearImages = equipment.mapValues { DungeonDemoSprites.equipment(it.value) }.toMutableMap()
    var onEquipmentChanged: (() -> Unit)? = null
    private var ratHp: Int
        get() = health[selectedRat]
        set(value) { health[selectedRat] = value }
    private val ink = 0xFF111729.toInt()
    private val gold = 0xFFDBB975.toInt()
    private val white = 0xFFE8EAD5.toInt()
    private val iceBlue = 0xFFA6E7EF.toInt()
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val path = Path()
    private val target = RectF()
    private var frame = Bitmap.createBitmap(240, 288, Bitmap.Config.ARGB_8888)
    private var pixels = Canvas(frame)
    private var background = Bitmap.createBitmap(240, 288, Bitmap.Config.ARGB_8888)
    private val rat = DungeonDemoSprites.rat()
    private val portrait = Bitmap.createBitmap(32, 36, Bitmap.Config.ARGB_8888)
    private var portraitDirty = true
    private var nextNod = 3500f
    private var nodStart = -1000f
    private val labels = listOf(
        R.string.dungeon_demo_warrior, R.string.dungeon_demo_rat,
        R.string.dungeon_demo_ready, R.string.dungeon_demo_strike, R.string.dungeon_demo_frozen,
        R.string.dungeon_demo_fracas, R.string.dungeon_demo_block,
        R.string.dungeon_demo_heavy, R.string.dungeon_demo_isotope, R.string.dungeon_demo_mixed
    ).associateWith { context.getString(it) }
    private val damage = context.getString(R.string.dungeon_demo_damage, 24)
    private val smallDamage = context.getString(R.string.dungeon_demo_damage, 12)

    private val ratHealth = intArrayOf(42, 30, 18).associateWith { context.getString(R.string.dungeon_demo_health, it, 42) }

    init {
        contentDescription = context.getString(R.string.dungeon_demo_description)
        drawBackground(Canvas(background))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        worldHeight = kotlin.math.ceil(h * 240.0 / w).toFloat()
        floorY = min(84f, worldHeight * .28f)
        heroY = floorY + 16
        ratYs[0] = worldHeight - 45
        ratYs[1] = floorY + (worldHeight - floorY) * .40f
        ratYs[2] = ratYs[0]
        frame = Bitmap.createBitmap(240, worldHeight.toInt(), Bitmap.Config.ARGB_8888)
        pixels = Canvas(frame)
        background = Bitmap.createBitmap(240, worldHeight.toInt(), Bitmap.Config.ARGB_8888)
        drawBackground(Canvas(background))
    }

    fun setRunning(value: Boolean) {
        running = value
        lastFrame = SystemClock.uptimeMillis()
        if (value) invalidate()
    }

    internal fun gear(slot: EquipSlot): DemoGear = equipment.getValue(slot)
    internal fun gearBitmap(slot: EquipSlot): Bitmap = gearImages.getValue(slot)

    internal fun equipFamily(family: Archetype) {
        DungeonDemoSprites.slots.forEach { slot ->
            equip(gear(slot).copy(family = family, hue = DungeonClassSprites.defaultHue(family), equipped = true))
        }
    }

    internal fun unequipAll() {
        DungeonDemoSprites.slots.forEach { equip(gear(it).copy(equipped = false)) }
    }

    internal fun equip(item: DemoGear) {
        if (equipment[item.slot] == item) return
        equipment[item.slot] = item
        gearImages[item.slot] = DungeonDemoSprites.equipment(item)
        portraitDirty = true
        onEquipmentChanged?.invoke()
        invalidate()
    }

    fun toggleSet() {
        val isotope = !equipment.values.all { it.isotope }
        DungeonDemoSprites.slots.forEach { equip(gear(it).copy(isotope = isotope)) }
    }

    fun toggleMobCount() {
        if (action != null) return
        mobCount = if (mobCount == 1) 3 else 1
        selectedRat = 0
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            if (action == null && target.contains(event.x, event.y)) {
                val x = 240f - (event.x - target.left) * 240 / target.width()
                val y = (event.y - target.top) * worldHeight / target.height()
                val index = (0 until mobCount).firstOrNull {
                    x >= ratXs[it] - 11 && x < ratXs[it] + 53 &&
                        y >= ratYs[it] - 2 && y < ratYs[it] + 32
                }
                if (index != null) { selectedRat = index; invalidate() }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun play(next: Action) {
        if (inputLocked) return
        paused = false
        queuedAction = next
        parryStarted = false
        heroImpactApplied = false
        guardGrade = DungeonGesture.Grade.MISS
        gesture.begin(if (next == Action.ATTACK) DungeonGesture.Kind.RIGHT else spellGesture, clock)
        ratHp = 42
        frozen = next == Action.ATTACK && frozen
        onBusyChanged?.invoke(true)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (running && !paused && lastFrame != 0L) clock += (now - lastFrame).coerceAtLeast(0).toFloat()
        lastFrame = now
        gesture.update(clock)
        if (!gesture.active && queuedAction != null) {
            strikeGrade = gesture.grade
            action = queuedAction
            queuedAction = null
            started = clock
        }
        if (!gesture.active && enemyGesture) {
            guardGrade = gesture.grade
            enemyGesture = false
            started = clock - 1850f
        }
        var t = (clock - started) / 1000f
        if (action == Action.ATTACK && t >= 1.45f && !parryStarted && !frozen) {
            parryStarted = true
            enemyGesture = true
            gesture.begin(if (doubleParry) DungeonGesture.Kind.DOUBLE_TAP else DungeonGesture.Kind.TAP, clock, defense = true)
        }
        if (enemyGesture) t = 1.45f + (gesture.progress(clock) / .72f).coerceAtMost(1f) * .4f
        val duration = when (action) { Action.ATTACK -> 2.5f; Action.ICE -> 1.5f; Action.SHATTER -> 2.8f; null -> 0f }
        if (action != null && t >= duration) {
            frozen = action == Action.ICE && strikeGrade != DungeonGesture.Grade.MISS
            action = null
            onBusyChanged?.invoke(false)
            t = 0f
        }
        val successful = strikeGrade != DungeonGesture.Grade.MISS
        val cast = (action == Action.ICE || action == Action.SHATTER) && successful
        val hitAt = if (action == Action.SHATTER) 1.35f else .48f
        val striking = action == Action.ATTACK || action == Action.SHATTER
        val hit = if (striking) t - hitAt else -10f
        val slash = striking && hit in -.2f.. .22f
        val windup = striking && t in (hitAt - .42f)..hitAt
        val shattered = successful && (action == Action.SHATTER || frozen) && hit >= 0f
        if (striking && hit >= 0f && successful) ratHp = 42 -
            (if (shattered) 24 else 12) - (if (strikeGrade == DungeonGesture.Grade.PERFECT) 6 else 0)
        val counter = action == Action.ATTACK && t in 1.45f..2.3f && !frozen
        val block = counter && !enemyGesture && t >= 1.85f && guardGrade != DungeonGesture.Grade.MISS
        if (counter && !enemyGesture && t >= 1.85f && !heroImpactApplied) {
            heroImpactApplied = true
            heroHp = (heroHp - when (guardGrade) {
                DungeonGesture.Grade.MISS -> 12; DungeonGesture.Grade.GOOD -> 3; DungeonGesture.Grade.PERFECT -> 0
            }).coerceAtLeast(0)
            onHeroHealthChanged?.invoke(heroHp)
        }
        val icy = (frozen || (cast && t >= .65f)) && !shattered
        if (action == null && clock >= nextNod) {
            nodStart = clock
            nextNod = clock + kotlin.random.Random.nextInt(4500, 9500)
        }
        val lunge = if (striking) triangle(t, hitAt - .2f, hitAt + .06f, hitAt + .45f) else 0f
        val ratLunge = if (counter) triangle(t, 1.45f, 1.85f, 2.3f) else 0f
        val recoil = if (successful && hit in 0f.. .4f) (sin(hit * 35) * (1 - hit / .4f) * 5).roundToInt() else 0

        // Toute la scène utilise le même miroir, trajectoires et décor compris.
        pixels.save()
        pixels.translate(240f, 0f)
        pixels.scale(-1f, 1f)
        pixels.drawBitmap(background, 0f, 0f, paint)
        drawAtmosphere(pixels)
        val enemyX = ratXs[selectedRat]
        val enemyY = ratYs[selectedRat]
        val impactX = enemyX + 14
        val impactY = enemyY + 9
        val hx = heroX + (lunge * (enemyX - 30 - heroX)).roundToInt()
        val hy = heroY + (lunge * ((enemyY - 10).coerceAtMost(worldHeight - 43) - heroY)).roundToInt()
        shadow(pixels, hx + 7, hy + 35, 17f)
        for (i in 0 until mobCount) {
            val chosen = i == selectedRat
            val rx = ratXs[i] + (if (chosen) recoil + (ratLunge * (heroX + 21 - enemyX)).roundToInt() else 0)
            val ry = ratYs[i] + if (chosen) (ratLunge * (heroY + 18 - enemyY)).roundToInt() else 0
            val iced = if (chosen) icy else frozenTargets[i]
            shadow(pixels, rx + 3, ry + 18, 24f)
            drawRat(pixels, rx, ry, iced, successful && chosen && hit in 0f.. .22f)
            if (iced) drawIce(pixels, rx + 12, ry + 9)
        }
        drawWarrior(pixels, hx, hy, windup, slash, cast && t < .65f, block)
        if (cast && t in .12f.. .7f) drawProjectile(pixels, ((t - .12f) / .58f).coerceIn(0f, 1f), impactX, impactY)
        if (slash) drawSlash(pixels, ((hit + .2f) / .42f).coerceIn(0f, 1f), impactX, impactY)
        if (successful && hit in 0f.. .7f) {
            fragments(pixels, impactX, impactY, hit / .7f, shattered)
        }
        if (block) fragments(pixels, heroX + 20, heroY + 23, (t - 1.85f) / .45f, false)
        val status = when {
            block -> R.string.dungeon_demo_block
            shattered && hit < .85f -> R.string.dungeon_demo_fracas
            icy -> R.string.dungeon_demo_frozen
            striking -> R.string.dungeon_demo_strike
            else -> R.string.dungeon_demo_ready
        }
        pixels.restore()
        drawHud(pixels)

        canvas.drawColor(ink)
        if (width == 0 || height == 0) return
        // Le décor remplit la vue sans bandes ; sa hauteur logique suit celle de l'écran.
        val scale = width / 240f
        val left = 0f
        val top = 0f
        target.set(0f, 0f, width.toFloat(), worldHeight * scale)
        canvas.save()
        canvas.translate(gesture.shake(clock) * scale, 0f)
        canvas.drawBitmap(frame, null, target, paint)
        canvas.restore()
        // Les textes sont tracés à la résolution de l'écran, jamais dans le petit bitmap.
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        drawLabels(canvas, status)
        if (successful && hit in 0f.. .7f) {
            text(canvas, context.getString(R.string.dungeon_demo_damage, 42 - ratHp), 240f - (impactX + 3), enemyY + 5 - hit * 16, 12f, if (shattered) iceBlue else gold)
        }
        gesture.draw(canvas, clock, 240f, worldHeight, 240f - (heroX + 15), heroY + 18)
        canvas.restore()
        if (running && !paused) {
            if (inputLocked) postInvalidateOnAnimation() else postInvalidateDelayed(100)
        }
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    private fun triangle(t: Float, start: Float, peak: Float, end: Float): Float = when {
        t < start || t > end -> 0f
        t < peak -> (t - start) / (peak - start)
        else -> (end - t) / (end - peak)
    }

    private fun box(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }

    private fun line(c: Canvas, x: Float, y: Float, endX: Float, endY: Float, color: Int, thickness: Float = 1f) {
        paint.color = color
        paint.strokeWidth = thickness
        c.drawLine(x, y, endX, endY, paint)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float = 232f, shadowColor: Int = ink) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = size
        val measured = textPaint.measureText(value)
        if (measured > maxWidth) textPaint.textSize *= maxWidth / measured
        textPaint.color = shadowColor
        c.drawText(value, x + 1, y + 1, textPaint)
        textPaint.color = color
        c.drawText(value, x, y, textPaint)
    }

    private fun sprite(c: Canvas, bitmap: Bitmap, x: Float, y: Float, scale: Float = 2f) {
        c.save()
        c.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        c.scale(scale, scale)
        c.drawBitmap(bitmap, 0f, 0f, paint)
        c.restore()
    }

    private fun drawBackground(c: Canvas) {
        c.drawColor(0xFF1A2439.toInt())
        // Mur du fond moins haut : une grande surface de sol pour les combattants.
        for (row in 0..(floorY / 12).toInt()) for (col in -1..8) {
            val x = col * 32f + if (row % 2 == 0) 0f else 16f
            val y = row * 12f
            box(c, x, y, 30f, 10f, if ((row + col) % 3 == 0) 0xFF293A50.toInt() else 0xFF243348.toInt())
            box(c, x + 1, y, 28f, 1f, 0xFF35465A.toInt())
        }
        for (center in intArrayOf(52, 185)) {
            val top = floorY - 53
            box(c, center - 16f, top + 10, 32f, 43f, 0xFF0E1A2A.toInt())
            box(c, center - 11f, top + 4, 22f, 9f, 0xFF0E1A2A.toInt())
            box(c, center - 6f, top, 12f, 5f, 0xFF0E1A2A.toInt())
            for (bar in -1..1) box(c, center + bar * 9f, top + 12, 2f, 41f, 0xFF283348.toInt())
            for (side in intArrayOf(-1, 1)) {
                val x = center + side * 22f - 4
                box(c, x, top, 8f, 53f, 0xFF37455A.toInt())
                box(c, x, top, 2f, 53f, 0xFF526071.toInt())
                for (joint in 0..4) box(c, x, top + joint * 12, 8f, 1f, 0xFF222E44.toInt())
                box(c, x - 2, floorY - 3, 12f, 5f, 0xFF435367.toInt())
            }
        }
        for (i in 0..17) {
            val x = ((i * 43 + 13) % 238).toFloat()
            val length = 5f + i * 7 % 20
            box(c, x, 0f, 2f, length, 0xFF285353.toInt())
            box(c, x, 0f, 1f, length * .65f, 0xFF478477.toInt())
        }
        box(c, 0f, floorY + 2, 240f, worldHeight, 0xFF293B4B.toInt())
        for (row in 0..((worldHeight - floorY) / 16).toInt()) {
            val y = floorY + 4 + row * 16
            for (col in -1..6) {
                val x = col * 45f + if (row % 2 == 0) 0 else 22
                box(c, x, y, 43f, 14f, if ((col + row) % 3 == 0) 0xFF354B59.toInt() else 0xFF304452.toInt())
                box(c, x + 2, y, 39f, 1f, 0xFF425764.toInt())
                if ((col * 3 + row) % 4 == 0) {
                    line(c, x + 9, y + 5, x + 14, y + 8, 0xFF233644.toInt())
                    line(c, x + 14, y + 8, x + 12, y + 12, 0xFF233644.toInt())
                }
            }
        }
        // Eau derrière les acteurs ; les emplacements de combat restent sur les dalles.
        box(c, 87f, floorY + 4, 43f, 12f, 0xFF21424F.toInt())
        box(c, 90f, floorY + 6, 35f, 1f, 0xFF3D7177.toInt())
        for (i in 0..22) {
            val x = ((i * 61 + 5) % 234).toFloat()
            val range = (worldHeight - floorY - 16).toInt().coerceAtLeast(1)
            val y = floorY + 12 + i * 29 % range
            box(c, x, y, 4f, 2f, 0xFF456C61.toInt())
            box(c, x + 2, y - 1, 3f, 1f, 0xFF709079.toInt())
        }
        for (x in intArrayOf(14, 219)) {
            box(c, x - 2f, floorY - 31, 7f, 13f, ink)
            box(c, x.toFloat(), floorY - 31, 2f, 11f, 0xFF8B6950.toInt())
        }
        for (x in intArrayOf(0, 232)) {
            box(c, x.toFloat(), worldHeight - 36, 8f, 36f, 0xFF172439.toInt())
            box(c, x - 2f, worldHeight - 39, 12f, 4f, 0xFF425366.toInt())
        }
    }

    private fun drawAtmosphere(c: Canvas) {
        val tick = (clock / 140).toInt()
        for (x in intArrayOf(14, 219)) {
            val y = floorY - 41
            box(c, x - 4f, y, 10f, 12f, 0x228DC56E)
            box(c, x - 2f, y + 2, 7f, 9f, 0xFFBD704C.toInt())
            box(c, x - 1f, y + tick % 3, 4f, 10f - tick % 3, 0xFFECB15E.toInt())
            box(c, x.toFloat(), y + 4, 2f, 6f, 0xFFFFE4A0.toInt())
            box(c, x + (tick % 3).toFloat(), y - 5 - tick % 4, 1f, 1f, gold)
        }
        for (i in 0..3) {
            val x = 92f + (i * 9 + tick) % 29
            box(c, x, floorY + 7 + i * 2, 4f, 1f, 0xFF4C8187.toInt())
        }
    }
    private fun shadow(c: Canvas, x: Float, y: Float, width: Float) {
        box(c, x + 2, y - 1, width - 4, 3f, 0x66313B49)
        box(c, x, y, width, 2f, 0xAA172538.toInt())
    }

    private fun drawWarrior(c: Canvas, x: Float, y: Float, windup: Boolean, swing: Boolean, casting: Boolean, blocking: Boolean) {
        c.save()
        c.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        c.scale(1f, 1f)
        drawPaperDoll(c, windup, swing, casting, blocking, animateIdle = action == null)
        c.restore()
    }

    /** Trois quarts face : jambe/torse/casque, bras droit à l'arrière, bouclier devant à gauche. */
    private fun drawPaperDoll(c: Canvas, windup: Boolean, swing: Boolean, casting: Boolean, blocking: Boolean, animateIdle: Boolean = false) {
        val tones = if (gear(EquipSlot.CHEST).equipped) DungeonDemoSprites.metal(gear(EquipSlot.CHEST).hue)
            else intArrayOf(0xFFAA7964.toInt(), 0xFFD5AA89.toInt(), 0xFFE8C5A3.toInt())
        val bowOnBack = gear(EquipSlot.OFFHAND).equipped && gear(EquipSlot.OFFHAND).family == Archetype.ROGUE
        if (bowOnBack) {
            // Pointe derrière l'épaule libre, à l'opposé de l'arme ; le miroir de scène
            // s'applique ensuite à l'ensemble du personnage, arc compris.
            c.save()
            c.translate(9f, 17f)
            c.rotate(60f)
            c.scale(1.25f, 1.25f)
            sprite(c, gearBitmap(EquipSlot.OFFHAND), -12f, -4f, 1f)
            c.restore()
        }
        // Bassin et jambes sous les pièces, pas de jambes redessinées par-dessus les bottes.
        box(c, 8f, 22f, 12f, 6f, ink)
        box(c, 9f, 23f, 4f, 7f, tones[0])
        box(c, 16f, 23f, 4f, 7f, tones[0])
        wornPiece(c, EquipSlot.BOOTS, 6f, 26f)
        // Bras court : raccord arrondi au coude, poignée au point (8, 2).
        c.save()
        c.translate(19f, 15f)
        c.rotate(when { swing -> 70f; windup -> -55f; casting -> -25f; else -> 8f })
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(3f, 4f)
        path.lineTo(8f, 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ink
        paint.strokeWidth = 5f
        c.drawPath(path, paint)
        paint.color = tones[1]
        paint.strokeWidth = 3f
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeCap = Paint.Cap.BUTT
        c.save()
        if (animateIdle) c.rotate(sin(clock / 900f) * 4f, 8f, 2f)
        wornPiece(c, EquipSlot.WEAPON, 3f, -15f)
        c.restore()
        box(c, 6f, 1f, 4f, 3f, tones[1])
        box(c, 7f, 1f, 2f, 1f, tones[2])
        c.restore()
        wornPiece(c, EquipSlot.CHEST, 7f, 12f)
        c.save()
        if (animateIdle) {
            val nod = triangle(clock - nodStart, 0f, 220f, 600f)
            c.rotate(nod * 6f, 14f, 12f)
        }
        wornPiece(c, EquipSlot.HELMET, 6f, 1f)
        c.restore()
        // Bras proche visible entre l'épaule et le bouclier, celui-ci passe DEVANT le torse.
        val shieldX = if (blocking) 10f else 0f
        val shieldY = if (blocking) 11f else 14f
        if (bowOnBack || !gear(EquipSlot.OFFHAND).equipped) {
            // Bras libre au repos : aucune prise ni animation de bouclier pour l'arc rangé.
            line(c, 8f, 18f, 6f, 24f, ink, 5f)
            line(c, 8f, 18f, 6f, 24f, 0xFFD5AA89.toInt(), 3f)
            box(c, 5f, 23f, 3f, 3f, 0xFFD5AA89.toInt())
        } else {
            line(c, 8f, 18f, shieldX + 6, shieldY + 5, ink, 5f)
            line(c, 8f, 18f, shieldX + 6, shieldY + 5, tones[1], 3f)
            wornPiece(c, EquipSlot.OFFHAND, shieldX, shieldY)
        }
    }

    private fun wornPiece(c: Canvas, slot: EquipSlot, x: Float, y: Float) {
        val item = gear(slot)
        sprite(c, gearBitmap(slot), x + if (item.equipped) DungeonClassSprites.offsetX(item) else 0f,
            y + (if (item.equipped) DungeonClassSprites.offsetY(item) else 0f), 1f)
    }

    private fun drawRat(c: Canvas, x: Float, y: Float, icy: Boolean, hurt: Boolean) {
        c.save()
        c.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        c.scale(1f, 1f)
        val twitch = if (icy) 0f else ((clock / 260).toInt() % 3 - 1).toFloat()
        // Racine sous la croupe (26,11), puis courbe au ras du sol, sur la grille du corps.
        path.reset()
        path.moveTo(26f, 11f)
        path.lineTo(30f, 14f)
        path.lineTo(34f, 16f)
        path.lineTo(38f, 16f)
        path.lineTo(41f, 14f + twitch)
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        c.drawPath(path, paint)
        paint.color = 0xFFBB8185.toInt()
        paint.strokeWidth = 1f
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        sprite(c, rat, 0f, 0f, 1f)
        // Traits courts sur la joue ; aucune pointe claire devant le nez rose.
        line(c, 4f, 10f, 7f, 9f, 0xFF958C85.toInt())
        line(c, 5f, 12f, 8f, 12f, 0xFF958C85.toInt())
        if (hurt) {
            line(c, 8f, 8f, 10f, 10f, white)
            line(c, 8f, 10f, 10f, 8f, white)
        } else if (!icy && (clock.toInt() % 3200) < 150) {
            box(c, 8f, 8f, 3f, 3f, 0xFF86746F.toInt())
            box(c, 8f, 9f, 3f, 1f, ink)
        }
        c.restore()
    }
    private fun crystal(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        path.reset()
        path.moveTo(x, y - size)
        path.lineTo(x + size * .45f, y)
        path.lineTo(x, y + size * .6f)
        path.lineTo(x - size * .4f, y)
        path.close()
        paint.color = color
        c.drawPath(path, paint)
        line(c, x, y - size + 1, x, y + size * .4f, white)
    }

    private fun drawIce(c: Canvas, centerX: Float, centerY: Float) {
        c.save()
        c.translate(centerX, centerY)
        c.scale(.5f, .5f)
        val x = 0f
        val y = 0f
        crystal(c, x - 19, y + 8, 18f, 0xAA57A9CD.toInt())
        crystal(c, x + 19, y + 9, 23f, 0xBB438CB5.toInt())
        crystal(c, x + 1, y + 15, 15f, 0xAA8DCFDA.toInt())
        line(c, x - 26, y + 19, x + 28, y + 19, iceBlue, 2f)
        line(c, x - 16, y - 13, x - 4, y - 18, iceBlue)
        c.restore()
    }

    private fun drawProjectile(c: Canvas, progress: Float, targetX: Float, targetY: Float) {
        val x = heroX + 28 + progress * (targetX - heroX - 28)
        val y = heroY + 16 + progress * (targetY - heroY - 16) - sin(progress * PI).toFloat() * 12
        for (i in 1..5) box(c, x - i * 3, y + i, 2f, 1f, 0xFF468AA7.toInt())
        crystal(c, x, y, 4.5f, iceBlue)
    }

    private fun drawSlash(c: Canvas, p: Float, targetX: Float, targetY: Float) {
        // Arc en segments sur grille, bref et sans masquer toute la cible.
        for (i in 0..12) {
            val angle = -.9f + (p * 1.6f) - i * .085f
            val x = targetX - 14 + cos(angle) * 16
            val y = targetY + sin(angle) * 12
            box(c, x.roundToInt().toFloat(), y.roundToInt().toFloat(), if (i < 5) 3f else 2f, 3f,
                if (i < 5) white else gold)
        }
    }

    private fun fragments(c: Canvas, x: Float, y: Float, progress: Float, ice: Boolean) {
        if (progress !in 0f..1f) return
        for (i in 0 until 12) {
            val angle = i * (PI * 2 / 12)
            val distance = progress * (10 + i % 4 * 5)
            val dx = x + (cos(angle) * distance).toFloat()
            val dy = y + (sin(angle) * distance).toFloat() + progress * progress * 12
            if (ice) crystal(c, dx.roundToInt().toFloat(), dy.roundToInt().toFloat(), (1 - progress) * 3 + 1, iceBlue)
            else box(c, dx.roundToInt().toFloat(), dy.roundToInt().toFloat(), 2f, 2f, if (i % 2 == 0) gold else white)
        }
    }

    private fun drawHud(c: Canvas) {
        if (portraitDirty) {
            portrait.eraseColor(android.graphics.Color.TRANSPARENT)
            drawPaperDoll(Canvas(portrait), false, false, false, false)
            portraitDirty = false
        }
        // File au sommet, à côté de Retour ; le décor continue derrière.
        for (i in 0..3) {
            val x = 82f + i * 27
            box(c, x, 6f, 23f, 24f, if (i == 0) gold else 0xFF42566B.toInt())
            box(c, x + 1, 7f, 21f, 22f, ink)
            c.save()
            c.clipRect(x + 2, 8f, x + 21, 28f)
            c.translate(2 * x + 23, 0f)
            c.scale(-1f, 1f)
            sprite(c, if (i % 2 == 0) portrait else rat, x - 4, 9f, 1f)
            c.restore()
        }
        for (i in 0 until mobCount) {
            val x = 240f - (ratXs[i] - 9 + 52)
            val y = ratYs[i] + 24
            box(c, x, y, 52f, 6f, if (i == selectedRat) 0xFF91A9B5.toInt() else ink)
            box(c, x + 1, y + 1, 50f, 4f, ink)
            box(c, x + 1, y + 1, 50f * health[i] / 42, 4f, 0xFFAA505D.toInt())
            box(c, x + 1, y + 1, 50f * health[i] / 42, 1f, 0xFFDD897D.toInt())
        }
    }

    /** Les glyphes restent à la résolution de l'écran, même avec les petits personnages. */
    private fun drawLabels(c: Canvas, status: Int) {
        for (i in 0 until mobCount) {
            text(c, context.getString(R.string.dungeon_demo_health, health[i], 42), 240f - (ratXs[i] + 17), ratYs[i] + 29, 5.5f, android.graphics.Color.BLACK, 50f, white)
        }
        if (!gesture.active && (action != null || frozen)) {
            text(c, labels.getValue(status), 120f, worldHeight - 8, 8f,
                if (status == R.string.dungeon_demo_frozen || status == R.string.dungeon_demo_fracas) iceBlue else gold)
        }
    }
}
