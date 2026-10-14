package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** Preserve the painted region's aspect ratio on phones and tablets. */
private fun drawRegion(c: Canvas, bitmap: Bitmap?, width: Int, height: Int, paint: Paint, rect: RectF) {
    paint.shader = null; paint.style = Paint.Style.FILL; paint.alpha = 255
    if (bitmap == null) { c.drawColor(0xFF54845D.toInt()); return }
    val k = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    rect.set((width - bitmap.width * k) / 2, (height - bitmap.height * k) / 2,
        (width + bitmap.width * k) / 2, (height + bitmap.height * k) / 2)
    c.drawBitmap(bitmap, null, rect, paint)
}

/** Peinture et animation uniquement ; la résolution du combat reste dans le moteur Kotlin pur. */
class HamletBattleView(context: Context, val battle: HamletBattle, private val assets: HamletAssets,
    private val label: (String, Array<out Any>) -> String, private val haptics: Boolean,
    private val completed: (HamletBattle) -> Unit) : View(context) {
    var speed = 1
    var paused = false
    var effectsEnabled = true
    private var last = 0L
    private var attached = false
    private var finished = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val spots = HashMap<Int, PointF>()
    private val buttons = HashMap<Int, RectF>()
    private val dp = resources.displayMetrics.density
    private val party = battle.units.filter { !it.enemy && !it.summoned }.take(6)
    private var backdropShade: Shader? = null
    private val statusLabels = arrayOf(context.getString(R.string.hamlet_combat_status_stun),
        context.getString(R.string.hamlet_combat_status_poison), context.getString(R.string.hamlet_combat_status_buff),
        context.getString(R.string.hamlet_combat_status_weak))
    private val statusColors = intArrayOf(0xFFFFCD83.toInt(), 0xFFB7DF8E.toInt(), 0xFFFFE29F.toInt(), 0xFFEDADA0.toInt())
    private val statusActive = BooleanArray(4)
    private val fieldLabel = context.getString(when (battle.field) {
        BattleField.MEADOW -> R.string.hamlet_combat_field_meadow
        BattleField.MIST -> R.string.hamlet_combat_field_mist
        BattleField.QUARRY -> R.string.hamlet_combat_field_quarry
        BattleField.EMBER -> R.string.hamlet_combat_field_ember
        BattleField.MARSH -> R.string.hamlet_combat_field_marsh
        BattleField.FROST -> R.string.hamlet_combat_field_frost
    })
    init {
        assets.get("region", battle.region)
        battle.units.forEach { HamletCharacterArt.prewarm(it.heroVisual, it.sprite) }
        HamletCharacterArt.prewarm(false, 0) // Summons can enter after the battle starts.
        if (battle.pet >= 0) HamletCharacterArt.prewarm(false, intArrayOf(0, 5, 7)[battle.pet])
    }
    private val loop = object : Runnable {
        override fun run() {
            if (!attached || !hasWindowFocus()) { last = 0; return }
            val now = System.nanoTime()
            if (last > 0 && !paused) battle.advance(((now - last) / 1e9).coerceIn(0.0, 0.1).toFloat() * speed)
            last = now; invalidate()
            if (battle.outcome != BattleOutcome.RUNNING && !finished) { finished = true; post { completed(battle) }; return }
            postOnAnimation(this)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attached = true; last = 0; postOnAnimation(loop) }
    override fun onDetachedFromWindow() { attached = false; removeCallbacks(loop); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus); removeCallbacks(loop); last = 0; if (focus && attached && !finished) postOnAnimation(loop) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdropShade = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x8811202C.toInt(), 0x0011202C, 0xEE11202C.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        paint.color = color; paint.textSize = size * dp; paint.textAlign = align; paint.typeface = Typeface.DEFAULT_BOLD; paint.style = Paint.Style.FILL
        c.drawText(value, x, y, paint)
    }
    override fun onDraw(c: Canvas) {
        drawRegion(c, assets.get("region", battle.region), width, height, paint, rect)
        paint.shader = backdropShade
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
        for (u in battle.units) {
            val column = if (u.front) 1 else 0
            val row = if (u.front) u.slot else (u.slot - 2) % 4
            var x = width * (if (u.enemy) 0.68f + column * -0.08f else 0.18f + column * 0.14f)
            val y = height * (if (u.front) 0.43f + row * 0.20f else 0.32f + row * 0.135f) + if (u.summoned) 18f * dp else 0f
            if (effectsEnabled) x += (if (u.enemy) -1 else 1) * sin((u.lunge / 0.22f) * PI).toFloat() * 22f * dp
            val size = minOf(width * 0.21f, height * 0.27f) * (if (u.maxHp > 1500 && u.enemy && u.slot == 0) 1.25f else if (u.summonLeft > 0) 0.7f else 1f)
            spots.getOrPut(u.index) { PointF() }.set(x, y - size * 0.4f)
            if (!u.alive) {
                HamletCharacterArt.draw(c, u.heroVisual, u.sprite, x, y, size, pose = HamletCharacterArt.Pose.DOWN, mirrored = u.enemy, alpha = 95)
                continue
            }
            paint.color = 0x44312D24; c.drawOval(x - size * 0.28f, y - 6f * dp, x + size * 0.28f, y + 3f * dp, paint)
            val castEvent = battle.events.lastOrNull { it.source == u.index && it.kind == BattleEventKind.SKILL && battle.time - it.time < 0.57f }
            val pose = when {
                !effectsEnabled -> HamletCharacterArt.Pose.IDLE
                u.flash > 0 -> HamletCharacterArt.Pose.HURT
                castEvent != null -> HamletCharacterArt.Pose.CAST
                u.lunge > 0 -> HamletCharacterArt.Pose.ATTACK
                else -> HamletCharacterArt.Pose.IDLE
            }
            val poseTime = when (pose) {
                HamletCharacterArt.Pose.HURT -> 0.16f - u.flash
                HamletCharacterArt.Pose.CAST -> battle.time - (castEvent?.time ?: battle.time)
                HamletCharacterArt.Pose.ATTACK -> 0.22f - u.lunge
                else -> battle.time + u.index * 0.31f
            }
            HamletCharacterArt.draw(c, u.heroVisual, u.sprite, x, y, size, if (effectsEnabled) poseTime else 0f, pose, u.enemy)
            if (u.shield > 0) {
                paint.color = 0x775CDBF3; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * dp
                c.drawOval(x - size * 0.32f, y - size * 0.88f, x + size * 0.32f, y, paint); paint.style = Paint.Style.FILL
            }
            if (u.stun > 0) for (i in 0..2) {
                val angle = (if (effectsEnabled) battle.time * 4 else 0f) + i * 2.094f
                HamletGlyphs.draw(c, "star", x + kotlin.math.cos(angle) * size * 0.2f, y - size * 0.88f + sin(angle) * 3f * dp, 6f * dp)
            }
            val hpY = y - size * 0.98f
            val bar = size * 0.56f
            paint.color = 0xCC203128.toInt(); c.drawRoundRect(x - bar / 2, hpY, x + bar / 2, hpY + 4f * dp, 2f * dp, 2f * dp, paint)
            paint.color = if (u.enemy) 0xFFCD785A.toInt() else 0xFF91D282.toInt()
            c.drawRoundRect(x - bar / 2, hpY, x - bar / 2 + bar * (u.hp / u.maxHp).toFloat(), hpY + 4f * dp, 2f * dp, 2f * dp, paint)
            statusActive[0] = u.stun > 0; statusActive[1] = u.poison > 0 || u.venom > 0
            statusActive[2] = u.buff > 0; statusActive[3] = u.weakened > 0
            val count = statusActive.count { it }
            var badge = 0
            for (status in statusActive.indices) if (statusActive[status]) {
                val bx = x + (badge++ - (count - 1) / 2f) * 24f * dp
                paint.color = 0xDD233A42.toInt()
                c.drawRoundRect(bx - 11f * dp, hpY + 6f * dp, bx + 11f * dp, hpY + 17f * dp, 3f * dp, 3f * dp, paint)
                text(c, statusLabels[status], bx, hpY + 14f * dp, 7f, statusColors[status])
            }
        }
        var warningRow = 0
        for (warning in battle.warnings()) {
            for (index in warning.targets) spots[index]?.let { p ->
                paint.color = 0x44ED7955; c.drawOval(p.x - 22f * dp, p.y - 4f * dp, p.x + 22f * dp, p.y + 8f * dp, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * dp; paint.color = 0xFFEC9A6F.toInt()
                c.drawOval(p.x - 22f * dp, p.y - 4f * dp, p.x + 22f * dp, p.y + 8f * dp, paint)
                paint.style = Paint.Style.FILL
            }
            val title = context.getString(when (warning.pattern) {
                BossPattern.CLEAVE -> R.string.hamlet_combat_boss_cleave
                BossPattern.DEVASTATION -> R.string.hamlet_combat_boss_devastation
                BossPattern.DREAD -> R.string.hamlet_combat_boss_dread
            })
            text(c, context.getString(R.string.hamlet_combat_boss_warning, title, warning.left),
                width / 2f, (59f + warningRow++ * 16f) * dp, 11f, 0xFFFFC59C.toInt())
        }
        for (event in battle.events) {
            val source = spots[event.source]; val target = spots[event.target] ?: continue
            val age = battle.time - event.time
            val alpha = (1 - age / 1.4f).coerceIn(0f, 1f)
            if (effectsEnabled && source != null && event.kind == BattleEventKind.HIT && age < 0.18f) {
                paint.color = Color.argb((alpha * 200).toInt(), 255, 219, 138); paint.strokeWidth = 2f * dp
                c.drawLine(source.x, source.y, target.x, target.y, paint)
            }
            if (effectsEnabled && event.kind == BattleEventKind.SKILL && age < 0.7f) {
                paint.color = Color.argb((alpha * 170).toInt(), 230, 201, 109); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * dp
                c.drawCircle(target.x, target.y, (10 + age * 36) * dp, paint); paint.style = Paint.Style.FILL
            }
            if (event.value > 0 && (event.kind == BattleEventKind.HIT || event.kind == BattleEventKind.HEAL)) text(c,
                label(if (event.kind == BattleEventKind.HEAL) "heal_float" else "damage_float", arrayOf(event.value)),
                target.x, target.y - 25f * dp - (if (effectsEnabled) age * 20f * dp else 0f), 12f,
                Color.argb((alpha * 255).toInt(), if (event.kind == BattleEventKind.HEAL) 142 else 255, 245, if (event.kind == BattleEventKind.HEAL) 165 else 215))
        }
        text(c, label("combat_clock", arrayOf(battle.time.toInt())), width / 2f, 24f * dp, 14f, 0xFFF9E7B2.toInt())
        text(c, fieldLabel, width / 2f, 40f * dp, 10f, 0xFFCBDDD0.toInt())
        if (battle.pet >= 0) HamletCharacterArt.draw(c, false, intArrayOf(0, 5, 7)[battle.pet], width * 0.1f, height * 0.86f, 40f * dp, if (effectsEnabled) battle.time else 0f)
        val heroes = party
        val spacing = width.toFloat() / maxOf(1, heroes.size)
        for ((i, u) in heroes.withIndex()) {
            val x = spacing * (i + 0.5f); val y = height - 8f * dp; val side = minOf(48f * dp, spacing - 4f * dp)
            val ready = u.ultimateLeft <= 0 && u.alive && u.stun <= 0
            val bounds = buttons.getOrPut(u.index) { RectF() }.apply { set(x - side / 2, y - side, x + side / 2, y) }
            paint.color = if (ready) 0xFFE0B451.toInt() else 0xCC263F4C.toInt(); c.drawRoundRect(bounds, 8f * dp, 8f * dp, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (ready) 2f else 1f) * dp
            paint.color = if (ready) 0xFFFFE8A1.toInt() else 0x886E8B96.toInt()
            c.drawRoundRect(bounds, 8f * dp, 8f * dp, paint); paint.style = Paint.Style.FILL
            HamletCharacterArt.draw(c, true, u.sprite, x, y, side, if (effectsEnabled) battle.time + u.index else 0f,
                if (u.alive) HamletCharacterArt.Pose.IDLE else HamletCharacterArt.Pose.DOWN)
            if (!ready) {
                paint.color = 0x990D2230.toInt(); c.drawRoundRect(bounds, 8f * dp, 8f * dp, paint)
                if (u.alive) text(c, label("cooldown", arrayOf(maxOf(0, ceil(u.ultimateLeft).toInt()))), x, y - 5f * dp, 10f, 0xFFE9DDBE.toInt())
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            buttons.entries.firstOrNull { it.value.contains(event.x, event.y) }?.let {
                if (battle.ultimate(it.key) && haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            performClick(); invalidate()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

class HamletAdventureMapView(context: Context, private val assets: HamletAssets, var page: Int,
    val adventure: HamletAdventure, private val number: (Int) -> String, private val select: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val points = ArrayList<Pair<Int, PointF>>()
    private val destination = RectF()
    private val dp = resources.displayMetrics.density
    private var layoutPage = -1
    private var layoutWidth = -1
    private var layoutHeight = -1
    var selected = adventure.frontier
    override fun onDraw(c: Canvas) {
        drawRegion(c, assets.get("region", adventure.region(page * 12)), width, height, paint, destination)
        paint.color = 0x66203031; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (layoutPage != page || layoutWidth != width || layoutHeight != height) {
            points.clear(); layoutPage = page; layoutWidth = width; layoutHeight = height
            for (i in 0 until 12) {
                val row = i / 4; val col = if (row % 2 == 0) i % 4 else 3 - i % 4
                val stage = page * 12 + i
                points.add(stage to PointF(width * (0.13f + col * 0.247f), height * (0.2f + row * 0.3f)))
            }
        }
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 1 until points.size) {
            val a = points[i - 1].second; val b = points[i].second
            paint.strokeWidth = dp * 7f; paint.color = 0x77243537
            c.drawLine(a.x, a.y + 2f * dp, b.x, b.y + 2f * dp, paint)
            paint.strokeWidth = dp * 3f
            paint.color = if (points[i].first <= adventure.frontier) 0xFFE5C377.toInt() else 0x88677B7A.toInt()
            c.drawLine(a.x, a.y, b.x, b.y, paint)
        }
        val r = minOf(width / 17f, height / 10f)
        for ((stage, p) in points) {
            val available = stage <= adventure.frontier
            val cleared = (adventure.stars[stage] ?: 0) > 0
            paint.color = 0x8830443C.toInt(); c.drawCircle(p.x, p.y + 2f * dp, r + dp, paint)
            paint.color = when { cleared -> 0xFFB9DBBA.toInt(); stage == adventure.frontier -> 0xFFFFD477.toInt(); available -> 0xFFEDD8A4.toInt(); else -> 0xFF697D7A.toInt() }
            c.drawCircle(p.x, p.y, r, paint)
            paint.color = if (stage == selected) 0xFFFFF3CE.toInt() else 0xFF364B4D.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = if (stage == selected) 2.5f * dp else dp
            c.drawCircle(p.x, p.y, r + dp, paint); paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = r * 0.82f
            paint.color = if (available) 0xFF233C3D.toInt() else 0xFFB5C2B8.toInt()
            c.drawText(number(stage + 1), p.x, p.y + r * 0.3f, paint)
            val kind = adventure.node(stage)
            if (kind == AdventureNode.BOSS) HamletGlyphs.draw(c, "star", p.x, p.y - r * 1.55f, r * 0.8f)
            else if (kind == AdventureNode.RESCUE) HamletGlyphs.draw(c, "gift", p.x, p.y - r * 1.55f, r * 0.8f)
            else if (kind == AdventureNode.GATHER) HamletGlyphs.draw(c, "log", p.x, p.y - r * 1.55f, r * 0.7f)
            val stars = adventure.stars[stage] ?: 0
            for (j in 0 until stars) HamletGlyphs.draw(c, "star", p.x + (j - 1) * r * 0.55f, p.y + r * 1.4f, r * 0.44f)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val r = maxOf(minOf(width / 17f, height / 10f) + 4f * dp, 22f * dp)
            points.firstOrNull { (_, p) -> (p.x - event.x) * (p.x - event.x) + (p.y - event.y) * (p.y - event.y) < r * r }?.let { selected = it.first; select(it.first); invalidate() }
            performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Huit ressources à cueillir : une petite activité gratuite pour rapporter du matériel et du travail. */
class HamletGatherView(context: Context, private val assets: HamletAssets, private val region: Int,
    private val haptics: Boolean, private val finished: () -> Unit) : View(context) {
    var effectsEnabled = true
    var gathered = 0
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val glyphs = arrayOf("log", "ore", "fruit")
    private val dp = resources.displayMetrics.density
    private val locations = arrayOf(0.22f to 0.6f, 0.64f to 0.52f, 0.42f to 0.74f, 0.8f to 0.76f, 0.32f to 0.44f, 0.6f to 0.82f, 0.82f to 0.46f, 0.18f to 0.82f)
    override fun onDraw(c: Canvas) {
        drawRegion(c, assets.get("region", region), width, height, paint, destination)
        // Eight trail stones make this short activity's progress visible without another dialog.
        for (i in locations.indices) {
            val px = width / 2f + (i - 3.5f) * 16f * dp
            paint.color = 0x88213936.toInt(); c.drawCircle(px, 18f * dp, 6f * dp, paint)
            paint.color = if (i < gathered) 0xFFFFD985.toInt() else 0xFF78988A.toInt()
            c.drawCircle(px, 18f * dp, 3.5f * dp, paint)
        }
        if (gathered < locations.size) {
            val (x, y) = locations[gathered]
            val r = 26f * dp
            paint.color = 0x99213936.toInt(); c.drawCircle(x * width, y * height + 3f * dp, r + 2f * dp, paint)
            paint.color = 0x99FFF1B2.toInt(); c.drawCircle(x * width, y * height, r, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * dp; paint.color = 0xFFFFE2A0.toInt()
            c.drawCircle(x * width, y * height, r, paint); paint.style = Paint.Style.FILL
            HamletGlyphs.draw(c, glyphs[gathered % 3], x * width, y * height, r * 1.3f)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && gathered < locations.size) {
            val (x, y) = locations[gathered]; val radius = 44f * dp
            if ((event.x - x * width) * (event.x - x * width) + (event.y - y * height) * (event.y - y * height) <= radius * radius) {
                gathered++; if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (effectsEnabled) { animate().cancel(); alpha = 0.75f; animate().alpha(1f).setDuration(180).start() }
                if (gathered == locations.size) post(finished)
            }
            invalidate(); performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
