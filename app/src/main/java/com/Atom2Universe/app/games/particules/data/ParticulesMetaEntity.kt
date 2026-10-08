package com.Atom2Universe.app.games.particules.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "particules_meta")
data class ParticulesMetaEntity(
    @PrimaryKey val id: Int = 1,
    val highScore: Long = 0L,
    val highestLevel: Int = 0,
    val runsPlayed: Int = 0,
    val totalGold: Long = 0L,
    val totalXp: Long = 0L,
    val bestCombo: Int = 0,
    val shopExtraLives: Int = 0,
    val shopSlowBall: Int = 0,
    val shopWidePaddle: Int = 0,
    val shopStartShield: Int = 0,
    val shopGoldMagnet: Int = 0,
    val shopMultiStart: Int = 0,
    val shopStackTimers: Int = 0
)
