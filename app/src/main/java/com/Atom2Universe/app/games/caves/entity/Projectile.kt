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
    val maxRange: Float = 90f
) {
    var stuck = false
    var age = 0f
    var travelDist = 0.0
    var velY: Double = dirY * speed.toDouble()
}
