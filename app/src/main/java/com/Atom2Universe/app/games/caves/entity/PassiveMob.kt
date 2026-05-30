package com.Atom2Universe.app.games.caves.entity

internal enum class PassiveMobType(
    val maxHp: Int,
    val speed: Float,
    val eyeHeight: Float,
    val radius: Float,
    val texturePath: String
) {
    COW(10, 1.5f, 1.3f, 0.45f, "Cave World/Mobs/cow.png")
}

internal enum class PassiveMobState { IDLE, WANDER, FLEE }

internal class PassiveMob(
    val id: Int,
    val type: PassiveMobType,
    var x: Double,
    var y: Double,
    var z: Double
) {
    var hp: Int = type.maxHp
    var state = PassiveMobState.IDLE
    var yaw = 0f
    var animTime = 0f
    var velY = 0.0
    var wanderTimer = 0f
    var wanderDirX = 0f
    var wanderDirZ = 0f
    var idleTimer = 2f
}
