package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import com.Atom2Universe.app.games.roguelike.demo.DemoGear
import com.Atom2Universe.app.games.roguelike.demo.DungeonClassSprites
import com.Atom2Universe.app.games.roguelike.demo.DungeonDemoSprites

internal val Equipment.inventoryColor: Int get() = if (isotopeZ != null) EquipmentArt.LEGENDARY else rarity.colorArgb

/** The same pixel-art pieces are used by the inventory and the combat paper doll. */
internal object EquipmentArt {
    const val LEGENDARY = 0xFFFFA348.toInt()
    private val cache = LruCache<String, Bitmap>(128)

    fun gear(slot: EquipSlot, item: Equipment?, fallback: Archetype): DemoGear {
        val family = if (item == null) fallback else item.weight?.let { w ->
            Archetype.entries.firstOrNull { it.weight == w }
        } ?: if (item.base == ItemBase.BOW) Archetype.ROGUE else Archetype.WARRIOR
        return DemoGear(slot, item?.isotopeZ != null, DungeonClassSprites.defaultHue(family), family, item != null)
    }

    fun icon(item: Equipment): Bitmap {
        val key = "${item.base}:${item.weight}:${item.isotopeZ != null}"
        return cache.get(key) ?: piece(item).also { cache.put(key, it) }
    }

    private fun piece(item: Equipment): Bitmap {
        if (item.base in ArmorWeight.WEIGHTED) return DungeonDemoSprites.equipment(gear(item.slot, item, Archetype.WARRIOR))
        return accessory(item.base, item.isotopeZ != null)
    }

    fun empty(slot: EquipSlot): Bitmap = icon(Equipment(
        base = ItemBase.entries.first { it.slot == slot }, power = 1, rarity = Rarity.NORMAL,
        damageMin = 0, damageMax = 0, armor = 0, implicits = emptyList(), affixes = emptyList(),
        spriteRow = 0, spriteCol = 0, lootId = 0,
    ))

    /** 16 × 24 pixels, with the weapon grip at the combat renderer's attachment point. */
    private fun accessory(base: ItemBase, legendary: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint().apply { isAntiAlias = false }
        val dark = 0xFF172034.toInt()
        val metal = 0xFF9EAFC7.toInt()
        val light = 0xFFE2E8ED.toInt()
        val wood = 0xFF92704E.toInt()
        val gold = if (legendary) LEGENDARY else 0xFFD5B66F.toInt()
        val gem = if (legendary) 0xFFFFE0A1.toInt() else 0xFF80D6C9.toInt()
        fun r(x: Int, y: Int, w: Int, h: Int, color: Int) { p.color = color; c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), p) }
        fun handle() { r(5, 10, 3, 12, dark); r(6, 10, 1, 11, wood); r(5, 20, 3, 2, gold) }
        when (base) {
            ItemBase.SWORD, ItemBase.DAGGER -> {
                val top = if (base == ItemBase.DAGGER) 7 else 1
                r(5, top, 3, 15-top, dark); r(6, top+1, 2, 13-top, metal); r(6, top+1, 1, 11-top, light)
                r(3, 14, 8, 2, dark); r(4, 14, 6, 1, gold); r(6, 16, 2, 5, wood); r(5, 21, 4, 1, gold)
            }
            ItemBase.AXE -> {
                handle(); r(5, 2, 3, 12, wood); r(2, 3, 10, 7, dark); r(1, 5, 12, 4, dark)
                r(2, 5, 10, 3, metal); r(3, 4, 8, 2, metal); r(2, 5, 1, 3, light); r(11, 5, 1, 3, light); r(6, 3, 1, 8, gold)
            }
            ItemBase.MACE -> {
                handle(); r(3, 2, 8, 9, dark); r(2, 4, 10, 5, dark); r(3, 4, 8, 5, metal)
                r(5, 3, 4, 7, light); r(6, 4, 2, 5, gold)
            }
            ItemBase.STAFF, ItemBase.SCEPTER -> {
                handle(); r(5, 2, 3, 12, wood); r(3, 2, 7, 7, dark); r(4, 1, 5, 7, gold)
                r(5, 2, 3, 4, if (base == ItemBase.STAFF) 0xFFAA8BDF.toInt() else gem)
                r(6, 2, 1, 2, light); r(4, 9, 5, 2, gold)
            }
            ItemBase.SHIELD -> {
                r(1, 3, 13, 12, dark); r(2, 15, 11, 3, dark); r(4, 18, 7, 2, dark); r(6, 20, 3, 1, dark)
                r(2, 4, 11, 11, metal); r(3, 15, 9, 2, metal); r(5, 17, 5, 2, metal)
                r(6, 4, 3, 14, gold); r(3, 8, 9, 2, gold); r(6, 8, 3, 3, gem)
            }
            ItemBase.ORB -> {
                r(4, 5, 8, 12, dark); r(2, 7, 12, 8, dark); r(3, 8, 10, 6, 0xFF68579E.toInt())
                r(5, 6, 6, 10, 0xFF9A7DDD.toInt()); r(5, 7, 3, 3, light); r(4, 17, 8, 2, gold)
            }
            ItemBase.BOW -> {
                r(3, 2, 3, 2, gold); r(5, 4, 3, 3, wood); r(7, 7, 3, 10, wood)
                r(5, 17, 3, 3, wood); r(3, 20, 3, 2, gold); r(3, 3, 1, 18, light); r(1, 11, 12, 1, metal); r(12, 10, 2, 3, light)
            }
            ItemBase.GRIMOIRE -> {
                r(1, 5, 13, 15, dark); r(3, 6, 10, 13, 0xFF66746D.toInt()); r(2, 6, 2, 13, wood)
                r(4, 18, 8, 1, light); r(6, 9, 5, 6, gold); r(7, 10, 3, 4, gem)
            }
            ItemBase.LANTERN -> {
                r(5, 2, 5, 5, dark); r(6, 3, 3, 3, wood); r(3, 7, 9, 13, dark)
                r(4, 9, 7, 8, gold); r(5, 10, 5, 6, 0xFFFFDE97.toInt()); r(7, 9, 1, 8, wood)
                r(2, 7, 11, 2, metal); r(2, 18, 11, 2, metal)
            }
            ItemBase.RING -> {
                r(4, 7, 8, 2, gold); r(2, 9, 2, 7, gold); r(12, 9, 2, 7, gold); r(4, 16, 8, 2, gold)
                r(5, 4, 6, 5, dark); r(6, 4, 4, 4, gem); r(6, 4, 2, 1, light)
            }
            ItemBase.AMULET -> {
                r(2, 3, 2, 7, gold); r(12, 3, 2, 7, gold); r(3, 10, 2, 3, gold); r(11, 10, 2, 3, gold)
                r(5, 13, 6, 2, gold); r(4, 15, 8, 6, dark); r(5, 15, 6, 5, gold); r(6, 16, 4, 3, gem); r(6, 16, 1, 1, light)
            }
            else -> Unit
        }
        return bitmap
    }
}
