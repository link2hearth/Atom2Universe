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
 * actions en dessous — quatre boutons comme les quatre attaques d'un Pokémon (l'attaque
 * à l'arme, deux reliques, le Spécial de l'archétype). Il mesure les deux gestes en rythme et les transmet au [Combat] :
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
        private const val STATUS_MS  = 600L
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

        // Couleurs des états du grimoire (celles des éléments sont dans elementColor)
        private const val SOAKED_COLOR    = 0xFF4DD0E1.toInt()
        private const val FRACTURED_COLOR = 0xFFBCAAA4.toInt()
        private const val WEAKENED_COLOR  = 0xFFB0BEC5.toInt()
        private const val BLINDED_COLOR   = 0xFF9E9E9E.toInt()
        private const val MARKED_COLOR    = 0xFFFFD54F.toInt()
        private const val RAGE_COLOR      = 0xFFFF5252.toInt()
        private const val EMPOWERED_COLOR = 0xFFFF8A65.toInt()
        private const val BLEED_COLOR     = 0xFFE53935.toInt()
        private const val CHARMED_COLOR   = 0xFFF48FB1.toInt()
        private const val BARRIER_COLOR   = 0xFF90CAF9.toInt()
        private const val STONESKIN_COLOR = 0xFFBDBDBD.toInt()
        private const val REGEN_COLOR     = 0xFF81C784.toInt()
        private const val SLOWED_COLOR    = 0xFF9FA8DA.toInt()
        private const val HASTE_COLOR     = 0xFF80CBC4.toInt()
        private const val HOURGLASS_COLOR = 0xFFE6C75A.toInt()
        private const val PUPPET_COLOR    = 0xFF80CBC4.toInt()
        private const val ROLL_COLOR      = 0xFFAED581.toInt()

        // La barre d'ordre des tours : chaque ennemi a sa couleur, reprise au-dessus de lui
        private val ENEMY_MARKS = intArrayOf(0xFFFFB74D.toInt(), 0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt())
        private const val HERO_MARK    = 0xFFE0E0E0.toInt()
        private const val CURRENT_MARK = 0xFFFFD54F.toInt()
        private const val FROZEN_VEIL  = 0x6681D4FA
    }

    private enum class Stage { INTRO, CHOOSE, STRIKE_TIMING, PLAYER_HIT, HERO_STATUS, ENEMY_STATUS, ENEMY_PAUSE, ENEMY_WINDUP, ENEMY_IMPACT, END_PANEL }
    private sealed class Action { object Attack : Action(); object Deadly : Action(); data class Cast(val relic: Relic) : Action() }

    private var stage = Stage.INTRO
    private var stageStart = 0L
    private var target = 0
    private var pendingAction: Action? = null
    /** Vagabond : le geste du premier coup de l'Enchaînement, en attendant celui du second. */
    private var chainFirst: Timing? = null
    private val attackers = ArrayDeque<Int>()
    private var attacker = -1
    private var parry: Timing? = null
    /** Le Sablier : l'élan de cette attaque est plus lent, et les fenêtres de parade plus larges. */
    private var windupScale = 1f
    private fun windupMs() = (WINDUP_MS * windupScale).toLong()
    /** Les ennemis touchés par la dernière action : ils tremblent, et ceux qui meurent s'effacent. */
    private var hitTargets = emptySet<Int>()

    private data class Floater(val text: String, val x: Float, val y: Float, val color: Int, val big: Boolean, val start: Long)
    private val floaters = mutableListOf<Floater>()
    private var banner: String? = null
    private var bannerColor = Color.WHITE
    private var bannerStart = 0L

    // ── Géométrie ───────────────────────────────────────────────────────────────
    private val density get() = resources.displayMetrics.density
    private val sp get() = density * resources.configuration.fontScale

    /** PV, dégâts, soins, or : abrégés au-delà de 100 000 (« 290k »). */
    private fun num(v: Int) = DungeonNumbers.format(context, v)
    private val enemyRects = mutableListOf<RectF>()
    private var heroRect = RectF()
    private var buttonsArea = RectF()
    private var attackBtn = RectF()
    private val relicBtns = Array(Hero.RELIC_SLOTS) { RectF() }
    private var specialBtn = RectF()
    private var strikeBar = RectF()
    private var orderBar = RectF()
    /**
     * Le coût de l'action que le doigt touche (ou qu'on est en train de jouer) : la barre
     * d'ordre montre alors où tomberait le prochain tour du héros. Null : aucune action visée.
     */
    private var previewCost: Double? = null

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
        attackers.clear(); attacker = -1; parry = null; pendingAction = null; chainFirst = null; hitTargets = emptySet(); previewCost = null
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
        val m0 = 10f * density
        orderBar = RectF(m0, 6f * density, w - m0, 6f * density + 36f * density)
        val size = min(w / (n + 0.8f), h * 0.22f)
        val gap = (w - size * n) / (n + 1)
        // Sous la barre d'ordre, avec la place du repère de couleur et de l'arc
        val enemiesTop = maxOf(h * 0.12f, orderBar.bottom + 20f * density + size * 0.12f)
        enemyRects.clear()
        for (i in 0 until n) {
            val left = gap + i * (size + gap)
            // Le groupe forme un léger arc : celui du milieu recule un peu
            val top = enemiesTop + if (n == 3 && i == 1) -size * 0.12f else 0f
            enemyRects += RectF(left, top, left + size, top + size)
        }
        val heroSize = min(w * 0.34f, h * 0.20f)
        heroRect = RectF(w * 0.10f, h * 0.53f, w * 0.10f + heroSize, h * 0.53f + heroSize)

        // Grille 3 × 2 : l'attaque et le Spécial à gauche, les quatre reliques à droite
        buttonsArea = RectF(0f, h * 0.80f, w, h)
        val m = 10f * density
        val cellW = (w - 4 * m) / 3f
        val bh = h * 0.075f
        val row1 = h * 0.81f
        val row2 = row1 + bh + m
        fun cell(col: Int, top: Float) = RectF(m + col * (cellW + m), top, m + col * (cellW + m) + cellW, top + bh)
        attackBtn    = cell(0, row1)
        specialBtn   = cell(0, row2)
        relicBtns[0] = cell(1, row1)
        relicBtns[1] = cell(2, row1)
        relicBtns[2] = cell(1, row2)
        relicBtns[3] = cell(2, row2)
        strikeBar = RectF(w * 0.10f, h * 0.45f, w * 0.90f, h * 0.45f + 22f * density)
    }

    // ── Boucle d'animation ──────────────────────────────────────────────────────

    private fun tick() {
        val c = combat ?: return
        val t = elapsed()
        when (stage) {
            Stage.INTRO -> if (t >= INTRO_MS) proceed()
            Stage.STRIKE_TIMING -> if (t >= STRIKE_MS) resolveStrike(Timing.MISS)
            Stage.PLAYER_HIT -> if (t >= HIT_MS) afterPlayerAction()
            Stage.HERO_STATUS -> if (t >= STATUS_MS) proceed()
            Stage.ENEMY_STATUS -> if (t >= STATUS_MS) {
                if (c.phase == CombatPhase.VICTORY) enter(Stage.END_PANEL) else nextAttacker()
            }
            Stage.ENEMY_PAUSE -> if (t >= PAUSE_MS) nextAttacker()
            Stage.ENEMY_WINDUP -> {
                val lateLimit = windupMs() + goodWindow()
                val tapped = parry
                if (t >= lateLimit || (tapped != null && t >= windupMs())) resolveEnemyStrike(tapped ?: Timing.MISS)
            }
            Stage.ENEMY_IMPACT -> if (t >= IMPACT_MS) {
                if (c.phase == CombatPhase.DEFEAT) enter(Stage.END_PANEL) else nextAttacker()
            }
            Stage.CHOOSE, Stage.END_PANEL -> {}
        }
    }

    /** En garde (guerrier), les deux fenêtres de parade doublent. */
    private fun guardMult() = if (combat?.guarding == true) 2 else 1
    private fun goodWindow() = ((PARRY_GOOD_MS + (combat?.hero?.parryBonusMs ?: 0)) * guardMult() * windupScale).toInt()
    private fun perfectWindow() = ((PARRY_PERFECT_MS + (combat?.hero?.parryBonusMs ?: 0) / 2) * guardMult() * windupScale).toInt()

    // ── Tour du joueur ──────────────────────────────────────────────────────────

    private fun choose(action: Action) {
        val c = combat ?: return
        pendingAction = action
        chainFirst = null
        // La barre d'ordre garde l'aperçu pendant le geste
        previewCost = when (action) {
            is Action.Cast -> c.relicCost(action.relic)
            Action.Deadly  -> c.specialCost()
            Action.Attack  -> c.attackCost()
        }
        enter(Stage.STRIKE_TIMING)
    }

    private fun resolveStrike(timing: Timing) {
        val c = combat ?: return
        if (!c.enemies[target].alive) target = c.aliveIndices().first()
        // L'Enchaînement : un geste par coup. Le premier est noté, la barre repart pour le second
        if (pendingAction == Action.Deadly && c.hero.archetype == Archetype.VAGABOND && chainFirst == null) {
            chainFirst = timing
            when (timing) {
                Timing.PERFECT -> showBanner(context.getString(R.string.roguelike_combat_perfect), 0xFFFFD54F.toInt())
                Timing.GOOD    -> showBanner(context.getString(R.string.roguelike_combat_good), 0xFFAED581.toInt())
                Timing.MISS    -> {}
            }
            enter(Stage.STRIKE_TIMING)
            return
        }
        val hits = when (val a = pendingAction) {
            is Action.Cast -> c.castRelic(a.relic, target, timing).hits
            Action.Deadly  -> if (c.hero.archetype == Archetype.VAGABOND) c.chain(target, chainFirst ?: timing, timing) else listOf(c.deadlyStrike(target, timing))
            else           -> listOf(c.attack(target, timing))
        }
        chainFirst = null
        pendingAction = null
        previewCost = null
        when (timing) {
            Timing.PERFECT -> showBanner(context.getString(R.string.roguelike_combat_perfect), 0xFFFFD54F.toInt())
            Timing.GOOD    -> showBanner(context.getString(R.string.roguelike_combat_good), 0xFFAED581.toInt())
            Timing.MISS    -> {}
        }
        showHits(hits)
    }

    /** Ce que la dernière action a fait à chaque ennemi touché, puis on passe au tour ennemi. */
    private fun showHits(hits: List<HitResult>) {
        hitTargets = hits.filter { !it.noDamage }.map { it.target }.toSet()
        floatHits(hits)
        if (hits.any { !it.noDamage }) onStrike?.invoke(hits.any { it.crit })
        if (hits.any { it.killed }) onEnemyDied?.invoke()
        enter(Stage.PLAYER_HIT)
    }

    /** Les textes d'une touche sur chaque ennemi : dégâts, affinité, réactions, jets. */
    private fun floatHits(hits: List<HitResult>) {
        for (result in hits) {
            val r = enemyRects[result.target]
            if (!result.noDamage) floatText(
                if (result.crit) context.getString(R.string.roguelike_combat_crit_damage, num(result.damage))
                else context.getString(R.string.roguelike_combat_damage, num(result.damage)),
                r.centerX(), r.top, if (result.crit) 0xFFFFEB3B.toInt() else Color.WHITE, result.crit,
            )
            var y = r.top + 26f * sp
            if (result.echo > 0) {
                floatText(context.getString(R.string.roguelike_combat_echo, num(result.echo)), r.centerX(), y, PUPPET_COLOR, false)
                y += 22f * sp
            }
            affinityRes(result.affinity)?.let { (res, color) ->
                floatText(context.getString(res), r.centerX(), y, color, false)
                y += 22f * sp
            }
            // Les réactions : c'est comme ça qu'on les découvre
            for (reaction in result.reactions) {
                floatText(context.getString(reaction.labelRes), r.centerX(), y, reaction.color, true)
                y += 26f * sp
            }
            if (result.explosion > 0)
                floatText(context.getString(R.string.roguelike_combat_damage, num(result.explosion)), r.centerX(), r.centerY(),
                    Reaction.EXPLOSION.color, true)
            result.save?.let { floatSave(it, r) }
            if (result.enraged) floatText(context.getString(R.string.roguelike_combat_enraged), r.centerX(), r.bottom, 0xFFFF5252.toInt(), true)
        }
    }

    /**
     * Un sort qui ne frappe pas (Cri de guerre, Lames empoisonnées) : pas de geste à réussir,
     * il part tout de suite, et son nom s'affiche.
     */
    private fun castWithoutStrike(c: Combat, relic: Relic) {
        if (!c.enemies[target].alive) target = c.aliveIndices().first()
        val before = c.hero.hp
        val cast = c.castRelic(relic, target, Timing.MISS)
        showBanner(context.getString(relic.labelRes), 0xFFFFE0B2.toInt())
        val healed = c.hero.hp - before
        if (healed > 0) floatText(context.getString(R.string.roguelike_combat_heal, num(healed)), heroRect.centerX(), heroRect.top, 0xFF81C784.toInt(), false)
        showHits(cast.hits)
    }

    /** Garde et Image miroir : pas de geste, le tour part tout de suite. */
    private fun useInstantSpecial(c: Combat) {
        when (c.hero.archetype) {
            Archetype.WARRIOR -> { c.guard(); showBanner(context.getString(R.string.roguelike_combat_guard), 0xFFBCAAA4.toInt()) }
            Archetype.MAGE    -> { c.mirrorImage(); showBanner(context.getString(R.string.roguelike_combat_mirror_cast), 0xFFB39DDB.toInt()) }
            Archetype.NECROMANCER -> { c.recallPuppets(); showBanner(context.getString(R.string.roguelike_combat_puppets), 0xFF80CBC4.toInt()) }
            else -> return
        }
        hitTargets = emptySet()
        enter(Stage.PLAYER_HIT)
    }

    /** La fin du tour du héros (Météore, Régénération) s'affiche, puis la main passe. */
    private fun afterPlayerAction() {
        val c = combat ?: return
        val end = c.lastHeroTurnEnd
        if (end.isEmpty) { proceed(); return }
        if (end.meteor.isNotEmpty()) {
            showBanner(context.getString(R.string.roguelike_combat_meteor_impact), Relic.METEOR.color or 0xFF303030.toInt())
            floatHits(end.meteor)
            if (end.meteor.any { it.killed }) onEnemyDied?.invoke()
        }
        if (end.healed > 0)
            floatText(context.getString(R.string.roguelike_combat_heal, num(end.healed)), heroRect.centerX(), heroRect.top, 0xFF81C784.toInt(), false)
        floatTicks(end.ticks)
        enter(Stage.HERO_STATUS)
    }

    /** La main passe à qui la jauge désigne : un ennemi, le héros, ou la fin du combat. */
    private fun proceed() {
        val c = combat ?: return
        when (c.phase) {
            CombatPhase.VICTORY, CombatPhase.DEFEAT -> enter(Stage.END_PANEL)
            CombatPhase.ENEMY_TURN -> beginEnemyTurn()
            CombatPhase.PLAYER_TURN -> {
                attacker = -1
                if (!c.enemies[target].alive) target = c.aliveIndices().firstOrNull() ?: 0
                enter(Stage.CHOOSE)
            }
        }
    }

    // ── Tour des ennemis ────────────────────────────────────────────────────────

    private fun beginEnemyTurn() {
        val c = combat ?: return
        val turn = c.startEnemyTurn()
        attackers.clear(); attackers.addAll(turn.attackers)
        floatTicks(turn.ticks)
        for (stop in turn.stopped) {
            val r = enemyRects[stop.enemy]
            val res = if (stop.element == Element.ICE) R.string.roguelike_combat_frozen_skip else R.string.roguelike_combat_paralyzed_skip
            floatText(context.getString(res), r.centerX(), r.centerY(), elementColor(stop.element), false)
        }
        for (s in turn.saves) floatSave(s.save, enemyRects[s.enemy])
        for (i in turn.enraged)
            floatText(context.getString(R.string.roguelike_combat_enraged), enemyRects[i].centerX(), enemyRects[i].bottom, 0xFFFF5252.toInt(), true)
        when {
            turn.ticks.isNotEmpty() || turn.stopped.isNotEmpty() || turn.saves.isNotEmpty() -> enter(Stage.ENEMY_STATUS)
            turn.attackers.isEmpty() -> enter(Stage.ENEMY_PAUSE)
            else                     -> nextAttacker()
        }
    }

    /** Ce que rongent la brûlure, le poison, l'Aube. */
    private fun floatTicks(ticks: List<DotTick>) {
        for (tick in ticks) {
            val r = enemyRects[tick.enemy]
            floatText(context.getString(R.string.roguelike_combat_damage, num(tick.damage)), r.centerX(), r.top, elementColor(tick.element), false)
            if (tick.killed) onEnemyDied?.invoke()
        }
    }

    /** « Efficace ! », « Peu efficace… », « Immunisé ! » : c'est comme ça qu'on apprend les affinités. */
    private fun affinityRes(a: Affinity): Pair<Int, Int>? = when (a) {
        Affinity.VULNERABLE -> R.string.roguelike_combat_effective to 0xFFFFD54F.toInt()
        Affinity.RESISTANT  -> R.string.roguelike_combat_not_effective to 0xFF90A4AE.toInt()
        Affinity.IMMUNE     -> R.string.roguelike_combat_immune to 0xFF90A4AE.toInt()
        Affinity.NORMAL     -> null
    }

    /** Le jet de sauvegarde à l'écran, façon D&D : « 🎲 14 contre DD 13 ». */
    private fun floatSave(save: SaveRoll, r: RectF) {
        when (save.reason) {
            SaveReason.IMMUNE -> {}
            SaveReason.RAGE   -> floatText(context.getString(R.string.roguelike_combat_save_rage), r.centerX(), r.bottom + 14f * sp, 0xFFB0BEC5.toInt(), false)
            // Le dé reste caché pour l'instant : on ne montre que le résultat.
            // Pour le montrer : R.string.roguelike_combat_save_roll (save.total, save.dc).
            SaveReason.ROLLED -> if (save.saved)
                floatText(context.getString(R.string.roguelike_combat_resisted), r.centerX(), r.centerY(), 0xFFB0BEC5.toInt(), false)
        }
    }

    private fun elementColor(e: Element) = when (e) {
        Element.FIRE      -> 0xFFFF8A65.toInt()
        Element.ICE       -> 0xFF81D4FA.toInt()
        Element.LIGHTNING -> 0xFFFFEE58.toInt()
        Element.POISON    -> 0xFF9CCC65.toInt()
        Element.PHYSICAL  -> 0xFFE0E0E0.toInt()
        Element.HOLY      -> 0xFFFFF59D.toInt()
        Element.ARCANE    -> 0xFFCE93D8.toInt()
    }

    private fun nextAttacker() {
        val c = combat ?: return
        if (c.phase == CombatPhase.VICTORY) { enter(Stage.END_PANEL); return }
        val next = attackers.removeFirstOrNull()
        if (next == null) {
            // Le tour de cet ennemi est fini : la jauge dit qui joue ensuite
            c.endEnemyTurn()
            attacker = -1
            proceed()
            return
        }
        attacker = next
        parry = null
        windupScale = if (c.hourglassStrikes > 0) Relic.HOURGLASS_SLOW else 1f
        enter(Stage.ENEMY_WINDUP)
    }

    private fun resolveEnemyStrike(timing: Timing) {
        val c = combat ?: return
        val strike = c.resolveStrike(attacker, timing)
        val ar = enemyRects[attacker]
        if (strike.bleed > 0) {
            floatText(context.getString(R.string.roguelike_combat_damage, num(strike.bleed)), ar.centerX(), ar.top, BLEED_COLOR, false)
            if (strike.bledOut) { onEnemyDied?.invoke(); enter(Stage.ENEMY_IMPACT); return }
        }
        if (strike.charmed) {
            floatText(context.getString(R.string.roguelike_combat_charmed_strike), ar.centerX(), ar.bottom, CHARMED_COLOR, true)
            strike.charmHit?.let { hit ->
                val r = enemyRects[hit.target]
                floatText(context.getString(R.string.roguelike_combat_damage, num(hit.damage)), r.centerX(), r.top, CHARMED_COLOR, true)
                if (hit.killed) onEnemyDied?.invoke()
            }
            enter(Stage.ENEMY_IMPACT)
            return
        }
        if (strike.thorns > 0) {
            floatText(context.getString(R.string.roguelike_combat_thorns, num(strike.thorns)), ar.centerX(), ar.bottom, STONESKIN_COLOR, false)
            if (strike.thornsKilled) onEnemyDied?.invoke()
        }
        if (strike.absorbed > 0)
            floatText(context.getString(R.string.roguelike_combat_absorbed, num(strike.absorbed)), heroRect.centerX(), heroRect.bottom, BARRIER_COLOR, false)
        if (strike.puppetAbsorbed > 0)
            floatText(context.getString(R.string.roguelike_combat_puppet_absorbed, num(strike.puppetAbsorbed)), heroRect.centerX(), heroRect.bottom + 18f * sp, PUPPET_COLOR, false)
        if (strike.missed) {
            // Le texte dit ce que fait le héros, pas ce que rate le monstre : avec un bouclier
            // ou en guerrier il encaisse sur son armure, sinon il s'écarte
            val hero = c.hero
            val res = if (hero.hasShield || hero.archetype == Archetype.WARRIOR) R.string.roguelike_combat_blocked
                else R.string.roguelike_combat_dodged
            floatText(context.getString(res), heroRect.centerX(), heroRect.top, 0xFFB0BEC5.toInt(), true)
            enter(Stage.ENEMY_IMPACT)
            return
        }
        if (strike.imageHit) {
            floatText(context.getString(R.string.roguelike_combat_image_hit), heroRect.centerX(), heroRect.top, 0xFFB39DDB.toInt(), true)
            enter(Stage.ENEMY_IMPACT)
            return
        }
        if (strike.blocked || strike.dodged) {
            val roll = strike.dodged && c.hero.archetype == Archetype.VAGABOND
            showBanner(context.getString(when { roll -> R.string.roguelike_combat_roll; strike.blocked -> R.string.roguelike_combat_blocked
                else -> R.string.roguelike_combat_dodged }), if (roll) ROLL_COLOR else 0xFFFFD54F.toInt())
            onParry?.invoke(true)
            strike.counter?.let { hit ->
                val r = enemyRects[hit.target]
                floatText(context.getString(R.string.roguelike_combat_counter, num(hit.damage)), r.centerX(), r.top, Color.WHITE, true)
                if (hit.killed) onEnemyDied?.invoke()
            }
            enter(Stage.ENEMY_IMPACT)
            return
        }
        if (strike.recovered)
            floatText(context.getString(R.string.roguelike_combat_counterspell), heroRect.centerX(), heroRect.bottom, 0xFFB39DDB.toInt(), false)
        when (timing) {
            Timing.PERFECT -> { showBanner(context.getString(R.string.roguelike_combat_parry_perfect), 0xFFFFD54F.toInt()); onParry?.invoke(true) }
            Timing.GOOD    -> { showBanner(context.getString(R.string.roguelike_combat_parry_good), 0xFF81D4FA.toInt()); onParry?.invoke(false) }
            Timing.MISS    -> onHeroHit?.invoke()
        }
        floatText(context.getString(R.string.roguelike_combat_damage, num(strike.damage)), heroRect.centerX(), heroRect.top,
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

        drawOrderBar(canvas, c)
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

        if (stage != Stage.CHOOSE && stage != Stage.END_PANEL || floaters.isNotEmpty() || banner != null || previewCost != null)
            postInvalidateOnAnimation()
    }

    private fun drawEnemies(canvas: Canvas, c: Combat) {
        val now = SystemClock.uptimeMillis()
        for ((i, e) in c.enemies.withIndex()) {
            val base = enemyRects.getOrNull(i) ?: continue
            if (!e.alive && !(stage == Stage.PLAYER_HIT && i in hitTargets)) continue
            val r = RectF(base)

            // L'attaquant s'avance pendant son élan
            if (stage == Stage.ENEMY_WINDUP && attacker == i) {
                val p = (elapsed().toFloat() / windupMs()).coerceIn(0f, 1f)
                r.offset(0f, base.height() * 0.25f * p * p)
            }
            // Recul quand on le touche
            val hitNow = stage == Stage.PLAYER_HIT && i in hitTargets
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
            canvas.drawText(context.getString(R.string.roguelike_combat_hp, num(e.hp), num(e.maxHp)), base.centerX(), bar.bottom + 13f * sp, pText)

            if (e.alive) {
                // Sa couleur, celle de ses cases dans la barre d'ordre
                val my = base.top - 11f * density
                pFill.color = ENEMY_MARKS[i % ENEMY_MARKS.size]
                canvas.drawCircle(base.centerX(), my, 5f * density, pFill)
                if (c.actingEnemy == i) {
                    pStroke.color = CURRENT_MARK; pStroke.strokeWidth = 2f * density
                    canvas.drawCircle(base.centerX(), my, 8f * density, pStroke)
                }
                drawStatuses(canvas, e, base.centerX(), bar.bottom + 27f * sp)
            }
        }
    }

    /**
     * La barre d'ordre des tours, façon FFX : à gauche celui qui a la main (cadre doré), puis
     * les tours à venir tels que la jauge les prévoit ([Combat.forecast]). Chaque ennemi garde
     * sa couleur, reprise au-dessus de lui ; un ennemi gelé est voilé de glace (il perdra ce
     * tour) ; le Météore a sa case là où il tombe. Quand le doigt vise une action, la case du
     * prochain tour du héros s'allume : c'est là qu'il tomberait.
     */
    private fun drawOrderBar(canvas: Canvas, c: Combat) {
        val current = when (c.phase) {
            CombatPhase.PLAYER_TURN -> Combat.HERO
            CombatPhase.ENEMY_TURN  -> c.actingEnemy
            else -> return
        }
        val b = orderBar
        val cell = b.height()
        val gap = 5f * density
        val afterCurrent = 8f * density
        val count = ((b.width() - afterCurrent + gap) / (cell + gap)).toInt()
        if (count < 2) return
        val slots = listOf(TurnSlot(current, frozen = current >= 0 && c.enemies[current].frozen)) +
            c.forecast(count - 1, previewCost ?: c.attackCost())
        val preview = previewCost != null
        var previewDone = false
        var x = b.left
        for ((k, slot) in slots.withIndex()) {
            val r = RectF(x, b.top, x + cell, b.bottom)
            val lit = preview && k > 0 && slot.actor == Combat.HERO && !previewDone
            if (lit) {
                previewDone = true
                // Le prochain tour du héros, si l'action visée part : il pulse
                r.offset(0f, -2f * density * (1f + sin(SystemClock.uptimeMillis() / 120.0).toFloat()))
            }
            drawOrderCell(canvas, c, slot, r, current = k == 0, lit = lit)
            x += cell + gap + if (k == 0) afterCurrent else 0f
        }
    }

    private fun drawOrderCell(canvas: Canvas, c: Combat, slot: TurnSlot, r: RectF, current: Boolean, lit: Boolean) {
        val corner = 6f * density
        pFill.color = 0xFF263238.toInt()
        canvas.drawRoundRect(r, corner, corner, pFill)
        val inner = RectF(r).apply { inset(3f * density, 3f * density) }
        val mark = when (slot.actor) {
            Combat.METEOR -> {
                // Le Météore : une boule de feu, pas un portrait
                pFill.color = Relic.METEOR.color
                canvas.drawCircle(inner.centerX(), inner.centerY(), inner.width() * 0.42f, pFill)
                pFill.color = elementColor(Element.FIRE)
                canvas.drawCircle(inner.centerX() - inner.width() * 0.1f, inner.centerY() - inner.width() * 0.1f, inner.width() * 0.2f, pFill)
                Relic.METEOR.color
            }
            Combat.HERO -> {
                heroSpritePath?.let { path -> SpriteLoader.load(context.assets, path)?.let { canvas.drawBitmap(it, null, inner, pSprite) } }
                HERO_MARK
            }
            else -> {
                SpriteLoader.load(context.assets, SpriteLoader.monsterPath(c.enemies[slot.actor].type))
                    ?.let { canvas.drawBitmap(it, null, inner, pSprite) }
                ENEMY_MARKS[slot.actor % ENEMY_MARKS.size]
            }
        }
        if (slot.frozen) { pFill.color = FROZEN_VEIL; canvas.drawRoundRect(r, corner, corner, pFill) }
        pStroke.color = if (current || lit) CURRENT_MARK else mark
        pStroke.strokeWidth = (if (current || lit) 3f else 2f) * density
        canvas.drawRoundRect(r, corner, corner, pStroke)
    }

    /** Les effets en cours sous la barre de vie, chacun à sa couleur. */
    private fun drawStatuses(canvas: Canvas, e: Enemy, cx: Float, y: Float) {
        val parts = buildList<Pair<String, Int>> {
            if (e.burnTurns > 0) add(context.getString(R.string.roguelike_combat_burning, e.burnTurns) to elementColor(Element.FIRE))
            if (e.poisonTurns > 0) add(context.getString(R.string.roguelike_combat_poisoned, e.poisonDoses, e.poisonTurns) to elementColor(Element.POISON))
            if (e.frozen) add(context.getString(R.string.roguelike_combat_frozen) to elementColor(Element.ICE))
            if (e.paralyzedTurns > 0) add(context.getString(R.string.roguelike_combat_paralyzed, e.paralyzedTurns) to elementColor(Element.LIGHTNING))
            if (e.soakedTurns > 0) add(context.getString(R.string.roguelike_combat_soaked, e.soakedTurns) to SOAKED_COLOR)
            if (e.fracturedTurns > 0) add(context.getString(R.string.roguelike_combat_fractured, e.fracturedTurns) to FRACTURED_COLOR)
            if (e.weakenedTurns > 0) add(context.getString(R.string.roguelike_combat_weakened, e.weakenedTurns) to WEAKENED_COLOR)
            if (e.blindedTurns > 0) add(context.getString(R.string.roguelike_combat_blinded, e.blindedTurns) to BLINDED_COLOR)
            if (e.marked) add(context.getString(R.string.roguelike_combat_marked) to MARKED_COLOR)
            if (e.bleedTurns > 0) add(context.getString(R.string.roguelike_combat_bleeding, e.bleedTurns) to BLEED_COLOR)
            if (e.charmed) add(context.getString(R.string.roguelike_combat_charmed) to CHARMED_COLOR)
            if (e.enraged) add(context.getString(R.string.roguelike_combat_rage_status, e.rageTurns) to RAGE_COLOR)
            if (e.slowTurns > 0) add(context.getString(R.string.roguelike_combat_slowed, e.slowTurns) to SLOWED_COLOR)
        }
        pText.textSize = 11f * sp
        pText.textAlign = Paint.Align.LEFT
        // Deux effets par ligne au plus : trois ennemis côte à côte laissent peu de place
        val gap = 8f * density
        parts.chunked(2).forEachIndexed { line, pair ->
            val widths = pair.map { pText.measureText(it.first) }
            var x = cx - (widths.sum() + gap * (pair.size - 1)) / 2f
            for ((i, part) in pair.withIndex()) {
                pText.color = part.second
                canvas.drawText(part.first, x, y + line * 13f * sp, pText)
                x += widths[i] + gap
            }
        }
        pText.textAlign = Paint.Align.CENTER
    }

    private fun drawHero(canvas: Canvas, c: Combat) {
        val r = RectF(heroRect)
        if (stage == Stage.PLAYER_HIT && hitTargets.isNotEmpty()) {
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
        canvas.drawText(context.getString(R.string.roguelike_combat_hp, num(hero.hp), num(hero.maxHp)), left, heroRect.centerY() - 10f * density, pText)
        val bar = RectF(left, heroRect.centerY(), right, heroRect.centerY() + 12f * density)
        pFill.color = 0xFF333333.toInt(); canvas.drawRect(bar, pFill)
        val ratio = hero.hp.toFloat() / hero.maxHp
        pFill.color = when { ratio > 0.5f -> 0xFF43A047.toInt(); ratio > 0.25f -> 0xFFFB8C00.toInt(); else -> 0xFFE53935.toInt() }
        canvas.drawRect(bar.left, bar.top, bar.left + bar.width() * ratio, bar.bottom, pFill)
        // Garde et doubles sous la barre de vie
        pText.textSize = 12f * sp
        var y = bar.bottom + 16f * sp
        if (c.guarding) {
            pText.color = 0xFFBCAAA4.toInt()
            canvas.drawText(context.getString(R.string.roguelike_combat_guarding), left, y, pText)
            y += 15f * sp
        }
        if (c.mirrorImages > 0) {
            pText.color = 0xFFB39DDB.toInt()
            canvas.drawText(context.getString(R.string.roguelike_combat_images, c.mirrorImages), left, y, pText)
            y += 15f * sp
        }
        if (c.rollReady) {
            pText.color = ROLL_COLOR
            canvas.drawText(context.getString(R.string.roguelike_combat_roll_ready), left, y, pText)
            y += 15f * sp
        }
        if (c.puppetHp.isNotEmpty()) {
            pText.color = PUPPET_COLOR
            canvas.drawText(context.getString(R.string.roguelike_combat_puppets_line, c.puppetHp.count { it > 0 }, c.puppetHp.size), left, y, pText)
            y += 6f * sp
            val n = c.puppetHp.size
            val gap = 6f * density
            val barW = ((right - left) - gap * (n - 1)) / n
            val barH = 9f * density
            for ((i, hp) in c.puppetHp.withIndex()) {
                val x0 = left + i * (barW + gap)
                pFill.color = 0xFF263238.toInt(); canvas.drawRect(x0, y, x0 + barW, y + barH, pFill)
                pFill.color = PUPPET_COLOR
                canvas.drawRect(x0, y, x0 + barW * (hp.toFloat() / c.puppetMaxHp).coerceIn(0f, 1f), y + barH, pFill)
            }
            y += barH + 10f * sp
        }
        if (c.empoweredAttacks > 0) {
            pText.color = EMPOWERED_COLOR
            canvas.drawText(context.getString(R.string.roguelike_combat_empowered, c.empoweredAttacks), left, y, pText)
            y += 15f * sp
        }
        val buffs = buildList<Pair<String, Int>> {
            if (c.poisonedBlades > 0) add(context.getString(R.string.roguelike_combat_poisoned_blades, c.poisonedBlades) to elementColor(Element.POISON))
            if (c.ambushReady) add(context.getString(R.string.roguelike_combat_ambush_ready) to MARKED_COLOR)
            if (c.barrier > 0) add(context.getString(R.string.roguelike_combat_barrier, num(c.barrier)) to BARRIER_COLOR)
            if (c.regenTurns > 0) add(context.getString(R.string.roguelike_combat_regen, c.regenTurns) to REGEN_COLOR)
            if (c.stoneskinTurns > 0) add(context.getString(R.string.roguelike_combat_stoneskin, c.stoneskinTurns) to STONESKIN_COLOR)
            if (c.hasteTurns > 0) add(context.getString(R.string.roguelike_combat_haste, c.hasteTurns) to HASTE_COLOR)
            if (c.hourglassStrikes > 0) add(context.getString(R.string.roguelike_combat_hourglass, c.hourglassStrikes) to HOURGLASS_COLOR)
            if (c.meteorTurns > 0) add(context.getString(R.string.roguelike_combat_meteor_incoming, c.meteorTurns) to elementColor(Element.FIRE))
        }
        for ((text, color) in buffs) {
            pText.color = color
            canvas.drawText(text, left, y, pText)
            y += 15f * sp
        }
        pText.textAlign = Paint.Align.CENTER
    }

    private fun drawButtons(canvas: Canvas, c: Combat) {
        val active = stage == Stage.CHOOSE
        drawButton(canvas, attackBtn, context.getString(R.string.roguelike_combat_attack), null, active, 0xFF8D2B2B.toInt())
        for ((i, r) in relicBtns.withIndex()) {
            val relic = c.hero.relicSlots[i]
            if (i >= c.hero.unlockedRelicSlots) {
                // Pas encore ouvert : l'étage qui l'ouvrira
                drawButton(canvas, r, context.getString(R.string.roguelike_combat_relic_locked),
                    context.getString(R.string.roguelike_combat_relic_unlock_floor, Hero.RELIC_SLOT_FLOORS[i - 1]), false, 0)
                continue
            }
            if (relic == null) {
                drawButton(canvas, r, context.getString(R.string.roguelike_combat_relic_empty), null, false, 0)
                continue
            }
            val cd = c.relicCooldowns[relic] ?: 0
            drawButton(canvas, r, context.getString(relic.labelRes),
                if (cd > 0) context.getString(R.string.roguelike_combat_cooldown, cd) else null,
                active && c.canCast(relic), relic.color)
        }
        val archetype = c.hero.archetype
        if (archetype == null) {
            drawButton(canvas, specialBtn, context.getString(R.string.roguelike_combat_special),
                context.getString(R.string.roguelike_combat_special_locked), false, 0)
        } else {
            val cd = c.hero.specialCooldown
            drawButton(canvas, specialBtn, context.getString(archetype.specialRes),
                if (cd > 0) context.getString(R.string.roguelike_combat_cooldown, cd) else context.getString(archetype.labelRes),
                active && c.canUseSpecial(), archetype.color)
        }
    }

    private fun drawButton(canvas: Canvas, r: RectF, label: String, sub: String?, enabled: Boolean, color: Int) {
        pFill.color = if (enabled) color else 0xFF2A2A2A.toInt()
        canvas.drawRoundRect(r, 12f * density, 12f * density, pFill)
        pText.color = if (enabled) Color.WHITE else 0xFF777777.toInt()
        pText.textSize = 15f * sp
        // Un nom long rétrécit plutôt que de déborder du bouton
        val room = r.width() - 12f * density
        val tw = pText.measureText(label)
        if (tw > room) pText.textSize *= room / tw
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
        val p = (elapsed().toFloat() / windupMs()).coerceAtMost(1.3f)
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
        val res = if (c != null && c.ambush) R.string.roguelike_combat_ambush else R.string.roguelike_combat_start
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
            canvas.drawText(context.getString(R.string.roguelike_combat_reward_gold, num(r.gold)), cx, y, pText)
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
                if (stage == Stage.CHOOSE) setPreview(previewAt(c, event.x, event.y))
                // La parade se joue à l'appui : c'est le geste le plus précis
                if (stage == Stage.ENEMY_WINDUP && parry == null) {
                    val delta = abs(elapsed() - windupMs())
                    parry = when {
                        delta <= perfectWindow() -> Timing.PERFECT
                        delta <= goodWindow()    -> Timing.GOOD
                        else                     -> Timing.MISS   // trop tôt : raté, pas de second essai
                    }
                    postInvalidateOnAnimation()
                }
            }
            MotionEvent.ACTION_MOVE -> if (stage == Stage.CHOOSE) setPreview(previewAt(c, event.x, event.y))
            MotionEvent.ACTION_CANCEL -> if (stage == Stage.CHOOSE) setPreview(null)
            MotionEvent.ACTION_UP -> {
                if (stage == Stage.CHOOSE) setPreview(null)
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

    /** Le coût de l'action sous le doigt, si elle est jouable : c'est l'aperçu de la barre d'ordre. */
    private fun previewAt(c: Combat, x: Float, y: Float): Double? {
        val slot = relicBtns.indexOfFirst { it.contains(x, y) }
        val relic = if (slot >= 0) c.hero.relicSlots[slot] else null
        return when {
            attackBtn.contains(x, y) -> c.attackCost()
            specialBtn.contains(x, y) && c.canUseSpecial() -> c.specialCost()
            relic != null && c.canCast(relic) -> c.relicCost(relic)
            else -> null
        }
    }

    private fun setPreview(cost: Double?) {
        if (cost == previewCost) return
        previewCost = cost
        postInvalidateOnAnimation()
    }

    private fun handleChooseTap(c: Combat, x: Float, y: Float) {
        val tappedEnemy = enemyRects.indexOfFirst { it.contains(x, y) }
        when {
            tappedEnemy >= 0 && c.enemies[tappedEnemy].alive -> { target = tappedEnemy; invalidate() }
            attackBtn.contains(x, y) -> choose(Action.Attack)
            specialBtn.contains(x, y) && c.canUseSpecial() ->
                if (c.hero.archetype == Archetype.ROGUE || c.hero.archetype == Archetype.VAGABOND) choose(Action.Deadly) else useInstantSpecial(c)
            else -> {
                val slot = relicBtns.indexOfFirst { it.contains(x, y) }
                val relic = if (slot >= 0) c.hero.relicSlots[slot] else null
                if (relic != null && c.canCast(relic)) {
                    if (relic.hits) choose(Action.Cast(relic)) else castWithoutStrike(c, relic)
                }
            }
        }
    }
}
