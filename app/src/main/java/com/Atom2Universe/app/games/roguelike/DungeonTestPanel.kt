package com.Atom2Universe.app.games.roguelike

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.*
import com.Atom2Universe.app.R
import kotlin.random.Random

/** Outils d'une session temporaire, sans écriture dans la sauvegarde normale. */
internal class DungeonTestPanel(
    private val activity: Activity,
    private val game: () -> RoguelikeGame,
    private val replace: (RoguelikeGame) -> Unit,
    private val refresh: () -> Unit,
) {
    companion object {
        fun unlock(hero: Hero) {
            hero.deepestFloor = maxOf(hero.deepestFloor, Hero.RELIC_SLOT_FLOORS.last())
            Relic.entries.forEach { hero.addRelic(it) }
        }
    }

    private fun form() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    private fun LinearLayout.label(res: Int) { addView(TextView(activity).apply { setText(res) }) }
    private fun LinearLayout.number(res: Int, value: Int): EditText {
        label(res)
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
        }
        addView(input)
        return input
    }
    private fun LinearLayout.choose(res: Int, labels: List<String>): Spinner {
        label(res)
        val input = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        addView(input)
        return input
    }
    private fun dialog(title: Int, form: LinearLayout, action: () -> Boolean) {
        val dialog = AlertDialog.Builder(activity, R.style.Theme_Dungeon_Dialog)
            .setTitle(title).setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (action()) { refresh(); dialog.dismiss() }
        } }
        dialog.show()
    }
    private fun EditText.valid(max: Int): Int? {
        val value = text.toString().toIntOrNull()
        if (value == null || value !in 1..max) {
            error = activity.getString(R.string.dungeon_test_range, max)
            return null
        }
        return value
    }
    fun show() {
        val actions = intArrayOf(R.string.dungeon_test_floor, R.string.dungeon_test_gear,
            R.string.dungeon_test_item, R.string.dungeon_test_restore, R.string.dungeon_test_gold,
            R.string.dungeon_test_reveal)
        AlertDialog.Builder(activity, R.style.Theme_Dungeon_Dialog)
            .setTitle(R.string.dungeon_test_tools)
            .setItems(actions.map { activity.getString(it) }.toTypedArray()) { _, index ->
                when (index) {
                    0 -> floor()
                    1 -> gear()
                    2 -> item()
                    3 -> { game().hero.apply { healFull(); relicCooldowns.clear(); specialCooldown = 0 }; refresh() }
                    4 -> { game().hero.gold = (game().hero.gold.toLong() + 10000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); refresh() }
                    5 -> { game().level.explored.forEach { it.fill(true) }; refresh() }
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun floor() {
        val form = form()
        form.label(R.string.dungeon_test_notice)
        val floor = form.number(R.string.dungeon_test_floor, game().floor)
        for (preset in listOf(20, 500)) form.addView(Button(activity).apply {
            text = activity.getString(R.string.dungeon_test_preset, preset)
            setOnClickListener { floor.setText(preset.toString()) }
        })
        dialog(R.string.dungeon_test_floor, form) {
            val value = floor.valid(10000) ?: return@dialog false
            replace(RoguelikeGame(hero = game().hero, startFloor = value))
            true
        }
    }
    private fun gear() {
        val form = form()
        val archetype = form.choose(R.string.dungeon_test_class, Archetype.entries.map { activity.getString(it.labelRes) })
        val equipmentFloor = form.number(R.string.dungeon_test_equipment_floor, game().floor)
        val seed = form.number(R.string.dungeon_test_seed, 1)
        val isotope = CheckBox(activity).apply { setText(R.string.dungeon_test_set) }
        form.addView(isotope)
        dialog(R.string.dungeon_test_gear, form) {
            val selectedFloor = equipmentFloor.valid(10000) ?: return@dialog false
            val rollSeed = seed.valid(Int.MAX_VALUE) ?: return@dialog false
            val rng = Random(rollSeed)
            val type = Archetype.entries[archetype.selectedItemPosition]
            val set = IsotopeSets.forArchetype(type)
            val hero = game().hero
            val bases = listOf(type.weapons.first(), type.offhand, ItemBase.HELMET,
                ItemBase.ARMOR, ItemBase.BOOTS, ItemBase.AMULET, ItemBase.RING)
            bases.forEach { base ->
                val equipment = LootSystem.create(base, LootSystem.rollPowerForFloor(selectedFloor, rng), Rarity.RARE, hero.nextLootId++, rng, type.weight).let {
                    if (isotope.isChecked && base in IsotopeSets.BASES) it.copy(isotopeZ = set.index) else it
                }
                if (isotope.isChecked) hero.knownSets += set.z
                hero.equipped.put(base.slot, equipment)?.let { hero.bag.add(it) }
            }
            hero.healFull()
            true
        }
    }
    private fun item() {
        val form = form()
        val base = form.choose(R.string.dungeon_test_item, ItemBase.entries.map { activity.getString(it.nounRes) })
        val rarity = form.choose(R.string.dungeon_test_rarity, Rarity.entries.map { activity.getString(it.labelRes) })
        val weight = form.choose(R.string.dungeon_test_weight, ArmorWeight.entries.map { activity.getString(it.labelRes) })
        val equipmentFloor = form.number(R.string.dungeon_test_equipment_floor, game().floor)
        val quantity = form.number(R.string.dungeon_test_quantity, 1)
        dialog(R.string.dungeon_test_item, form) {
            val selectedFloor = equipmentFloor.valid(10000) ?: return@dialog false
            val count = quantity.valid(100) ?: return@dialog false
            val hero = game().hero
            repeat(count) {
                hero.bag += LootSystem.create(ItemBase.entries[base.selectedItemPosition], LootSystem.rollPowerForFloor(selectedFloor, Random),
                    Rarity.entries[rarity.selectedItemPosition], hero.nextLootId++, Random,
                    ArmorWeight.entries[weight.selectedItemPosition])
            }
            true
        }
    }
}
