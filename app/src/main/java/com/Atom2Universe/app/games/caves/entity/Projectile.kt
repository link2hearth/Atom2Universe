package com.Atom2Universe.app.games.caves.entity

internal class Projectile(
    var x: Double, var y: Double, var z: Double,
    val dirX: Double, val dirY: Double, val dirZ: Double,
    val speed: Float,
    val damage: Int,
    val weapon: WeaponDef,
    val isRock: Boolean = false,
    /** Stats rollés de l'arme qui a tiré ce projectile (crit, vol de vie, éléments…) —
     *  vide pour un caillou lancé à la main. */
    val stats: Map<String, Int> = emptyMap(),
    /** true si tiré par une arme équipée (arc, arbalète…) : passe par la résolution de
     *  combat complète (critique, statuts, recul) au lieu d'un simple dégât plat. */
    val isPlayerWeapon: Boolean = false,
    val kind: ProjectileKind = if (isRock) ProjectileKind.ROCK else ProjectileKind.LEGACY,
    val ammoId: Short? = null,
    val maxRange: Float = 90f,
    /** Tiré par un ennemi (soldat du mode Assaut) : ne touche que le joueur, jamais les autres ennemis. */
    val fromEnemy: Boolean = false
) {
    var stuck = false
    var age = 0f
    var travelDist = 0.0
    var velY: Double = dirY * speed.toDouble()

    fun substeps(dt: Float): Int = kotlin.math.ceil(maxOf(speed.toDouble(),kotlin.math.abs(velY))*dt/.15).toInt().coerceIn(1,256)

    fun advance(dt: Float) {
        val ox=x;val oy=y;val oz=z
        velY-=kind.gravity*dt
        x+=dirX*speed*dt;y+=velY*dt;z+=dirZ*speed*dt
        travelDist+=kotlin.math.sqrt((x-ox)*(x-ox)+(y-oy)*(y-oy)+(z-oz)*(z-oz))
    }
}
