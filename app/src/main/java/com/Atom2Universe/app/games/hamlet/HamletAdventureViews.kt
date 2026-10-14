package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.SparseArray
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** Peinture et animation uniquement ; la résolution du combat reste dans le moteur Kotlin pur. */
class HamletBattleView(context: Context, val battle: HamletBattle,
    private val label: (String, Array<out Any>) -> String, private val haptics: Boolean,
    private val completed: (HamletBattle) -> Unit) : View(context) {
    var speed = 1
    var paused = false
    var effectsEnabled = true
    private var last = 0L
    private var attached = false
    private var finished = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; typeface = Typeface.DEFAULT_BOLD }
    private val scene = HamletScene(battle.region)
    private val fx = HamletBattleFx()
    private val foot = PointF()
    /** Where every fighter stands this frame (and the pet, last), by position in the unit list. */
    private var order = IntArray(16)
    private var footX = FloatArray(16)
    private var footY = FloatArray(16)
    private var depth = FloatArray(16)
    private var drawX = FloatArray(16)
    private var drawSize = FloatArray(16)
    private val spots = HashMap<Int, PointF>()
    private val buttons = HashMap<Int, RectF>()
    private val arc = RectF()
    private val dp = resources.displayMetrics.density
    /** Text follows the game's font scale, which is larger on tablets. */
    private val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
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
    /** Skill and ultimate names by role, hero names by id: looked up once, never while drawing. */
    private val skillNames = Array(HeroRole.entries.size) { label("skill_${HeroRole.entries[it].name.lowercase()}_0", emptyArray()) }
    private val ultimateNames = Array(HeroRole.entries.size) { label("skill_${HeroRole.entries[it].name.lowercase()}_3", emptyArray()) }
    private val heroNames = Array(HamletHeroes.defs.size) { label("hero_$it", emptyArray()) }
    private val separator = context.getString(R.string.hamlet_ui_dot)
    private val damageTexts = SparseArray<String>()
    private val critTexts = SparseArray<String>()
    private val healTexts = SparseArray<String>()
    private val clockTexts = SparseArray<String>()
    private val cooldownTexts = SparseArray<String>()
    private fun cached(cache: SparseArray<String>, key: String, value: Int): String {
        cache.get(value)?.let { return it }
        if (cache.size() > 400) cache.clear()
        return label(key, arrayOf(value)).also { cache.put(value, it) }
    }
    /** Enemies wear their region's creatures; heroes and summons keep their own art. */
    private fun art(u: BattleUnit) = if (u.heroVisual) u.sprite else if (u.summoned) HamletEnemies.SUMMON
        else HamletEnemies.visual(u.sprite, battle.region)
    init {
        battle.units.forEach { HamletCharacterArt.prewarm(it.heroVisual, art(it)) }
        HamletCharacterArt.prewarm(false, HamletEnemies.SUMMON) // Summons can enter after the battle starts.
        if (battle.pet >= 0) HamletCharacterArt.prewarm(false, HamletEnemies.pet(battle.pet))
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
            intArrayOf(0x6611202C, 0x0011202C, 0x0011202C, 0xDD11202C.toInt()), floatArrayOf(0f, .2f, .8f, 1f), Shader.TileMode.CLAMP)
        scene.layout(w, h)
    }
    /** Bold text with a dark rim, readable over any ground. */
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER, rim: Boolean = true) {
        paint.color = color; paint.textSize = size * sp; paint.textAlign = align; paint.typeface = Typeface.DEFAULT_BOLD; paint.style = Paint.Style.FILL
        if (rim) {
            outline.textSize = paint.textSize; outline.textAlign = align; outline.strokeWidth = maxOf(2f, paint.textSize * .16f)
            outline.color = (((color ushr 24) * 3 / 4) shl 24) or 0x14100C
            c.drawText(value, x, y, outline)
        }
        c.drawText(value, x, y, paint)
    }
    /** A summon takes the first or second vanguard spot of its camp, alternately. */
    private fun vanguard(i: Int): Int {
        val u = battle.units[i]; var n = 0
        for (j in 0 until i) { val o = battle.units[j]; if (o.summoned && o.enemy == u.enemy) n++ }
        return n % 2
    }
    /** Position in the unit list of the fighter with this [index], or -1. */
    private fun position(index: Int): Int {
        val units = battle.units
        for (i in units.indices) if (units[i].index == index) return i
        return -1
    }
    /** The skill cast that produced [hit], if it was one. */
    private fun castOf(hit: BattleEvent): BattleEvent? {
        val events = battle.events
        for (k in events.indices) {
            val e = events[k]
            if (e.kind == BattleEventKind.SKILL && e.source == hit.source && kotlin.math.abs(e.time - hit.time) < 1e-4f) return e
        }
        return null
    }
    private fun handX(i: Int) = drawX[i] + (if (battle.units[i].enemy) -1 else 1) * drawSize[i] * .22f
    private fun handY(i: Int) = footY[i] - drawSize[i] * .55f
    private fun chestY(i: Int) = footY[i] - drawSize[i] * .45f

    override fun onDraw(c: Canvas) {
        val time = if (effectsEnabled) battle.time else 0f
        scene.layout(width, height)
        scene.drawBack(c, time)
        fx.px = HamletArena.pixel(width, height)
        paint.shader = backdropShade
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
        val units = battle.units
        val events = battle.events
        val count = units.size + if (battle.pet >= 0) 1 else 0
        if (order.size < count) {
            order = IntArray(count + 8); footX = FloatArray(count + 8); footY = FloatArray(count + 8)
            depth = FloatArray(count + 8); drawX = FloatArray(count + 8); drawSize = FloatArray(count + 8)
        }
        val base = HamletArena.size(width, height)
        for (i in units.indices) {
            val u = units[i]
            depth[i] = HamletArena.place(u.enemy, u.slot, if (u.summoned) vanguard(i) else -1, width, height, foot)
            footX[i] = foot.x; footY[i] = foot.y
            drawX[i] = foot.x + if (effectsEnabled) (if (u.enemy) -1 else 1) * sin((u.lunge / 0.22f) * PI).toFloat() * 22f * dp else 0f
            drawSize[i] = base * depth[i] * (if (u.boss) 1.25f else if (u.summonLeft > 0) 0.7f else 1f)
            spots.getOrPut(u.index) { PointF() }.set(drawX[i], footY[i] - drawSize[i] * 0.4f)
        }
        if (battle.pet >= 0) { depth[units.size] = HamletArena.pet(width, height, foot); footX[units.size] = foot.x; footY[units.size] = foot.y }
        for (i in 0 until count) {
            var j = i
            while (j > 0 && footY[order[j - 1]] > footY[i]) { order[j] = order[j - 1]; j-- }
            order[j] = i
        }
        // An ultimate dims the field for a moment so its caster stands out.
        if (effectsEnabled) for (k in events.indices) {
            val e = events[k]
            if (e.kind != BattleEventKind.SKILL || e.value != 1) continue
            val age = battle.time - e.time
            if (age < .45f) { paint.color = ((90 * (1 - age / .45f)).toInt() shl 24) or 0x0A0A14; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint) }
        }
        // Behind the fighters: casting auras, pillars and summoning circles.
        if (effectsEnabled) for (k in events.indices) {
            val e = events[k]
            val age = battle.time - e.time
            if (e.kind == BattleEventKind.SKILL) {
                val i = position(e.source); if (i < 0) continue
                fx.cast(c, units[i].role, drawX[i], footY[i], drawSize[i], age, e.value == 1)
            } else if (e.kind == BattleEventKind.SUMMON) {
                val i = position(e.target); if (i < 0) continue
                fx.summon(c, drawX[i], footY[i], drawSize[i], age)
            }
        }
        for (k in 0 until count) {
            val i = order[k]
            if (i < units.size) unit(c, units[i], i, time)
            else HamletCharacterArt.draw(c, false, HamletEnemies.pet(battle.pet), footX[i], footY[i], base * .5f * depth[i], time)
        }
        // In front: projectiles, impacts, heals and shields.
        if (effectsEnabled) for (k in events.indices) {
            val e = events[k]
            val age = battle.time - e.time
            val t = position(e.target); if (t < 0) continue
            when (e.kind) {
                BattleEventKind.HIT -> {
                    val s = position(e.source); if (s < 0) continue
                    val cast = castOf(e)
                    if (cast != null) fx.skill(c, units[s].role, handX(s), handY(s), drawX[t], chestY(t), drawSize[t], age, cast.value == 1)
                    else fx.attack(c, units[s].role, handX(s), handY(s), drawX[t], chestY(t), drawSize[t], age)
                }
                BattleEventKind.HEAL -> fx.heal(c, drawX[t], chestY(t), drawSize[t], age)
                BattleEventKind.SHIELD -> if (e.value > 0) fx.shield(c, drawX[t], chestY(t), drawSize[t], age)
                else -> Unit
            }
        }
        for (i in units.indices) overlay(c, units[i], i, time)
        scene.drawFront(c, time)
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
                width / 2f, 62f * sp + warningRow++ * 17f * sp, 12f, 0xFFFFC59C.toInt())
        }
        numbers(c)
        labels(c)
        text(c, cached(clockTexts, "combat_clock", battle.time.toInt()), width / 2f, 24f * sp, 15f, 0xFFF9E7B2.toInt())
        text(c, fieldLabel, width / 2f, 41f * sp, 11.5f, 0xFFCBDDD0.toInt())
        ultimates(c)
    }

    private fun unit(c: Canvas, u: BattleUnit, i: Int, time: Float) {
        if (u.summoned && !u.alive) return
        val x = drawX[i]; val y = footY[i]; val size = drawSize[i]
        if (!u.alive) {
            HamletCharacterArt.draw(c, u.heroVisual, art(u), x, y, size, pose = HamletCharacterArt.Pose.DOWN, mirrored = u.enemy, alpha = 95)
            return
        }
        paint.color = 0x44312D24; c.drawOval(x - size * 0.28f, y - 6f * dp, x + size * 0.28f, y + 3f * dp, paint)
        var cast: BattleEvent? = null
        val events = battle.events
        var k = events.size - 1
        while (k >= 0) {
            val e = events[k--]
            if (e.source == u.index && e.kind == BattleEventKind.SKILL && battle.time - e.time < 0.57f) { cast = e; break }
        }
        val pose = when {
            !effectsEnabled -> HamletCharacterArt.Pose.IDLE
            u.flash > 0 -> HamletCharacterArt.Pose.HURT
            cast != null -> HamletCharacterArt.Pose.CAST
            u.lunge > 0 -> HamletCharacterArt.Pose.ATTACK
            else -> HamletCharacterArt.Pose.IDLE
        }
        val poseTime = when (pose) {
            HamletCharacterArt.Pose.HURT -> 0.16f - u.flash
            HamletCharacterArt.Pose.CAST -> battle.time - (cast?.time ?: battle.time)
            HamletCharacterArt.Pose.ATTACK -> 0.22f - u.lunge
            else -> battle.time + u.index * 0.31f
        }
        HamletCharacterArt.draw(c, u.heroVisual, art(u), x, y, size, if (effectsEnabled) poseTime else 0f, pose, u.enemy,
            fx = if (effectsEnabled) time + u.index * 0.31f else 0f)
    }

    /** Shield bubble, stun stars, health bar and states, above every sprite. */
    private fun overlay(c: Canvas, u: BattleUnit, i: Int, time: Float) {
        if (!u.alive) return
        val x = drawX[i]; val y = footY[i]; val size = drawSize[i]
        if (u.shield > 0) fx.guard(c, x, chestY(i), size, time)
        if (u.stun > 0) for (s in 0..2) {
            val angle = time * 4 + s * 2.094f
            HamletGlyphs.draw(c, "star", x + kotlin.math.cos(angle) * size * 0.2f, y - size * 0.92f + sin(angle) * 3f * dp, 7f * dp)
        }
        val hpY = y - size * 0.98f
        val bar = size * 0.56f; val tall = 5f * dp
        paint.color = 0xCC203128.toInt(); c.drawRoundRect(x - bar / 2 - dp, hpY - dp, x + bar / 2 + dp, hpY + tall + dp, 3f * dp, 3f * dp, paint)
        paint.color = if (u.enemy) 0xFFE0745A.toInt() else 0xFF8FDB7E.toInt()
        c.drawRoundRect(x - bar / 2, hpY, x - bar / 2 + bar * (u.hp / u.maxHp).toFloat(), hpY + tall, 2f * dp, 2f * dp, paint)
        if (u.shield > 0) {
            paint.color = 0xFF9AE8FF.toInt()
            c.drawRect(x - bar / 2, hpY + tall - 1.5f * dp, x - bar / 2 + bar * (u.shield / u.maxHp).toFloat().coerceAtMost(1f), hpY + tall, paint)
        }
        statusActive[0] = u.stun > 0; statusActive[1] = u.poison > 0 || u.venom > 0
        statusActive[2] = u.buff > 0; statusActive[3] = u.weakened > 0
        val count = statusActive.count { it }
        var badge = 0
        val w = 30f * sp
        for (status in statusActive.indices) if (statusActive[status]) {
            val bx = x + (badge++ - (count - 1) / 2f) * (w + 2 * dp)
            paint.color = 0xDD233A42.toInt()
            c.drawRoundRect(bx - w / 2, hpY + 8f * dp, bx + w / 2, hpY + 8f * dp + 13f * sp, 4f * dp, 4f * dp, paint)
            text(c, statusLabels[status], bx, hpY + 8f * dp + 10f * sp, 8.5f, statusColors[status], rim = false)
        }
    }

    /** Damage pops out and rises; critical hits are larger and orange, heals green. A skill's number
     * waits for its projectile to land. */
    private fun numbers(c: Canvas) {
        val events = battle.events
        for (k in events.indices) {
            val e = events[k]
            if (e.value <= 0 || (e.kind != BattleEventKind.HIT && e.kind != BattleEventKind.HEAL)) continue
            val target = spots[e.target] ?: continue
            var age = battle.time - e.time
            if (effectsEnabled && e.kind == BattleEventKind.HIT && castOf(e) != null) {
                val s = position(e.source)
                if (s >= 0) age -= HamletBattleFx.travel(battle.units[s].role)
            }
            if (age < 0) continue
            val alpha = (1 - age / 1.2f).coerceIn(0f, 1f)
            val pop = if (effectsEnabled) 1f + (0.14f - age).coerceAtLeast(0f) * 3f else 1f
            val heal = e.kind == BattleEventKind.HEAL
            val value = when { heal -> cached(healTexts, "heal_float", e.value); e.critical -> cached(critTexts, "crit_float", e.value); else -> cached(damageTexts, "damage_float", e.value) }
            val victim = position(e.target)
            val onHero = victim >= 0 && !battle.units[victim].enemy
            val rgb = when { heal -> 0x8EF5A5; e.critical -> 0xFFB43A; onHero -> 0xFF9A88; else -> 0xFFF4D6 }
            val jitter = ((e.value * 37 + e.source * 11) % 9 - 4) * 2.5f * dp
            text(c, value, target.x + jitter, target.y - 26f * dp - (if (effectsEnabled) age * 22f * dp else 0f),
                (if (e.critical) 18f else 14f) * pop, ((alpha * 255).toInt() shl 24) or rgb)
        }
    }

    /** A skill's name above its caster; an ultimate's in a banner across the field. */
    private fun labels(c: Canvas) {
        if (!effectsEnabled) return
        val events = battle.events
        for (k in events.indices) {
            val e = events[k]
            if (e.kind != BattleEventKind.SKILL) continue
            val i = position(e.source); if (i < 0) continue
            val u = battle.units[i]
            if (u.enemy || !u.heroVisual) continue
            val age = battle.time - e.time
            if (e.value == 1) { banner(c, u, age); continue }
            if (age > 1f) continue
            val alpha = (1 - (age - .6f).coerceAtLeast(0f) / .4f)
            text(c, skillNames[u.role.ordinal], drawX[i], footY[i] - drawSize[i] * 1.06f - age * 10f * dp, 11f,
                ((alpha * 255).toInt() shl 24) or (HamletBattleFx.glow(u.role) and 0xFFFFFF))
        }
    }
    private fun banner(c: Canvas, u: BattleUnit, age: Float) {
        if (age > 1.5f) return
        val slide = (1 - (age / .15f).coerceAtMost(1f))
        val alpha = (1 - (age - 1.1f).coerceAtLeast(0f) / .4f).coerceIn(0f, 1f)
        val rarity = HamletHeroes.defs[Math.floorMod(u.sprite, HamletHeroes.defs.size)].rarity
        val y = 76f * sp; val tall = 32f * sp
        val cx = width / 2f + slide * width * .3f
        paint.color = ((alpha * 0xC8).toInt() shl 24) or 0x14201C
        c.drawRect(0f, y - tall / 2, width.toFloat(), y + tall / 2, paint)
        paint.color = ((alpha * 255).toInt() shl 24) or (HamletHeroFx.main(rarity) and 0xFFFFFF)
        c.drawRect(0f, y - tall / 2, width.toFloat(), y - tall / 2 + 2 * dp, paint)
        c.drawRect(0f, y + tall / 2 - 2 * dp, width.toFloat(), y + tall / 2, paint)
        val name = heroNames[Math.floorMod(u.sprite, heroNames.size)]
        val skill = ultimateNames[u.role.ordinal]
        paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 17f * sp
        val nameWidth = paint.measureText(name); val sepWidth = paint.measureText(separator)
        paint.textSize = 16f * sp
        val total = nameWidth + sepWidth + paint.measureText(skill)
        val left = cx - total / 2; val base = y + 6f * sp
        text(c, name, left, base, 17f, ((alpha * 255).toInt() shl 24) or (HamletHeroFx.light(rarity) and 0xFFFFFF), Paint.Align.LEFT)
        text(c, separator, left + nameWidth, base, 17f, ((alpha * 200).toInt() shl 24) or 0xF6EDD9, Paint.Align.LEFT)
        text(c, skill, left + nameWidth + sepWidth, base, 16f, ((alpha * 255).toInt() shl 24) or 0xFFE08A, Paint.Align.LEFT)
    }

    /** One large portrait per hero along the bottom: a dial empties as the ultimate charges, gold when ready. */
    private fun ultimates(c: Canvas) {
        val heroes = party
        val spacing = width.toFloat() / maxOf(1, heroes.size)
        val side = minOf(60f * sp, spacing - 6f * dp)
        val pulse = if (effectsEnabled) .5f + .5f * sin(battle.time * 6f) else 1f
        for (i in heroes.indices) {
            val u = heroes[i]
            val x = spacing * (i + 0.5f); val y = height - 8f * dp
            val ready = u.ultimateLeft <= 0 && u.alive && u.stun <= 0
            val bounds = buttons.getOrPut(u.index) { RectF() }.apply { set(x - side / 2, y - side, x + side / 2, y) }
            val radius = 10f * dp
            if (ready) {
                paint.color = ((60 + 80 * pulse).toInt() shl 24) or 0xFFD36A
                c.drawRoundRect(bounds.left - 4 * dp, bounds.top - 4 * dp, bounds.right + 4 * dp, bounds.bottom + 4 * dp, radius + 4 * dp, radius + 4 * dp, paint)
            }
            paint.color = if (ready) 0xFFE0B451.toInt() else 0xE6263F4C.toInt(); c.drawRoundRect(bounds, radius, radius, paint)
            HamletCharacterArt.draw(c, true, u.sprite, x, y - 3f * dp, side * .92f, if (effectsEnabled) battle.time + u.index else 0f,
                if (u.alive) HamletCharacterArt.Pose.IDLE else HamletCharacterArt.Pose.DOWN, alpha = if (u.alive) 255 else 120)
            if (!ready && u.alive) {
                val left = (u.ultimateLeft / u.ultimatePeriod).coerceIn(0f, 1f)
                c.save(); c.clipRect(bounds)
                arc.set(x - side, y - side * 1.5f, x + side, y + side * .5f)
                paint.color = 0x990D2230.toInt(); c.drawArc(arc, -90f, 360f * left, true, paint)
                c.restore()
                text(c, cached(cooldownTexts, "cooldown", maxOf(0, ceil(u.ultimateLeft).toInt())), x, y - 6f * dp, 12f, 0xFFF4E8C8.toInt())
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (ready) 2.5f else 1f) * dp
            paint.color = if (ready) 0xFFFFF0B8.toInt() else 0x886E8B96.toInt()
            c.drawRoundRect(bounds, radius, radius, paint); paint.style = Paint.Style.FILL
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

class HamletAdventureMapView(context: Context, var page: Int,
    val adventure: HamletAdventure, private val number: (Int) -> String, private val select: (Int) -> Unit) : View(context) {
    var effectsEnabled = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val points = ArrayList<Pair<Int, PointF>>()
    private var scene: HamletScene? = null
    private var sceneRegion = -1
    private val start = SystemClock.uptimeMillis()
    private val dp = resources.displayMetrics.density
    private var layoutPage = -1
    private var layoutWidth = -1
    private var layoutHeight = -1
    var selected = adventure.frontier
    override fun onDraw(c: Canvas) {
        val region = adventure.region(page * 12)
        val ground = scene?.takeIf { sceneRegion == region } ?: HamletScene(region).also { scene = it; sceneRegion = region }
        val time = if (effectsEnabled) (SystemClock.uptimeMillis() - start) / 1000f else 0f
        ground.layout(width, height); ground.drawBack(c, time); ground.drawFront(c, time)
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
        if (effectsEnabled) postInvalidateOnAnimation()
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
class HamletGatherView(context: Context, region: Int,
    private val haptics: Boolean, private val finished: () -> Unit) : View(context) {
    var effectsEnabled = true
    var gathered = 0
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scene = HamletScene(region)
    private val start = SystemClock.uptimeMillis()
    private val glyphs = arrayOf("log", "ore", "fruit")
    private val dp = resources.displayMetrics.density
    private val locations = arrayOf(0.22f to 0.6f, 0.64f to 0.52f, 0.42f to 0.74f, 0.8f to 0.76f, 0.32f to 0.44f, 0.6f to 0.82f, 0.82f to 0.46f, 0.18f to 0.82f)
    override fun onDraw(c: Canvas) {
        val time = if (effectsEnabled) (SystemClock.uptimeMillis() - start) / 1000f else 0f
        scene.layout(width, height); scene.drawBack(c, time); scene.drawFront(c, time)
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
        if (effectsEnabled) postInvalidateOnAnimation()
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
