package com.Atom2Universe.app.games.caves.entity

internal class Projectile(
    var x: Double, var y: Double, var z: Double,
    val dirX: Double, val dirY: Double, val dirZ: Double,
    val speed: Float,
    val damage: Int,
    val weapon: WeaponDef
) {
    var travelDist = 0.0
}
