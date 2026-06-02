package com.Atom2Universe.app.games.caves.entity

internal class Projectile(
    var x: Double, var y: Double, var z: Double,
    val dirX: Double, val dirY: Double, val dirZ: Double,
    val speed: Float,
    val damage: Int,
    val weapon: WeaponDef,
    val isRock: Boolean = false
) {
    var travelDist = 0.0
    var velY: Double = dirY * speed.toDouble()
}
