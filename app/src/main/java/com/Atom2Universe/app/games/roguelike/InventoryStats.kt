package com.Atom2Universe.app.games.roguelike

import com.Atom2Universe.app.R

/** Read-only equipment preview: use the combat formulas without touching the live hero. */
internal object InventoryStats {
    fun preview(hero: Hero, item: Equipment) = Hero().apply {
        floor = hero.floor
        deepestFloor = hero.deepestFloor
        equipped.putAll(hero.equipped)
        equipped[item.slot] = item
        hero.relicSlots.copyInto(relicSlots)
        hp = hero.hp.coerceAtMost(maxHp)
    }

    data class Value(val key: String, val label: Int, val value: Double,
        val unit: Unit = Unit.NUMBER, val lowerIsBetter: Boolean = false, val name: Int? = null)
    enum class Unit { NUMBER, PERCENT, MS, TURNS }

    fun values(hero: Hero): List<Value> = buildList {
        fun add(key: String, label: Int, value: Number, unit: Unit = Unit.NUMBER) {
            add(Value(key, label, value.toDouble(), unit))
        }
        add("hp", R.string.roguelike_stattype_maxhp, hero.maxHp)
        add("armor", R.string.roguelike_stattype_armor, hero.armor)
        add("mitigation", R.string.inv_reduction, (1 - hero.mitigate(1f, hero.floor)) * 100, Unit.PERCENT)
        add("min", R.string.inv_damage_min, hero.weaponMin)
        add("max", R.string.inv_damage_max, hero.weaponMax)
        add("crit", R.string.roguelike_stattype_crit_chance, hero.critChance(hero.floor) * 100, Unit.PERCENT)
        add("critMult", R.string.roguelike_stattype_crit_damage, hero.critMult * 100, Unit.PERCENT)
        add("dodge", R.string.inv_dodge, hero.dodgeChance(hero.floor) * 100, Unit.PERCENT)
        add("classPerk", R.string.roguelike_stattype_class_perk, hero.classPerkChance * 100, Unit.PERCENT)
        add("speed", R.string.roguelike_stattype_speed, hero.speed * 100, Unit.PERCENT)
        add("steal", R.string.roguelike_stattype_life_steal, hero.lifeSteal * 100, Unit.PERCENT)
        add("spellBonus", R.string.inv_spell_bonus,
            hero.equipped.values.sumOf { it.sum(StatType.SPELL_DMG).toDouble() } * 100, Unit.PERCENT)
        add("parry", R.string.inv_parry, hero.parryBonusMs, Unit.MS)
        add("gold", R.string.inv_gold_bonus, (hero.goldMult - 1) * 100, Unit.PERCENT)
        for (stat in StatType.ATTRIBUTES) add(stat.name, stat.labelRes, hero.attribute(stat))
        for (relic in hero.relicSlots.filterNotNull()) {
            if (relic.hits) {
                val (lo, hi) = hero.relicDamage(relic)
                add(Value("${relic.name}:min", R.string.inv_relic_min, lo.toDouble(), name = relic.labelRes))
                add(Value("${relic.name}:max", R.string.inv_relic_max, hi.toDouble(), name = relic.labelRes))
            }
            val amount = hero.relicAmount(relic)
            if (amount != 0) add(Value("${relic.name}:amount", R.string.inv_relic_amount, amount.toDouble(), name = relic.labelRes))
            add(Value("${relic.name}:cooldown", R.string.inv_relic_cooldown,
                hero.castCooldown(relic).toDouble(), Unit.TURNS, true, relic.labelRes))
        }
    }
}
