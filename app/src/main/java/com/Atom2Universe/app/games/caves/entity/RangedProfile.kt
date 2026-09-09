package com.Atom2Universe.app.games.caves.entity

/** Identités de gameplay, indépendantes des jets de dégâts et de rareté. */
internal data class RangedProfile(
    val interval: Float, val speed: Float, val spread: Float, val range: Float,
    val magazine: Int = 0, val reload: Float = 0f, val automatic: Boolean = false,
    val pellets: Int = 1, val kind: ProjectileKind = ProjectileKind.BULLET
) {
    companion object {
        val all = linkedMapOf(
            "sling" to RangedProfile(.55f,24f,.012f,60f,kind=ProjectileKind.ROCK),
            "bow" to RangedProfile(.60f,32f,.003f,90f,kind=ProjectileKind.ARROW),
            "crossbow" to RangedProfile(.85f,44f,.002f,100f,kind=ProjectileKind.BOLT),
            "gun" to RangedProfile(.32f,65f,.009f,75f,12,1.25f),
            "shotgun" to RangedProfile(.90f,55f,.12f,24f,6,2.2f,pellets=6,kind=ProjectileKind.PELLET),
            "smg" to RangedProfile(.095f,65f,.026f,48f,28,1.65f,automatic=true),
            "dual_pistols" to RangedProfile(.18f,65f,.027f,50f,20,1.9f,automatic=true),
            "lever_rifle" to RangedProfile(.95f,90f,.002f,120f,8,2.0f)
        )
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
    LEGACY(0.0), ROCK(12.0), ARROW(4.5), BOLT(1.8), BULLET(0.0), PELLET(0.0)
}
