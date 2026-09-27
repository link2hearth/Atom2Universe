package com.Atom2Universe.app.games.caves.node

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.world.MineralProgression as P
import kotlin.math.roundToInt

/** IDs below 4000 are reserved here, outside the persistent firearm-instance range. */
internal object MineralItems {
    enum class Form(val label: Int) {
        ORE(R.string.cave_mineral_ore), RAW(R.string.cave_mineral_raw), DUST(R.string.cave_mineral_dust),
        INGOT(R.string.cave_mineral_ingot), MOLTEN(R.string.cave_mineral_molten), PLATE(R.string.cave_mineral_plate),
        HEAD(R.string.cave_mineral_head), BLADE(R.string.cave_mineral_blade), PICK(R.string.cave_mineral_pick),
        SWORD(R.string.cave_mineral_sword), SPEAR(R.string.cave_mineral_spear), HAMMER(R.string.cave_mineral_hammer),
        ARMOR(R.string.cave_mineral_armor), ARMOR_PART(R.string.cave_mineral_armor_part)
    }
    data class Metal(val key: String, val label: Int, val color: Int)
    val metals = listOf(
        Metal("iron", R.string.cave_metal_iron, 0xC9936D),
        Metal("silver", R.string.cave_metal_silver, 0xD7D6ED),
        Metal("gold", R.string.cave_metal_gold, 0xEDBF46),
        Metal("cobalt", R.string.cave_metal_cobalt, 0x387FDF),
        Metal("titanium", R.string.cave_metal_titanium, 0xA9D4E5),
        Metal("chromium", R.string.cave_metal_chromium, 0x83C4A2),
        Metal("tungsten", R.string.cave_metal_tungsten, 0xA39B7E),
        Metal("platinum", R.string.cave_metal_platinum, 0xEFE0BC),
        Metal("iridium", R.string.cave_metal_iridium, 0xB28ADD)
    )
    data class Variant(val stage: Int, val form: Form) {
        val tier get() = P.tier(stage)
        val metal get() = metals[P.metal(stage)]
        val id get() = id(stage, form)
    }
    // Preserve the names and IDs already stored in worlds, recipes and player inventories.
    private val legacy = mapOf(
        (0 to Form.ORE) to 3001, (0 to Form.RAW) to 3101, (0 to Form.DUST) to 9857,
        (0 to Form.INGOT) to 3114, (0 to Form.MOLTEN) to 9868, (0 to Form.PLATE) to 9875,
        (0 to Form.BLADE) to 9927, (0 to Form.PICK) to 9836, (0 to Form.SWORD) to 9902,
        (0 to Form.SPEAR) to 9903, (0 to Form.ARMOR) to 9911,
        (1 to Form.ORE) to 3002, (1 to Form.RAW) to 3102, (1 to Form.INGOT) to 3117,
        (1 to Form.MOLTEN) to 9874,
        (2 to Form.ORE) to 3003, (2 to Form.RAW) to 3103, (2 to Form.INGOT) to 3116,
        (2 to Form.MOLTEN) to 9873
    )
    private val stageIds = Array(P.LAST_STAGE + 1) { stage ->
        ShortArray(Form.entries.size) { form ->
            (legacy[stage to Form.entries[form]] ?: (3200 + stage * Form.entries.size + form)).toShort()
        }
    }
    fun id(stage: Int, form: Form): Short {
        require(stage in 0..P.LAST_STAGE)
        return stageIds[stage][form.ordinal]
    }
    val variants = (0..P.LAST_STAGE).flatMap { stage -> Form.entries.map { Variant(stage, it) } }
    private val byId = variants.associateBy { it.id }
    // Called for every meshed face and scanned voxel: no Short boxing or hash lookup here.
    private val oreIds = BooleanArray(65536).apply {
        for (stage in 0..P.LAST_STAGE) this[id(stage, Form.ORE).toInt() and 0xffff] = true
        this[3004] = true
    }
    fun variant(id: Short?) = byId[id]
    fun steel(tier: Int): Short = if (tier == 1) FrontierItems.STEEL else (3960 + tier - 1).toShort()
    fun moltenSteel(tier: Int): Short = if (tier == 1) FrontierItems.MOLTEN_STEEL else (3966 + tier - 1).toShort()
    fun reinforcement(tier: Int): Short = (3980 + tier - 1).toShort()
    fun reinforcementTier(id: Short) = (id.toInt() - 3979).takeIf { it in 1..P.TIERS }
    val molten = variants.filter { it.form == Form.MOLTEN }.map { it.id }.toSet() + (1..P.TIERS).map(::moltenSteel)
    val solid = variants.filter { it.form == Form.MOLTEN }.associate { it.id to id(it.stage, Form.INGOT) } +
        (1..P.TIERS).associate { moltenSteel(it) to steel(it) }

    fun name(context: Context, id: Short): String? {
        if(id==ExpeditionItems.FORGE) return forgeName(context,0)
        variant(id)?.let { return context.getString(it.form.label, context.getString(it.metal.label), it.tier) }
        reinforcementTier(id)?.let { return context.getString(R.string.cave_forge_reinforcement, it) }
        for (tier in 1..P.TIERS) {
            if (id == steel(tier)) return context.getString(Form.INGOT.label, context.getString(R.string.cave_metal_steel), tier)
            if (id == moltenSteel(tier)) return context.getString(Form.MOLTEN.label, context.getString(R.string.cave_metal_steel), tier)
        }
        return null
    }
    fun forgeName(context: Context, level: Int) = when (level) {
        0 -> context.getString(R.string.cave_forge_basic)
        1 -> context.getString(R.string.cave_forge_improved)
        else -> context.getString(R.string.cave_forge_improved_tier, level)
    }
    fun isOre(id: Short) = oreIds[id.toInt() and 0xffff]
    fun isEquipment(id: Short) = variant(id)?.form in setOf(Form.SWORD, Form.SPEAR, Form.HAMMER, Form.ARMOR)
    val melee by lazy { variants.mapNotNull { v ->
        val scale=P.scale(v.stage)
        val profile=when(v.form) {
            Form.SWORD -> ExpeditionItems.Melee("sword",(13*scale).roundToInt(),3.0,.78,.5f,2)
            Form.SPEAR -> ExpeditionItems.Melee("spear",(17*scale).roundToInt(),4.2,.96,.78f,1)
            Form.HAMMER -> ExpeditionItems.Melee("hammer",(27*scale).roundToInt(),2.9,.68,1.05f,3)
            else -> null
        }
        profile?.let { v.id to it }
    }.toMap() }
    fun armorStage(id: Short?) = variant(id)?.takeIf { it.form==Form.ARMOR }?.stage
    fun armorHp(id: Short?) = (50 * P.scale(armorStage(id) ?: 0)).roundToInt()
    fun armorReduction(id: Short?, enemyStage: Int): Float? = armorStage(id)?.let {
        val rating=25f*P.scale(it)
        (rating/(rating+50f*P.scale(enemyStage))).coerceAtMost(.65f)
    }
    fun pickStage(id: Short?): Int? = variant(id)?.takeIf { it.form == Form.PICK }?.stage
        ?: if (id == 9880.toShort()) 0 else null // Existing steel tools never skip a mineral.
    fun canMine(ore: Short, held: Short?): Boolean {
        val v = variant(ore)?.takeIf { it.form == Form.ORE } ?: return true
        val tool = pickStage(held)
        return if (v.stage == 0) tool != null || held == 9833.toShort()
        else tool != null && tool >= v.stage - 1
    }
    fun requiredPick(ore: Short): Short = variant(ore)?.let {
        if (it.stage == 0) 9833.toShort() else id(it.stage - 1, Form.PICK)
    } ?: 9833.toShort()
    fun miningSpeed(held: Short?, target: BlockDef?): Float? {
        val stage = pickStage(held) ?: return null
        if (target == null || target.creativeTab !in setOf("ores", "stone")) return 1f
        val ore = variant(target.id)?.takeIf { it.form == Form.ORE }
        return if (ore == null) 5f + minOf(stage, 12) * .3f
        else target.hardness / when { stage < ore.stage -> 3f; stage == ore.stage -> 1.7f; else -> .8f }
    }

    fun definitions(existing: Map<Short, BlockDef>): List<BlockDef> {
        val resource = existing.getValue(3101)
        val ore = existing.getValue(3001)
        val result = variants.map { v ->
            val old = existing[v.id]
            val texture = MineralArt.textureKey(P.metal(v.stage), v.form, if(v.form == Form.ORE) minOf(v.tier, 3) else 1)
            val rawName = existing[id(v.stage, Form.RAW)]?.name ?: "mineral_${v.stage}_${Form.RAW.name.lowercase()}"
            (if (v.form == Form.ORE) ore else resource).copy(
                id=v.id, name=old?.name ?: "mineral_${v.stage}_${v.form.name.lowercase()}",
                textureTop=texture, textureSide=texture, textureBottom=texture,
                color=0xFF000000.toInt() or v.metal.color,
                lightEmission=if(v.form == Form.ORE) 3 else 0,
                hardness=if(v.form == Form.ORE) 5f else 1f,
                placeable=v.form == Form.ORE,
                creativeTab=if(v.form == Form.ORE) "ores" else "resources",
                drop=if(v.form == Form.ORE) rawName else "", tags=if(v.form == Form.ORE) setOf("ore") else emptySet()
            )
        }.toMutableList()
        for (tier in 2..P.TIERS) for ((id, form) in listOf(steel(tier) to Form.INGOT, moltenSteel(tier) to Form.MOLTEN)) {
            val texture = MineralArt.textureKey(9, form, 1)
            result += resource.copy(id=id,name="steel_${tier}_${form.name.lowercase()}",drop="",textureTop=texture,textureSide=texture,textureBottom=texture)
        }
        for(tier in 1..P.TIERS) {
            val texture = MineralArt.textureKey(9, Form.ARMOR_PART, 1)
            result += resource.copy(id=reinforcement(tier),name="forge_reinforcement_$tier",drop="",textureTop=texture,textureSide=texture,textureBottom=texture)
        }
        val copperTexture=MineralArt.textureKey(10,Form.ORE,1)
        result += existing.getValue(3004).copy(textureTop=copperTexture,textureSide=copperTexture,textureBottom=copperTexture,
            lightEmission=3,color=0xFFE09650.toInt())
        return result
    }
}
