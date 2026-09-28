package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.entity.RangedProfile
import com.Atom2Universe.app.games.caves.node.*
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.MineralItems as M

/** Inventory organization is independent of the shortcut's interaction mode. */
internal enum class InventoryCategory(val label: Int, val icon: String) {
    ALL(R.string.cave_ui_all, "all"),
    ARMOR(R.string.cave_category_armor, "armor"),
    WEAPONS(R.string.cave_category_weapons, "combat"),
    AMMO(R.string.cave_category_ammo, "ammo"),
    TOOLS(R.string.cave_category_tools, "tools"),
    GARDEN(R.string.cave_ui_garden, "garden"),
    KITCHEN(R.string.cave_category_kitchen, "kitchen"),
    BLOCKS(R.string.cave_category_blocks, "place"),
    UTILITIES(R.string.cave_ui_functional, "machine"),
    TERRAIN(R.string.cave_ui_terrain, "terrain"),
    WOOD(R.string.cave_ui_wood, "wood"),
    STONE(R.string.cave_ui_stone, "stone"),
    NATURE(R.string.cave_ui_nature, "nature"),
    FABRICS(R.string.cave_ui_cotton, "fabric"),
    ORES(R.string.cave_ui_ores, "ore"),
    RESOURCES(R.string.cave_ui_resources, "resources");

    fun matches(id: Short): Boolean {
        if (this == ALL) return true
        val primary = classify(id)
        if (this == primary) return true
        val def = BlockRegistry.get(ForgedEquipment.base(id) ?: id)
        // Material shortcuts remain available inside the building-block family.
        return primary == BLOCKS && when (this) {
            TERRAIN -> def?.creativeTab == "terrain"
            WOOD -> def?.creativeTab == "wood"
            STONE -> def?.creativeTab == "stone"
            FABRICS -> def?.creativeTab == "cotton"
            else -> false
        }
    }

    companion object {
        private val ammunition = setOf<Short>(2020, 2021, 8010, 8011, 8012)
        private val garden = setOf(FarmSoil.HOE, FarmSoil.FARMLAND, F.COMPOST, F.COMPOSTER,
            F.TROUGH, F.CHARM, E.ROD, E.BAIT)
        private val kitchen = setOf(F.COOKER, F.MILL, F.MILK, F.EGG, F.TRUFFLE, F.FLOUR,
            F.PLATE, E.FISH_OIL)

        private fun classify(id: Short): InventoryCategory {
            val base = ForgedEquipment.base(id) ?: id
            val mineral = M.variant(base)
            if (ForgedEquipment.template(base) != null || E.armor(base) > 0f || base == E.SHIELD) return ARMOR
            if (RangedProfile.of(base) != null || E.melee(base) != null) return WEAPONS
            if (base in ammunition) return AMMO
            if (base in garden || FarmItems.seedCrop(base) != null || FarmShowcasePlants.sample(base) != null) return GARDEN
            if (F.toolIndex(base) >= 0 || mineral?.form == M.Form.PICK || base == F.SHEARS ||
                BlockRegistry.get(base)?.name in setOf("bucket_empty", "bucket_full")) return TOOLS
            if (KitchenItems.isItem(base) || F.healing(base) > 0 || FarmItems.produceCrop(base) != null ||
                base in kitchen || base in E.RIVER_FISH..E.DEEP_FISH || base.toInt() in 3130..3132) return KITCHEN
            val def = BlockRegistry.get(base) ?: return RESOURCES
            if (def.creativeTab == "ores") return ORES
            if (base == F.WOOL || base == F.CLOTH || base == F.SAIL || base.toInt() in 3111..3112) return FABRICS
            if (def.creativeTab == "nature") return NATURE
            if (def.placeable && def.creativeTab in setOf("terrain", "wood", "stone", "cotton") || def.name == "glass") return BLOCKS
            if (def.placeable) return UTILITIES
            return RESOURCES
        }
    }
}
