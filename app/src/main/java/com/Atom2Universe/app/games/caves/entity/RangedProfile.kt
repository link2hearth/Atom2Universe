package com.Atom2Universe.app.games.caves.entity

/**
 * Une arme à distance : un objet ordinaire à identifiant fixe, aux dégâts fixes. Sans chargeur,
 * elle se tend (dégâts × tension, de 45 % à 100 %) ; avec, elle tire à pleine puissance.
 * [gear] : les bonus d'armure (critique, vitesse) s'appliquent à ses projectiles.
 */
internal data class RangedProfile(
    val type: String, val item: Short, val damage: Int,
    val interval: Float, val speed: Float, val spread: Float, val range: Float,
    val magazine: Int = 0, val reload: Float = 0f, val automatic: Boolean = false,
    val pellets: Int = 1, val kind: ProjectileKind = ProjectileKind.BULLET, val gear: Boolean = false
) {
    /** Armes à feu : réservées au mode Assaut, jamais fabriquées en Survie. */
    val firearm get() = kind == ProjectileKind.BULLET || kind == ProjectileKind.PELLET

    companion object {
        val all = listOf(
            RangedProfile("sling",9906,12,.55f,24f,.012f,60f,kind=ProjectileKind.ROCK),
            RangedProfile("bow",9907,14,.60f,32f,.003f,90f,kind=ProjectileKind.ARROW,gear=true),
            // Armée d'avance : le carreau part d'un coup, puis l'arbalète se réarme seule.
            RangedProfile("crossbow",9908,24,.85f,44f,.002f,100f,1,1.9f,kind=ProjectileKind.BOLT,gear=true),
            RangedProfile("gun",9914,26,.32f,65f,.009f,75f,12,1.25f),
            RangedProfile("shotgun",9915,87,.90f,55f,.12f,24f,6,2.2f,pellets=6,kind=ProjectileKind.PELLET),
            RangedProfile("smg",9916,10,.095f,65f,.026f,48f,28,1.65f,automatic=true),
            RangedProfile("dual_pistols",9917,17,.18f,65f,.027f,50f,20,1.9f,automatic=true),
            RangedProfile("lever_rifle",9918,72,.95f,90f,.002f,120f,8,2.0f)
        ).associateBy { it.type }
        private val byItem = all.values.associateBy { it.item }
        fun of(id: Short?): RangedProfile? = byItem[id]
    }
}

/** Les munitions sont débitées au tir ; le chargeur limite la cadence sans en créer. */
internal class MagazineState(private val capacity: Int, private val duration: Float) {
    var remaining = capacity
        private set
    var reloadRemaining = 0f
        private set
    var shots = 0
        private set
    val progress get() = if (reloadRemaining > 0f) 1f-reloadRemaining/duration else 0f
    fun snapshot()=org.json.JSONObject().put("remaining",remaining).put("reload",reloadRemaining.toDouble()).put("shots",shots)
    fun restore(j: org.json.JSONObject) {
        remaining=j.optInt("remaining",capacity).coerceIn(0,capacity)
        reloadRemaining=j.optDouble("reload",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,duration) ?: 0f
        shots=j.optInt("shots",0).coerceAtLeast(0)
    }
    fun reload() { if (remaining < capacity && reloadRemaining <= 0f) reloadRemaining = duration }
    fun update(dt: Float) {
        if (reloadRemaining <= 0f) return
        reloadRemaining = (reloadRemaining-dt).coerceAtLeast(0f)
        if (reloadRemaining == 0f) remaining = capacity
    }
    fun shoot(): Boolean {
        if (reloadRemaining > 0f || remaining <= 0) return false
        remaining--; shots++
        return true
    }
}

internal enum class ProjectileKind(val gravity: Double) {
    LEGACY(0.0), ROCK(12.0), ARROW(4.5), BOLT(1.8), BULLET(0.0), PELLET(0.0), VENOM(1.2)
}
