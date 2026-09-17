package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * L'écran de combat façon FF / Pokémon : les ennemis en haut, le héros en bas, les
 * actions en dessous. Il mesure les deux gestes en rythme et les transmet au [Combat] :
 *  - pendant sa propre attaque, un swipe quand le curseur traverse la zone dorée ;
 *  - pendant l'attaque d'un ennemi, une touche au moment où l'anneau se referme.
 */
class CombatView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var onFinished:   (() -> Unit)? = null
    var onStrike:     ((crit: Boolean) -> Unit)? = null
    var onEnemyDied:  (() -> Unit)? = null
    var onHeroHit:    (() -> Unit)? = null
    var onParry:      ((perfect: Boolean) -> Unit)? = null

    private var combat: Combat? = null
    private var heroSpritePath: String? = null

    companion object {
        private const val INTRO_MS   = 700L
        private const val STRIKE_MS  = 1000L
        private const val HIT_MS     = 550L
        private const val BURN_MS    = 500L
        private const val PAUSE_MS   = 450L
        private const val WINDUP_MS  = 950L
        private const val IMPACT_MS  = 500L
        private const val FLOAT_MS   = 900L

        // Zone de la frappe, en fraction de la barre
        private const val STRIKE_CENTER  = 0.72f
        private const val STRIKE_GOOD    = 0.13f
        private const val STRIKE_PERFECT = 0.045f

        // Fenêtre de parade autour de l'impact
        private const val PARRY_PERFECT_MS = 70
        private const val PARRY_GOOD_MS    = 160
    }

    private enum class Stage { INTRO, CHOOSE, STRIKE_TIMING, PLAYER_HIT, ENEMY_BURN, ENEMY_PAUSE, ENEMY_WINDUP, ENEMY_IMPACT, END_PANEL }
    private sealed class Action { object Attack : Action(); data class Cast(val relic: Relic) : Action() }

    private var stage = Stage.INTRO
    private var stageStart = 0L
    private var target = 0
    private var pendingAction: Action? = null
    private val attackers = ArrayDeque<Int>()
    private var attacker = -1
    private var parry: Timing? = null
    private var lastHitTarget = -1

    private data class Floater(val text: String, val x: Float, val y: Float, val color: Int, val big: Boolean, val start: Long)
    private val floaters = mutableListOf<Floater>()
    private var banner: String? = null
    private var bannerColor = Color.WHITE
    private var bannerStart = 0L

    // ── Géométrie ───────────────────────────────────────────────────────────────
    private val density get() = resources.displayMetrics.density
    private val sp get() = density * resources.configuration.fontScale
    private val enemyRects = mutableListOf<RectF>()
    private var heroRect = RectF()
    private var buttonsArea = RectF()
    private var attackBtn = RectF()
    private var relicBtn = RectF()
    private var potionBtn = RectF()
    private var strikeBar = RectF()

    // ── Peintures ───────────────────────────────────────────────────────────────
    private val pBg      = Paint()
    private val pSprite  = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pText    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pFill    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pOverlay = Paint().apply { color = 0xCC000000.toInt() }

    // ── Démarrage ───────────────────────────────────────────────────────────────

    fun start(c: Combat, heroSprite: String) {
        combat = c
        heroSpritePath = heroSprite
        target = c.aliveIndices().firstOrNull() ?: 0
        attackers.clear(); attacker = -1; parry = null; pendingAction = null; lastHitTarget = -1
        floaters.clear(); banner = null
        layoutRects()
        enter(Stage.INTRO)
    }

    private fun enter(s: Stage) {
        stage = s
        stageStart = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun elapsed() = SystemClock.uptimeMillis() - stageStart

    private fun showBanner(text: String, color: Int) {
        banner = text; bannerColor = color; bannerStart = SystemClock.uptimeMillis()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pBg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1B1420.toInt(), 0xFF0A0A0F.toInt(), Shader.TileMode.CLAMP)
        layoutRects()
    }

    private fun layoutRects() {
        val c = combat ?: return
        if (width == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val n = c.enemies.size
        val size = min(w / (n + 0.8f), h * 0.22f)
        val gap = (w - size * n) / (n + 1)
        enemyRects.clear()
        for (i in 0 until n) {
            val left = gap + i * (size + gap)
            // Le groupe forme un léger arc : celui du milieu recule un peu
            val top = h * 0.12f + if (n == 3 && i == 1) -size * 0.12f else 0f
            enemyRects += RectF(left, top, left + size, top + size)
        }
        val heroSize = min(w * 0.34f, h * 0.20f)
        heroRect = RectF(w * 0.10f, h * 0.53f, w * 0.10f + heroSize, h * 0.53f + heroSize)

        buttonsArea = RectF(0f, h * 0.80f, w, h)
        val bw = (w - 4 * 12f * density) / 3f
        val bh = h * 0.12f
        val by = h * 0.84f
        attackBtn = RectF(12f * density, by, 12f * density + bw, by + bh)
        relicBtn  = RectF(attackBtn.right + 12f * density, by, attackBtn.right + 12f * density + bw, by + bh)
        potionBtn = RectF(relicBtn.right + 12f * density, by, relicBtn.right + 12f * density + bw, by + bh)
        strikeBar = RectF(w * 0.10f, h * 0.45f, w * 0.90f, h * 0.45f + 22f * density)
    }

    // ── Boucle d'animation ──────────────────────────────────────────────────────

    private fun tick() {
        val c = combat ?: return
        val t = elapsed()
        when (stage) {
            Stage.INTRO -> if (t >= INTRO_MS) {
                if (c.phase == CombatPhase.ENEMY_TURN) beginEnemyTurn() else enter(Stage.CHOOSE)
            }
            Stage.STRIKE_TIMING -> if (t >= STRIKE_MS) resolveStrike(Timing.MISS)
            Stage.PLAYER_HIT -> if (t >= HIT_MS) afterPlayerAction()
            Stage.ENEMY_BURN -> if (t >= BURN_MS) {
                if (c.phase == CombatPhase.VICTORY) enter(Stage.END_PANEL) else nextAttacker()
            }
            Stage.ENEMY_PAUSE -> if (t >= PAUSE_MS) nextAttacker()
            Stage.ENEMY_WINDUP -> {
                val lateLimit = WINDUP_MS + goodWindow()
                val tapped = parry
                if (t >= lateLimit || (tapped != null && t >= WINDUP_MS)) resolveEnemyStrike(tapped ?: Timing.MISS)
            }
            Stage.ENEMY_IMPACT -> if (t >= IMPACT_MS) {
                if (c.phase == CombatPhase.DEFEAT) enter(Stage.END_PANEL) else nextAttacker()
            }
            Stage.CHOOSE, Stage.END_PANEL -> {}
        }
    }

    private fun goodWindow() = PARRY_GOOD_MS + (combat?.hero?.parryBonusMs ?: 0)
    private fun perfectWindow() = PARRY_PERFECT_MS + (combat?.hero?.parryBonusMs ?: 0) / 2

    // ── Tour du joueur ──────────────────────────────────────────────────────────

    private fun choose(action: Action) {
        pendingAction = action
        enter(Stage.STRIKE_TIMING)
    }

    private fun resolveStrike(timing: Timing) {
        val c = combat ?: return
        if (!c.enemies[target].alive) target = c.aliveIndices().first()
        val result = when (val a = pendingAction) {
            is Action.Cast -> c.castRelic(a.relic, target, timing)
            else           -> c.attack(target, timing)
        }
        pendingAction = null
        lastHitTarget = target
        when (timing) {
            Timing.PERFECT -> showBanner(context.getString(R.string.roguelike_combat_perfect), 0xFFFFD54F.toInt())
            Timing.GOOD    -> showBanner(context.getString(R.string.roguelike_combat_good), 0xFFAED581.toInt())
            Timing.MISS    -> {}
        }
        val r = enemyRects[result.target]
        floatText(
            if (result.crit) context.getString(R.string.roguelike_combat_crit_damage, result.damage)
            else context.getString(R.string.roguelike_combat_damage, result.damage),
            r.centerX(), r.top, if (result.crit) 0xFFFFEB3B.toInt() else Color.WHITE, result.crit,
        )
        onStrike?.invoke(result.crit)
        if (result.killed) onEnemyDied?.invoke()
        enter(Stage.PLAYER_HIT)
    }

    private fun drinkPotion() {
        val c = combat ?: return
        val healed = c.drinkPotion()
        floatText(context.getString(R.string.roguelike_combat_heal, healed), heroRect.centerX(), heroRect.top, 0xFF81C784.toInt(), false)
        lastHitTarget = -1
        enter(Stage.PLAYER_HIT)
    }

    private fun afterPlayerAction() {
        val c = combat ?: return
        when (c.phase) {
            CombatPhase.VICTORY -> enter(Stage.END_PANEL)
            CombatPhase.ENEMY_TURN -> beginEnemyTurn()
            else -> enter(Stage.CHOOSE)
        }
    }

    // ── Tour des ennemis ────────────────────────────────────────────────────────

    private fun beginEnemyTurn() {
        val c = combat ?: return
        val (burns, list) = c.startEnemyTurn()
        attackers.clear(); attackers.addAll(list)
        for (b in burns) {
            val r = enemyRects[b.enemy]
            floatText(context.getString(R.string.roguelike_combat_damage, b.damage), r.centerX(), r.top, 0xFFFF8A65.toInt(), false)
            if (b.killed) onEnemyDied?.invoke()
        }
        when {
            burns.isNotEmpty() -> enter(Stage.ENEMY_BURN)
            list.isEmpty()     -> enter(Stage.ENEMY_PAUSE)
            else               -> nextAttacker()
        }
    }

    private fun nextAttacker() {
        val c = combat ?: return
        if (c.phase == CombatPhase.VICTORY) { enter(Stage.END_PANEL); return }
        val next = attackers.removeFirstOrNull()
        if (next == null) {
            c.endEnemyTurn()
            attacker = -1
            if (!c.enemies[target].alive) target = c.aliveIndices().firstOrNull() ?: 0
            enter(Stage.CHOOSE)
            return
        }
        attacker = next
        parry = null
        enter(Stage.ENEMY_WINDUP)
    }

    private fun resolveEnemyStrike(timing: Timing) {
        val c = combat ?: return
        val strike = c.resolveStrike(attacker, timing)
        when (timing) {
            Timing.PERFECT -> { showBanner(context.getString(R.string.roguelike_combat_parry_perfect), 0xFFFFD54F.toInt()); onParry?.invoke(true) }
            Timing.GOOD    -> { showBanner(context.getString(R.string.roguelike_combat_parry_good), 0xFF81D4FA.toInt()); onParry?.invoke(false) }
            Timing.MISS    -> onHeroHit?.invoke()
        }
        floatText(context.getString(R.string.roguelike_combat_damage, strike.damage), heroRect.centerX(), heroRect.top,
            if (timing == Timing.MISS) 0xFFEF5350.toInt() else 0xFFB0BEC5.toInt(), timing == Timing.MISS)
        enter(Stage.ENEMY_IMPACT)
    }

    private fun floatText(text: String, x: Float, y: Float, color: Int, big: Boolean) {
        floaters += Floater(text, x, y, color, big, SystemClock.uptimeMillis())
    }

    // ── Dessin ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val c = combat ?: return
        tick()
        if (combat == null) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBg)

        drawEnemies(canvas, c)
        drawHero(canvas, c)
        drawButtons(canvas, c)
        drawHint(canvas)
        if (stage == Stage.STRIKE_TIMING) drawStrikeBar(canvas)
        if (stage == Stage.ENEMY_WINDUP) drawParryRing(canvas)
        drawFloaters(canvas)
        drawBanner(canvas)
        if (stage == Stage.INTRO) drawIntro(canvas)
        if (stage == Stage.END_PANEL) drawEndPanel(canvas, c)

        if (stage != Stage.CHOOSE && stage != Stage.END_PANEL || floaters.isNotEmpty() || banner != null)
            postInvalidateOnAnimation()
    }

    private fun drawEnemies(canvas: Canvas, c: Combat) {
        val now = SystemClock.uptimeMillis()
        for ((i, e) in c.enemies.withIndex()) {
            val base = enemyRects.getOrNull(i) ?: continue
            if (!e.alive && !(stage == Stage.PLAYER_HIT && lastHitTarget == i)) continue
            val r = RectF(base)

            // L'attaquant s'avance pendant son élan
            if (stage == Stage.ENEMY_WINDUP && attacker == i) {
                val p = (elapsed().toFloat() / WINDUP_MS).coerceIn(0f, 1f)
                r.offset(0f, base.height() * 0.25f * p * p)
            }
            // Recul quand on le touche
            val hitNow = stage == Stage.PLAYER_HIT && lastHitTarget == i
            if (hitNow) r.offset(sin(elapsed() / 30.0).toFloat() * 6f * density, 0f)

            // Cible choisie
            if (stage == Stage.CHOOSE && target == i && e.alive) {
                pStroke.color = 0xFFFFD54F.toInt(); pStroke.strokeWidth = 3f * density
                canvas.drawRoundRect(r.left - 6f, r.top - 6f, r.right + 6f, r.bottom + 6f, 12f * density, 12f * density, pStroke)
            }

            val bmp = SpriteLoader.load(context.assets, SpriteLoader.monsterPath(e.type))
            pSprite.alpha = if (!e.alive) ((1f - elapsed().toFloat() / HIT_MS).coerceIn(0f, 1f) * 255).toInt() else 255
            if (bmp != null) canvas.drawBitmap(bmp, null, r, pSprite)
            pSprite.alpha = 255
            if (hitNow && now % 120 < 60) { pFill.color = 0x66FFFFFF; canvas.drawRect(r, pFill) }

            // Nom, barre de vie, compte à rebours
            pText.textSize = 13f * sp; pText.color = 0xFFDDDDDD.toInt()
            canvas.drawText(context.getString(e.type.labelRes), base.centerX(), base.bottom + 16f * sp, pText)
            val barTop = base.bottom + 22f * sp
            val bar = RectF(base.left, barTop, base.right, barTop + 7f * density)
            pFill.color = 0xFF333333.toInt(); canvas.drawRect(bar, pFill)
            pFill.color = 0xFFE53935.toInt()
            canvas.drawRect(bar.left, bar.top, bar.left + bar.width() * e.hp / e.maxHp, bar.bottom, pFill)
            pText.textSize = 11f * sp; pText.color = 0xFFBBBBBB.toInt()
            canvas.drawText(context.getString(R.string.roguelike_combat_hp, e.hp, e.maxHp), base.centerX(), bar.bottom + 13f * sp, pText)

            if (e.alive) {
                val ready = e.countdown <= 1
                pText.textSize = 13f * sp
                pText.color = if (ready) 0xFFFF7043.toInt() else 0xFF90A4AE.toInt()
                canvas.drawText(context.getString(R.string.roguelike_combat_countdown, e.countdown), base.centerX(), base.top - 8f * density, pText)
                if (e.burnTurns > 0) {
                    pText.color = 0xFFFF8A65.toInt(); pText.textSize = 11f * sp
                    canvas.drawText(context.getString(R.string.roguelike_combat_burning, e.burnTurns), base.centerX(), bar.bottom + 27f * sp, pText)
                }
            }
        }
    }

    private fun drawHero(canvas: Canvas, c: Combat) {
        val r = RectF(heroRect)
        if (stage == Stage.PLAYER_HIT && lastHitTarget >= 0) {
            val p = (elapsed().toFloat() / HIT_MS)
            r.offset(0f, -heroRect.height() * 0.3f * sin(p * Math.PI).toFloat())
        }
        if (stage == Stage.ENEMY_IMPACT && parry == null) r.offset(sin(elapsed() / 25.0).toFloat() * 7f * density, 0f)
        heroSpritePath?.let { path -> SpriteLoader.load(context.assets, path)?.let { canvas.drawBitmap(it, null, r, pSprite) } }

        val hero = c.hero
        val left = heroRect.right + 16f * density
        val right = width - 16f * density
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 15f * sp; pText.color = Color.WHITE
        canvas.drawText(context.getString(R.string.roguelike_combat_hp, hero.hp, hero.maxHp), left, heroRect.centerY() - 10f * density, pText)
        val bar = RectF(left, heroRect.centerY(), right, heroRect.centerY() + 12f * density)
        pFill.color = 0xFF333333.toInt(); canvas.drawRect(bar, pFill)
        val ratio = hero.hp.toFloat() / hero.maxHp
        pFill.color = when { ratio > 0.5f -> 0xFF43A047.toInt(); ratio > 0.25f -> 0xFFFB8C00.toInt(); else -> 0xFFE53935.toInt() }
        canvas.drawRect(bar.left, bar.top, bar.left + bar.width() * ratio, bar.bottom, pFill)
        pText.textAlign = Paint.Align.CENTER
    }

    private fun drawButtons(canvas: Canvas, c: Combat) {
        val active = stage == Stage.CHOOSE
        val relic = Relic.FIREBALL
        val cd = c.relicCooldowns[relic] ?: 0
        drawButton(canvas, attackBtn, context.getString(R.string.roguelike_combat_attack), null, active, 0xFF8D2B2B.toInt())
        drawButton(canvas, relicBtn, context.getString(relic.labelRes),
            if (cd > 0) context.getString(R.string.roguelike_combat_cooldown, cd) else null,
            active && c.canCast(relic), 0xFFB5451B.toInt())
        drawButton(canvas, potionBtn, context.getString(R.string.roguelike_combat_potion),
            context.getString(R.string.roguelike_combat_potion_count, c.hero.potions),
            active && c.canDrinkPotion(), 0xFF2E6B3A.toInt())
    }

    private fun drawButton(canvas: Canvas, r: RectF, label: String, sub: String?, enabled: Boolean, color: Int) {
        pFill.color = if (enabled) color else 0xFF2A2A2A.toInt()
        canvas.drawRoundRect(r, 12f * density, 12f * density, pFill)
        pText.color = if (enabled) Color.WHITE else 0xFF777777.toInt()
        pText.textSize = 15f * sp
        val y = if (sub == null) r.centerY() + 5f * sp else r.centerY() - 2f * sp
        canvas.drawText(label, r.centerX(), y, pText)
        if (sub != null) {
            pText.textSize = 11f * sp
            canvas.drawText(sub, r.centerX(), r.centerY() + 15f * sp, pText)
        }
    }

    private fun drawHint(canvas: Canvas) {
        val res = when (stage) {
            Stage.CHOOSE        -> R.string.roguelike_combat_hint_choose
            Stage.STRIKE_TIMING -> R.string.roguelike_combat_hint_strike
            Stage.ENEMY_WINDUP  -> R.string.roguelike_combat_hint_parry
            else -> return
        }
        pText.textSize = 14f * sp; pText.color = 0xFFB0BEC5.toInt()
        canvas.drawText(context.getString(res), width / 2f, height * 0.78f, pText)
    }

    private fun drawStrikeBar(canvas: Canvas) {
        val b = strikeBar
        val cr = b.height() / 2f
        pFill.color = 0xFF263238.toInt(); canvas.drawRoundRect(b, cr, cr, pFill)
        val goodL = b.left + b.width() * (STRIKE_CENTER - STRIKE_GOOD)
        val goodR = b.left + b.width() * (STRIKE_CENTER + STRIKE_GOOD)
        pFill.color = 0xFF7CB342.toInt(); canvas.drawRect(goodL, b.top, goodR, b.bottom, pFill)
        val perfL = b.left + b.width() * (STRIKE_CENTER - STRIKE_PERFECT)
        val perfR = b.left + b.width() * (STRIKE_CENTER + STRIKE_PERFECT)
        pFill.color = 0xFFFFD54F.toInt(); canvas.drawRect(perfL, b.top, perfR, b.bottom, pFill)
        val x = b.left + b.width() * (elapsed().toFloat() / STRIKE_MS).coerceIn(0f, 1f)
        pFill.color = Color.WHITE
        canvas.drawRect(x - 3f * density, b.top - 8f * density, x + 3f * density, b.bottom + 8f * density, pFill)
    }

    /** L'anneau se referme sur le héros : toucher quand il épouse le cercle intérieur. */
    private fun drawParryRing(canvas: Canvas) {
        val cx = heroRect.centerX(); val cy = heroRect.centerY()
        val inner = heroRect.width() * 0.62f
        val p = (elapsed().toFloat() / WINDUP_MS).coerceAtMost(1.3f)
        val radius = inner * (1f + 2.2f * (1f - p).coerceAtLeast(0f))
        pStroke.strokeWidth = 3f * density
        pStroke.color = 0x88FFFFFF.toInt()
        canvas.drawCircle(cx, cy, inner, pStroke)
        pStroke.strokeWidth = 5f * density
        pStroke.color = when (parry) { Timing.PERFECT -> 0xFFFFD54F.toInt(); Timing.GOOD -> 0xFF81D4FA.toInt(); Timing.MISS -> 0xFF616161.toInt(); null -> 0xFFEF5350.toInt() }
        canvas.drawCircle(cx, cy, radius, pStroke)
        if (parry == Timing.MISS) {
            pText.textSize = 14f * sp; pText.color = 0xFF9E9E9E.toInt()
            canvas.drawText(context.getString(R.string.roguelike_combat_too_early), cx, heroRect.bottom + 20f * sp, pText)
        }
    }

    private fun drawFloaters(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        floaters.removeAll { now - it.start > FLOAT_MS }
        for (f in floaters) {
            val p = (now - f.start).toFloat() / FLOAT_MS
            pText.textSize = (if (f.big) 30f else 22f) * sp
            pText.color = f.color
            pText.alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(f.text, f.x, f.y - p * 50f * density, pText)
            pText.alpha = 255
        }
    }

    private fun drawBanner(canvas: Canvas) {
        val text = banner ?: return
        val age = SystemClock.uptimeMillis() - bannerStart
        if (age > FLOAT_MS) { banner = null; return }
        pText.textSize = 26f * sp; pText.color = bannerColor
        pText.alpha = ((1f - age.toFloat() / FLOAT_MS) * 255).toInt().coerceIn(0, 255)
        canvas.drawText(text, width / 2f, height * 0.42f, pText)
        pText.alpha = 255
    }

    private fun drawIntro(canvas: Canvas) {
        val p = (elapsed().toFloat() / INTRO_MS).coerceIn(0f, 1f)
        pOverlay.alpha = ((1f - p) * 204).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pOverlay)
        pOverlay.alpha = 204
        pText.textSize = 32f * sp; pText.color = 0xFFFF7043.toInt()
        val c = combat
        val res = if (c != null && c.phase == CombatPhase.ENEMY_TURN) R.string.roguelike_combat_ambush else R.string.roguelike_combat_start
        canvas.drawText(context.getString(res), width / 2f, height * 0.40f, pText)
    }

    private fun drawEndPanel(canvas: Canvas, c: Combat) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pOverlay)
        val cx = width / 2f; var y = height * 0.35f
        if (c.phase == CombatPhase.VICTORY) {
            val r = c.rewards ?: return
            pText.textSize = 34f * sp; pText.color = 0xFFFFD54F.toInt()
            canvas.drawText(context.getString(R.string.roguelike_combat_victory), cx, y, pText)
            pText.textSize = 18f * sp; pText.color = Color.WHITE
            y += 50f * sp
            canvas.drawText(context.getString(R.string.roguelike_combat_reward_gold, r.gold), cx, y, pText)
            if (r.potions > 0) { y += 30f * sp; canvas.drawText(context.getString(R.string.roguelike_combat_reward_potions, r.potions), cx, y, pText) }
            if (r.equipment.isNotEmpty()) {
                y += 30f * sp
                pText.color = 0xFF81D4FA.toInt()
                canvas.drawText(context.resources.getQuantityString(R.plurals.roguelike_combat_reward_items, r.equipment.size, r.equipment.size), cx, y, pText)
            }
        } else {
            pText.textSize = 34f * sp; pText.color = 0xFFEF5350.toInt()
            canvas.drawText(context.getString(R.string.roguelike_death_title), cx, y, pText)
        }
        pText.textSize = 14f * sp; pText.color = 0xFFB0BEC5.toInt()
        canvas.drawText(context.getString(R.string.roguelike_combat_tap_continue), cx, height * 0.70f, pText)
    }

    // ── Toucher ─────────────────────────────────────────────────────────────────

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = combat ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                // La parade se joue à l'appui : c'est le geste le plus précis
                if (stage == Stage.ENEMY_WINDUP && parry == null) {
                    val delta = abs(elapsed() - WINDUP_MS)
                    parry = when {
                        delta <= perfectWindow() -> Timing.PERFECT
                        delta <= goodWindow()    -> Timing.GOOD
                        else                     -> Timing.MISS   // trop tôt : raté, pas de second essai
                    }
                    postInvalidateOnAnimation()
                }
            }
            MotionEvent.ACTION_UP -> {
                val dist = hypot(event.x - downX, event.y - downY)
                val swipe = dist > 24f * density
                when (stage) {
                    Stage.STRIKE_TIMING -> if (swipe) {
                        val p = elapsed().toFloat() / STRIKE_MS
                        val d = abs(p - STRIKE_CENTER)
                        resolveStrike(when {
                            d <= STRIKE_PERFECT -> Timing.PERFECT
                            d <= STRIKE_GOOD    -> Timing.GOOD
                            else                -> Timing.MISS
                        })
                    }
                    Stage.CHOOSE -> if (!swipe) handleChooseTap(c, downX, downY)
                    Stage.END_PANEL -> if (!swipe) {
                        combat = null
                        onFinished?.invoke()
                    }
                    else -> {}
                }
            }
        }
        return true
    }

    private fun handleChooseTap(c: Combat, x: Float, y: Float) {
        val tappedEnemy = enemyRects.indexOfFirst { it.contains(x, y) }
        when {
            tappedEnemy >= 0 && c.enemies[tappedEnemy].alive -> { target = tappedEnemy; invalidate() }
            attackBtn.contains(x, y) -> choose(Action.Attack)
            relicBtn.contains(x, y) && c.canCast(Relic.FIREBALL) -> choose(Action.Cast(Relic.FIREBALL))
            potionBtn.contains(x, y) && c.canDrinkPotion() -> drinkPotion()
        }
    }
}
