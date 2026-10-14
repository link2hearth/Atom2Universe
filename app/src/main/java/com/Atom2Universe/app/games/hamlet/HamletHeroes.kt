package com.Atom2Universe.app.games.hamlet

enum class HeroFaction { GROVE, DAWN, FORGE }
enum class HeroRole { GUARDIAN, RANGER, ROGUE, HEALER, BARD, MAGE, DUELIST, SUMMONER, ENGINEER }
enum class HeroRarity { COMMON, RARE, EPIC, LEGENDARY, MYTHIC }
enum class HeroAbility { NONE, WARD, PURIFY, FROST, EXPOSE, REJUVENATE, EXECUTION }

data class HeroDef(val id: Int, val faction: HeroFaction, val role: HeroRole, val rarity: HeroRarity,
    val rescue: Int, val hp: Int, val attack: Int, val armor: Int, val interval: Float, val skill: Float, val ultimate: Float,
    val ability: HeroAbility = HeroAbility.NONE) {
    val portraitBase: Int get() = if (id < 12) id else when (role) {
        HeroRole.RANGER -> 0; HeroRole.GUARDIAN -> 1; HeroRole.ROGUE -> 2; HeroRole.HEALER -> 3
        HeroRole.BARD -> 4; HeroRole.MAGE -> 5; HeroRole.DUELIST -> 7; HeroRole.SUMMONER -> 9; HeroRole.ENGINEER -> 11
    }
}

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
    const val STAR_CAP = 10
    const val STAR_BONUS = 0.05
    val defs = listOf(
        HeroDef(0, HeroFaction.GROVE, HeroRole.RANGER, HeroRarity.COMMON, 0, 420, 70, 10, 1.25f, 7f, 18f),
        HeroDef(1, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.COMMON, 0, 900, 40, 42, 1.6f, 8f, 20f),
        HeroDef(2, HeroFaction.GROVE, HeroRole.ROGUE, HeroRarity.COMMON, 6, 470, 90, 12, 1.05f, 6f, 18f),
        HeroDef(3, HeroFaction.GROVE, HeroRole.HEALER, HeroRarity.COMMON, 0, 490, 43, 15, 1.7f, 7f, 20f),
        HeroDef(4, HeroFaction.DAWN, HeroRole.BARD, HeroRarity.COMMON, 14, 570, 52, 20, 1.5f, 8f, 21f),
        HeroDef(5, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.EPIC, 22, 500, 94, 12, 1.6f, 8f, 22f),
        HeroDef(6, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.RARE, 32, 960, 48, 46, 1.5f, 9f, 21f),
        HeroDef(7, HeroFaction.FORGE, HeroRole.DUELIST, HeroRarity.COMMON, 0, 580, 74, 22, 1.2f, 7f, 19f),
        HeroDef(8, HeroFaction.DAWN, HeroRole.HEALER, HeroRarity.LEGENDARY, 44, 650, 55, 24, 1.5f, 8f, 20f),
        HeroDef(9, HeroFaction.GROVE, HeroRole.SUMMONER, HeroRarity.EPIC, 54, 590, 68, 19, 1.5f, 9f, 22f),
        HeroDef(10, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.MYTHIC, 72, 570, 96, 20, 1.6f, 9f, 24f),
        HeroDef(11, HeroFaction.FORGE, HeroRole.ENGINEER, HeroRarity.EPIC, 90, 620, 85, 26, 1.25f, 8f, 22f),
        // Append-only identities: the first twelve remain compatible with every existing save.
        HeroDef(12, HeroFaction.GROVE, HeroRole.GUARDIAN, HeroRarity.COMMON, 0, 820, 42, 39, 1.6f, 8f, 20f, HeroAbility.REJUVENATE),
        HeroDef(13, HeroFaction.DAWN, HeroRole.RANGER, HeroRarity.COMMON, 0, 430, 68, 13, 1.25f, 7f, 18f, HeroAbility.EXPOSE),
        HeroDef(14, HeroFaction.FORGE, HeroRole.HEALER, HeroRarity.COMMON, 0, 540, 40, 21, 1.7f, 7f, 20f, HeroAbility.PURIFY),
        HeroDef(15, HeroFaction.GROVE, HeroRole.BARD, HeroRarity.COMMON, 4, 550, 51, 18, 1.5f, 8f, 21f, HeroAbility.REJUVENATE),
        HeroDef(16, HeroFaction.DAWN, HeroRole.GUARDIAN, HeroRarity.RARE, 8, 920, 46, 44, 1.6f, 8f, 21f, HeroAbility.WARD),
        HeroDef(17, HeroFaction.FORGE, HeroRole.RANGER, HeroRarity.COMMON, 12, 480, 77, 19, 1.25f, 7f, 19f, HeroAbility.EXECUTION),
        HeroDef(18, HeroFaction.GROVE, HeroRole.MAGE, HeroRarity.RARE, 18, 510, 87, 17, 1.6f, 8f, 22f, HeroAbility.FROST),
        HeroDef(19, HeroFaction.DAWN, HeroRole.ROGUE, HeroRarity.COMMON, 16, 480, 84, 15, 1.1f, 6f, 18f, HeroAbility.EXPOSE),
        HeroDef(20, HeroFaction.FORGE, HeroRole.BARD, HeroRarity.RARE, 20, 610, 50, 24, 1.5f, 8f, 21f, HeroAbility.WARD),
        HeroDef(21, HeroFaction.GROVE, HeroRole.DUELIST, HeroRarity.RARE, 24, 610, 76, 24, 1.2f, 7f, 19f, HeroAbility.REJUVENATE),
        HeroDef(22, HeroFaction.DAWN, HeroRole.SUMMONER, HeroRarity.RARE, 28, 580, 67, 20, 1.5f, 9f, 22f, HeroAbility.WARD),
        HeroDef(23, HeroFaction.FORGE, HeroRole.ROGUE, HeroRarity.RARE, 34, 520, 88, 23, 1.1f, 6f, 19f, HeroAbility.EXECUTION),
        HeroDef(24, HeroFaction.GROVE, HeroRole.ENGINEER, HeroRarity.RARE, 38, 600, 78, 21, 1.3f, 8f, 22f, HeroAbility.EXPOSE),
        HeroDef(25, HeroFaction.DAWN, HeroRole.DUELIST, HeroRarity.RARE, 42, 680, 79, 26, 1.25f, 7f, 20f, HeroAbility.PURIFY),
        HeroDef(26, HeroFaction.FORGE, HeroRole.MAGE, HeroRarity.RARE, 46, 560, 88, 21, 1.6f, 8f, 22f, HeroAbility.EXPOSE),
        HeroDef(27, HeroFaction.GROVE, HeroRole.HEALER, HeroRarity.RARE, 50, 620, 54, 20, 1.6f, 8f, 21f, HeroAbility.PURIFY),
        HeroDef(28, HeroFaction.DAWN, HeroRole.ENGINEER, HeroRarity.RARE, 30, 610, 75, 23, 1.3f, 8f, 22f, HeroAbility.FROST),
        HeroDef(29, HeroFaction.FORGE, HeroRole.SUMMONER, HeroRarity.EPIC, 58, 660, 64, 26, 1.6f, 9f, 23f, HeroAbility.WARD),
        HeroDef(30, HeroFaction.GROVE, HeroRole.RANGER, HeroRarity.LEGENDARY, 66, 580, 88, 21, 1.2f, 7f, 21f, HeroAbility.EXECUTION),
        HeroDef(31, HeroFaction.DAWN, HeroRole.BARD, HeroRarity.EPIC, 74, 690, 58, 23, 1.5f, 8f, 22f, HeroAbility.PURIFY),
        HeroDef(32, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.EPIC, 82, 1050, 49, 48, 1.6f, 9f, 23f, HeroAbility.REJUVENATE),
        HeroDef(33, HeroFaction.GROVE, HeroRole.SUMMONER, HeroRarity.EPIC, 94, 630, 71, 22, 1.5f, 9f, 23f, HeroAbility.REJUVENATE),
        HeroDef(34, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.EPIC, 102, 580, 95, 21, 1.6f, 9f, 24f, HeroAbility.FROST),
        HeroDef(35, HeroFaction.FORGE, HeroRole.DUELIST, HeroRarity.LEGENDARY, 110, 730, 86, 29, 1.25f, 7f, 21f, HeroAbility.EXECUTION),
    )
    val bonds = listOf(intArrayOf(0, 2, 3), intArrayOf(1, 6, 7), intArrayOf(4, 5, 8), intArrayOf(2, 7, 11))
    /** Le contre augmente les dégâts de 20 % ; le triangle laisse une réponse à toute faction. */
    fun counter(a: HeroFaction, b: HeroFaction) = if ((a.ordinal + 2) % 3 == b.ordinal) 1.2 else if ((b.ordinal + 2) % 3 == a.ordinal) 0.85 else 1.0
    fun numbers(h: HeroState, academy: Int = 0, level: Int = h.level): HeroNumbers {
        val d = h.def
        val advancement = 1.0 + (level - 1) * 0.09
        val veterancy = 1.0 + h.stars.coerceIn(0, STAR_CAP) * STAR_BONUS
        val set = h.gear.minOrNull() ?: 0
        val setTier = h.gearTier.minOrNull() ?: 0
        val gearAttack = (h.gear[0] + h.gear[3]) * 5.0 + (h.gearTier[0] + h.gearTier[3]) * 12.0
        val gearHp = (h.gear[1] + h.gear[2]) * 30.0 + (h.gearTier[1] + h.gearTier[2]) * 60.0
        val mastery = 1.0 + set * 0.008 + setTier * 0.02
        val hp = (d.hp * advancement + gearHp) * mastery * veterancy * (1 + h.traits[1] * 0.025 + academy * 0.015 + (h.skills[2] - 1) * 0.025)
        val attack = (d.attack * advancement + gearAttack) * mastery * veterancy * (1 + h.traits[0] * 0.025)
        return HeroNumbers(hp, attack, d.armor + h.gear[1] * 2.0 + h.gear[2] + h.traits[2] * 2,
            (0.05 + h.stars * 0.006 + h.skills[1] * 0.004).coerceAtMost(0.35),
            1.0 + (h.skills[0] - 1) * 0.05 + (h.skills[3] - 1) * 0.03,
            (h.traits[2] * 0.008).coerceAtMost(0.25), (h.traits[3] * 0.012).coerceAtMost(0.3))
    }
}
