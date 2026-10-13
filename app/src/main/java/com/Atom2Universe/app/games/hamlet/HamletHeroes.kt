package com.Atom2Universe.app.games.hamlet

enum class HeroFaction { GROVE, DAWN, FORGE }
enum class HeroRole { GUARDIAN, RANGER, ROGUE, HEALER, BARD, MAGE, DUELIST, SUMMONER, ENGINEER }
enum class HeroRarity { RARE, EPIC, LEGENDARY, MYTHIC }

data class HeroDef(val id: Int, val faction: HeroFaction, val role: HeroRole, val rarity: HeroRarity,
    val rescue: Int, val hp: Int, val attack: Int, val armor: Int, val interval: Float, val skill: Float, val ultimate: Float)

class HeroState(val id: Int) {
    var owned = false
    var level = 1
    var stars = 0
    var shards = 0L
    val skills = intArrayOf(1, 1, 1, 1)
    val gear = IntArray(4)
    val gearTier = IntArray(4)
    val traits = IntArray(4)
    val def get() = HamletHeroes.defs[id]
}

data class HeroNumbers(val hp: Double, val attack: Double, val armor: Double, val crit: Double,
    val skillBoost: Double, val reduction: Double, val haste: Double)

object HamletHeroes {
    val defs = listOf(
        HeroDef(0, HeroFaction.GROVE, HeroRole.RANGER, HeroRarity.RARE, 0, 420, 70, 10, 1.25f, 7f, 18f),
        HeroDef(1, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.RARE, 0, 900, 40, 42, 1.6f, 8f, 20f),
        HeroDef(2, HeroFaction.GROVE, HeroRole.ROGUE, HeroRarity.EPIC, 6, 470, 90, 12, 1.05f, 6f, 18f),
        HeroDef(3, HeroFaction.GROVE, HeroRole.HEALER, HeroRarity.RARE, 0, 490, 43, 15, 1.7f, 7f, 20f),
        HeroDef(4, HeroFaction.DAWN, HeroRole.BARD, HeroRarity.EPIC, 14, 570, 52, 20, 1.5f, 8f, 21f),
        HeroDef(5, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.LEGENDARY, 22, 500, 94, 12, 1.6f, 8f, 22f),
        HeroDef(6, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.EPIC, 32, 960, 48, 46, 1.5f, 9f, 21f),
        HeroDef(7, HeroFaction.FORGE, HeroRole.DUELIST, HeroRarity.RARE, 0, 580, 74, 22, 1.2f, 7f, 19f),
        HeroDef(8, HeroFaction.DAWN, HeroRole.HEALER, HeroRarity.LEGENDARY, 44, 650, 55, 24, 1.5f, 8f, 20f),
        HeroDef(9, HeroFaction.GROVE, HeroRole.SUMMONER, HeroRarity.LEGENDARY, 54, 590, 68, 19, 1.5f, 9f, 22f),
        HeroDef(10, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.MYTHIC, 72, 570, 96, 20, 1.6f, 9f, 24f),
        HeroDef(11, HeroFaction.FORGE, HeroRole.ENGINEER, HeroRarity.MYTHIC, 90, 620, 85, 26, 1.25f, 8f, 22f),
    )
    val bonds = listOf(intArrayOf(0, 2, 3), intArrayOf(1, 6, 7), intArrayOf(4, 5, 8), intArrayOf(2, 7, 11))
    /** Le contre augmente les dégâts de 20 % ; le triangle laisse une réponse à toute faction. */
    fun counter(a: HeroFaction, b: HeroFaction) = if ((a.ordinal + 2) % 3 == b.ordinal) 1.2 else if ((b.ordinal + 2) % 3 == a.ordinal) 0.85 else 1.0
    fun numbers(h: HeroState, academy: Int = 0): HeroNumbers {
        val d = h.def
        val advancement = 1.0 + (h.level - 1) * 0.09 + h.stars * 0.055
        val set = h.gear.minOrNull() ?: 0
        val setTier = h.gearTier.minOrNull() ?: 0
        val gearAttack = (h.gear[0] + h.gear[3]) * 5.0 + (h.gearTier[0] + h.gearTier[3]) * 12.0
        val gearHp = (h.gear[1] + h.gear[2]) * 30.0 + (h.gearTier[1] + h.gearTier[2]) * 60.0
        val mastery = 1.0 + set * 0.008 + setTier * 0.02
        val hp = (d.hp * advancement + gearHp) * mastery * (1 + h.traits[1] * 0.025 + academy * 0.015 + (h.skills[2] - 1) * 0.025)
        val attack = (d.attack * advancement + gearAttack) * mastery * (1 + h.traits[0] * 0.025)
        return HeroNumbers(hp, attack, d.armor + h.gear[1] * 2.0 + h.gear[2] + h.traits[2] * 2,
            (0.05 + h.stars * 0.006 + h.skills[1] * 0.004).coerceAtMost(0.35),
            1.0 + (h.skills[0] - 1) * 0.05 + (h.skills[3] - 1) * 0.03,
            (h.traits[2] * 0.008).coerceAtMost(0.25), (h.traits[3] * 0.012).coerceAtMost(0.3))
    }
}
