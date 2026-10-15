package com.Atom2Universe.app.games.hamlet

import kotlin.random.Random

/** Only land is parked. The purse, quests, heroes and expedition always belong to the same player. */
class HamletRealms(private val game: HamletGame) {
    enum class Template { STARTER, EMPTY, SHOWCASE }
    data class Requirement(val level: Int, val cost: Long)
    data class Info(val no: Int, val name: String, val hall: Int, val size: Int, val objects: Int, val current: Boolean)
    internal data class Land(val no: Int, var name: String, val objects: LinkedHashMap<Int, Obj>, val paths: IntArray, val hall: Int, val expand: Int)

    var current = 1
        private set
    private var currentName = ""
    private val parked = linkedMapOf<Int, Land>()
    val count get() = parked.size + 1

    internal fun allObjects(): List<Obj> = game.objs.values.toList() + parked.values.flatMap { it.objects.values }
    fun bestHall() = maxOf(game.hall, parked.values.maxOfOrNull { it.hall } ?: 1)

    fun reset() { current = 1; currentName = ""; parked.clear() }
    fun name(no: Int = current): String = if (no == current) currentName else parked[no]?.name.orEmpty()
    fun entries(): List<Info> = (parked.values.map { Info(it.no, it.name, it.hall, Hamlet.realmSize(it.hall, it.expand), it.objects.size, false) } +
        Info(current, currentName, game.hall, game.realmSize(), game.objs.size, true)).sortedBy { it.no }

    fun requirement(no: Int = count + 1): Requirement = when (no) {
        1 -> Requirement(1, 0)
        2 -> Requirement(15, 50_000)
        3 -> Requirement(20, 150_000)
        4 -> Requirement(25, 400_000)
        5 -> Requirement(30, 1_000_000)
        // More islands stay available after the original five, including unlimited free creative islands.
        else -> Requirement((30L + (no - 5L) * 5).coerceAtMost(Hamlet.MAX_LEVEL.toLong()).toInt(),
            (1_000_000.0 * Math.pow(1.6, (no - 5).toDouble())).coerceAtMost(Long.MAX_VALUE.toDouble() / 2).toLong())
    }

    fun rename(no: Int, value: String): Boolean {
        val clean = value.trim().filterNot { it.isISOControl() }.take(40)
        if (no == current) currentName = clean else (parked[no] ?: return false).name = clean
        game.listener?.onChanged()
        return true
    }

    fun canFound(): Boolean {
        if (count == Int.MAX_VALUE || game.nextId > Int.MAX_VALUE - 256) return false
        val r = requirement()
        return game.sandbox || game.level() >= r.level && game.coins >= r.cost
    }

    fun found(template: Template = Template.STARTER): Boolean {
        if (!canFound() || !game.sandbox && template != Template.STARTER) return false
        game.adventure.advanceProduction(true)
        val requirement = requirement()
        val no = count + 1
        val fresh = HamletGame(game::now, Random(no)).apply {
            when (template) {
                Template.STARTER -> startNew(false)
                Template.SHOWCASE -> fillDemo(Random(no))
                Template.EMPTY -> {
                    startNew(true)
                    val hallObject = objs.values.first { it.t == "hall" }
                    objs.clear(); paths.fill(0); occ.fill(0)
                    hallObject.x = (Hamlet.GRID - hallObject.w) / 2; hallObject.y = (Hamlet.GRID - hallObject.d) / 2
                    objs[hallObject.id] = hallObject
                    expand = 19
                }
            }
        }
        val freshObjects = linkedMapOf<Int, Obj>()
        for (o in fresh.objs.values) clone(o, game.nextId++).also { freshObjects[it.id] = it }
        parked[current] = capture()
        if (!game.sandbox) game.coins -= requirement.cost
        activate(Land(no, "", freshObjects, fresh.paths.copyOf(), fresh.hall, fresh.expand))
        game.stat("realms")
        game.chronicle.record(HamletChronicle.Kind.REALM, subject = no.toString(), amount = no.toLong())
        if (!game.sandbox) game.addXp((requirement.cost / 25).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        game.listener?.onChanged()
        return true
    }

    fun switchTo(no: Int): Boolean {
        if (no == current) return true
        val land = parked[no] ?: return false
        game.adventure.advanceProduction(true)
        parked[current] = capture()
        parked.remove(no)
        activate(land)
        game.listener?.onChanged()
        return true
    }

    private fun activate(land: Land) {
        game.buildHistory.clear()
        current = land.no; currentName = land.name
        game.objs.clear(); game.objs.putAll(land.objects)
        land.paths.copyInto(game.paths)
        game.occ.fill(0)
        for (o in game.objs.values) for (y in o.y until o.y + o.d) for (x in o.x until o.x + o.w) game.occ[game.idx(x, y)] = o.id
        game.hall = land.hall; game.expand = land.expand
        game.bump()
    }

    internal fun capture(): Land = Land(current, currentName, LinkedHashMap(game.objs), game.paths.copyOf(), game.hall, game.expand)
    internal fun stored(): List<Land> = parked.values.sortedBy { it.no }
    internal fun restore(no: Int, name: String, lands: List<Land>) {
        current = no; currentName = name.trim().filterNot { it.isISOControl() }.take(40)
        parked.clear()
        lands.forEach { parked[it.no] = it.copy(name = it.name.trim().filterNot { c -> c.isISOControl() }.take(40)) }
    }
    internal fun copyFrom(other: HamletRealms) = restore(other.current, other.currentName, other.stored().map { land ->
        land.copy(objects = LinkedHashMap<Int, Obj>().apply { land.objects.values.forEach { put(it.id, clone(it)) } }, paths = land.paths.copyOf())
    })

    companion object {
        internal fun clone(o: Obj, id: Int = o.id) = Obj(id, o.t, o.x, o.y, o.flip).also {
            it.crop = o.crop; it.t0 = o.t0; it.b0 = o.b0; it.next = o.next
            it.order = o.order?.let { order -> Order(order.from, order.t0, order.recipeRank) }
            it.tier = o.tier; it.tended = o.tended
        }
    }
}
