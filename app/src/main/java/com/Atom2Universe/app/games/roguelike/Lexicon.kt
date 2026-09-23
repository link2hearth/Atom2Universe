package com.Atom2Universe.app.games.roguelike

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.periodic.getPeriodicElements
import com.Atom2Universe.app.periodic.localizedName
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Le lexique du Donjon : les règles ne s'écrivent plus à l'écran, elles vivent ici. Chaque mot
 * ou chaque stat qui compte a une **fiche** courte (une phrase, des chiffres, la ligne « Toi »),
 * qu'un lien ([LexiconText]) ouvre, et que l'écran Lexique ([LexiconPanel]) permet de parcourir.
 *
 * **Aucun nombre n'est écrit dans les textes** : chaque chiffre d'une fiche est lu dans les
 * constantes de [Hero], [Combat], [Relic], [Reaction], [AffixBudget]… et passé en argument. Si un
 * réglage change, la fiche suit toute seule. `LexiconTest` vérifie que chaque stat, élément,
 * relique, réaction, résonance, poids, archétype, base, rareté et monstre a bien sa fiche.
 *
 * Ce que le jeu ne dit pas (réactions, résonances, affinités des monstres, reliques pas encore
 * trouvées) reste **caché** tant que le joueur ne l'a pas vu : voir [LexiconEntry.secret].
 */
enum class LexiconCategory(@StringRes val labelRes: Int) {
    ATTRIBUTES(R.string.lex_cat_attributes), STATS(R.string.lex_cat_stats), COMBAT(R.string.lex_cat_combat),
    STATES(R.string.lex_cat_states), ELEMENTS(R.string.lex_cat_elements), REACTIONS(R.string.lex_cat_reactions),
    RELICS(R.string.lex_cat_relics), RESONANCES(R.string.lex_cat_resonances), ARCHETYPES(R.string.lex_cat_archetypes),
    ITEMS(R.string.lex_cat_items), MATERIALS(R.string.lex_cat_materials), MONSTERS(R.string.lex_cat_monsters),
    DEPTH(R.string.lex_cat_depth),
}

/** Une ligne de fiche : [personal] pour la ligne « Toi : … », lue sur le héros. */
class LexLine(val text: String, val personal: Boolean = false)

/** Ce qu'une fiche a besoin de savoir pour se calculer : la langue, le héros (s'il y en a un) et son étage. */
class LexiconEnv(val ctx: Context, val hero: Hero?, val floor: Int) {
    fun s(@StringRes id: Int, vararg args: Any): String = ctx.getString(id, *args)
    fun link(id: String, text: String) = LexiconText.link(id, text)
    fun num(v: Int) = DungeonNumbers.format(ctx, v)
    fun pct(f: Float) = Math.round(f * 100)
    /** Un nombre à virgule, au format de la langue : « 0,5 » en français, « 0.5 » en anglais. */
    fun dec(v: Number, max: Int = 1, min: Int = 0): String =
        NumberFormat.getNumberInstance(ctx.resources.configuration.locales[0]).apply {
            maximumFractionDigits = max; minimumFractionDigits = min; isGroupingUsed = false
        }.format(v.toDouble())
    fun signed(n: Int) = if (n > 0) "+$n" else if (n < 0) "−${-n}" else "0"
}

/**
 * Une entrée. [secret] : elle n'apparaît qu'une fois [known] (sinon le lexique révélerait ce que
 * le jeu veut faire découvrir). [lines] : la première ligne est la phrase, les autres les chiffres.
 */
class LexiconEntry(
    val id: String,
    val category: LexiconCategory,
    val secret: Boolean,
    val title: (LexiconEnv) -> String,
    val known: (Hero?) -> Boolean,
    val lines: (LexiconEnv) -> List<LexLine>,
) {
    fun visible(hero: Hero?) = !secret || known(hero)
}

object Lexicon {

    // ── Les identifiants : ceux que les liens `[[id|texte]]` visent ──────────────

    fun idOf(t: StatType) = (if (t in StatType.ATTRIBUTES) "attr_" else "stat_") + t.name.lowercase()
    fun idOf(e: Element) = "element_" + e.name.lowercase()
    fun idOf(r: Relic) = "relic_" + r.name.lowercase()
    fun idOf(r: Reaction) = "reaction_" + r.name.lowercase()
    fun idOf(r: Resonance) = "resonance_" + r.name.lowercase()
    fun idOf(w: ArmorWeight) = "weight_" + w.name.lowercase()
    fun idOf(a: Archetype) = "archetype_" + a.name.lowercase()
    fun idOf(b: ItemBase) = "base_" + b.name.lowercase()
    fun idOf(r: Rarity) = "rarity_" + r.name.lowercase()
    fun idOf(m: MonsterType) = "monster_" + m.name.lowercase()
    fun materialId(element: Int) = "material_$element"
    fun tierId(type: StatType, tier: Int) = "tier:${type.name}:$tier"
    /** Le Spécial d'un archétype : Garde, Coup mortel, Image miroir. */
    fun specialId(a: Archetype) = when (a) {
        Archetype.WARRIOR -> "special_guard"; Archetype.ROGUE -> "special_deadly"; Archetype.MAGE -> "special_mirror"
        Archetype.BARBARIAN -> "special_smash"
        Archetype.VAGABOND -> "special_combo"; Archetype.NECROMANCER -> "special_puppets"
    }

    @StringRes fun elementRes(e: Element) = when (e) {
        Element.FIRE -> R.string.lex_element_fire; Element.ICE -> R.string.lex_element_ice
        Element.LIGHTNING -> R.string.lex_element_lightning; Element.POISON -> R.string.lex_element_poison
        Element.HOLY -> R.string.lex_element_holy; Element.PHYSICAL -> R.string.lex_element_physical
    }

    @StringRes private fun attrNameRes(t: StatType) = when (t) {
        StatType.STR -> R.string.lex_attr_name_str; StatType.DEX -> R.string.lex_attr_name_dex
        StatType.CON -> R.string.lex_attr_name_con; StatType.INT -> R.string.lex_attr_name_int
        StatType.END -> R.string.lex_attr_name_end
        StatType.WIS -> R.string.lex_attr_name_wis; else -> R.string.lex_attr_name_cha
    }

    @StringRes private fun targetRes(t: RelicTarget) = when (t) {
        RelicTarget.ONE -> R.string.lex_target_one; RelicTarget.ALL -> R.string.lex_target_all
        RelicTarget.CHAIN -> R.string.lex_target_chain; RelicTarget.MISSILES -> R.string.lex_target_missiles
        RelicTarget.SELF -> R.string.lex_target_self
    }

    // ── Le calcul des fiches ────────────────────────────────────────────────────

    private class Sheet(val env: LexiconEnv) {
        val lines = mutableListOf<LexLine>()
        fun add(@StringRes r: Int, vararg a: Any) { lines += LexLine(env.s(r, *a)) }
        fun text(t: String) { lines += LexLine(t) }
        fun you(@StringRes r: Int, vararg a: Any) { lines += LexLine(env.s(r, *a), personal = true) }
        /**
         * Au-delà de l'étage 100, un point de caractéristique vaut moins : on le dit ici, à côté de la
         * ligne « Toi », avec un lien vers la fiche de la résistance en profondeur. Rien avant.
         */
        fun youDepth() {
            val w = LootSystem.attributeWeightAt(env.floor)
            if (env.hero != null && w < 1f) you(R.string.lex_you_depth_point, env.floor, env.dec(w * 100f, 1))
        }
    }

    private fun entry(
        id: String, cat: LexiconCategory, @StringRes titleRes: Int,
        secret: Boolean = false, known: (Hero?) -> Boolean = { true }, body: Sheet.() -> Unit,
    ) = entryT(id, cat, { it.s(titleRes) }, secret, known, body)

    private fun entryT(
        id: String, cat: LexiconCategory, title: (LexiconEnv) -> String,
        secret: Boolean = false, known: (Hero?) -> Boolean = { true }, body: Sheet.() -> Unit,
    ) = LexiconEntry(id, cat, secret, title, known) { env -> Sheet(env).apply(body).lines }

    /** Une puissance d'objet, en étage où elle apparaît (la puissance typique d'un étage est 1 + 0,4 × (étage − 1)). */
    private fun floorOfPower(power: Int): Int {
        val step = LootSystem.powerCenter(2) - LootSystem.powerCenter(1)
        return ((power - 1) / step).roundToInt() + 1
    }

    /** Des points de CA en pourcentage d'esquive. */
    private fun dodgePct(ac: Int) = Math.round(ac * ArmorClass.AC_STEP * 100)

    private val periodic by lazy { getPeriodicElements() }

    val categories: List<LexiconCategory> get() = LexiconCategory.entries

    // ── Le catalogue ────────────────────────────────────────────────────────────

    val entries: List<LexiconEntry> by lazy {
        buildList {
            addAll(attributes()); addAll(stats()); addAll(combat()); addAll(states()); addAll(elements())
            addAll(reactions()); addAll(relics()); addAll(resonances()); addAll(archetypes()); addAll(items())
            addAll(materials()); addAll(monsters()); addAll(depth())
        }
    }

    private val byId by lazy { entries.associateBy { it.id } }

    /** L'entrée d'un lien, ou null. Les paliers (`tier:STR:4`) se fabriquent à la demande. */
    fun find(id: String): LexiconEntry? = byId[id] ?: tierEntry(id)

    fun visibleIn(category: LexiconCategory, hero: Hero?) = entries.filter { it.category == category && it.visible(hero) }

    // ── Caractéristiques ────────────────────────────────────────────────────────

    private fun attributes() = StatType.ATTRIBUTES.map { t ->
        entryT(idOf(t), LexiconCategory.ATTRIBUTES,
            { env -> env.s(R.string.lex_title_attr, env.s(t.labelRes), env.s(attrNameRes(t))) }) {
            when (t) {
                StatType.STR -> { add(R.string.lex_attr_str_1); add(R.string.lex_attr_str_2, env.pct(Hero.WEAPON_ATTRIBUTE_DAMAGE_PER_POINT)) }
                StatType.DEX -> {
                    add(R.string.lex_attr_dex_1)
                    add(R.string.lex_attr_dex_2, env.pct(Hero.CRIT_PER_DEX))
                    add(R.string.lex_attr_dex_4, env.pct(ArmorClass.AC_STEP))
                }
                StatType.END -> { add(R.string.lex_attr_end_1); add(R.string.lex_attr_end_2) }
                StatType.CON -> { add(R.string.lex_attr_con_1); add(R.string.lex_attr_con_2, env.num(Hero.HP_PER_CON)) }
                StatType.INT -> add(R.string.lex_attr_int_1)
                StatType.WIS -> add(R.string.lex_attr_wis_1)
                else -> { add(R.string.lex_attr_cha_1); add(R.string.lex_attr_cha_2, env.pct(Hero.GOLD_PER_CHA)) }
            }
            // Chaque relique suit sa caractéristique : un point de plus au DD, c'est un cran de contrôle en plus
            val step = SpellSave.landChance(SpellSave.DC_BASE + 1, 0) - SpellSave.landChance(SpellSave.DC_BASE, 0)
            add(R.string.lex_attr_relics, env.pct(Hero.RELIC_DAMAGE_PER_POINT), env.pct(step))
            add(R.string.lex_attr_base, Hero.BASE_ATTRIBUTE)
            env.hero?.let { you(R.string.lex_you_attr, it.attribute(t)) }
            // Les PV de la CON sont des PV comme les autres : la profondeur ne les touche pas
            if (t != StatType.CON) youDepth()
        }
    }

    // ── Stats ───────────────────────────────────────────────────────────────────

    private fun stats(): List<LexiconEntry> {
        val c = LexiconCategory.STATS
        return listOf(
            entry(idOf(StatType.MAX_HP), c, R.string.lex_stat_max_hp) {
                add(R.string.lex_stat_max_hp_1)
                add(R.string.lex_stat_max_hp_2, Hero.BASE_HP, Hero.HP_PER_CON)
                add(R.string.lex_stat_hp_curve)
                add(R.string.lex_stat_max_hp_3)
                env.hero?.let { you(R.string.lex_you_hp, env.num(it.hp), env.num(it.maxHp)) }
            },
            entry(idOf(StatType.ARMOR), c, R.string.lex_stat_armor) {
                add(R.string.lex_stat_armor_1); add(R.string.lex_stat_armor_2)
                add(R.string.lex_stat_armor_3, env.dec(ArmorWeight.ULTRALIGHT.armorMult), env.dec(ArmorWeight.CLOTH.armorMult), env.dec(ArmorWeight.LIGHT.armorMult),
                    env.dec(ArmorWeight.MEDIUM.armorMult), env.dec(ArmorWeight.HEAVY.armorMult))
                env.hero?.let { you(R.string.lex_you_armor, env.num(it.armor), 100 - Math.round(it.mitigate(100f, env.floor)), env.floor) }
            },
            entry("stat_dodge", c, R.string.lex_stat_dodge) {
                add(R.string.lex_stat_dodge_1)
                add(R.string.lex_stat_dodge_2, dodgePct(ArmorWeight.LIGHT.acPerPiece), dodgePct(ArmorClass.SHIELD))
                add(R.string.lex_stat_dodge_3, env.pct(ArmorClass.AC_STEP))
                add(R.string.lex_stat_dodge_4, env.pct(1f - ArmorClass.hitChance(-999, 0)), env.pct(1f - ArmorClass.hitChance(999, 0)))
                env.hero?.let { you(R.string.lex_you_dodge, env.pct(it.dodgeChance(env.floor)), env.floor) }
            },
            entry(idOf(StatType.CRIT_CHANCE), c, R.string.lex_stat_crit_chance) {
                add(R.string.lex_stat_crit_chance_1)
                add(R.string.lex_stat_crit_chance_2, env.pct(Hero.BASE_CRIT), env.pct(Hero.CRIT_PER_DEX))
                add(R.string.lex_stat_crit_chance_3, env.pct(Hero.MAX_CRIT))
                add(R.string.lex_stat_crit_chance_4)
                env.hero?.let { you(R.string.lex_you_crit, env.pct(it.critChance(env.floor)), env.floor) }
            },
            entry(idOf(StatType.CRIT_DAMAGE), c, R.string.lex_stat_crit_damage) {
                add(R.string.lex_stat_crit_damage_1)
                add(R.string.lex_stat_crit_damage_2, env.dec(Hero.BASE_CRIT_MULT))
                env.hero?.let { you(R.string.lex_you_crit_mult, env.dec(it.critMult, 2)) }
            },
            entry(idOf(StatType.SPEED), c, R.string.lex_stat_speed) {
                add(R.string.lex_stat_speed_1)
                add(R.string.lex_stat_speed_2, 100, env.pct(Hero.MIN_SPEED))
                add(R.string.lex_stat_speed_3, env.signed(Math.round(ArmorWeight.HEAVY.speedPerPiece * 100)), env.signed(Math.round(ArmorWeight.LIGHT.speedPerPiece * 100)))
                env.hero?.let { you(R.string.lex_you_speed, env.pct(it.speed)) }
            },
            entry(idOf(StatType.LIFE_STEAL), c, R.string.lex_stat_life_steal) {
                add(R.string.lex_stat_life_steal_1)
                env.hero?.let { you(R.string.lex_you_life_steal, env.dec(it.lifeSteal * 100f)) }
            },
            entry(idOf(StatType.WEAPON_DMG), c, R.string.lex_stat_weapon_dmg) {
                add(R.string.lex_stat_weapon_dmg_1)
                add(R.string.lex_stat_weapon_dmg_2, env.pct(Hero.WEAPON_ATTRIBUTE_DAMAGE_PER_POINT))
                add(R.string.lex_stat_weapon_dmg_3, Hero.FIST_MIN, Hero.FIST_MAX)
                env.hero?.let { you(R.string.lex_you_weapon, env.num(it.weaponMin), env.num(it.weaponMax)) }
                youDepth()
            },
            entry(idOf(StatType.SPELL_DMG), c, R.string.lex_stat_spell_dmg) {
                add(R.string.lex_stat_spell_dmg_1)
                add(R.string.lex_stat_spell_dmg_2,
                    env.s(ItemBase.STAFF.nounRes), env.pct(ItemBase.STAFF.spellBonus),
                    env.s(ItemBase.SCEPTER.nounRes), env.pct(ItemBase.SCEPTER.spellBonus),
                    env.s(ItemBase.ORB.nounRes), env.pct(ItemBase.ORB.spellBonus))
            },
            entry("stat_rating", c, R.string.lex_stat_rating) {
                add(R.string.lex_stat_rating_1)
                add(R.string.lex_stat_rating_2, env.pct(AffixBudget.SHARE))
                add(R.string.lex_stat_rating_3)
                add(R.string.lex_stat_rating_4)
            },
            entry("stat_power", c, R.string.lex_stat_power) {
                add(R.string.lex_stat_power_1)
                add(R.string.lex_stat_power_2, Math.round((LootSystem.scale(2) - LootSystem.scale(1)) * 100))
                add(R.string.lex_stat_power_3, env.dec(LootSystem.powerCenter(2) - LootSystem.powerCenter(1)))
                add(R.string.lex_stat_power_4)
            },
        )
    }

    // ── Combat ──────────────────────────────────────────────────────────────────

    private fun combat(): List<LexiconEntry> {
        val c = LexiconCategory.COMBAT
        val dc = SpellSave.DC_BASE
        return listOf(
            entry("gauge", c, R.string.lex_gauge) {
                add(R.string.lex_gauge_1); add(R.string.lex_gauge_2); add(R.string.lex_gauge_3); add(R.string.lex_gauge_4)
                add(R.string.lex_gauge_5, env.pct(Combat.SUPPORT_ACTION.toFloat()))
            },
            entry("cadence", c, R.string.lex_cadence) {
                add(R.string.lex_cadence_1, env.s(MonsterType.RAT.labelRes), MonsterType.RAT.cadence, env.s(MonsterType.VAMPIRE.labelRes), MonsterType.VAMPIRE.cadence)
                add(R.string.lex_cadence_2)
                add(R.string.lex_cadence_3, env.dec(Encounters.SPEED_PER_FLOOR * 100), Encounters.DEEP_FLOOR)
            },
            entry("cooldown", c, R.string.lex_cooldown) {
                add(R.string.lex_cooldown_1)
                add(R.string.lex_cooldown_2)
                add(R.string.lex_cooldown_3, Hero.SPECIAL_COOLDOWN)
            },
            entry("parry", c, R.string.lex_parry) {
                add(R.string.lex_parry_1)
                add(R.string.lex_parry_2, env.dec(Combat.PARRY_GOOD_MULT), env.dec(Combat.PARRY_PERFECT_MULT), env.dec(Combat.PARRY_MISS_MULT))
                add(R.string.lex_parry_defense, env.pct(ArmorClass.timingBonus(Timing.GOOD) * ArmorClass.AC_STEP),
                    env.pct(ArmorClass.timingBonus(Timing.PERFECT) * ArmorClass.AC_STEP), env.pct(1f - ArmorClass.MIN_HIT))
                add(R.string.lex_parry_3)
                add(R.string.lex_parry_4)
            },
            entry("strike", c, R.string.lex_strike) {
                add(R.string.lex_strike_1)
                add(R.string.lex_strike_2)
                add(R.string.lex_parry_3)
                add(R.string.lex_strike_miss)
                add(R.string.lex_strike_3, env.pct(SpellSave.landChance(dc + SpellSave.GOOD_STRIKE_DC, 0) - SpellSave.landChance(dc, 0)))
            },
            entry("ambush", c, R.string.lex_ambush) { add(R.string.lex_ambush_1); add(R.string.lex_ambush_2); add(R.string.lex_ambush_3) },
            entry("rage", c, R.string.lex_rage) {
                add(R.string.lex_rage_1, Relic.RAGE_AFTER, Relic.RAGE_TURNS)
                add(R.string.lex_rage_2, env.dec(Relic.RAGE_SPEED))
            },
            entry("resist", c, R.string.lex_resist) {
                add(R.string.lex_resist_1)
                add(R.string.lex_resist_2, env.pct(SpellSave.landChance(dc, 0)),
                    env.pct(SpellSave.landChance(dc, Affinity.VULNERABLE.saveBonus)), env.pct(SpellSave.landChance(dc, Affinity.RESISTANT.saveBonus)))
                add(R.string.lex_resist_3); add(R.string.lex_resist_4)
            },
            entry("special", c, R.string.lex_special) { add(R.string.lex_special_1); add(R.string.lex_special_2, Hero.SPECIAL_COOLDOWN) },
            entry("rest", c, R.string.lex_rest) {
                add(R.string.lex_rest_1); add(R.string.lex_rest_2)
                add(R.string.lex_rest_3); add(R.string.lex_rest_4)
            },
            entry("chase", c, R.string.lex_chase) {
                add(R.string.lex_chase_1); add(R.string.lex_chase_2); add(R.string.lex_chase_3, RoguelikeGame.CHAIN_DISTANCE)
            },
            entry("death", c, R.string.lex_death) {
                add(R.string.lex_death_1, RoguelikeGame.CHECKPOINT_INTERVAL); add(R.string.lex_death_2, env.pct(RoguelikeGame.DEATH_GOLD_LOSS))
            },
        )
    }

    // ── États ───────────────────────────────────────────────────────────────────

    private fun states(): List<LexiconEntry> {
        val c = LexiconCategory.STATES
        val dc = SpellSave.DC_BASE
        return listOf(
            entry("state_burn", c, R.string.lex_state_burn) {
                add(R.string.lex_state_burn_1); add(R.string.lex_state_burn_2, env.pct(Relic.BURN_SHARE))
            },
            entry("state_poison", c, R.string.lex_state_poison) {
                add(R.string.lex_state_poison_1); add(R.string.lex_state_poison_2, Relic.POISON_MAX_TURNS)
            },
            entry("state_frozen", c, R.string.lex_state_frozen) {
                add(R.string.lex_state_frozen_1, env.dec(Relic.CHILL_SPEED, 2), env.dec(Relic.FREEZE_TURN_LENGTH), env.dec(Combat.NUMB_MULT))
                add(R.string.lex_state_frozen_2)
            },
            entry("state_paralyzed", c, R.string.lex_state_paralyzed) { add(R.string.lex_state_paralyzed_1) },
            entry("state_exposed", c, R.string.lex_state_exposed) { add(R.string.lex_state_exposed_1) },
            entry("state_fractured", c, R.string.lex_state_fractured) { add(R.string.lex_state_fractured_1, env.pct(Combat.FRACTURE_MULT - 1f)) },
            entry("state_weakened", c, R.string.lex_state_weakened) { add(R.string.lex_state_weakened_1, env.pct(1f - Combat.WEAKEN_MULT)) },
            entry("state_bleeding", c, R.string.lex_state_bleeding) { add(R.string.lex_state_bleeding_1); add(R.string.lex_state_bleeding_2) },
            entry("state_marked", c, R.string.lex_state_marked) {
                add(R.string.lex_state_marked_1, env.dec(Combat.MARK_CRIT_BONUS)); add(R.string.lex_state_marked_2); add(R.string.lex_state_marked_3)
            },
            entry("state_blinded", c, R.string.lex_state_blinded) { add(R.string.lex_state_blinded_1) },
            entry("state_charmed", c, R.string.lex_state_charmed) { add(R.string.lex_state_charmed_1) },
            entry("state_slowed", c, R.string.lex_state_slowed) { add(R.string.lex_state_slowed_1, env.dec(Relic.SLOW_SPEED)) },
            entry("state_fragile", c, R.string.lex_state_fragile) { add(R.string.lex_state_fragile_1, env.dec(Reaction.FRAGILE_MULT)) },
            entry("state_element_mark", c, R.string.lex_state_element_mark) {
                add(R.string.lex_state_element_mark_1, env.dec(Combat.ELEMENT_MARK_TIME)); add(R.string.lex_state_element_mark_2)
            },
            entry("state_barrier", c, R.string.lex_state_barrier) { add(R.string.lex_state_barrier_1) },
            entry("state_thorns", c, R.string.lex_state_thorns) {
                add(R.string.lex_state_thorns_1)
                add(R.string.lex_state_thorns_2, env.pct(Relic.THORNS_SHARE), env.pct(Combat.GUARD_THORNS_SHARE), env.pct(Combat.BLOCK_THORNS_SHARE))
            },
            entry("state_stoneskin", c, R.string.lex_state_stoneskin) {
                add(R.string.lex_state_stoneskin_1, env.dec(Relic.STONESKIN_ARMOR), env.pct(Relic.THORNS_SHARE))
            },
            entry("state_haste", c, R.string.lex_state_haste) { add(R.string.lex_state_haste_1, env.dec(Relic.HASTE_SPEED)) },
            entry("state_hourglass", c, R.string.lex_state_hourglass) { add(R.string.lex_state_hourglass_1, env.dec(Relic.HOURGLASS_SLOW)) },
            entry("state_empowered", c, R.string.lex_state_empowered) { add(R.string.lex_state_empowered_1, Relic.WARCRY_ATTACKS) },
            entry("state_poisoned_blades", c, R.string.lex_state_poisoned_blades) { add(R.string.lex_state_poisoned_blades_1) },
            entry("state_ambush", c, R.string.lex_state_ambush) { add(R.string.lex_state_ambush_1) },
            entry("state_meteor", c, R.string.lex_state_meteor) { add(R.string.lex_state_meteor_1, Relic.METEOR.effectTurns) },
        )
    }

    // ── Éléments, affinités ─────────────────────────────────────────────────────

    private fun elements(): List<LexiconEntry> {
        val c = LexiconCategory.ELEMENTS
        val dc = SpellSave.DC_BASE
        val withAffinity = setOf(Element.FIRE, Element.ICE, Element.LIGHTNING, Element.POISON, Element.HOLY)
        val one = mapOf(
            Element.FIRE to R.string.lex_element_fire_1, Element.ICE to R.string.lex_element_ice_1,
            Element.LIGHTNING to R.string.lex_element_lightning_1, Element.POISON to R.string.lex_element_poison_1,
            Element.HOLY to R.string.lex_element_holy_1, Element.PHYSICAL to R.string.lex_element_physical_1,
        )
        return Element.entries.map { e ->
            entry(idOf(e), c, elementRes(e)) {
                add(one.getValue(e))
                if (e in withAffinity) add(R.string.lex_element_affinity)
            }
        } + entry("affinities", c, R.string.lex_affinities) {
            add(R.string.lex_affinities_1)
            add(R.string.lex_affinities_2, env.dec(Affinity.VULNERABLE.damageMult),
                env.pct(SpellSave.landChance(dc, Affinity.VULNERABLE.saveBonus) - SpellSave.landChance(dc, 0)))
            add(R.string.lex_affinities_3, env.dec(Affinity.RESISTANT.damageMult),
                env.pct(SpellSave.landChance(dc, 0) - SpellSave.landChance(dc, Affinity.RESISTANT.saveBonus)))
            add(R.string.lex_affinities_4); add(R.string.lex_affinities_5)
        }
    }

    // ── Réactions (cachées jusqu'à ce qu'on les voie) ───────────────────────────

    private fun reactions(): List<LexiconEntry> {
        val c = LexiconCategory.REACTIONS
        val intro = entry("reactions", c, R.string.lex_reactions) { add(R.string.lex_reactions_1); add(R.string.lex_reactions_2); add(R.string.lex_reactions_3, env.pct(Reaction.BASE_ATTACK_PART)) }
        return listOf(intro) + Reaction.entries.map { r ->
            entryT(idOf(r), c, { env -> env.s(r.labelRes).trimEnd('!', ' ', ' ') },
                secret = true, known = { h -> h != null && r in h.knownReactions }) {
                when (r) {
                    Reaction.THERMAL_SHOCK -> add(R.string.lex_reaction_thermal_shock_1, env.dec(Reaction.THERMAL_MULT))
                    Reaction.VAPOR -> add(R.string.lex_reaction_vapor_1, env.dec(Reaction.VAPOR_MULT), Reaction.VAPOR_BLIND_TURNS)
                    Reaction.EXPLOSION -> add(R.string.lex_reaction_explosion_1)
                    Reaction.PLASMA -> add(R.string.lex_reaction_plasma_1, env.dec(Reaction.PLASMA_MULT), Reaction.PLASMA_BURN_TURNS)
                    Reaction.CALCINATION -> add(R.string.lex_reaction_calcination_1, env.dec(Reaction.CALCINATION_MULT))
                    Reaction.SHORT_CIRCUIT -> add(R.string.lex_reaction_short_circuit_1, env.dec(Reaction.SHORT_CIRCUIT_MULT))
                    Reaction.RIGIDITY -> add(R.string.lex_reaction_rigidity_1, env.dec(Reaction.FRAGILE_MULT))
                    Reaction.POISON_ICE -> add(R.string.lex_reaction_poison_ice_1)
                    Reaction.FROZEN_ARMOR -> add(R.string.lex_reaction_frozen_armor_1, Reaction.RIGIDITY_TURNS, Reaction.EXPOSED_EXTRA)
                    Reaction.CONVULSIONS -> add(R.string.lex_reaction_convulsions_1, Reaction.CONVULSION_TURNS)
                    Reaction.ICE_SHOCK -> add(R.string.lex_reaction_ice_shock_1, Reaction.ICE_SHOCK_TURNS)
                    Reaction.LIGHTNING_ROD -> add(R.string.lex_reaction_lightning_rod_1, env.pct(Reaction.ROD_SHARE))
                    Reaction.SPARK -> add(R.string.lex_reaction_spark_1)
                    Reaction.FROSTBITE -> add(R.string.lex_reaction_frostbite_1)
                    Reaction.NEUROTOXIN -> add(R.string.lex_reaction_neurotoxin_1, Reaction.NEUROTOXIN_EXTRA)
                    Reaction.INFECTION -> add(R.string.lex_reaction_infection_1, Reaction.EXPOSED_EXTRA)
                    Reaction.SHATTER -> add(R.string.lex_reaction_shatter_1, env.dec(Reaction.SHATTER_MULT))
                    Reaction.DEATHBLOW -> add(R.string.lex_reaction_deathblow_1)
                    Reaction.PURIFY -> add(R.string.lex_reaction_purify_1, Reaction.PURIFIED_TURNS, env.dec(Reaction.PURIFIED_ARMOR), 0)
                    Reaction.HOLY_FIRE -> add(R.string.lex_reaction_holy_fire_1, env.pct(Reaction.HOLY_FIRE_SHARE))
                }
            }
        }
    }

    // ── Reliques (seulement celles qu'on a trouvées) ────────────────────────────

    /**
     * La description d'une relique, avec les vrais chiffres du héros. Toutes les descriptions
     * reçoivent les mêmes arguments dans le même ordre, chacune prend ceux qui la concernent
     * (voir les `roguelike_relic_*_desc`).
     */
    fun relicText(env: LexiconEnv, relic: Relic, hero: Hero): String {
        val (lo, hi) = hero.relicDamage(relic)
        val empowerPct = (RelicBudget.empowerBonus(relic) * 100).roundToInt()
        return env.s(relic.descRes, env.num(lo), env.num(hi), relic.effectTurns, hero.castCooldown(relic),
            env.num(hero.poisonDose(relic)), hero.spellDc(relic), empowerPct, env.num(hero.relicAmount(relic)),
            env.pct(Combat.FRACTURE_MULT - 1f), env.pct(1f - Combat.WEAKEN_MULT), env.pct(1f - Relic.CHAIN_FALLOFF),
            Relic.CRYSTAL_MULT.roundToInt(), 0, Relic.STONESKIN_ARMOR.roundToInt(),
            Relic.MISSILE_COUNT, Relic.WARCRY_ATTACKS, Relic.POISON_MAX_TURNS, env.dec(Relic.FREEZE_TURN_LENGTH),
            env.pct(Relic.BURN_SHARE))
    }

    private fun relics(): List<LexiconEntry> {
        val c = LexiconCategory.RELICS
        val intro = entry("relics", c, R.string.lex_relics) {
            add(R.string.lex_relics_1); add(R.string.lex_relics_2)
            add(R.string.lex_relics_3, Hero.RELIC_SLOT_FLOORS.joinToString(", "))
            add(R.string.lex_relics_4); add(R.string.lex_relics_5)
        }
        return listOf(intro) + Relic.entries.map { r ->
            entry(idOf(r), c, r.labelRes, secret = true, known = { h -> h != null && r in h.relics }) {
                env.hero?.let { text(relicText(env, r, it)) }
                add(R.string.lex_relic_facts,
                    env.link(idOf(r.element), env.s(elementRes(r.element))),
                    env.link(idOf(r.attribute), env.s(r.attribute.labelRes)),
                    env.s(targetRes(r.target)))
            }
        }
    }

    // ── Résonances (cachées jusqu'à la première fois) ───────────────────────────

    private fun resonances(): List<LexiconEntry> {
        val c = LexiconCategory.RESONANCES
        val intro = entry("resonances", c, R.string.lex_resonances) { add(R.string.lex_resonances_1, Resonance.BONUS); add(R.string.lex_resonances_2) }
        return listOf(intro) + Resonance.entries.map { r ->
            entry(idOf(r), c, r.labelRes, secret = true, known = { h -> h != null && r in h.knownResonances }) {
                add(R.string.lex_resonance_pair, env.s(r.a.labelRes), env.s(r.b.labelRes))
                add(R.string.lex_resonance_bonus, Resonance.BONUS, env.link(idOf(r.attribute), env.s(r.attribute.labelRes)))
                text(env.s(r.descRes).replaceFirstChar { it.uppercase() })
            }
        }
    }

    // ── Archétypes ──────────────────────────────────────────────────────────────

    private fun archetypes(): List<LexiconEntry> {
        val c = LexiconCategory.ARCHETYPES
        val general = entry("archetype", c, R.string.lex_archetype) {
            add(R.string.lex_archetype_1); add(R.string.lex_archetype_2, Hero.ARCHETYPE_PIECES)
            add(R.string.lex_archetype_3); add(R.string.lex_archetype_4)
        }
        val each = Archetype.entries.map { a ->
            entry(idOf(a), c, a.labelRes) {
                when (a) {
                    Archetype.BARBARIAN -> { add(R.string.lex_archetype_barbarian) }
                    Archetype.WARRIOR -> { add(R.string.lex_archetype_warrior_1, env.pct(1f - Hero.WARRIOR_WEAPON_DAMAGE_MULT)); add(R.string.lex_archetype_warrior_2, env.pct(Combat.BLOCK_THORNS_SHARE), env.pct(Combat.BARE_BLOCK_THORNS_SHARE)) }
                    Archetype.ROGUE -> { add(R.string.lex_archetype_rogue_1); add(R.string.lex_archetype_rogue_2) }
                    Archetype.MAGE -> { add(R.string.lex_archetype_mage_1); add(R.string.lex_archetype_mage_2) }
                    Archetype.VAGABOND -> { add(R.string.lex_archetype_vagabond_1); add(R.string.lex_archetype_vagabond_2, env.pct(Combat.ROLL_BONUS)); add(R.string.lex_archetype_vagabond_3) }
                    Archetype.NECROMANCER -> {
                        add(R.string.lex_archetype_necromancer_1)
                        add(R.string.lex_archetype_necromancer_2, env.pct(Combat.PUPPET_PARRY_HEAL))
                        add(R.string.lex_archetype_necromancer_3, Combat.PUPPETS, env.pct(Combat.PUPPET_HP_SHARE),
                            env.pct(1f - Combat.PUPPET_SELF_SHARE), env.pct(Combat.ECHO_SHARE))
                    }
                }
                add(R.string.lex_archetype_special, env.link(specialId(a), env.s(a.specialRes)))
                val weapons = (listOf(ItemBase.SWORD) + a.weapons).joinToString(", ") { env.link(idOf(it), env.s(it.nounRes)) }
                add(R.string.lex_archetype_weapons, weapons, env.pct(Hero.WRONG_WEAPON_MALUS))
            }
        }
        val specials = listOf(
            entry(specialId(Archetype.BARBARIAN), c, Archetype.BARBARIAN.specialRes) { add(R.string.lex_special_smash); add(R.string.lex_special_2, Hero.SPECIAL_COOLDOWN) },
            entry(specialId(Archetype.WARRIOR), c, Archetype.WARRIOR.specialRes) {
                add(R.string.lex_special_guard_1, env.pct(Combat.GUARD_THORNS_SHARE)); add(R.string.lex_special_2, Hero.SPECIAL_COOLDOWN)
            },
            entry(specialId(Archetype.ROGUE), c, Archetype.ROGUE.specialRes) {
                add(R.string.lex_special_deadly_1, env.dec(Combat.DEADLY_CRIT_BONUS))
                add(R.string.lex_special_deadly_2, env.pct(Combat.DEADLY_HP_THRESHOLD)); add(R.string.lex_special_2, Hero.SPECIAL_COOLDOWN)
            },
            entry(specialId(Archetype.MAGE), c, Archetype.MAGE.specialRes) {
                add(R.string.lex_special_mirror_1, Combat.MIRROR_IMAGES, 0); add(R.string.lex_special_mirror_fixed, Hero.SPECIAL_COOLDOWN, IsotopeSets.SPECIAL_COOLDOWN)
            },
            entry(specialId(Archetype.VAGABOND), c, Archetype.VAGABOND.specialRes) {
                add(R.string.lex_special_combo_1, Combat.CHAIN_HITS); add(R.string.lex_special_2, Hero.SPECIAL_COOLDOWN)
            },
            entry(specialId(Archetype.NECROMANCER), c, Archetype.NECROMANCER.specialRes) {
                add(R.string.lex_special_puppets_1, env.pct(Combat.PUPPET_SUMMON_HIT_SHARE), env.pct(Combat.PUPPET_RECALL_HP_SHARE))
                add(R.string.lex_special_2, Combat.PUPPET_RECALL_COOLDOWN)
            },
        )
        return listOf(general) + each + specials
    }

    // ── Objets : poids, raretés, bases, paliers ─────────────────────────────────

    private fun items(): List<LexiconEntry> {
        val c = LexiconCategory.ITEMS
        val weights = ArmorWeight.entries.map { w ->
            entry(idOf(w), c, w.labelRes) {
                add(when (w) {
                    ArmorWeight.CLOTH -> R.string.lex_weight_cloth_1
                    ArmorWeight.LIGHT -> R.string.lex_weight_light_1
                    ArmorWeight.HEAVY -> R.string.lex_weight_heavy_1
                    ArmorWeight.MEDIUM -> R.string.lex_weight_medium_1
                    ArmorWeight.FUR -> R.string.lex_weight_fur
                    ArmorWeight.ULTRALIGHT -> R.string.lex_weight_ultralight_1
                })
                add(R.string.lex_weight_armor, env.dec(w.armorMult))
                if (w.acPerPiece > 0) add(R.string.lex_weight_dodge, dodgePct(w.acPerPiece))
                if (w.speedPerPiece != 0f) add(R.string.lex_weight_speed, env.signed(Math.round(w.speedPerPiece * 100)))
                if (w.dexCounts && w.dexCap != Int.MAX_VALUE) add(R.string.lex_weight_dex_cap, w.dexCap)
                else add(if (w.dexCounts) R.string.lex_weight_dex_yes else R.string.lex_weight_dex_no)
                Archetype.entries.firstOrNull { it.weight == w }?.let {
                    add(R.string.lex_weight_archetype, env.link(idOf(it), env.s(it.labelRes)))
                }
            }
        }
        val rarities = Rarity.entries.map { r ->
            entry(idOf(r), c, r.labelRes) {
                if (r.maxAffixes == 0) add(R.string.lex_rarity_normal_1)
                else add(R.string.lex_rarity_affixes, r.minAffixes, r.maxAffixes)
                add(R.string.lex_rarity_sell, r.sellMult.roundToInt())
            }
        }
        val bases = ItemBase.entries.map { b ->
            entry(idOf(b), c, b.nounRes) {
                add(R.string.lex_base_slot, env.s(b.slot.labelRes))
                val attr = b.attribute
                if (attr != null) add(R.string.lex_base_attr, env.link(idOf(attr), env.s(attr.labelRes)))
                else add(R.string.lex_base_attr_random)
                if (b.damageMult > 0f) add(R.string.lex_base_damage, env.dec(b.damageMult, 2))
                if (b.armorBase > 0f) add(R.string.lex_base_armor, env.num(b.armorBase.roundToInt()))
                if (b.spellBonus > 0f) add(R.string.lex_base_spell, env.pct(b.spellBonus))
                if (b == ItemBase.SHIELD) add(R.string.lex_base_shield, dodgePct(ArmorClass.SHIELD))
                if (b == ItemBase.ORB) add(R.string.lex_base_orb_damage, env.pct(Hero.ORB_DAMAGE_SHARE))
                if (b == ItemBase.BOW) add(R.string.lex_base_bow, env.pct(Combat.BOW_EXPOSE_THRESHOLD), env.pct(Combat.DEADLY_HP_THRESHOLD))
                if (b == ItemBase.GRIMOIRE) add(R.string.lex_base_grimoire, 0, env.pct(Combat.GRIMOIRE_ECHO_BONUS))
                if (b == ItemBase.CLUB) add(R.string.lex_base_club)
                if (b == ItemBase.LANTERN) add(R.string.lex_base_lantern, env.pct(Combat.LANTERN_CHAIN_BONUS))
                if (b in ArmorWeight.WEIGHTED) add(R.string.lex_base_weights)
            }
        }
        val tiers = entry("tiers", c, R.string.lex_tiers) {
            add(R.string.lex_tiers_1); add(R.string.lex_tiers_2)
            for (t in 1..AffixBudget.TIERS) add(R.string.lex_tiers_3, t, floorOfPower(AffixBudget.tierPower(t)))
            add(R.string.lex_tiers_4, env.dec(AffixBudget.DEEP_TIER_GROWTH))
        }
        return weights + rarities + bases + tiers + isotopeSets()
    }

    // ── Sets d'isotope : cachés tant qu'aucune pièce n'est tombée ───────────────

    private fun isotopeSets() = IsotopeSets.PERMANENT.map { set ->
        entryT(set.lexiconId, LexiconCategory.ITEMS, { env -> env.s(R.string.lex_set_title, set.label(env.ctx)) },
            secret = true, known = { h -> h != null && IsotopeSets.discovered(h, set) }) {
            val a = set.archetype
            add(R.string.lex_set_1, env.link(idOf(a), env.s(a.labelRes)), env.link(idOf(a.weight), env.s(a.weight.labelRes)))
            add(R.string.lex_set_2, IsotopeSets.SLOTS.size, env.link(specialId(a), env.s(a.specialRes)))
            when (a) {
                Archetype.BARBARIAN -> { add(R.string.lex_set_barbarian) }
                Archetype.WARRIOR -> {
                    add(R.string.lex_set_special_guard, env.pct(IsotopeSets.GUARD_THORNS_SHARE), env.pct(Combat.GUARD_THORNS_SHARE),
                        IsotopeSets.SPECIAL_COOLDOWN, Hero.SPECIAL_COOLDOWN)
                    add(R.string.lex_set_hp, env.pct(IsotopeSets.HP_SHARE))
                }
                Archetype.ROGUE -> {
                    add(R.string.lex_set_special_deadly, env.dec(IsotopeSets.DEADLY_CRIT_BONUS), env.dec(Combat.DEADLY_CRIT_BONUS),
                        IsotopeSets.SPECIAL_COOLDOWN, Hero.SPECIAL_COOLDOWN)
                    add(R.string.lex_set_speed, env.pct(IsotopeSets.SPEED_BONUS))
                }
                Archetype.MAGE -> {
                    add(R.string.lex_set_special_mirror, IsotopeSets.MIRROR_IMAGES, Combat.MIRROR_IMAGES,
                        IsotopeSets.SPECIAL_COOLDOWN, Hero.SPECIAL_COOLDOWN)
                    add(R.string.lex_set_spell, env.pct(IsotopeSets.SPELL_SHARE))
                }
                Archetype.VAGABOND -> {
                    add(R.string.lex_set_special_combo, env.pct(IsotopeSets.CHAIN_DAMAGE_BONUS),
                        IsotopeSets.SPECIAL_COOLDOWN, Hero.SPECIAL_COOLDOWN)
                    add(R.string.lex_set_crit_damage, env.dec(IsotopeSets.CRIT_DAMAGE_BONUS))
                }
                Archetype.NECROMANCER -> {
                    add(R.string.lex_set_special_puppets, IsotopeSets.PUPPETS, Combat.PUPPETS,
                        Combat.PUPPET_RECALL_COOLDOWN, Combat.PUPPET_RECALL_COOLDOWN)
                    add(R.string.lex_set_recharge, IsotopeSets.RECHARGE_CUT)
                }
            }
            add(R.string.lex_set_drop, env.pct(IsotopeSets.DROP_SHARE))
            env.hero?.let { h ->
                you(R.string.lex_you_set, IsotopeSets.SLOTS.count { h.equipped[it]?.isotopeSet?.archetype == a }, IsotopeSets.SLOTS.size)
            }
        }
    }

    /** Un palier précis d'une stat (« +3 CON (P4) ») : sa fourchette, et l'étage où il s'ouvre. */
    private fun tierEntry(id: String): LexiconEntry? {
        val p = id.split(':')
        if (p.size != 3 || p[0] != "tier") return null
        val type = runCatching { StatType.valueOf(p[1]) }.getOrNull() ?: return null
        val tier = p[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        return entryT(id, LexiconCategory.ITEMS, { env -> env.s(R.string.lex_tier_title, tier) }) {
            fun value(v: Float) = if (type.isPercent) env.s(R.string.lex_value_percent, env.dec(v * 100f)) else env.num(v.roundToInt())
            add(R.string.lex_tier_1, env.link(idOf(type), env.s(type.labelRes)), tier,
                value(AffixBudget.minRoll(type, tier)), value(AffixBudget.nominal(type, tier)))
            val power = AffixBudget.tierPower(tier)
            add(R.string.lex_tier_2, power, floorOfPower(power))
            if (AffixBudget.maxTierOf(type) < Int.MAX_VALUE) add(R.string.lex_tier_3, AffixBudget.maxTierOf(type))
        }
    }

    // ── Matières : le tableau périodique ────────────────────────────────────────

    private fun materials(): List<LexiconEntry> {
        val c = LexiconCategory.MATERIALS
        val powerPerElement = Grade.TIERS * Grade.POWER_PER_TIER
        val general = entry("materials", c, R.string.lex_materials) {
            val words = env.ctx.resources.getStringArray(R.array.roguelike_grade_cycles).filter { it.isNotEmpty() }
            add(R.string.lex_materials_1)
            add(R.string.lex_materials_2, floorOfPower(Grade.POWER_PER_TIER + 1) - 1, floorOfPower(powerPerElement + 1) - 1)
            add(R.string.lex_materials_3, words.joinToString(", "))
            add(R.string.lex_materials_4)
        }
        return listOf(general) + (0 until Grade.ELEMENTS).map { i ->
            entryT(materialId(i), c, { env -> periodic[i].localizedName(env.ctx) }) {
                add(R.string.lex_material_1, i + 1)
                add(R.string.lex_material_2, floorOfPower(i * powerPerElement + 1), floorOfPower((i + 1) * powerPerElement + 1) - 1)
            }
        }
    }

    // ── Monstres : les affinités, notées à mesure qu'on les découvre ────────────

    private fun monsters(): List<LexiconEntry> {
        val c = LexiconCategory.MONSTERS
        val elements = listOf(Element.FIRE, Element.ICE, Element.LIGHTNING, Element.POISON, Element.HOLY)
        return MonsterType.entries.filter { DungeonBestiary.habitats(it).isNotEmpty() }.map { m ->
            entry(idOf(m), c, m.labelRes) {
                add(R.string.roguelike_monster_habitats,
                    DungeonBestiary.habitats(m).joinToString(", ") { env.s(it.label) })
                if (m == MonsterType.PIRATE_CAPTAIN) add(R.string.roguelike_monster_captain_hint)
                if (m == MonsterType.SPIDER) add(R.string.roguelike_monster_spider_hint)
                for (e in elements) {
                    val known = env.hero?.knownAffinities?.contains(Hero.affinityKey(m, e)) == true
                    val label = if (!known) R.string.lex_affinity_unknown else when (m.affinity(e)) {
                        Affinity.VULNERABLE -> R.string.lex_affinity_vulnerable
                        Affinity.NORMAL -> R.string.lex_affinity_normal
                        Affinity.RESISTANT -> R.string.lex_affinity_resistant
                        Affinity.IMMUNE -> R.string.lex_affinity_immune
                    }
                    add(R.string.lex_monster_line, env.link(idOf(e), env.s(elementRes(e))), env.s(label))
                }
            }
        }
    }

    // ── Profondeur : hors du jeu, seulement ici ─────────────────────────────────

    private fun depth(): List<LexiconEntry> = listOf(
        entry("depth", LexiconCategory.DEPTH, R.string.lex_depth) {
            val deep = Encounters.DEEP_FLOOR
            fun w(floor: Int) = env.dec(LootSystem.attributeWeightAt(floor), 2, 2)
            fun crit(floor: Int) = env.dec(AffixBudget.critResistance(LootSystem.powerCenter(floor).roundToInt()) * 100f)
            add(R.string.lex_depth_1, deep)
            add(R.string.lex_depth_2, deep, w(1_000), w(10_000))
            add(R.string.lex_depth_3)
            add(R.string.lex_depth_4, crit(1_000), crit(10_000))
            env.hero?.let { you(R.string.lex_you_depth, env.floor, w(env.floor)) }
        },
    )
}
