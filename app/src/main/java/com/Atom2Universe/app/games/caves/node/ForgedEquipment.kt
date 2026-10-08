package com.Atom2Universe.app.games.caves.node

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.world.MineralProgression as P
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

/** Templates are stable; every finished item gets its own persistent ID and immutable rolls. */
internal object ForgedEquipment {
    enum class Slot(val label: Int, val title: Int, val weight: Float, val plates: Int) {
        HELMET(R.string.cave_gear_helmet, R.string.cave_gear_slot_helmet, .20f, 2),
        CHEST(R.string.cave_gear_chest, R.string.cave_gear_slot_chest, .40f, 4),
        LEGS(R.string.cave_gear_legs, R.string.cave_gear_slot_legs, .25f, 3),
        BOOTS(R.string.cave_gear_boots, R.string.cave_gear_slot_boots, .15f, 2)
    }
    enum class Bonus(val key: String, val label: Int, val range: IntRange, val cap: Int) {
        SPEED("move_speed", R.string.cave_gear_speed, 4..8, 30),
        JUMP("jump_height", R.string.cave_gear_jump, 8..15, 50),
        ATTACK("attack_speed", R.string.cave_gear_attack, 4..8, 40),
        CRIT("crit_chance", R.string.cave_gear_crit, 2..5, 40),
        CRIT_DAMAGE("crit_dmg", R.string.cave_gear_crit_damage, 8..15, 100),
        DODGE("dodge", R.string.cave_gear_dodge, 2..4, 25)
    }
    data class Template(val stage: Int, val slot: Slot) {
        val id: Short get() = (4100 + stage * 4 + slot.ordinal).toShort()
    }
    data class Item(val base: Short, val damage: Int, val hp: Int, val defense: Int, val bonuses: Map<Bonus, Int>)
    val templates = (0..P.LAST_STAGE).flatMap { stage -> Slot.entries.map { Template(stage, it) } }
    private val byId = templates.associateBy { it.id }
    private val instances = java.util.concurrent.ConcurrentHashMap<Short, Item>()
    fun get(id: Short?) = if(id == null) null else instances[id]
    fun base(id: Short?) = get(id)?.base ?: id
    fun template(id: Short?) = byId[base(id)]
    fun isCraft(id: Short) = id in byId || id in ExpeditionItems.melee
    fun bounds(value: Float): IntRange = (value * .85f).roundToInt().coerceAtLeast(1)..(value * 1.15f).roundToInt().coerceAtLeast(1)
    fun hpRange(t: Template) = bounds((50f * P.scale(t.stage) - 25f) * t.slot.weight)
    fun defenseRange(t: Template) = bounds(25f * P.scale(t.stage) * t.slot.weight)
    fun damageRange(id: Short) = bounds((ExpeditionItems.melee[id]?.damage ?: 1).toFloat())
    fun pool(slot: Slot?) = when(slot) {
        Slot.HELMET -> listOf(Bonus.CRIT, Bonus.CRIT_DAMAGE, Bonus.ATTACK, Bonus.DODGE)
        Slot.CHEST -> listOf(Bonus.DODGE, Bonus.ATTACK, Bonus.CRIT_DAMAGE, Bonus.SPEED)
        Slot.LEGS -> listOf(Bonus.SPEED, Bonus.JUMP, Bonus.DODGE, Bonus.ATTACK)
        Slot.BOOTS -> listOf(Bonus.SPEED, Bonus.JUMP, Bonus.DODGE, Bonus.CRIT)
        null -> listOf(Bonus.ATTACK, Bonus.CRIT, Bonus.CRIT_DAMAGE, Bonus.DODGE)
    }
    fun roll(id: Short, rng: Random = Random.Default): Item {
        require(isCraft(id))
        val t=template(id)
        val bonuses=pool(t?.slot).shuffled(rng).take(2).associateWith { it.range.random(rng) }
        return if(t != null) Item(id, 0, hpRange(t).random(rng), defenseRange(t).random(rng), bonuses)
        else Item(id, damageRange(id).random(rng), 0, 0, bonuses)
    }
    @Synchronized fun allocate(item: Item): Short {
        for(i in 10002..32767) {
            val id=i.toShort()
            if(!instances.containsKey(id) && BlockRegistry.get(id)==null) {
                instances[id]=item; return id
            }
        }
        error("No equipment IDs available")
    }
    fun free(id: Short) { instances.remove(id) }
    fun clear() { instances.clear() }
    fun snapshot(): String = JSONObject().also { root -> instances.forEach { (id,item) ->
        root.put(id.toString(), JSONObject().put("base", item.base.toInt()).put("damage", item.damage)
            .put("hp", item.hp).put("defense", item.defense).put("bonuses", JSONObject().also { b ->
                item.bonuses.forEach { (bonus,value) -> b.put(bonus.key,value) }
            }))
    } }.toString()
    fun restore(json: String) {
        clear()
        val root=runCatching { JSONObject(json) }.getOrNull() ?: return
        root.keys().forEach { key -> runCatching {
            val id=key.toInt(); require(id in 10002..32767)
            val o=root.getJSONObject(key); val base=o.getInt("base").toShort(); require(isCraft(base))
            val b=o.optJSONObject("bonuses") ?: JSONObject()
            instances[id.toShort()]=Item(base,o.optInt("damage").coerceAtLeast(0),o.optInt("hp").coerceAtLeast(0),
                o.optInt("defense").coerceAtLeast(0),Bonus.entries.filter { b.has(it.key) }
                    .associateWith { b.optInt(it.key).coerceIn(0,it.cap) })
        } }
    }
    fun name(context: Context, id: Short): String? {
        template(id)?.let { return context.getString(it.slot.label,
            context.getString(MineralItems.metals[P.metal(it.stage)].label),P.tier(it.stage)) }
        return get(id)?.let { MineralItems.name(context,it.base) }
    }
    fun describe(context: Context, id: Short, includeHint: Boolean = true, preview: Boolean = false): String? {
        val item=get(id); val base=base(id) ?: return null
        if(!isCraft(base)) return null
        val t=template(base)
        if(item==null && t==null && !preview) return null // Existing fixed weapons keep their original values.
        fun stat(label: Int, value: Int?, range: IntRange) = if(value==null)
            context.getString(R.string.cave_gear_range, context.getString(label), range.first, range.last)
        else context.getString(R.string.cave_gear_roll, context.getString(label), value, range.first, range.last)
        return buildList {
            if(t!=null) {
                add(stat(R.string.cave_gear_hp,item?.hp,hpRange(t)))
                add(stat(R.string.cave_gear_defense,item?.defense,defenseRange(t)))
            } else add(stat(R.string.cave_gear_damage,item?.damage,damageRange(base)))
            if(item!=null) item.bonuses.forEach { (b,v) -> add(context.getString(R.string.cave_gear_bonus,context.getString(b.label),v)) }
            else {
                add(context.getString(R.string.cave_gear_random_hint))
                pool(t?.slot).forEach { b -> add(context.getString(R.string.cave_gear_bonus_range,
                    context.getString(b.label),b.range.first,b.range.last)) }
            }
            if(includeHint) add(context.getString(R.string.cave_gear_equip_hint))
        }.joinToString("\n")
    }
    fun definitions(resource: BlockDef) = templates.map { t ->
        val texture="armor:${P.metal(t.stage)}:${t.slot.ordinal}"
        resource.copy(id=t.id,name="armor_${t.stage}_${t.slot.name.lowercase()}",drop="",placeable=false,
            textureTop=texture,textureSide=texture,textureBottom=texture,creativeTab="resources")
    }
}
