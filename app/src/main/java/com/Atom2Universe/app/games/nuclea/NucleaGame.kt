package com.Atom2Universe.app.games.nuclea

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  NUCLÉA — twin-stick de fusion stellaire.
//  On ne détruit jamais les atomes : on les POUSSE avec un flux de photons pour
//  les faire fusionner entre eux (H+H=He, He+He=C, … jusqu'au Fe = supernova).
// ─────────────────────────────────────────────────────────────────────────────

enum class NucleaPhase { MENU, CONSTELLATION, PLAYING, PAUSED, GAME_OVER }

/**
 * SWIFT et THRUST ne tombent jamais comme bonus au sol : ils s'obtiennent en
 * percutant une antiparticule (voir [NucleaGame.DROP_POOL]).
 */
enum class PowerUpType { MAGNETIC, TIME, GAMMA, SHIELD, PULSAR, SWIFT, THRUST }

class Atom(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val tier: Int,
    val anti: Boolean = false,
    /** Antiparticules seulement : [NucleaGame.ANTI_SWIFT] ou [NucleaGame.ANTI_THRUST]. */
    val antiKind: Int = NucleaGame.ANTI_SWIFT
) {
    var radius = 0f
    var mass = 1f
    var dead = false
    var decayTimer = -1f   // > 0 : décroissance radioactive en cours (fin de vague bloquée)
    var superTimer = -1f   // Fe uniquement : compte à rebours avant la supernova
    var hitFlash = 0f
}

class SpawnMark(
    val x: Float, val y: Float, val tier: Int, val anti: Boolean, var timer: Float,
    val antiKind: Int = NucleaGame.ANTI_SWIFT
)

class Shockwave(val x: Float, val y: Float, val maxR: Float, val strength: Float) {
    var r = 0f
    var life = 1f
}

class Boom(val x: Float, val y: Float, val maxR: Float, val color: Int) {
    var life = 0.5f
    val maxLife = 0.5f
}

class NucleaParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Int, val r: Float
)

class DustMote(var x: Float, var y: Float, var vx: Float, var vy: Float, val value: Int) {
    var life = 12f
}

class PowerUp(var x: Float, var y: Float, val type: PowerUpType) {
    var life = 11f
    var phase = 0f
}

class FloatText(var x: Float, var y: Float, val text: String, val color: Int) {
    var life = 1.2f
}

/**
 * Rayon cosmique : une particule ultra-relativiste qui traverse tout l'écran en
 * ligne droite. Elle annonce sa trajectoire par une ligne pointillée pendant
 * [WARN_TIME], puis file si vite que le flux de photons n'a aucune prise sur elle :
 * la seule parade est de sortir de la ligne avant qu'elle ne parte.
 */
class CosmicRay(
    val x0: Float, val y0: Float,
    val dirX: Float, val dirY: Float,
    /** Longueur totale de la traversée : au-delà, la particule a quitté l'arène. */
    val span: Float,
    val speed: Float
) {
    var warn = WARN_TIME
    var travelled = 0f
    var hit = false        // le joueur n'encaisse qu'une seule fois par rayon
    val x get() = x0 + dirX * travelled
    val y get() = y0 + dirY * travelled
    companion object { const val WARN_TIME = 0.8f }
}

/**
 * Neutron libre : lent, sans charge, il part dans une direction au hasard et
 * change de cap toutes les [WOBBLE] secondes. Contrairement au rayon cosmique
 * le flux le repousse — mais son vrai danger n'est pas les PV du joueur : s'il
 * percute un noyau lourd, il le FISSIONNE en deux noyaux de l'élément précédent.
 * Comme un vrai neutron libre, il finit par se désintégrer tout seul.
 */
class Neutron(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    var wobble = WOBBLE
    var life = LIFE
    var hitFlash = 0f
    var dead = false
    companion object {
        const val WOBBLE = 0.25f
        /** Durée de vie sur le terrain : court, pour qu'il ne squatte pas l'arène. */
        const val LIFE = 8f
    }
}

class BlackHole(var x: Float, var y: Float, val massNeeded: Int) {
    var massFed = 0
    var baseHorizon = 0f
    var spin = 0f
    var pulseTimer = 5f    // temps avant la prochaine éruption
    var pulseWarn = 0f     // > 0 : éruption imminente (avertissement visuel)
    /** Axe du jet polaire, figé pendant l'avertissement pour être lisible. */
    var jetAngle = 0f
    /** > 0 : le jet vient de partir (rendu du faisceau lumineux). */
    var jetFlash = 0f
    /** Temps d'apparition : tant qu'il court, le trou noir n'aspire pas et ne blesse pas. */
    var grace = SPAWN_GRACE
    /**
     * Sonné après une évasion : il ne traque plus et n'aspire plus pendant ce répit.
     * Champ distinct de [grace] parce que celui-ci pilote aussi l'animation
     * d'apparition — le réutiliser ferait rapetisser le trou noir à l'écran.
     */
    var stun = 0f
    fun horizon(): Float {
        val growth = if (massNeeded <= 0) 0f else massFed.toFloat() / massNeeded
        return baseHorizon * (1f + growth * 0.45f)
    }
    companion object { const val SPAWN_GRACE = 1.2f }
}

/** Nœud de l'arbre méta « constellations » — il est posé sur une vraie étoile du ciel. */
class MetaNode(
    val key: String,
    val constellation: Int,   // 0 = Orion, 1 = Cassiopée, 2 = Lyre
    val maxLvl: Int,
    val baseCost: Int,
    val labelRes: Int
)

/**
 * Tracé fidèle d'une constellation. Les coordonnées viennent des ascensions droites
 * et déclinaisons réelles des étoiles, converties en degrés d'arc projetés
 * (x = vers l'ouest du ciel, y = vers le sud), donc la forme dessinée est bien
 * celle qu'on voit dans le ciel — pas trois points au hasard.
 */
class ConstellationShape(
    /** x0, y0, x1, y1, … en degrés d'arc, origine arbitraire. */
    val stars: FloatArray,
    /** Éclat 0..1 de chaque étoile (dérivé de sa magnitude) → taille du point. */
    val mags: FloatArray,
    /** Paires d'indices d'étoiles reliées par l'astérisme. */
    val lines: IntArray,
    /** Étoile qui porte chaque nœud méta, dans l'ordre des META_NODES de la constellation. */
    val nodeStars: IntArray,
    /** Cadre d'affichage en portrait : gauche, haut, droite, bas (fractions de la zone de ciel). */
    val panel: FloatArray,
    /** Même chose en paysage, où le ciel est large et bas : les trois formes se mettent en colonnes. */
    val panelLand: FloatArray
) {
    val minX = (stars.indices step 2).minOf { stars[it] }
    val maxX = (stars.indices step 2).maxOf { stars[it] }
    val minY = (1 until stars.size step 2).minOf { stars[it] }
    val maxY = (1 until stars.size step 2).maxOf { stars[it] }
}

/** Progression persistante (poussière d'étoile, constellations, records). */
class NucleaMeta {
    var dust = 0L
    var bestWave = 0
    var foundMask = 1          // éléments déjà synthétisés (bit par tier, H connu de base)
    val levels = HashMap<String, Int>()

    fun lvl(key: String) = levels[key] ?: 0

    fun load(p: SharedPreferences) {
        dust = p.getLong("dust", 0L)
        bestWave = p.getInt("best_wave", 0)
        foundMask = p.getInt("found_mask", 1)
        for (n in NucleaGame.META_NODES) levels[n.key] = p.getInt("meta_" + n.key, 0)
    }

    fun save(p: SharedPreferences) {
        val e = p.edit()
        e.putLong("dust", dust)
        e.putInt("best_wave", bestWave)
        e.putInt("found_mask", foundMask)
        for (n in NucleaGame.META_NODES) e.putInt("meta_" + n.key, lvl(n.key))
        e.apply()
    }
}

class NucleaGame(private val ctx: Context) {

    companion object {
        const val TIER_COUNT = 7
        /** Clé des préférences où dort la partie en cours. */
        const val KEY_RUN = "run_state"
        const val FE = 6
        val SYMBOLS = arrayOf("H", "He", "C", "O", "Ne", "Si", "Fe")
        val TIER_COLORS = intArrayOf(
            Color.parseColor("#6EC6FF"),  // H  — bleu clair
            Color.parseColor("#FFD65C"),  // He — jaune
            Color.parseColor("#B0BEC5"),  // C  — gris
            Color.parseColor("#FF6E5E"),  // O  — rouge
            Color.parseColor("#FF6EC7"),  // Ne — rose néon
            Color.parseColor("#FFB74D"),  // Si — orange
            Color.parseColor("#F0F0FA")   // Fe — blanc métallique
        )
        // ─── Antiparticules ──────────────────────────────────────────────────
        //  Elles annihilent les atomes qu'elles touchent, mais le joueur peut les
        //  intercepter au corps à corps : son cœur de proto-étoile encaisse
        //  l'annihilation sans dommage et convertit l'énergie libérée en bonus.
        /** Positron e⁺ : léger et rapide → bonus de vitesse de déplacement. */
        const val ANTI_SWIFT = 0
        /** Antiproton p⁻ : lourd → bonus de puissance du flux. */
        const val ANTI_THRUST = 1
        val ANTI_COLOR = Color.parseColor("#B388FF")          // positron — lavande
        val ANTI_THRUST_COLOR = Color.parseColor("#E040FB")   // antiproton — magenta violacé
        fun antiColor(kind: Int) = if (kind == ANTI_THRUST) ANTI_THRUST_COLOR else ANTI_COLOR
        fun antiSymbol(kind: Int) = if (kind == ANTI_THRUST) "p⁻" else "e⁺"

        /** Durée ajoutée par antiparticule percutée, et plafond de cumul. */
        const val ANTI_BOOST_TIME = 4f
        const val ANTI_BOOST_MAX = 12f

        /** Bonus qui tombent au sol après une fusion — les boosts d'antimatière en sont exclus. */
        val DROP_POOL = listOf(
            PowerUpType.MAGNETIC, PowerUpType.TIME, PowerUpType.GAMMA,
            PowerUpType.SHIELD, PowerUpType.PULSAR
        )

        /** Durée maximale d'un bonus, pour l'arc de progression du HUD. */
        fun powerMaxDuration(t: PowerUpType): Float = when (t) {
            PowerUpType.PULSAR -> 12f
            PowerUpType.SWIFT, PowerUpType.THRUST -> ANTI_BOOST_MAX
            else -> 8f
        }
        val RAY_COLOR = Color.parseColor("#9CF6FF")       // rayon cosmique — cyan électrique
        val NEUTRON_COLOR = Color.parseColor("#C9CEDB")   // neutron — gris neutre, sans charge

        // ─── Menaces mobiles ─────────────────────────────────────────────────
        /** Vague à partir de laquelle les rayons cosmiques balaient l'arène. */
        const val RAY_FROM_WAVE = 3
        /** Vague à partir de laquelle des neutrons libres viennent fissionner les noyaux. */
        const val NEUTRON_FROM_WAVE = 7
        /** Un neutron ne fissionne que les noyaux au moins aussi lourds que le carbone. */
        const val FISSION_MIN_TIER = 2
        /**
         * Répit imposé après une fission : le temps de refusionner ce qui vient
         * d'être cassé avant qu'un nouveau neutron ne débarque.
         */
        const val NEUTRON_RESPITE = 7f

        // ─── Capture par le trou noir ────────────────────────────────────────
        /** Temps dont dispose le joueur pour s'arracher avant d'être écrasé. */
        const val CAPTURE_TIME = 5f
        /** Dégâts par seconde subis pendant la capture (contre 20/s en mort sèche avant). */
        const val CAPTURE_DPS = 10f
        const val CAPTURE_BASE_PRESSES = 15
        const val CAPTURE_PRESSES_PER_BOSS = 5
        /** Distance d'éjection après une évasion, en fraction du petit côté de l'écran. */
        const val ESCAPE_DISTANCE = 0.45f
        /** Temps pendant lequel le trou noir reste sonné après une évasion. */
        const val ESCAPE_STUN = 1.6f
        /** Portée du puits de gravité, en fraction du petit côté de l'écran. */
        const val PULL_RANGE = 0.55f

        // Arbre méta : 3 constellations × 3 étoiles
        // Coût d'un niveau = baseCost × (niveau actuel + 1)
        val META_NODES = listOf(
            // Orion — offense (Bételgeuse, Bellatrix, Rigel)
            MetaNode("force", 0, 3, 1500, R.string.nuclea_meta_force),
            MetaNode("width", 0, 3, 1250, R.string.nuclea_meta_width),
            MetaNode("reach", 0, 3, 1250, R.string.nuclea_meta_reach),
            // Cassiopée — défense (Segin, Gamma, Caph)
            MetaNode("hull", 1, 3, 1500, R.string.nuclea_meta_hull),
            MetaNode("regen", 1, 2, 2000, R.string.nuclea_meta_regen),
            MetaNode("shield", 1, 1, 5000, R.string.nuclea_meta_shield),
            // Lyre — fortune (Véga, Delta, Sulafat)
            MetaNode("dust", 2, 3, 1500, R.string.nuclea_meta_dust),
            MetaNode("luck", 2, 3, 1250, R.string.nuclea_meta_luck),
            MetaNode("heal", 2, 3, 1250, R.string.nuclea_meta_heal)
        )

        /** Les trois constellations, dans l'ordre des index de MetaNode.constellation. */
        val SHAPES = arrayOf(
            // ── Orion : le sablier (tête, deux épaules, la Ceinture, deux pieds) ──
            // 0 Bételgeuse  1 Meissa  2 Bellatrix  3 Mintaka  4 Alnilam  5 Alnitak
            // 6 Saiph  7 Rigel
            ConstellationShape(
                floatArrayOf(
                    0.00f, 2.50f,   5.01f, 0.00f,   7.51f, 3.58f,   5.79f, 10.20f,
                    4.74f, 11.10f,  3.60f, 11.90f,  1.85f, 19.60f, 10.16f, 18.10f
                ),
                floatArrayOf(0.90f, 0.35f, 0.60f, 0.50f, 0.62f, 0.58f, 0.48f, 1.00f),
                intArrayOf(1, 0,  1, 2,  0, 2,  0, 5,  2, 3,  3, 4,  4, 5,  5, 6,  3, 7),
                intArrayOf(0, 2, 7),
                floatArrayOf(0.03f, 0.02f, 0.38f, 0.58f),
                floatArrayOf(0.04f, 0.08f, 0.26f, 0.88f)
            ),
            // ── Cassiopée : le W ──
            // 0 Segin  1 Ruchbah  2 Gamma  3 Schedar  4 Caph
            ConstellationShape(
                floatArrayOf(
                    0.00f, 0.00f,   3.57f, 3.43f,   7.21f, 2.95f,   9.23f, 7.13f,  13.15f, 4.52f
                ),
                floatArrayOf(0.45f, 0.55f, 0.75f, 0.72f, 0.70f),
                intArrayOf(0, 1,  1, 2,  2, 3,  3, 4),
                intArrayOf(0, 2, 4),
                floatArrayOf(0.47f, 0.04f, 0.99f, 0.26f),
                floatArrayOf(0.32f, 0.12f, 0.62f, 0.52f)
            ),
            // ── Lyre : Véga posée sur le petit parallélogramme ──
            // 0 Véga  1 Zêta  2 Delta  3 Sheliak  4 Sulafat
            ConstellationShape(
                floatArrayOf(
                    4.51f, 0.00f,   2.90f, 1.18f,   1.01f, 1.88f,   1.82f, 5.42f,   0.00f, 6.09f
                ),
                floatArrayOf(1.00f, 0.32f, 0.30f, 0.45f, 0.50f),
                intArrayOf(0, 1,  0, 2,  1, 2,  2, 4,  4, 3,  3, 1),
                intArrayOf(0, 2, 4),
                floatArrayOf(0.46f, 0.36f, 0.90f, 0.74f),
                floatArrayOf(0.68f, 0.14f, 0.96f, 0.80f)
            )
        )
        val CONSTELLATION_NAMES = intArrayOf(
            R.string.nuclea_const_orion, R.string.nuclea_const_cassiopeia, R.string.nuclea_const_lyra
        )
        val CONSTELLATION_BONUS = intArrayOf(
            R.string.nuclea_const_orion_bonus, R.string.nuclea_const_cassiopeia_bonus, R.string.nuclea_const_lyra_bonus
        )
    }

    // ─── État global ──────────────────────────────────────────────────────────

    @Volatile var phase = NucleaPhase.MENU
    val meta = NucleaMeta()
    var sound: NucleaSoundEngine? = null
    var onGameOver: (() -> Unit)? = null
    var onMetaChanged: (() -> Unit)? = null   // persistance immédiate (achats, records)
    var onRunEnded: (() -> Unit)? = null      // la partie est finie : plus rien à reprendre

    /** Une partie est en mémoire : on peut la reprendre depuis le menu. */
    var runInProgress = false
        private set
    /** Partie sérialisée relue au démarrage, tant qu'elle n'a pas été reprise. */
    private var savedRun: String? = null
    /** Vague de la partie en attente, pour l'afficher dans le menu sans tout recharger. */
    private var savedWave = 0

    var screenW = 0f
    var screenH = 0f
    private var unit = 1f   // min(w,h)/400 — toutes les tailles sont en « unités »
    private fun u(v: Float) = v * unit

    fun onScreenSize(w: Float, h: Float) {
        screenW = w; screenH = h
        unit = min(w, h) / 400f
    }

    // ─── Joueur ───────────────────────────────────────────────────────────────

    var px = 0f; var py = 0f
    var hp = 100f
    var maxHp = 100f
    var iframe = 0f
    var shieldCharges = 0
    var reviveLeft = 0

    // Visée (stick droit) — le flux n'est actif que si le stick est incliné
    var aimX = 0f; var aimY = 0f
    var fluxActive = false

    // ─── Entités ──────────────────────────────────────────────────────────────

    val atoms = ArrayList<Atom>()
    val marks = ArrayList<SpawnMark>()
    val shocks = ArrayList<Shockwave>()
    val booms = ArrayList<Boom>()
    val particles = ArrayList<NucleaParticle>()
    val motes = ArrayList<DustMote>()
    val hpMotes = ArrayList<DustMote>()   // particules de soin lâchées par les fusions
    val powerups = ArrayList<PowerUp>()
    val floats = ArrayList<FloatText>()
    val rays = ArrayList<CosmicRay>()
    val neutrons = ArrayList<Neutron>()
    var blackHole: BlackHole? = null

    // ─── Capture par le trou noir ─────────────────────────────────────────────
    //  Franchir l'horizon ne tue plus : le joueur est happé et doit se débattre.

    /** Le joueur est happé : sticks coupés, il ne peut plus que marteler. */
    var captured = false
        private set
    /** Temps restant avant l'écrasement — la jauge « cohésion ». */
    var captureCohesion = 0f
        private set
    /** Appuis déjà donnés et appuis nécessaires pour s'arracher. */
    var capturePresses = 0
        private set
    var captureNeeded = 1
        private set
    /** Petite secousse visuelle à chaque appui, pour que le martèlement se sente. */
    var captureKick = 0f
        private set

    // ─── Vague / score ────────────────────────────────────────────────────────

    var wave = 1
    var wavesCleared = 0
    var runDust = 0L
    var comboCount = 0
    var comboTimer = 0f
    var shakeTimer = 0f
    var superFlash = 0f
    var waveBanner = 0f          // affichage « VAGUE n » en début de vague
    var bossBanner = 0f
    private val spawnQueue = ArrayList<Int>()   // tiers à faire apparaître (-1 = antimatière)
    private var spawnBurstTimer = 0f
    private var intermission = 0f               // pause entre deux vagues
    private var decayCheckTimer = 0f
    private var bossTrickleTimer = 0f
    private var rayTimer = 0f                   // prochain rayon cosmique
    private var neutronTimer = 0f               // prochain neutron libre

    // Power-ups actifs : temps restant par type
    val powerTimers = FloatArray(PowerUpType.entries.size)
    fun powerActive(t: PowerUpType) = powerTimers[t.ordinal] > 0f

    // Bonus Orion mis en cache au lancement du run (pas d'allocation à chaque frame)
    private var orionPierce = false

    // ─── Stats dérivées (méta + upgrades du run) ─────────────────────────────

    private fun metaLvl(key: String) = meta.lvl(key)

    fun constellationComplete(c: Int) =
        META_NODES.filter { it.constellation == c }.all { meta.lvl(it.key) >= it.maxLvl }

    fun fluxHalfAngle(): Float = 0.36f + 0.02f * metaLvl("width")
    fun fluxRange(): Float = u(200f) * (1f + 0.07f * metaLvl("reach"))
    /** Rayon de l'onde de répulsion du pulsar : un quart de la portée du flux. */
    fun auraRadius(): Float = fluxRange() * 0.25f
    private fun fluxForce(): Float {
        var f = u(520f) * (1f + 0.08f * metaLvl("force"))
        if (powerActive(PowerUpType.GAMMA)) f *= 2.2f
        if (powerActive(PowerUpType.THRUST)) f *= 1.5f   // antiproton percuté
        return f
    }
    private fun moveSpeed(): Float {
        var s = u(175f)
        if (powerActive(PowerUpType.SWIFT)) s *= 1.35f   // positron percuté
        return s
    }
    /**
     * Recul du flux — troisième loi de Newton : si les photons poussent les atomes,
     * ils repoussent le joueur d'autant dans l'autre sens. C'est la manœuvre qui
     * permet de s'arracher à l'attraction d'un trou noir (175 + 95 > 230), au prix
     * de devoir viser le trou noir au lieu des atomes.
     */
    private fun fluxRecoil(): Float = u(95f)
    /** Demi-largeur du faisceau du jet polaire : à côté, on est en sécurité. */
    fun jetHalfWidth(): Float = u(38f)
    fun magnetRadius(): Float = u(70f)
    private fun regenPerSec(): Float = 0.5f * metaLvl("regen")
    private fun dustMult(): Float = 1f + 0.15f * metaLvl("dust")
    private fun healMult(): Float = 1f + 0.15f * metaLvl("heal")
    private fun powerupChance(): Float = 0.07f + 0.03f * metaLvl("luck")

    // ─── Démarrage ────────────────────────────────────────────────────────────

    fun startGame() {
        atoms.clear(); marks.clear(); shocks.clear(); booms.clear()
        particles.clear(); motes.clear(); hpMotes.clear(); powerups.clear(); floats.clear()
        rays.clear(); neutrons.clear()
        blackHole = null
        spawnQueue.clear()
        powerTimers.fill(0f)
        captured = false; captureCohesion = 0f; capturePresses = 0; captureKick = 0f

        px = screenW / 2f; py = screenH / 2f
        maxHp = 100f + 15f * metaLvl("hull")
        hp = maxHp
        iframe = 0f
        shieldCharges = if (metaLvl("shield") > 0) 1 else 0
        reviveLeft = if (constellationComplete(1)) 1 else 0
        orionPierce = constellationComplete(0)
        comboCount = 0; comboTimer = 0f
        runDust = 0L
        wavesCleared = 0
        shakeTimer = 0f; superFlash = 0f; bossBanner = 0f
        intermission = 0f

        wave = if (constellationComplete(2)) 3 else 1
        startWave()
        runInProgress = true
        savedRun = null
        phase = NucleaPhase.PLAYING
    }

    private fun startWave() {
        waveBanner = 2f
        spawnQueue.clear()
        spawnBurstTimer = 0.3f
        decayCheckTimer = 1.5f
        // Menaces mobiles : elles arrivent progressivement au fil des vagues
        rayTimer = if (wave >= RAY_FROM_WAVE) rayInterval() * 0.6f else 0f
        neutronTimer = if (wave >= NEUTRON_FROM_WAVE) neutronInterval() * 0.6f else 0f

        if (wave % 5 == 0) {
            // Vague boss : trou noir mobile — il faut lui pousser les atomes dedans
            val bossIndex = wave / 5
            val horizon = u(34f)
            val spawn = bossSpawnPos(horizon)
            val bh = BlackHole(spawn[0], spawn[1], 16 + (bossIndex - 1) * 10)
            bh.baseHorizon = horizon
            // Axe de rotation tiré au sort dès la naissance : il sert d'axe au jet
            // polaire, et de direction d'éjection si le joueur s'arrache d'une capture
            bh.jetAngle = Random.nextFloat() * 2f * PI.toFloat()
            blackHole = bh
            bossBanner = 3f
            bossTrickleTimer = 0.5f
            sound?.onBlackHole()
        } else {
            var budget = 10 + wave * 5
            if (budget > 90) budget = 90
            val maxTier = min(2 + wave / 3, 3)   // on ne spawne jamais plus lourd que O
            while (budget > 0) {
                var affordable = maxTier
                while (affordable > 0 && (1 shl affordable) > budget) affordable--
                val t = Random.nextInt(affordable + 1)
                spawnQueue.add(t)
                budget -= (1 shl t)
            }
            // Antimatière à partir de la vague 6. Dans la file d'apparition,
            // -1 = positron et -2 = antiproton (les valeurs ≥ 0 sont des tiers).
            if (wave >= 6) {
                val antiCount = min((wave - 4) / 2, 4)
                repeat(antiCount) {
                    val code = if (Random.nextBoolean()) -1 else -2
                    spawnQueue.add(Random.nextInt(spawnQueue.size + 1), code)
                }
            }
        }
    }

    // ─── Boucle principale ────────────────────────────────────────────────────

    fun update(dt: Float, mx: Float, my: Float, ax: Float, ay: Float) {
        if (phase != NucleaPhase.PLAYING) return

        // Timers globaux
        if (comboTimer > 0f) { comboTimer -= dt; if (comboTimer <= 0f) comboCount = 0 }
        if (shakeTimer > 0f) shakeTimer -= dt
        if (superFlash > 0f) superFlash -= dt
        if (waveBanner > 0f) waveBanner -= dt
        if (bossBanner > 0f) bossBanner -= dt
        if (iframe > 0f) iframe -= dt
        if (captureKick > 0f) captureKick -= dt
        for (i in powerTimers.indices) if (powerTimers[i] > 0f) powerTimers[i] -= dt

        // Pendant une capture le monde tourne au ralenti : le joueur ne peut plus
        // rien piloter, il ne serait pas juste que le plateau continue à pleine vitesse.
        val timeScale = when {
            captured -> 0.5f
            powerActive(PowerUpType.TIME) -> 0.45f
            else -> 1f
        }
        val atomDt = dt * timeScale

        updatePlayer(dt, mx, my, ax, ay)
        updateCapture(dt)
        updateSpawns(dt)
        applyFlux(atomDt)
        applyAura(atomDt)
        updateAtoms(atomDt)
        resolveCollisions()
        updateShocks(atomDt)
        updateRays(atomDt)
        updateNeutrons(atomDt)
        updateBlackHole(dt)
        updatePickups(dt)
        updateCosmetics(dt)
        checkDecayStall(dt)
        checkWaveCleared(dt)

        // Régénération
        val regen = regenPerSec()
        if (regen > 0f && hp > 0f) hp = min(maxHp, hp + regen * dt)
    }

    // ─── Joueur ───────────────────────────────────────────────────────────────

    private fun updatePlayer(dt: Float, mx: Float, my: Float, ax: Float, ay: Float) {
        // Happé par le trou noir : plus aucun contrôle de pilotage, updateCapture gère
        if (captured) { fluxActive = false; return }

        val spd = moveSpeed()
        px += mx * spd * dt
        py += my * spd * dt

        // Visée : flux actif dès que le stick droit est incliné
        val aimLen = sqrt(ax * ax + ay * ay)
        fluxActive = aimLen > 0.25f
        if (fluxActive) {
            aimX = ax / aimLen; aimY = ay / aimLen
            // Recul : le flux repousse le joueur dans le sens opposé à la visée.
            // Sous champ magnétique le flux aspire au lieu de pousser, donc le
            // recul s'inverse aussi — on est tiré vers ce qu'on vise.
            val recoil = fluxRecoil() * (if (powerActive(PowerUpType.MAGNETIC)) -1f else 1f)
            px -= aimX * recoil * dt
            py -= aimY * recoil * dt
        }

        // Attraction du trou noir sur le joueur
        blackHole?.let { bh ->
            // Sonné après une évasion, il laisse le joueur souffler
            if (bh.grace > 0f || bh.stun > 0f) return@let
            val dx = bh.x - px; val dy = bh.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val pullRange = min(screenW, screenH) * PULL_RANGE
            if (d < pullRange) {
                val t = 1f - d / pullRange
                val pull = u(230f) * t * t
                px += dx / d * pull * dt
                py += dy / d * pull * dt
            }
            // Horizon franchi : on n'est plus tué d'office, on est CAPTURÉ
            if (d < bh.horizon() && iframe <= 0f) enterCapture(bh)
        }

        val margin = u(14f)
        px = px.coerceIn(margin, screenW - margin)
        py = py.coerceIn(margin, screenH - margin)
    }

    // ─── Capture : se débattre au lieu de mourir ─────────────────────────────

    private fun enterCapture(bh: BlackHole) {
        captured = true
        captureCohesion = CAPTURE_TIME
        capturePresses = 0
        // Plus le boss est avancé, plus il faut marteler pour s'en sortir
        captureNeeded = CAPTURE_BASE_PRESSES + CAPTURE_PRESSES_PER_BOSS * ((wave / 5) - 1).coerceAtLeast(0)
        fluxActive = false
        shakeTimer = 0.5f
        addFloat(bh.x, bh.y - u(60f), ctx.getString(R.string.nuclea_captured), Color.parseColor("#FF8A3C"))
        sound?.onCaptured()
    }

    /** Un appui du joueur (bouton A ou tap n'importe où) pendant la capture. */
    fun mashEscape() {
        if (!captured) return
        capturePresses++
        captureKick = 0.12f
        // Chaque appui rend un peu de marge : marteler vite, c'est aussi tenir plus longtemps
        captureCohesion = min(CAPTURE_TIME, captureCohesion + 0.05f)
        popParticles(px, py, Color.parseColor("#FFE9B8"), 3)
        sound?.onMash()
        if (capturePresses >= captureNeeded) escapeCapture()
    }

    private fun updateCapture(dt: Float) {
        if (!captured) return
        val bh = blackHole
        if (bh == null) { captured = false; return }   // le trou noir s'est effondré entre-temps

        // Le joueur est aspiré en spirale vers la singularité
        val dx = bh.x - px; val dy = bh.y - py
        val d = sqrt(dx * dx + dy * dy)
        if (d > 1f) {
            val pull = min(d, u(70f) * dt)
            px += dx / d * pull
            py += dy / d * pull
            // Composante tangentielle : la spirale, pas la ligne droite
            px += -dy / d * u(80f) * dt
            py += dx / d * u(80f) * dt
        }

        // Écrasement progressif : moins violent que les 20 PV/s d'avant, on a le temps de réagir
        hp -= CAPTURE_DPS * dt
        if (hp <= 0f) { hp = 0f; onFatalCapture(); return }

        captureCohesion -= dt
        if (captureCohesion <= 0f) onFatalCapture()
    }

    /** La cohésion (ou les PV) a lâché : on repasse par la mort normale, résurrection comprise. */
    private fun onFatalCapture() {
        hp = 0.01f
        damagePlayer(1f, 0f, 0f, instant = true)
    }

    /**
     * Évasion réussie : le joueur est recraché le long de l'axe polaire, et le trou
     * noir régurgite un quart de la masse déjà avalée — se sortir de là a un prix.
     */
    private fun escapeCapture() {
        captured = false
        capturePresses = 0
        iframe = 2f
        val bh = blackHole
        if (bh != null) {
            // Éjection le long de l'axe polaire, mais du côté qui pointe vers
            // l'intérieur de l'arène : sinon on est recraché dans un mur, donc
            // toujours collé au trou noir, donc réaspiré aussitôt.
            val ux = cos(bh.jetAngle); val uy = sin(bh.jetAngle)
            val s = if (ux * (screenW / 2f - bh.x) + uy * (screenH / 2f - bh.y) >= 0f) 1f else -1f
            val out = min(screenW, screenH) * ESCAPE_DISTANCE
            val margin = u(14f)
            px = (bh.x + ux * s * out).coerceIn(margin, screenW - margin)
            py = (bh.y + uy * s * out).coerceIn(margin, screenH - margin)
            // Garde-fou : si l'axe longeait un bord, le recadrage a pu nous laisser
            // tout près. Dans ce cas on part droit vers le centre de l'arène.
            if (dist(px, py, bh.x, bh.y) < out * 0.6f) {
                val cx = screenW / 2f - bh.x; val cy = screenH / 2f - bh.y
                val d = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
                px = (bh.x + cx / d * out).coerceIn(margin, screenW - margin)
                py = (bh.y + cy / d * out).coerceIn(margin, screenH - margin)
            }
            // Le trou noir est sonné : le temps de reprendre ses marques, il ne
            // traque plus et n'aspire plus. Sans ça, l'évasion ne servait à rien.
            bh.stun = ESCAPE_STUN
            bh.massFed = bh.massFed * 3 / 4
            shocks.add(Shockwave(px, py, u(240f), u(700f)))
            popParticles(px, py, Color.parseColor("#FFE9B8"), 24)
        }
        shakeTimer = 0.4f
        addFloat(px, py - u(34f), ctx.getString(R.string.nuclea_escaped), Color.parseColor("#FFE066"))
        sound?.onEscape()
    }

    private fun damagePlayer(amount: Float, knockX: Float, knockY: Float, instant: Boolean = false) {
        if (!instant) {
            if (iframe > 0f) return
            if (shieldCharges > 0) {
                shieldCharges--
                iframe = 0.9f
                sound?.onShieldBreak()
                addFloat(px, py - u(20f), ctx.getString(R.string.nuclea_shield_absorbed), Color.parseColor("#44CCFF"))
                return
            }
            iframe = 0.9f
        }
        hp -= amount
        if (!instant) {
            // Recul du joueur
            px -= knockX * u(18f)
            py -= knockY * u(18f)
            sound?.onPlayerHit()
        }
        if (hp <= 0f) {
            if (reviveLeft > 0) {
                reviveLeft--
                hp = maxHp * 0.5f
                // Une résurrection arrache aussi le joueur au trou noir, sinon il
                // reviendrait à la vie collé à la singularité et repartirait mourir
                if (captured) escapeCapture()
                iframe = 2f
                // Onde de choc de résurrection : repousse tout
                shocks.add(Shockwave(px, py, u(220f), u(680f)))
                addFloat(px, py - u(30f), ctx.getString(R.string.nuclea_revived), Color.parseColor("#FFE066"))
                sound?.onRevive()
            } else {
                hp = 0f
                endRun()
            }
        }
    }

    private fun endRun() {
        captured = false
        if (wave > meta.bestWave) meta.bestWave = wave
        runInProgress = false
        savedRun = null
        onRunEnded?.invoke()
        phase = NucleaPhase.GAME_OVER
        sound?.onGameOver()
        onMetaChanged?.invoke()
        onGameOver?.invoke()
    }

    // ─── Apparitions ──────────────────────────────────────────────────────────

    private fun updateSpawns(dt: Float) {
        // Marqueurs d'alerte → atome réel
        var i = 0
        while (i < marks.size) {
            val m = marks[i]
            m.timer -= dt
            if (m.timer <= 0f) {
                spawnAtomAt(m.x, m.y, m.tier, m.anti, m.antiKind)
                marks.removeAt(i)
            } else i++
        }

        if (blackHole != null) return   // les vagues boss gèrent leur propre trickle

        if (spawnQueue.isNotEmpty()) {
            spawnBurstTimer -= dt
            if (spawnBurstTimer <= 0f) {
                spawnBurstTimer = 3.5f
                val count = min(5, spawnQueue.size)
                repeat(count) {
                    val t = spawnQueue.removeAt(0)
                    queueMark(t)
                }
            }
        }
    }

    private fun queueMark(tierOrAnti: Int) {
        val anti = tierOrAnti < 0
        val tier = if (anti) 0 else tierOrAnti
        // -1 → positron, -2 → antiproton
        val kind = if (tierOrAnti == -2) ANTI_THRUST else ANTI_SWIFT
        val margin = u(26f)
        // Position sur un bord aléatoire
        val side = Random.nextInt(4)
        val x: Float; val y: Float
        when (side) {
            0 -> { x = Random.nextFloat() * screenW; y = margin }
            1 -> { x = Random.nextFloat() * screenW; y = screenH - margin }
            2 -> { x = margin; y = Random.nextFloat() * screenH }
            else -> { x = screenW - margin; y = Random.nextFloat() * screenH }
        }
        marks.add(SpawnMark(x, y, tier, anti, 1.2f, kind))
    }

    private fun spawnAtomAt(x: Float, y: Float, tier: Int, anti: Boolean, antiKind: Int = ANTI_SWIFT) {
        val a = Atom(x, y, 0f, 0f, tier, anti, antiKind)
        a.radius = u(10f + tier * 3.2f)
        a.mass = 2f.pow(tier)
        // Vitesse initiale vers l'intérieur de l'arène
        val dx = screenW / 2f - x; val dy = screenH / 2f - y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val spd = u(60f + Random.nextFloat() * 40f)
        a.vx = dx / d * spd; a.vy = dy / d * spd
        atoms.add(a)
    }

    // ─── Flux de photons (l'arme du joueur) ───────────────────────────────────

    private fun applyFlux(dt: Float) {
        if (!fluxActive) return
        val range = fluxRange()
        val half = fluxHalfAngle()
        val force = fluxForce()
        val magnetic = powerActive(PowerUpType.MAGNETIC)
        val pierce = orionPierce
        val aimAngle = atan2(aimY, aimX)

        for (a in atoms) {
            val dx = a.x - px; val dy = a.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > range + a.radius) continue
            val ang = atan2(dy, dx)

            var frac = 0f
            val diffF = angleDiff(ang, aimAngle)
            if (diffF < half + a.radius / d * 0.5f) frac = 1f
            if (frac <= 0f) continue

            val falloff = if (pierce) 1f else (1f - d / (range + a.radius)).coerceIn(0f, 1f).pow(0.7f)
            val push = force * falloff * frac / sqrt(a.mass) * (if (magnetic) -1f else 1f)
            a.vx += dx / d * push * dt
            a.vy += dy / d * push * dt
        }

        // Les neutrons sont légers : le flux les balaie bien plus fort que les noyaux
        val nr = u(7f)
        for (n in neutrons) {
            val dx = n.x - px; val dy = n.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > range + nr) continue
            if (angleDiff(atan2(dy, dx), aimAngle) >= half + nr / d * 0.5f) continue
            val falloff = if (pierce) 1f else (1f - d / (range + nr)).coerceIn(0f, 1f).pow(0.7f)
            val push = force * falloff * (if (magnetic) -1f else 1f)
            n.vx += dx / d * push * dt
            n.vy += dy / d * push * dt
        }
    }

    /**
     * Pulsar : une onde de répulsion permanente tout autour du joueur, courte portée.
     * Elle pousse toujours vers l'extérieur, même sous champ magnétique (le flux, lui,
     * aspire) — c'est une bulle défensive pour ne pas se faire coller aux atomes.
     */
    private fun applyAura(dt: Float) {
        if (!powerActive(PowerUpType.PULSAR)) return
        val r = auraRadius()
        val force = fluxForce() * 0.85f
        for (a in atoms) {
            val dx = a.x - px; val dy = a.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > r + a.radius) continue
            val falloff = (1f - (d - a.radius).coerceAtLeast(0f) / r).coerceIn(0f, 1f)
            val push = force * falloff / sqrt(a.mass)
            a.vx += dx / d * push * dt
            a.vy += dy / d * push * dt
        }
        // La bulle tient aussi les neutrons à distance
        for (n in neutrons) {
            val dx = n.x - px; val dy = n.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > r) continue
            val push = force * (1f - d / r).coerceIn(0f, 1f)
            n.vx += dx / d * push * dt
            n.vy += dy / d * push * dt
        }
    }

    // ─── Menaces mobiles : rayons cosmiques et neutrons libres ───────────────

    private fun rayInterval(): Float = (7.5f - wave * 0.25f).coerceAtLeast(2.2f)
    private fun neutronInterval(): Float = (18f - wave * 0.4f).coerceAtLeast(7f)
    /** Nombre de neutrons tolérés simultanément — sinon l'arène devient illisible. */
    private fun neutronCap(): Int = (1 + (wave - NEUTRON_FROM_WAVE) / 8).coerceIn(1, 3)

    private fun updateThreatSpawns(dt: Float) {
        // Pendant l'entre-deux-vagues on laisse souffler le joueur
        if (intermission > 0f) return

        if (wave >= RAY_FROM_WAVE) {
            rayTimer -= dt
            if (rayTimer <= 0f) {
                rayTimer = rayInterval() * (0.75f + Random.nextFloat() * 0.5f)
                spawnCosmicRay()
            }
        }
        if (wave >= NEUTRON_FROM_WAVE) {
            neutronTimer -= dt
            if (neutronTimer <= 0f) {
                neutronTimer = neutronInterval() * (0.75f + Random.nextFloat() * 0.5f)
                if (neutrons.size < neutronCap()) spawnNeutron()
            }
        }
    }

    /**
     * Le rayon part de hors-champ et traverse toute l'arène. Deux fois sur trois il
     * vise la position actuelle du joueur : comme il est annoncé 0,8 s à l'avance,
     * ça reste esquivable — c'est une incitation à ne jamais rester planté.
     */
    private fun spawnCosmicRay() {
        val diag = sqrt(screenW * screenW + screenH * screenH)
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val dx = cos(ang); val dy = sin(ang)
        val tx: Float; val ty: Float
        if (Random.nextFloat() < 0.66f) {
            tx = px; ty = py
        } else {
            tx = screenW * (0.15f + Random.nextFloat() * 0.7f)
            ty = screenH * (0.15f + Random.nextFloat() * 0.7f)
        }
        rays.add(CosmicRay(tx - dx * diag, ty - dy * diag, dx, dy, diag * 2f, u(900f)))
    }

    private fun spawnNeutron() {
        val margin = u(20f)
        val side = Random.nextInt(4)
        val x: Float; val y: Float
        when (side) {
            0 -> { x = Random.nextFloat() * screenW; y = margin }
            1 -> { x = Random.nextFloat() * screenW; y = screenH - margin }
            2 -> { x = margin; y = Random.nextFloat() * screenH }
            else -> { x = screenW - margin; y = Random.nextFloat() * screenH }
        }
        // Cap initial : vers l'intérieur, il divaguera tout seul ensuite
        val dx = screenW / 2f - x; val dy = screenH / 2f - y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val spd = neutronSpeed()
        neutrons.add(Neutron(x, y, dx / d * spd, dy / d * spd))
    }

    private fun neutronSpeed(): Float = u(120f)

    /** Distance d'un point au segment [ax,ay]–[bx,by] : le rayon va trop vite pour un test ponctuel. */
    private fun pointSegDist(pxx: Float, pyy: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 < 0.0001f) return dist(pxx, pyy, ax, ay)
        var t = ((pxx - ax) * vx + (pyy - ay) * vy) / len2
        t = t.coerceIn(0f, 1f)
        return dist(pxx, pyy, ax + vx * t, ay + vy * t)
    }

    private fun updateRays(dt: Float) {
        updateThreatSpawns(dt)
        var i = 0
        while (i < rays.size) {
            val r = rays[i]
            if (r.warn > 0f) {
                r.warn -= dt
                if (r.warn <= 0f) sound?.onCosmicRay()
                i++; continue
            }
            val fromX = r.x; val fromY = r.y
            r.travelled += r.speed * dt
            if (r.travelled > r.span) { rays.removeAt(i); continue }

            // Ionisation : quelques étincelles laissées dans le sillage
            if (Random.nextFloat() < 0.7f) {
                particles.add(NucleaParticle(
                    r.x, r.y,
                    (Random.nextFloat() - 0.5f) * u(60f), (Random.nextFloat() - 0.5f) * u(60f),
                    0.3f, 0.3f, RAY_COLOR, u(1.4f)
                ))
            }

            // Le joueur est touché si le segment parcouru pendant la frame le frôle
            if (!r.hit && !captured && iframe <= 0f) {
                if (pointSegDist(px, py, fromX, fromY, r.x, r.y) < u(15f)) {
                    r.hit = true
                    damagePlayer(12f, -r.dirX, -r.dirY)
                }
            }
            i++
        }
        while (particles.size > 260) particles.removeAt(0)
    }

    private fun updateNeutrons(dt: Float) {
        val spd = neutronSpeed()
        var i = 0
        while (i < neutrons.size) {
            val n = neutrons[i]
            if (n.hitFlash > 0f) n.hitFlash -= dt
            n.life -= dt
            if (n.life <= 0f) {
                // Désintégration β : le neutron libre ne vit pas éternellement
                popParticles(n.x, n.y, NEUTRON_COLOR, 6)
                sound?.onDecay()
                neutrons.removeAt(i); continue
            }

            // Divagation : nouveau cap au hasard, tout en gardant l'élan précédent
            n.wobble -= dt
            if (n.wobble <= 0f) {
                n.wobble = Neutron.WOBBLE * (0.6f + Random.nextFloat() * 0.8f)
                val ang = Random.nextFloat() * 2f * PI.toFloat()
                n.vx = n.vx * 0.35f + cos(ang) * spd
                n.vy = n.vy * 0.35f + sin(ang) * spd
            }

            // Vitesse plafonnée : le flux peut l'accélérer, pas le rendre incontrôlable
            val v = sqrt(n.vx * n.vx + n.vy * n.vy)
            val vmax = spd * 2.6f
            if (v > vmax) { n.vx = n.vx / v * vmax; n.vy = n.vy / v * vmax }

            n.x += n.vx * dt
            n.y += n.vy * dt

            val r = u(7f)
            if (n.x < r) { n.x = r; n.vx = -n.vx }
            if (n.x > screenW - r) { n.x = screenW - r; n.vx = -n.vx }
            if (n.y < r) { n.y = r; n.vy = -n.vy }
            if (n.y > screenH - r) { n.y = screenH - r; n.vy = -n.vy }

            // Contact avec un noyau. On repère la cible d'abord et on fissionne après
            // la boucle : fission() ajoute des atomes, il ne faut pas toucher à la
            // liste pendant qu'on la parcourt.
            var target: Atom? = null
            for (k in atoms.indices) {
                val a = atoms[k]
                if (a.dead) continue
                if (dist(n.x, n.y, a.x, a.y) >= a.radius + r) continue
                if (a.tier >= FISSION_MIN_TIER && a.superTimer <= 0f && !a.anti) {
                    target = a
                } else {
                    // Noyaux légers (H, He) : trop stables pour fissionner, le neutron ricoche
                    val dx = n.x - a.x; val dy = n.y - a.y
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    n.vx = dx / d * spd; n.vy = dy / d * spd
                    n.hitFlash = 0.15f
                }
                break
            }
            if (target != null) {
                fission(target)
                atoms.removeAll { it.dead }
                neutrons.removeAt(i)   // le neutron est absorbé par le noyau qu'il casse
                continue
            }

            // Contact avec le joueur
            if (!captured && iframe <= 0f && dist(n.x, n.y, px, py) < r + u(13f)) {
                val dx = px - n.x; val dy = py - n.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                damagePlayer(8f, -dx / d, -dy / d)
                n.vx = -dx / d * spd; n.vy = -dy / d * spd
                n.hitFlash = 0.2f
            }
            i++
        }
    }

    /** Fission : le noyau se casse en deux noyaux de l'élément précédent, éjectés en sens opposés. */
    private fun fission(a: Atom) {
        a.dead = true
        val child = a.tier - 1
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val kick = u(190f)
        val off = u(10f + child * 3.2f) * 1.05f
        for (s in intArrayOf(1, -1)) {
            val cx = a.x + cos(ang) * off * s
            val cy = a.y + sin(ang) * off * s
            val f = Atom(cx, cy, a.vx + cos(ang) * kick * s, a.vy + sin(ang) * kick * s, child)
            f.radius = u(10f + child * 3.2f)
            f.mass = 2f.pow(child)
            f.hitFlash = 0.2f
            atoms.add(f)
        }
        shocks.add(Shockwave(a.x, a.y, u(80f), u(260f)))
        popParticles(a.x, a.y, TIER_COLORS[a.tier], 12)
        addFloat(a.x, a.y - u(24f), ctx.getString(R.string.nuclea_fission), NEUTRON_COLOR)
        comboCount = 0   // la fission casse la chaîne : c'est bien une perte
        // Répit : on laisse le joueur refusionner ce qui vient d'être cassé avant
        // d'envoyer le neutron suivant. Sans ça, on ne s'en relève jamais.
        neutronTimer = maxOf(neutronTimer, NEUTRON_RESPITE)
        shakeTimer = maxOf(shakeTimer, 0.2f)
        sound?.onFission()
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = abs(a - b) % (2f * PI.toFloat())
        if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
        return d
    }

    // ─── Atomes : dérive, murs, décroissance ─────────────────────────────────

    private fun updateAtoms(dt: Float) {
        var doSupernova = false
        var superX = 0f; var superY = 0f
        var i = 0
        while (i < atoms.size) {
            val a = atoms[i]
            if (a.hitFlash > 0f) a.hitFlash -= dt

            // Légère attraction vers le joueur (les lourds dérivent moins)
            val dx = px - a.x; val dy = py - a.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val seek = u(26f) / a.mass.pow(0.4f)
            a.vx += dx / d * seek * dt
            a.vy += dy / d * seek * dt

            // Friction douce + vitesse max
            val damp = 1f - 0.35f * dt
            a.vx *= damp; a.vy *= damp
            val vmax = u(280f - a.tier * 22f)
            val v = sqrt(a.vx * a.vx + a.vy * a.vy)
            if (v > vmax) { a.vx = a.vx / v * vmax; a.vy = a.vy / v * vmax }

            a.x += a.vx * dt
            a.y += a.vy * dt

            // Rebond sur les murs
            if (a.x < a.radius) { a.x = a.radius; a.vx = -a.vx * 0.85f }
            if (a.x > screenW - a.radius) { a.x = screenW - a.radius; a.vx = -a.vx * 0.85f }
            if (a.y < a.radius) { a.y = a.radius; a.vy = -a.vy * 0.85f }
            if (a.y > screenH - a.radius) { a.y = screenH - a.radius; a.vy = -a.vy * 0.85f }

            // Décroissance radioactive (résolution des fins de vague bloquées)
            if (a.decayTimer > 0f) {
                a.decayTimer -= dt
                if (a.decayTimer <= 0f) {
                    a.dead = true
                    spawnMotes(a.x, a.y, a.tier + 1, 2)
                    popParticles(a.x, a.y, tierColor(a), 6)
                    sound?.onDecay()
                }
            }

            // Fer : supernova imminente
            if (a.superTimer > 0f) {
                a.superTimer -= dt
                if (a.superTimer <= 0f) {
                    doSupernova = true; superX = a.x; superY = a.y
                    a.dead = true
                }
            }

            // Contact avec le joueur
            if (!a.dead && !captured) {
                val pdx = px - a.x; val pdy = py - a.y
                val pd = sqrt(pdx * pdx + pdy * pdy)
                val minD = a.radius + u(13f)
                if (pd < minD) {
                    if (a.anti) {
                        // Interception : le joueur détruit l'antiparticule sans encaisser
                        // et récupère l'énergie en bonus. Volontairement hors du test
                        // d'invincibilité — le geste doit toujours marcher.
                        a.dead = true
                        annihilationBoom(a.x, a.y, antiColor(a.antiKind), harmPlayer = false)
                        grantAntiBoost(a.antiKind)
                    } else if (iframe <= 0f) {
                        damagePlayer(5f + a.tier * 3f, -pdx / pd.coerceAtLeast(1f), -pdy / pd.coerceAtLeast(1f))
                        // L'atome rebondit sur le joueur
                        val nd = pd.coerceAtLeast(1f)
                        a.vx = -pdx / nd * u(140f)
                        a.vy = -pdy / nd * u(140f)
                    }
                }
            }
            i++
        }
        atoms.removeAll { it.dead }
        if (doSupernova) supernova(superX, superY)
    }

    private fun tierColor(a: Atom) = if (a.anti) antiColor(a.antiKind) else TIER_COLORS[a.tier]

    // ─── Collisions atome-atome : fusion, annihilation, rebond ──────────────

    private fun resolveCollisions() {
        val newAtoms = ArrayList<Atom>()
        for (i in atoms.indices) {
            val a = atoms[i]
            if (a.dead) continue
            for (j in i + 1 until atoms.size) {
                val b = atoms[j]
                if (b.dead || a.dead) continue
                val dx = b.x - a.x; val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy)
                val minD = a.radius + b.radius
                if (dist >= minD || dist <= 0.001f) continue

                val nx = dx / dist; val ny = dy / dist
                // Vitesse de rapprochement
                val rvx = b.vx - a.vx; val rvy = b.vy - a.vy
                val closing = -(rvx * nx + rvy * ny)

                val canFuse = !a.anti && !b.anti && a.tier == b.tier && a.tier < FE &&
                        a.superTimer <= 0f && b.superTimer <= 0f && closing > u(60f)
                val annihilate = (a.anti != b.anti) && closing > u(30f)

                when {
                    canFuse -> {
                        a.dead = true; b.dead = true
                        newAtoms.add(makeFusion(a, b))
                    }
                    annihilate -> {
                        a.dead = true; b.dead = true
                        // La couleur vient de l'antiparticule des deux
                        val kind = if (a.anti) a.antiKind else b.antiKind
                        annihilationBoom((a.x + b.x) / 2f, (a.y + b.y) / 2f, antiColor(kind))
                    }
                    else -> {
                        // Rebond élastique (masses différentes)
                        if (closing > 0f) {
                            val impulse = 2f * closing / (a.mass + b.mass)
                            a.vx -= impulse * b.mass * nx * 0.92f
                            a.vy -= impulse * b.mass * ny * 0.92f
                            b.vx += impulse * a.mass * nx * 0.92f
                            b.vy += impulse * a.mass * ny * 0.92f
                            a.hitFlash = 0.15f; b.hitFlash = 0.15f
                            sound?.onBounce()
                        }
                        // Séparation des cercles
                        val overlap = (minD - dist) / 2f
                        a.x -= nx * overlap; a.y -= ny * overlap
                        b.x += nx * overlap; b.y += ny * overlap
                    }
                }
            }
        }
        atoms.removeAll { it.dead }
        atoms.addAll(newAtoms)
    }

    private fun makeFusion(a: Atom, b: Atom): Atom {
        val newTier = a.tier + 1
        val nx = (a.x + b.x) / 2f; val ny = (a.y + b.y) / 2f
        val fused = Atom(
            nx, ny,
            (a.vx * a.mass + b.vx * b.mass) / (a.mass + b.mass) * 0.5f,
            (a.vy * a.mass + b.vy * b.mass) / (a.mass + b.mass) * 0.5f,
            newTier
        )
        fused.radius = u(10f + newTier * 3.2f)
        fused.mass = 2f.pow(newTier)
        if (newTier == FE) fused.superTimer = 1.5f

        // Combo
        comboCount++
        comboTimer = 2.5f
        val comboMult = 1f + 0.25f * (comboCount - 1)

        // Onde de choc : peut enchaîner d'autres fusions
        val shockR = u(70f + newTier * 22f)
        shocks.add(Shockwave(nx, ny, shockR, u(300f + newTier * 60f)))
        popParticles(nx, ny, TIER_COLORS[newTier], 10 + newTier * 3)

        // Récompenses : poussière (monnaie) + particules de soin
        val dustGain = ((newTier + 1) * comboMult).toInt().coerceAtLeast(1)
        spawnMotes(nx, ny, dustGain, 2 + newTier)
        spawnHpMotes(nx, ny, newTier + 1, 2)

        if (comboCount >= 2)
            addFloat(nx, ny - u(24f), "×$comboCount", Color.parseColor("#FFE066"))

        // Première synthèse d'un élément : bonus + annonce
        val bit = 1 shl newTier
        if (meta.foundMask and bit == 0) {
            meta.foundMask = meta.foundMask or bit
            spawnMotes(nx, ny, 25, 6)
            addFloat(nx, ny - u(40f), ctx.getString(R.string.nuclea_new_element, SYMBOLS[newTier]), TIER_COLORS[newTier])
        }

        // Chance de bonus temporaire
        if (powerups.isEmpty() && Random.nextFloat() < powerupChance()) {
            powerups.add(PowerUp(nx, ny, DROP_POOL[Random.nextInt(DROP_POOL.size)]))
        }

        sound?.onFusion(newTier)
        return fused
    }

    /**
     * @param harmPlayer faux quand c'est le joueur lui-même qui a percuté
     *   l'antiparticule : dans ce cas l'explosion est spectaculaire mais inoffensive.
     */
    private fun annihilationBoom(x: Float, y: Float, color: Int, harmPlayer: Boolean = true) {
        val r = u(110f)
        booms.add(Boom(x, y, r, color))
        shocks.add(Shockwave(x, y, r * 1.4f, u(520f)))
        popParticles(x, y, color, 16)
        shakeTimer = 0.25f
        // Dégâts au joueur s'il est trop près
        if (harmPlayer) {
            val dx = px - x; val dy = py - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < r) damagePlayer(10f, dx / d.coerceAtLeast(1f), dy / d.coerceAtLeast(1f))
        }
        sound?.onAnnihilation()
    }

    /** Énergie d'annihilation récupérée : le bonus se cumule jusqu'à un plafond. */
    private fun grantAntiBoost(kind: Int) {
        val type = if (kind == ANTI_THRUST) PowerUpType.THRUST else PowerUpType.SWIFT
        val i = type.ordinal
        powerTimers[i] = min(powerTimers[i] + ANTI_BOOST_TIME, ANTI_BOOST_MAX)
        val nameRes = if (kind == ANTI_THRUST) R.string.nuclea_pow_thrust else R.string.nuclea_pow_swift
        addFloat(px, py - u(30f), ctx.getString(nameRes), antiColor(kind))
        sound?.onPowerUp()
    }

    private fun supernova(x: Float, y: Float) {
        superFlash = 0.9f
        shakeTimer = 0.7f
        booms.add(Boom(x, y, min(screenW, screenH) * 0.8f, Color.parseColor("#FFF3D0")))
        shocks.add(Shockwave(x, y, min(screenW, screenH), u(900f)))

        // Tous les atomes du plateau sont soufflés → poussière
        var total = 0
        for (a in atoms) {
            total += (a.tier + 1) * 2
            popParticles(a.x, a.y, tierColor(a), 8)
        }
        atoms.clear()
        // Le souffle balaie aussi les neutrons : la supernova fait table rase
        for (n in neutrons) popParticles(n.x, n.y, NEUTRON_COLOR, 5)
        neutrons.clear()
        spawnQueue.clear()
        total += 20   // prime de supernova (le bonus de récolte s'applique à la collecte)
        spawnMotes(x, y, total, 14)
        spawnHpMotes(x, y, 12, 5)
        addFloat(x, y - u(50f), ctx.getString(R.string.nuclea_supernova), Color.parseColor("#FFE066"))
        sound?.onSupernova()
    }

    // ─── Ondes de choc ────────────────────────────────────────────────────────

    private fun updateShocks(dt: Float) {
        var i = 0
        while (i < shocks.size) {
            val s = shocks[i]
            s.r += s.maxR * 3.2f * dt   // expansion rapide
            s.life -= dt * 2.6f
            if (s.life <= 0f || s.r > s.maxR) { shocks.removeAt(i); continue }
            // Pousse les atomes proches du front de l'onde
            for (a in atoms) {
                val dx = a.x - s.x; val dy = a.y - s.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                if (abs(d - s.r) < u(34f)) {
                    val f = s.strength * (1f - s.r / s.maxR) / sqrt(a.mass)
                    a.vx += dx / d * f * dt
                    a.vy += dy / d * f * dt
                }
            }
            // Les neutrons sont soufflés eux aussi — une supernova nettoie l'arène
            for (n in neutrons) {
                val dx = n.x - s.x; val dy = n.y - s.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                if (abs(d - s.r) < u(34f)) {
                    val f = s.strength * (1f - s.r / s.maxR)
                    n.vx += dx / d * f * dt
                    n.vy += dy / d * f * dt
                }
            }
            i++
        }
    }

    // ─── Trou noir (boss) ─────────────────────────────────────────────────────

    /**
     * Point d'apparition du trou noir : jamais collé au joueur, sinon il est aspiré
     * avant même d'avoir pu bouger (l'attraction dépasse sa vitesse de déplacement).
     * On part du symétrique du joueur par rapport au centre de l'écran ; si le joueur
     * campe au centre, on bascule sur le coin le plus éloigné de lui.
     */
    private fun bossSpawnPos(horizon: Float): FloatArray {
        val margin = horizon + u(30f)
        val minDist = min(screenW, screenH) * 0.5f
        var bx = (screenW - px).coerceIn(margin, screenW - margin)
        var by = (screenH - py).coerceIn(margin, screenH - margin)
        if (dist(bx, by, px, py) < minDist) {
            var best = -1f
            for (cx in floatArrayOf(margin, screenW - margin))
                for (cy in floatArrayOf(margin, screenH - margin)) {
                    val d = dist(cx, cy, px, py)
                    if (d > best) { best = d; bx = cx; by = cy }
                }
        }
        return floatArrayOf(bx, by)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun updateBlackHole(dt: Float) {
        val bh = blackHole ?: return
        bh.spin += dt * 2.4f
        val horizon = bh.horizon()

        // Apparition : le temps que le joueur repère où il pop, il ne traque ni n'aspire
        if (bh.grace > 0f) {
            bh.grace -= dt
            return
        }

        // Le trou noir traque lentement le joueur — impossible de camper sur un bord.
        // Sauf s'il vient d'être secoué par une évasion : là, il reste sur place.
        if (bh.stun > 0f) bh.stun -= dt
        else run {
            val dx = px - bh.x; val dy = py - bh.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val speed = u(26f)
            bh.x = (bh.x + dx / d * speed * dt).coerceIn(horizon, screenW - horizon)
            bh.y = (bh.y + dy / d * speed * dt).coerceIn(horizon, screenH - horizon)
        }

        // Trickle d'atomes légers : les munitions pour le gaver
        bossTrickleTimer -= dt
        if (bossTrickleTimer <= 0f && atoms.size + marks.size < 12) {
            bossTrickleTimer = 2.2f
            queueMark(Random.nextInt(3))
        }

        // Éruption périodique : deux faisceaux opposés le long de l'axe polaire.
        // Contrairement à une onde circulaire, il y a toujours une zone sûre —
        // perpendiculairement au jet — donc l'éruption s'esquive au lieu de se subir.
        if (bh.jetFlash > 0f) bh.jetFlash -= dt
        if (bh.pulseWarn > 0f) {
            bh.pulseWarn -= dt
            if (bh.pulseWarn <= 0f) fireJet(bh)
        } else {
            bh.pulseTimer -= dt
            if (bh.pulseTimer <= 0f) {
                bh.pulseTimer = 6f
                bh.pulseWarn = 1.1f
                // L'axe est figé dès l'avertissement : ce qu'on voit est ce qui partira
                bh.jetAngle = bh.spin % (2f * PI.toFloat())
                sound?.onBlackHole()
            }
        }

        // Absorption : le trou noir n'attire PAS les atomes — seuls ceux que le
        // joueur pousse au-delà de l'horizon sont avalés
        var fed = false
        for (a in atoms) {
            val dx = bh.x - a.x; val dy = bh.y - a.y
            if (sqrt(dx * dx + dy * dy) < horizon) {
                a.dead = true
                bh.massFed += a.tier + 1
                fed = true
                popParticles(a.x, a.y, tierColor(a), 8)
            }
        }
        // Un neutron poussé dedans compte pour une unité de masse : de quoi transformer
        // une nuisance en munition si on sait le viser
        var i = 0
        while (i < neutrons.size) {
            val n = neutrons[i]
            if (dist(n.x, n.y, bh.x, bh.y) < horizon) {
                bh.massFed += 1
                fed = true
                popParticles(n.x, n.y, NEUTRON_COLOR, 6)
                neutrons.removeAt(i)
            } else i++
        }
        if (fed) {
            atoms.removeAll { it.dead }
            sound?.onFeed()
            if (bh.massFed >= bh.massNeeded) collapseBlackHole(bh)
        }
    }

    /** Distance d'un point à l'axe du jet (les deux pôles comptent, d'où la valeur absolue). */
    fun jetDistance(bh: BlackHole, x: Float, y: Float): Float {
        val dx = x - bh.x; val dy = y - bh.y
        return abs(-sin(bh.jetAngle) * dx + cos(bh.jetAngle) * dy)
    }

    private fun fireJet(bh: BlackHole) {
        bh.jetFlash = 0.35f
        shakeTimer = 0.3f
        val half = jetHalfWidth()

        // Le joueur n'est touché que s'il est dans l'un des deux faisceaux.
        // Capturé, il est déjà en train d'être écrasé : pas de double peine.
        if (!captured && jetDistance(bh, px, py) < half) {
            val dx = px - bh.x; val dy = py - bh.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            damagePlayer(16f, dx / d, dy / d)
        }

        // Le faisceau souffle les atomes vers l'extérieur, le long de l'axe
        val ux = cos(bh.jetAngle); val uy = sin(bh.jetAngle)
        for (a in atoms) {
            if (jetDistance(bh, a.x, a.y) > half + a.radius) continue
            val along = (a.x - bh.x) * ux + (a.y - bh.y) * uy
            val s = if (along >= 0f) 1f else -1f
            a.vx += ux * s * u(620f) / sqrt(a.mass)
            a.vy += uy * s * u(620f) / sqrt(a.mass)
            a.hitFlash = 0.15f
        }
        for (n in neutrons) {
            if (jetDistance(bh, n.x, n.y) > half + u(7f)) continue
            val along = (n.x - bh.x) * ux + (n.y - bh.y) * uy
            val s = if (along >= 0f) 1f else -1f
            n.vx += ux * s * u(620f)
            n.vy += uy * s * u(620f)
        }

        // Gerbes de particules le long des deux pôles
        val reach = min(screenW, screenH) * 0.6f
        for (s in intArrayOf(1, -1)) {
            var t = bh.horizon()
            while (t < reach) {
                particles.add(NucleaParticle(
                    bh.x + ux * t * s + (Random.nextFloat() - 0.5f) * half,
                    bh.y + uy * t * s + (Random.nextFloat() - 0.5f) * half,
                    ux * s * u(120f), uy * s * u(120f),
                    0.5f, 0.5f, Color.parseColor("#FFB067"), u(2.2f)
                ))
                t += u(22f)
            }
        }
        while (particles.size > 260) particles.removeAt(0)
        sound?.onJet()
    }

    private fun collapseBlackHole(bh: BlackHole) {
        blackHole = null
        // S'il s'effondre pendant qu'on est happé (un atome poussé dedans par une onde
        // de choc, par exemple), on est libéré d'office avec un temps d'invincibilité
        if (captured) { captured = false; capturePresses = 0; iframe = 2f }
        superFlash = 0.9f
        shakeTimer = 0.8f
        booms.add(Boom(bh.x, bh.y, min(screenW, screenH) * 0.7f, Color.parseColor("#CBB8FF")))
        shocks.add(Shockwave(bh.x, bh.y, min(screenW, screenH), u(900f)))
        popParticles(bh.x, bh.y, Color.parseColor("#CBB8FF"), 40)
        spawnMotes(bh.x, bh.y, bh.massNeeded, 16)
        spawnHpMotes(bh.x, bh.y, 15, 5)
        addFloat(bh.x, bh.y - u(50f), ctx.getString(R.string.nuclea_pulsar), Color.parseColor("#CBB8FF"))
        sound?.onBossDefeated()
    }

    // ─── Poussière et bonus ───────────────────────────────────────────────────

    private fun spawnMotes(x: Float, y: Float, totalValue: Int, count: Int) {
        if (totalValue <= 0) return
        val n = count.coerceIn(1, 16)
        val per = (totalValue / n).coerceAtLeast(1)
        var remaining = totalValue
        repeat(n) {
            if (remaining <= 0) return@repeat
            val v = min(per, remaining)
            remaining -= v
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = u(40f + Random.nextFloat() * 80f)
            motes.add(DustMote(x, y, cos(ang) * spd, sin(ang) * spd, v))
        }
        if (remaining > 0) motes.add(DustMote(x, y, 0f, 0f, remaining))
    }

    /** Particules de soin : mêmes règles de vol que la poussière, mais rendent des PV. */
    private fun spawnHpMotes(x: Float, y: Float, totalHeal: Int, count: Int) {
        if (totalHeal <= 0) return
        val n = count.coerceIn(1, 6)
        val per = (totalHeal / n).coerceAtLeast(1)
        var remaining = totalHeal
        repeat(n) {
            if (remaining <= 0) return@repeat
            val v = min(per, remaining)
            remaining -= v
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = u(40f + Random.nextFloat() * 80f)
            hpMotes.add(DustMote(x, y, cos(ang) * spd, sin(ang) * spd, v))
        }
        if (remaining > 0) hpMotes.add(DustMote(x, y, 0f, 0f, remaining))
    }

    private fun updatePickups(dt: Float) {
        val magnetR = magnetRadius()
        var i = 0
        while (i < motes.size) {
            val m = motes[i]
            m.life -= dt
            if (m.life <= 0f) { motes.removeAt(i); continue }
            val dx = px - m.x; val dy = py - m.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d < magnetR) {
                m.vx += dx / d * u(900f) * dt
                m.vy += dy / d * u(900f) * dt
            } else {
                m.vx *= 1f - 2.2f * dt
                m.vy *= 1f - 2.2f * dt
            }
            m.x += m.vx * dt
            m.y += m.vy * dt
            if (d < u(18f)) {
                val gain = (m.value * dustMult()).toInt().coerceAtLeast(1)
                meta.dust += gain
                runDust += gain
                motes.removeAt(i)
                sound?.onPickup()
                continue
            }
            i++
        }

        // Particules de soin : même comportement, mais rendent des PV
        i = 0
        while (i < hpMotes.size) {
            val m = hpMotes[i]
            m.life -= dt
            if (m.life <= 0f) { hpMotes.removeAt(i); continue }
            val dx = px - m.x; val dy = py - m.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d < magnetR) {
                m.vx += dx / d * u(900f) * dt
                m.vy += dy / d * u(900f) * dt
            } else {
                m.vx *= 1f - 2.2f * dt
                m.vy *= 1f - 2.2f * dt
            }
            m.x += m.vx * dt
            m.y += m.vy * dt
            if (d < u(18f)) {
                hp = min(maxHp, hp + m.value * healMult())
                hpMotes.removeAt(i)
                sound?.onPickup()
                continue
            }
            i++
        }

        i = 0
        while (i < powerups.size) {
            val p = powerups[i]
            p.life -= dt
            p.phase += dt * 4f
            if (p.life <= 0f) { powerups.removeAt(i); continue }
            val dx = px - p.x; val dy = py - p.y
            if (dx * dx + dy * dy < u(26f) * u(26f)) {
                applyPowerUp(p.type)
                powerups.removeAt(i)
                continue
            }
            i++
        }
    }

    private fun applyPowerUp(type: PowerUpType) {
        val nameRes = when (type) {
            PowerUpType.MAGNETIC -> R.string.nuclea_pow_magnetic
            PowerUpType.TIME -> R.string.nuclea_pow_time
            PowerUpType.GAMMA -> R.string.nuclea_pow_gamma
            PowerUpType.SHIELD -> R.string.nuclea_pow_shield
            PowerUpType.PULSAR -> R.string.nuclea_pow_pulsar
            PowerUpType.SWIFT -> R.string.nuclea_pow_swift
            PowerUpType.THRUST -> R.string.nuclea_pow_thrust
        }
        addFloat(px, py - u(30f), ctx.getString(nameRes), Color.parseColor("#7EF9C8"))
        when (type) {
            PowerUpType.MAGNETIC -> powerTimers[type.ordinal] = 8f
            PowerUpType.TIME -> powerTimers[type.ordinal] = 8f
            PowerUpType.GAMMA -> powerTimers[type.ordinal] = 8f
            PowerUpType.PULSAR -> powerTimers[type.ordinal] = 12f
            PowerUpType.SHIELD -> shieldCharges = min(shieldCharges + 1, 2)
            // Hors DROP_POOL : ils ne s'obtiennent qu'en percutant une antiparticule
            PowerUpType.SWIFT, PowerUpType.THRUST ->
                powerTimers[type.ordinal] = min(powerTimers[type.ordinal] + ANTI_BOOST_TIME, ANTI_BOOST_MAX)
        }
        sound?.onPowerUp()
    }

    // ─── Effets visuels ───────────────────────────────────────────────────────

    private fun popParticles(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = u(50f + Random.nextFloat() * 170f)
            val life = 0.35f + Random.nextFloat() * 0.45f
            particles.add(
                NucleaParticle(x, y, cos(ang) * spd, sin(ang) * spd, life, life, color, u(1.6f + Random.nextFloat() * 2.4f))
            )
        }
        // Limite dure pour la perf
        while (particles.size > 260) particles.removeAt(0)
    }

    private fun addFloat(x: Float, y: Float, text: String, color: Int) {
        floats.add(FloatText(x, y, text, color))
        while (floats.size > 12) floats.removeAt(0)
    }

    private fun updateCosmetics(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) { particles.removeAt(i); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 1f - 2.5f * dt; p.vy *= 1f - 2.5f * dt
            i++
        }
        i = 0
        while (i < booms.size) {
            val b = booms[i]
            b.life -= dt
            if (b.life <= 0f) booms.removeAt(i) else i++
        }
        i = 0
        while (i < floats.size) {
            val f = floats[i]
            f.life -= dt
            f.y -= u(26f) * dt
            if (f.life <= 0f) floats.removeAt(i) else i++
        }
    }

    // ─── Anti-blocage : décroissance radioactive ─────────────────────────────

    private fun checkDecayStall(dt: Float) {
        if (blackHole != null || atoms.isEmpty() || spawnQueue.isNotEmpty() || marks.isNotEmpty()) return
        decayCheckTimer -= dt
        if (decayCheckTimer > 0f) return
        decayCheckTimer = 0.7f

        // Une fusion est-elle encore possible ? (paire de même élément, ou annihilation)
        val counts = IntArray(TIER_COUNT)
        var antiCount = 0
        var normalCount = 0
        var pendingSuper = false
        for (a in atoms) {
            if (a.superTimer > 0f) pendingSuper = true
            if (a.anti) antiCount++ else { counts[a.tier]++; normalCount++ }
        }
        if (pendingSuper) return
        val fusionPossible = counts.take(FE).any { it >= 2 }
        val annihilationPossible = antiCount > 0 && normalCount > 0
        if (fusionPossible || annihilationPossible) return

        // Blocage : tout ce qui reste devient instable et se désintègre
        var stagger = 0.8f
        for (a in atoms) {
            if (a.decayTimer <= 0f) {
                a.decayTimer = stagger
                stagger += 0.4f
            }
        }
    }

    // ─── Fin de vague ─────────────────────────────────────────────────────────

    private fun checkWaveCleared(dt: Float) {
        if (intermission > 0f) {
            intermission -= dt
            if (intermission <= 0f) {
                wave++
                startWave()
            }
            return
        }
        val bossAlive = blackHole != null
        if (!bossAlive && atoms.isEmpty() && marks.isEmpty() && spawnQueue.isEmpty()) {
            wavesCleared++
            hp = min(maxHp, hp + 15f)
            val bonus = (5 * wave * dustMult()).toInt()
            meta.dust += bonus
            runDust += bonus
            addFloat(screenW / 2f, screenH * 0.4f, ctx.getString(R.string.nuclea_wave_cleared, wave), Color.parseColor("#7EF9C8"))
            intermission = 2.5f
            sound?.onWaveCleared()
        }
    }


    // ─── Sauvegarde d'une partie en cours ─────────────────────────────────────
    //  Une partie quittée en cours de route est écrite dans les préférences pour
    //  pouvoir la reprendre exactement là où elle en était. Les positions sont
    //  enregistrées en fractions de l'écran et les vitesses en « unités », donc
    //  la reprise fonctionne même si l'écran a changé de taille ou d'orientation.

    /** Y a-t-il une partie à reprendre — en mémoire ou sur le disque ? */
    fun hasResumableRun() = runInProgress || savedRun != null

    fun loadRun(p: SharedPreferences) {
        savedRun = p.getString(KEY_RUN, null)
        savedWave = savedRun?.let {
            try { JSONObject(it).optInt("wave", 0) } catch (_: Exception) { savedRun = null; 0 }
        } ?: 0
    }

    /** Vague de la partie reprenable — celle en mémoire, ou celle qui dort sur le disque. */
    fun resumableWave() = if (runInProgress) wave else savedWave

    /** Appelé quand on quitte l'appli : mémorise la partie et le record atteint. */
    fun saveRun(p: SharedPreferences) {
        if (!runInProgress) return
        if (wave > meta.bestWave) meta.bestWave = wave
        val json = try { serializeRun() } catch (_: Exception) { return }
        savedRun = json
        savedWave = wave
        p.edit().putString(KEY_RUN, json).apply()
        meta.save(p)
    }

    fun clearSavedRun(p: SharedPreferences) {
        savedRun = null
        savedWave = 0
        p.edit().remove(KEY_RUN).apply()
    }

    /**
     * Reprend la partie : celle qui est encore en mémoire si on n'a fait qu'un
     * passage par le menu, sinon celle relue sur le disque. Le joueur récupère
     * 1,5 s d'invincibilité pour ne pas se faire toucher pendant qu'il reprend
     * ses marques.
     */
    fun resumeRun(): Boolean {
        if (!runInProgress) {
            val json = savedRun ?: return false
            try { deserializeRun(json) } catch (_: Exception) { savedRun = null; return false }
            runInProgress = true
        }
        // Les bonus de constellation ont pu changer pendant le passage par le menu
        orionPierce = constellationComplete(0)
        iframe = maxOf(iframe, 1.5f)
        waveBanner = 1.2f
        phase = NucleaPhase.PLAYING
        return true
    }

    // Conversions écran ⇄ sauvegarde
    private fun sx(x: Float) = (x / screenW).toDouble()
    private fun sy(y: Float) = (y / screenH).toDouble()
    private fun sv(v: Float) = (v / unit).toDouble()
    private fun rx(v: Double) = v.toFloat() * screenW
    private fun ry(v: Double) = v.toFloat() * screenH
    private fun rv(v: Double) = v.toFloat() * unit

    private fun serializeRun(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("wave", wave); o.put("cleared", wavesCleared); o.put("runDust", runDust)
        o.put("px", sx(px)); o.put("py", sy(py))
        o.put("hp", hp.toDouble()); o.put("maxHp", maxHp.toDouble())
        o.put("iframe", iframe.toDouble())
        o.put("shield", shieldCharges); o.put("revive", reviveLeft)
        o.put("combo", comboCount); o.put("comboT", comboTimer.toDouble())
        o.put("inter", intermission.toDouble()); o.put("burst", spawnBurstTimer.toDouble())
        o.put("decayT", decayCheckTimer.toDouble()); o.put("trickle", bossTrickleTimer.toDouble())
        o.put("rayT", rayTimer.toDouble()); o.put("neuT", neutronTimer.toDouble())
        // État de capture : on reprend exactement au milieu du corps à corps
        o.put("cap", captured)
        o.put("capCoh", captureCohesion.toDouble())
        o.put("capPress", capturePresses); o.put("capNeed", captureNeeded)

        o.put("power", JSONArray().also { for (t in powerTimers) it.put(t.toDouble()) })
        o.put("queue", JSONArray().also { for (t in spawnQueue) it.put(t) })

        o.put("atoms", JSONArray().also { arr ->
            // Le champ « anti » encode aussi le type : 0 = matière, 1 = positron,
            // 2 = antiproton. Une sauvegarde d'avant (qui n'avait que 0/1) se relit donc.
            for (a in atoms) arr.put(JSONArray()
                .put(sx(a.x)).put(sy(a.y)).put(sv(a.vx)).put(sv(a.vy))
                .put(a.tier).put(if (a.anti) 1 + a.antiKind else 0)
                .put(a.decayTimer.toDouble()).put(a.superTimer.toDouble()))
        })
        o.put("marks", JSONArray().also { arr ->
            for (m in marks) arr.put(JSONArray()
                .put(sx(m.x)).put(sy(m.y)).put(m.tier).put(if (m.anti) 1 + m.antiKind else 0)
                .put(m.timer.toDouble()))
        })
        o.put("motes", JSONArray().also { arr ->
            for (m in motes) arr.put(JSONArray()
                .put(sx(m.x)).put(sy(m.y)).put(sv(m.vx)).put(sv(m.vy))
                .put(m.value).put(m.life.toDouble()))
        })
        o.put("hpMotes", JSONArray().also { arr ->
            for (m in hpMotes) arr.put(JSONArray()
                .put(sx(m.x)).put(sy(m.y)).put(sv(m.vx)).put(sv(m.vy))
                .put(m.value).put(m.life.toDouble()))
        })
        o.put("powerups", JSONArray().also { arr ->
            for (p in powerups) arr.put(JSONArray()
                .put(sx(p.x)).put(sy(p.y)).put(p.type.ordinal)
                .put(p.life.toDouble()).put(p.phase.toDouble()))
        })
        o.put("rays", JSONArray().also { arr ->
            for (r in rays) arr.put(JSONArray()
                .put(sx(r.x0)).put(sy(r.y0)).put(r.dirX.toDouble()).put(r.dirY.toDouble())
                .put(sv(r.span)).put(sv(r.speed))
                .put(r.warn.toDouble()).put(sv(r.travelled)).put(if (r.hit) 1 else 0))
        })
        o.put("neutrons", JSONArray().also { arr ->
            for (n in neutrons) arr.put(JSONArray()
                .put(sx(n.x)).put(sy(n.y)).put(sv(n.vx)).put(sv(n.vy))
                .put(n.wobble.toDouble()).put(n.life.toDouble()))
        })
        blackHole?.let { bh ->
            o.put("bh", JSONArray()
                .put(sx(bh.x)).put(sy(bh.y)).put(bh.massNeeded).put(bh.massFed)
                .put(bh.spin.toDouble()).put(bh.pulseTimer.toDouble())
                .put(bh.pulseWarn.toDouble()).put(bh.grace.toDouble())
                .put(bh.jetAngle.toDouble()).put(bh.stun.toDouble()))
        }
        return o.toString()
    }

    private fun deserializeRun(json: String) {
        val o = JSONObject(json)
        atoms.clear(); marks.clear(); shocks.clear(); booms.clear()
        particles.clear(); motes.clear(); hpMotes.clear(); powerups.clear(); floats.clear()
        rays.clear(); neutrons.clear()
        spawnQueue.clear(); blackHole = null

        wave = o.getInt("wave"); wavesCleared = o.getInt("cleared"); runDust = o.getLong("runDust")
        px = rx(o.getDouble("px")); py = ry(o.getDouble("py"))
        hp = o.getDouble("hp").toFloat(); maxHp = o.getDouble("maxHp").toFloat()
        iframe = o.getDouble("iframe").toFloat()
        shieldCharges = o.getInt("shield"); reviveLeft = o.getInt("revive")
        comboCount = o.getInt("combo"); comboTimer = o.getDouble("comboT").toFloat()
        intermission = o.getDouble("inter").toFloat()
        spawnBurstTimer = o.getDouble("burst").toFloat()
        decayCheckTimer = o.getDouble("decayT").toFloat()
        bossTrickleTimer = o.getDouble("trickle").toFloat()
        // opt* : une partie sauvegardée avant l'ajout des menaces reste lisible
        rayTimer = o.optDouble("rayT", rayInterval().toDouble()).toFloat()
        neutronTimer = o.optDouble("neuT", neutronInterval().toDouble()).toFloat()
        captured = o.optBoolean("cap", false)
        captureCohesion = o.optDouble("capCoh", 0.0).toFloat()
        capturePresses = o.optInt("capPress", 0)
        captureNeeded = o.optInt("capNeed", CAPTURE_BASE_PRESSES).coerceAtLeast(1)
        captureKick = 0f
        // Bonus de constellation : relus depuis la méta, ils ont pu changer entre-temps
        orionPierce = constellationComplete(0)
        shakeTimer = 0f; superFlash = 0f; bossBanner = 0f; fluxActive = false

        val pw = o.getJSONArray("power")
        for (i in powerTimers.indices) powerTimers[i] = pw.optDouble(i, 0.0).toFloat()
        val q = o.getJSONArray("queue")
        for (i in 0 until q.length()) spawnQueue.add(q.getInt(i))

        val at = o.getJSONArray("atoms")
        for (i in 0 until at.length()) {
            val e = at.getJSONArray(i)
            val antiCode = e.getInt(5)
            val a = Atom(rx(e.getDouble(0)), ry(e.getDouble(1)),
                rv(e.getDouble(2)), rv(e.getDouble(3)), e.getInt(4),
                antiCode >= 1, (antiCode - 1).coerceAtLeast(ANTI_SWIFT))
            a.radius = u(10f + a.tier * 3.2f)
            a.mass = 2f.pow(a.tier)
            a.decayTimer = e.getDouble(6).toFloat()
            a.superTimer = e.getDouble(7).toFloat()
            atoms.add(a)
        }
        val mk = o.getJSONArray("marks")
        for (i in 0 until mk.length()) {
            val e = mk.getJSONArray(i)
            val antiCode = e.getInt(3)
            marks.add(SpawnMark(rx(e.getDouble(0)), ry(e.getDouble(1)),
                e.getInt(2), antiCode >= 1, e.getDouble(4).toFloat(),
                (antiCode - 1).coerceAtLeast(ANTI_SWIFT)))
        }
        for ((key, list) in listOf("motes" to motes, "hpMotes" to hpMotes)) {
            val arr = o.getJSONArray(key)
            for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                val m = DustMote(rx(e.getDouble(0)), ry(e.getDouble(1)),
                    rv(e.getDouble(2)), rv(e.getDouble(3)), e.getInt(4))
                m.life = e.getDouble(5).toFloat()
                list.add(m)
            }
        }
        val pu = o.getJSONArray("powerups")
        for (i in 0 until pu.length()) {
            val e = pu.getJSONArray(i)
            val p = PowerUp(rx(e.getDouble(0)), ry(e.getDouble(1)),
                PowerUpType.entries[e.getInt(2)])
            p.life = e.getDouble(3).toFloat()
            p.phase = e.getDouble(4).toFloat()
            powerups.add(p)
        }
        o.optJSONArray("rays")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                val r = CosmicRay(
                    rx(e.getDouble(0)), ry(e.getDouble(1)),
                    e.getDouble(2).toFloat(), e.getDouble(3).toFloat(),
                    rv(e.getDouble(4)), rv(e.getDouble(5))
                )
                r.warn = e.getDouble(6).toFloat()
                r.travelled = rv(e.getDouble(7))
                r.hit = e.getInt(8) == 1
                rays.add(r)
            }
        }
        o.optJSONArray("neutrons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                val n = Neutron(rx(e.getDouble(0)), ry(e.getDouble(1)),
                    rv(e.getDouble(2)), rv(e.getDouble(3)))
                n.wobble = e.getDouble(4).toFloat()
                n.life = e.getDouble(5).toFloat()
                neutrons.add(n)
            }
        }
        if (o.has("bh")) {
            val e = o.getJSONArray("bh")
            val bh = BlackHole(rx(e.getDouble(0)), ry(e.getDouble(1)), e.getInt(2))
            bh.baseHorizon = u(34f)
            bh.massFed = e.getInt(3)
            bh.spin = e.getDouble(4).toFloat()
            bh.pulseTimer = e.getDouble(5).toFloat()
            bh.pulseWarn = e.getDouble(6).toFloat()
            bh.grace = e.getDouble(7).toFloat()
            bh.jetAngle = e.optDouble(8, 0.0).toFloat()
            bh.stun = e.optDouble(9, 0.0).toFloat()
            blackHole = bh
        } else {
            captured = false   // pas de trou noir, pas de capture
        }
    }

    // ─── Constellations (méta) ────────────────────────────────────────────────

    fun nodeCost(node: MetaNode): Int = node.baseCost * (meta.lvl(node.key) + 1)

    /**
     * Effet chiffré d'un nœud à un niveau donné, prêt à afficher dans le panneau de détails.
     * Les unités sont des symboles (♥ points de vie, ✦ poussière, ◆ bouclier) pour rester
     * lisibles dans les 14 langues sans traduction.
     */
    fun nodeEffect(key: String, lvl: Int): String = when (key) {
        "force" -> "+${8 * lvl} %"                                    // poussée du flux
        "width" -> "${(((0.36f + 0.02f * lvl) * 2f) * 57.29578f + 0.5f).toInt()}°"  // ouverture totale du cône
        "reach" -> "+${7 * lvl} %"                                    // portée du flux
        "hull"  -> "${100 + 15 * lvl} ♥"                              // points de vie max
        "regen" -> if (lvl == 0) "0 ♥/s" else "${0.5f * lvl} ♥/s"     // régénération
        "shield"-> "$lvl ◆"                                           // boucliers au départ
        "dust"  -> "+${15 * lvl} % ✦"                                 // poussière récoltée
        "luck"  -> "${7 + 3 * lvl} %"                                 // chance de power-up
        "heal"  -> "+${15 * lvl} % ♥"                                 // efficacité des soins
        else    -> ""
    }

    /**
     * Poussière déjà investie dans les améliorations. Un niveau i coûte baseCost × i,
     * donc un nœud au niveau L a coûté baseCost × (1 + 2 + … + L) = baseCost × L(L+1)/2.
     */
    fun respecRefund(): Long = META_NODES.sumOf { n ->
        val l = meta.lvl(n.key).toLong()
        n.baseCost * l * (l + 1) / 2
    }

    /** Remet toutes les constellations à zéro et rend l'intégralité de la poussière dépensée. */
    fun respec(): Boolean {
        val refund = respecRefund()
        if (refund <= 0L) return false
        meta.dust += refund
        for (n in META_NODES) meta.levels[n.key] = 0
        sound?.onBuy()
        onMetaChanged?.invoke()
        return true
    }

    fun tryBuyNode(node: MetaNode): Boolean {
        val lvl = meta.lvl(node.key)
        if (lvl >= node.maxLvl) return false
        val cost = nodeCost(node)
        if (meta.dust < cost) return false
        meta.dust -= cost
        meta.levels[node.key] = lvl + 1
        sound?.onBuy()
        onMetaChanged?.invoke()
        return true
    }
}
