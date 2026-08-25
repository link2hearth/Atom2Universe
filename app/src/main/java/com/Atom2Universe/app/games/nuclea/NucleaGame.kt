package com.Atom2Universe.app.games.nuclea

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.Atom2Universe.app.R
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

enum class PowerUpType { MAGNETIC, TIME, GAMMA, SHIELD, BINARY }

class Atom(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val tier: Int,
    val anti: Boolean = false
) {
    var radius = 0f
    var mass = 1f
    var dead = false
    var decayTimer = -1f   // > 0 : décroissance radioactive en cours (fin de vague bloquée)
    var superTimer = -1f   // Fe uniquement : compte à rebours avant la supernova
    var hitFlash = 0f
}

class SpawnMark(val x: Float, val y: Float, val tier: Int, val anti: Boolean, var timer: Float)

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

class BlackHole(var x: Float, var y: Float, val massNeeded: Int) {
    var massFed = 0
    var baseHorizon = 0f
    var spin = 0f
    var pulseTimer = 5f    // temps avant la prochaine éruption
    var pulseWarn = 0f     // > 0 : éruption imminente (avertissement visuel)
    fun horizon(): Float {
        val growth = if (massNeeded <= 0) 0f else massFed.toFloat() / massNeeded
        return baseHorizon * (1f + growth * 0.45f)
    }
}

/** Nœud de l'arbre méta « constellations » (position normalisée 0..1). */
class MetaNode(
    val key: String,
    val constellation: Int,   // 0 = Orion, 1 = Cassiopée, 2 = Lyre
    val nx: Float, val ny: Float,
    val maxLvl: Int,
    val baseCost: Int,
    val labelRes: Int
)

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
        val ANTI_COLOR = Color.parseColor("#B388FF")

        // Arbre méta : 3 constellations × 3 étoiles
        // Coût d'un niveau = baseCost × (niveau actuel + 1)
        val META_NODES = listOf(
            // Orion — offense
            MetaNode("force", 0, 0.18f, 0.30f, 3, 1500, R.string.nuclea_meta_force),
            MetaNode("width", 0, 0.34f, 0.16f, 3, 1250, R.string.nuclea_meta_width),
            MetaNode("reach", 0, 0.46f, 0.34f, 3, 1250, R.string.nuclea_meta_reach),
            // Cassiopée — défense
            MetaNode("hull", 1, 0.62f, 0.14f, 3, 1500, R.string.nuclea_meta_hull),
            MetaNode("regen", 1, 0.76f, 0.28f, 2, 2000, R.string.nuclea_meta_regen),
            MetaNode("shield", 1, 0.88f, 0.12f, 1, 5000, R.string.nuclea_meta_shield),
            // Lyre — fortune
            MetaNode("dust", 2, 0.30f, 0.72f, 3, 1500, R.string.nuclea_meta_dust),
            MetaNode("luck", 2, 0.52f, 0.84f, 3, 1250, R.string.nuclea_meta_luck),
            MetaNode("heal", 2, 0.70f, 0.68f, 3, 1250, R.string.nuclea_meta_heal)
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
    var blackHole: BlackHole? = null

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
    private fun fluxForce(): Float {
        var f = u(520f) * (1f + 0.08f * metaLvl("force"))
        if (powerActive(PowerUpType.GAMMA)) f *= 2.2f
        return f
    }
    private fun moveSpeed(): Float = u(175f)
    fun magnetRadius(): Float = u(70f)
    private fun regenPerSec(): Float = 0.5f * metaLvl("regen")
    private fun dustMult(): Float = 1f + 0.15f * metaLvl("dust")
    private fun healMult(): Float = 1f + 0.15f * metaLvl("heal")
    private fun powerupChance(): Float = 0.07f + 0.03f * metaLvl("luck")

    // ─── Démarrage ────────────────────────────────────────────────────────────

    fun startGame() {
        atoms.clear(); marks.clear(); shocks.clear(); booms.clear()
        particles.clear(); motes.clear(); hpMotes.clear(); powerups.clear(); floats.clear()
        blackHole = null
        spawnQueue.clear()
        powerTimers.fill(0f)

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
        phase = NucleaPhase.PLAYING
    }

    private fun startWave() {
        waveBanner = 2f
        spawnQueue.clear()
        spawnBurstTimer = 0.3f
        decayCheckTimer = 1.5f

        if (wave % 5 == 0) {
            // Vague boss : trou noir mobile — il faut lui pousser les atomes dedans
            val bossIndex = wave / 5
            val bh = BlackHole(screenW / 2f, screenH * 0.38f, 16 + (bossIndex - 1) * 10)
            bh.baseHorizon = u(34f)
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
            // Antimatière à partir de la vague 6
            if (wave >= 6) {
                val antiCount = min((wave - 4) / 2, 4)
                repeat(antiCount) { spawnQueue.add(Random.nextInt(spawnQueue.size + 1), -1) }
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
        for (i in powerTimers.indices) if (powerTimers[i] > 0f) powerTimers[i] -= dt

        val timeScale = if (powerActive(PowerUpType.TIME)) 0.45f else 1f
        val atomDt = dt * timeScale

        updatePlayer(dt, mx, my, ax, ay)
        updateSpawns(dt)
        applyFlux(atomDt)
        updateAtoms(atomDt)
        resolveCollisions()
        updateShocks(atomDt)
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
        val spd = moveSpeed()
        px += mx * spd * dt
        py += my * spd * dt

        // Visée : flux actif dès que le stick droit est incliné
        val aimLen = sqrt(ax * ax + ay * ay)
        fluxActive = aimLen > 0.25f
        if (fluxActive) { aimX = ax / aimLen; aimY = ay / aimLen }

        // Attraction du trou noir sur le joueur
        blackHole?.let { bh ->
            val dx = bh.x - px; val dy = bh.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val pullRange = min(screenW, screenH) * 0.75f
            if (d < pullRange) {
                val t = 1f - d / pullRange
                val pull = u(230f) * t * t
                px += dx / d * pull * dt
                py += dy / d * pull * dt
            }
            // Horizon : gros dégâts continus
            if (d < bh.horizon() + u(12f) && iframe <= 0f) {
                damagePlayer(20f * dt, dx / d, dy / d, instant = true)
            }
        }

        val margin = u(14f)
        px = px.coerceIn(margin, screenW - margin)
        py = py.coerceIn(margin, screenH - margin)
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
        if (wave > meta.bestWave) meta.bestWave = wave
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
                spawnAtomAt(m.x, m.y, m.tier, m.anti)
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
        marks.add(SpawnMark(x, y, tier, anti, 1.2f))
    }

    private fun spawnAtomAt(x: Float, y: Float, tier: Int, anti: Boolean) {
        val a = Atom(x, y, 0f, 0f, tier, anti)
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
        val binary = powerActive(PowerUpType.BINARY)

        for (a in atoms) {
            val dx = a.x - px; val dy = a.y - py
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > range + a.radius) continue
            val ang = atan2(dy, dx)

            // Cône avant + cône miroir pendant « étoile binaire »
            var frac = 0f
            val diffF = angleDiff(ang, aimAngle)
            if (diffF < half + a.radius / d * 0.5f) frac = 1f
            else if (binary) {
                val diffB = angleDiff(ang, aimAngle + PI.toFloat())
                if (diffB < half + a.radius / d * 0.5f) frac = 1f
            }
            if (frac <= 0f) continue

            val falloff = if (pierce) 1f else (1f - d / (range + a.radius)).coerceIn(0f, 1f).pow(0.7f)
            val push = force * falloff * frac / sqrt(a.mass) * (if (magnetic) -1f else 1f)
            a.vx += dx / d * push * dt
            a.vy += dy / d * push * dt
        }
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
            if (!a.dead && iframe <= 0f) {
                val pdx = px - a.x; val pdy = py - a.y
                val pd = sqrt(pdx * pdx + pdy * pdy)
                val minD = a.radius + u(13f)
                if (pd < minD) {
                    if (a.anti) {
                        a.dead = true
                        annihilationBoom(a.x, a.y)
                        damagePlayer(14f, -pdx / pd.coerceAtLeast(1f), -pdy / pd.coerceAtLeast(1f))
                    } else {
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

    private fun tierColor(a: Atom) = if (a.anti) ANTI_COLOR else TIER_COLORS[a.tier]

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
                        annihilationBoom((a.x + b.x) / 2f, (a.y + b.y) / 2f)
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
            powerups.add(PowerUp(nx, ny, PowerUpType.entries[Random.nextInt(PowerUpType.entries.size)]))
        }

        sound?.onFusion(newTier)
        return fused
    }

    private fun annihilationBoom(x: Float, y: Float) {
        val r = u(110f)
        booms.add(Boom(x, y, r, ANTI_COLOR))
        shocks.add(Shockwave(x, y, r * 1.4f, u(520f)))
        popParticles(x, y, ANTI_COLOR, 16)
        shakeTimer = 0.25f
        // Dégâts au joueur s'il est trop près
        val dx = px - x; val dy = py - y
        val d = sqrt(dx * dx + dy * dy)
        if (d < r) damagePlayer(10f, dx / d.coerceAtLeast(1f), dy / d.coerceAtLeast(1f))
        sound?.onAnnihilation()
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
            i++
        }
    }

    // ─── Trou noir (boss) ─────────────────────────────────────────────────────

    /** Rayon de la zone de danger de l'éruption (partagé avec l'anneau d'avertissement). */
    fun pulseRadius(bh: BlackHole): Float = bh.horizon() + u(90f)

    private fun updateBlackHole(dt: Float) {
        val bh = blackHole ?: return
        bh.spin += dt * 2.4f
        val horizon = bh.horizon()

        // Le trou noir traque lentement le joueur — impossible de camper sur un bord
        run {
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

        // Éruption périodique : recrache les atomes proches et blesse le joueur
        if (bh.pulseWarn > 0f) {
            bh.pulseWarn -= dt
            if (bh.pulseWarn <= 0f) {
                val r = pulseRadius(bh)
                booms.add(Boom(bh.x, bh.y, r, Color.parseColor("#FF8A3C")))
                shocks.add(Shockwave(bh.x, bh.y, r * 1.5f, u(700f)))
                val dx = px - bh.x; val dy = py - bh.y
                val d = sqrt(dx * dx + dy * dy)
                if (d < r) damagePlayer(16f, dx / d.coerceAtLeast(1f), dy / d.coerceAtLeast(1f))
                shakeTimer = 0.3f
                sound?.onAnnihilation()
            }
        } else {
            bh.pulseTimer -= dt
            if (bh.pulseTimer <= 0f) {
                bh.pulseTimer = 6f
                bh.pulseWarn = 1.1f
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
        if (fed) {
            atoms.removeAll { it.dead }
            sound?.onFeed()
            if (bh.massFed >= bh.massNeeded) collapseBlackHole(bh)
        }
    }

    private fun collapseBlackHole(bh: BlackHole) {
        blackHole = null
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
            PowerUpType.BINARY -> R.string.nuclea_pow_binary
        }
        addFloat(px, py - u(30f), ctx.getString(nameRes), Color.parseColor("#7EF9C8"))
        when (type) {
            PowerUpType.MAGNETIC -> powerTimers[type.ordinal] = 8f
            PowerUpType.TIME -> powerTimers[type.ordinal] = 8f
            PowerUpType.GAMMA -> powerTimers[type.ordinal] = 8f
            PowerUpType.BINARY -> powerTimers[type.ordinal] = 12f
            PowerUpType.SHIELD -> shieldCharges = min(shieldCharges + 1, 2)
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

    // ─── Constellations (méta) ────────────────────────────────────────────────

    fun nodeCost(node: MetaNode): Int = node.baseCost * (meta.lvl(node.key) + 1)

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
