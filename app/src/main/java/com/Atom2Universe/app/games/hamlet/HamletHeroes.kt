package com.Atom2Universe.app.games.hamlet

enum class HeroFaction { GROVE, DAWN, FORGE }
enum class HeroRole { GUARDIAN, RANGER, ROGUE, HEALER, BARD, MAGE, DUELIST, SUMMONER, ENGINEER }
enum class HeroRarity { COMMON, RARE, EPIC, LEGENDARY, MYTHIC }
enum class HeroAbility { NONE, WARD, PURIFY, FROST, EXPOSE, REJUVENATE, EXECUTION }

data class HeroDef(val id: Int, val faction: HeroFaction, val role: HeroRole, val rarity: HeroRarity,
    val hp: Int, val attack: Int, val armor: Int, val interval: Float, val skill: Float, val ultimate: Float,
    val ability: HeroAbility = HeroAbility.NONE)

class HeroState(val id: Int) {
    var owned = false
    var level = 1
    var stars = 0
    var shards = 0L
    val skills = intArrayOf(1, 1, 1, 1)
    val traits = IntArray(4)
    val def get() = HamletHeroes.defs[id]
}

data class HeroNumbers(val hp: Double, val attack: Double, val armor: Double, val crit: Double,
    val skillBoost: Double, val reduction: Double, val haste: Double)

object HamletHeroes {
    const val STAR_CAP = 10
    const val STAR_BONUS = 0.05
    /** Les trois premiers appels sont écrits d'avance : un duelliste, une soigneuse, une archère (tous communs). */
    val OPENING = intArrayOf(7, 3, 0)
    /** Les héros qui ne sortent jamais d'un tirage : ils viennent des veillées. */
    val LEGENDS = intArrayOf(8, 30, 35)
    const val MYTHIC = 10
    /** Niveau maximal d'un héros selon ses étoiles : franchir un cap demande un fragment de plus. */
    fun levelCap(stars: Int) = 10 + 8 * stars.coerceIn(0, STAR_CAP)
    /** Les étoiles que la halle permet d'atteindre : la puissance des héros suit le développement du village. */
    fun starLimit(hall: Int) = intArrayOf(2, 3, 4, 5, 6, 7, 8, 10)[(hall - 1).coerceIn(0, 7)]
    /** Fragments gagnés quand un tirage retombe sur un héros déjà connu. */
    fun duplicateShards(rarity: HeroRarity) = when (rarity) { HeroRarity.COMMON -> 1L; HeroRarity.RARE -> 2L; else -> 4L }
    val defs = listOf(
        HeroDef(0, HeroFaction.GROVE, HeroRole.RANGER, HeroRarity.COMMON, 420, 70, 10, 1.25f, 7f, 18f),
        HeroDef(1, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.COMMON, 900, 40, 42, 1.6f, 8f, 20f),
        HeroDef(2, HeroFaction.GROVE, HeroRole.ROGUE, HeroRarity.COMMON, 470, 90, 12, 1.05f, 6f, 18f),
        HeroDef(3, HeroFaction.GROVE, HeroRole.HEALER, HeroRarity.COMMON, 490, 43, 15, 1.7f, 7f, 20f),
        HeroDef(4, HeroFaction.DAWN, HeroRole.BARD, HeroRarity.COMMON, 570, 52, 20, 1.5f, 8f, 21f),
        HeroDef(5, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.EPIC, 500, 94, 12, 1.6f, 8f, 22f),
        HeroDef(6, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.RARE, 960, 48, 46, 1.5f, 9f, 21f),
        HeroDef(7, HeroFaction.FORGE, HeroRole.DUELIST, HeroRarity.COMMON, 580, 74, 22, 1.2f, 7f, 19f),
        HeroDef(8, HeroFaction.DAWN, HeroRole.HEALER, HeroRarity.LEGENDARY, 650, 55, 24, 1.5f, 8f, 20f),
        HeroDef(9, HeroFaction.GROVE, HeroRole.SUMMONER, HeroRarity.EPIC, 590, 68, 19, 1.5f, 9f, 22f),
        HeroDef(10, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.MYTHIC, 570, 96, 20, 1.6f, 9f, 24f),
        HeroDef(11, HeroFaction.FORGE, HeroRole.ENGINEER, HeroRarity.EPIC, 620, 85, 26, 1.25f, 8f, 22f),
                HeroDef(12, HeroFaction.GROVE, HeroRole.GUARDIAN, HeroRarity.COMMON, 820, 42, 39, 1.6f, 8f, 20f, HeroAbility.REJUVENATE),
        HeroDef(13, HeroFaction.DAWN, HeroRole.RANGER, HeroRarity.COMMON, 430, 68, 13, 1.25f, 7f, 18f, HeroAbility.EXPOSE),
        HeroDef(14, HeroFaction.FORGE, HeroRole.HEALER, HeroRarity.COMMON, 540, 40, 21, 1.7f, 7f, 20f, HeroAbility.PURIFY),
        HeroDef(15, HeroFaction.GROVE, HeroRole.BARD, HeroRarity.COMMON, 550, 51, 18, 1.5f, 8f, 21f, HeroAbility.REJUVENATE),
        HeroDef(16, HeroFaction.DAWN, HeroRole.GUARDIAN, HeroRarity.RARE, 920, 46, 44, 1.6f, 8f, 21f, HeroAbility.WARD),
        HeroDef(17, HeroFaction.FORGE, HeroRole.RANGER, HeroRarity.COMMON, 480, 77, 19, 1.25f, 7f, 19f, HeroAbility.EXECUTION),
        HeroDef(18, HeroFaction.GROVE, HeroRole.MAGE, HeroRarity.RARE, 510, 87, 17, 1.6f, 8f, 22f, HeroAbility.FROST),
        HeroDef(19, HeroFaction.DAWN, HeroRole.ROGUE, HeroRarity.COMMON, 480, 84, 15, 1.1f, 6f, 18f, HeroAbility.EXPOSE),
        HeroDef(20, HeroFaction.FORGE, HeroRole.BARD, HeroRarity.RARE, 610, 50, 24, 1.5f, 8f, 21f, HeroAbility.WARD),
        HeroDef(21, HeroFaction.GROVE, HeroRole.DUELIST, HeroRarity.RARE, 610, 76, 24, 1.2f, 7f, 19f, HeroAbility.REJUVENATE),
        HeroDef(22, HeroFaction.DAWN, HeroRole.SUMMONER, HeroRarity.RARE, 580, 67, 20, 1.5f, 9f, 22f, HeroAbility.WARD),
        HeroDef(23, HeroFaction.FORGE, HeroRole.ROGUE, HeroRarity.RARE, 520, 88, 23, 1.1f, 6f, 19f, HeroAbility.EXECUTION),
        HeroDef(24, HeroFaction.GROVE, HeroRole.ENGINEER, HeroRarity.RARE, 600, 78, 21, 1.3f, 8f, 22f, HeroAbility.EXPOSE),
        HeroDef(25, HeroFaction.DAWN, HeroRole.DUELIST, HeroRarity.RARE, 680, 79, 26, 1.25f, 7f, 20f, HeroAbility.PURIFY),
        HeroDef(26, HeroFaction.FORGE, HeroRole.MAGE, HeroRarity.RARE, 560, 88, 21, 1.6f, 8f, 22f, HeroAbility.EXPOSE),
        HeroDef(27, HeroFaction.GROVE, HeroRole.HEALER, HeroRarity.RARE, 620, 54, 20, 1.6f, 8f, 21f, HeroAbility.PURIFY),
        HeroDef(28, HeroFaction.DAWN, HeroRole.ENGINEER, HeroRarity.RARE, 610, 75, 23, 1.3f, 8f, 22f, HeroAbility.FROST),
        HeroDef(29, HeroFaction.FORGE, HeroRole.SUMMONER, HeroRarity.EPIC, 660, 64, 26, 1.6f, 9f, 23f, HeroAbility.WARD),
        HeroDef(30, HeroFaction.GROVE, HeroRole.RANGER, HeroRarity.LEGENDARY, 580, 88, 21, 1.2f, 7f, 21f, HeroAbility.EXECUTION),
        HeroDef(31, HeroFaction.DAWN, HeroRole.BARD, HeroRarity.EPIC, 690, 58, 23, 1.5f, 8f, 22f, HeroAbility.PURIFY),
        HeroDef(32, HeroFaction.FORGE, HeroRole.GUARDIAN, HeroRarity.EPIC, 1050, 49, 48, 1.6f, 9f, 23f, HeroAbility.REJUVENATE),
        HeroDef(33, HeroFaction.GROVE, HeroRole.SUMMONER, HeroRarity.EPIC, 630, 71, 22, 1.5f, 9f, 23f, HeroAbility.REJUVENATE),
        HeroDef(34, HeroFaction.DAWN, HeroRole.MAGE, HeroRarity.EPIC, 580, 95, 21, 1.6f, 9f, 24f, HeroAbility.FROST),
        HeroDef(35, HeroFaction.FORGE, HeroRole.DUELIST, HeroRarity.LEGENDARY, 730, 86, 29, 1.25f, 7f, 21f, HeroAbility.EXECUTION),
    )
    val bonds = listOf(intArrayOf(0, 2, 3), intArrayOf(1, 6, 7), intArrayOf(4, 5, 8), intArrayOf(2, 7, 11))
    /** Le contre augmente les dégâts de 20 % ; le triangle laisse une réponse à toute faction. */
    fun counter(a: HeroFaction, b: HeroFaction) = if ((a.ordinal + 2) % 3 == b.ordinal) 1.2 else if ((b.ordinal + 2) % 3 == a.ordinal) 0.85 else 1.0
    /** Les forces d'un héros : niveau, étoiles, compétences, traits ; l'équipement et le reste s'ajoutent par l'aventure. */
    fun numbers(h: HeroState, academy: Int = 0, level: Int = h.level): HeroNumbers {
        val d = h.def
        val advancement = 1.0 + (level - 1) * 0.09
        val veterancy = 1.0 + h.stars.coerceIn(0, STAR_CAP) * STAR_BONUS
        val hp = d.hp * advancement * veterancy * (1 + h.traits[1] * 0.025 + academy * 0.015 + (h.skills[2] - 1) * 0.025)
        val attack = d.attack * advancement * veterancy * (1 + h.traits[0] * 0.025)
        return HeroNumbers(hp, attack, d.armor + h.traits[2] * 2.0,
            (0.05 + h.stars * 0.006 + h.skills[1] * 0.004).coerceAtMost(0.35),
            1.0 + (h.skills[0] - 1) * 0.05 + (h.skills[3] - 1) * 0.03,
            (h.traits[2] * 0.008).coerceAtMost(0.25), (h.traits[3] * 0.012).coerceAtMost(0.3))
    }
}
