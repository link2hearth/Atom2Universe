package com.Atom2Universe.app.games.caves.world

import kotlin.random.Random

/** Simulation-only tree lifecycle. Null reads mean unloaded, never empty space. */
internal class Saplings(
    private val seed: Long,
    private val read: (Int, Int, Int) -> Short?,
    private val meta: (Int, Int, Int) -> Byte,
    private val write: (Int, Int, Int, Short, Byte) -> Unit,
    private val light: (Int, Int, Int) -> Int,
    private val soil: (Short) -> Boolean,
) {
    data class Pos(val x: Int, val y: Int, val z: Int) {
        fun neighbors() = listOf(Pos(x-1,y,z), Pos(x+1,y,z), Pos(x,y-1,z),
            Pos(x,y+1,z), Pos(x,y,z-1), Pos(x,y,z+1))
    }
    data class Plant(val species: Int, val shapeSeed: Long, var growth: Long = 0L, var checked: Long = 0L)
    data class Claim(val owner: String, val order: Long)
    // A cut may reach a leaf across a currently unloaded chunk. Resume that exact path later.
    private data class Search(val claim: Claim, val frontier: MutableMap<Pos, Int>, val seen: MutableSet<Pos>)
    private val plants = linkedMapOf<Pos, Plant>()
    private val claims = hashMapOf<Pos, Claim>()
    private val searches = arrayListOf<Search>()
    private var clock = 0L
    private var tick = 0L
    private var serial = 0L
    private var cursor = 0

    @Synchronized fun planted(x: Int, y: Int, z: Int, id: Short) {
        val species = TreeSpecies.species(id) ?: return
        val shapeSeed = seed xor (x.toLong()*341873128712L) xor (y.toLong()*73428767L) xor
            (z.toLong()*132897987541L) xor clock
        plants[Pos(x,y,z)] = Plant(species, shapeSeed, checked = clock)
    }

    /** Every replacement invalidates old progress and ownership, including creative edits. */
    @Synchronized fun removed(x: Int, y: Int, z: Int) {
        val p = Pos(x,y,z)
        plants.remove(p)
        claims.remove(p)
        searches.forEach { it.frontier.remove(p) }
    }

    @Synchronized fun advance(ms: Long) {
        val elapsed = ms.coerceIn(0,1000)
        clock += elapsed; tick += elapsed
        if (tick < 1000) return
        tick %= 1000
        val positions = plants.keys.toList()
        if (positions.isEmpty()) { cursor = 0; return }
        repeat(minOf(128, positions.size)) {
            val p = positions[cursor % positions.size]; cursor = (cursor + 1) % positions.size
            val plant = plants[p] ?: return@repeat
            val dt = (clock - plant.checked).coerceIn(0,10_000)
            plant.checked = clock
            val current = read(p.x,p.y,p.z) ?: return@repeat
            val ground = read(p.x,p.y-1,p.z) ?: return@repeat
            if (TreeSpecies.species(current) != plant.species) { plants.remove(p); return@repeat }
            if (!soil(ground)) {
                plants.remove(p); write(p.x,p.y,p.z,AIR,0); return@repeat
            }
            if (light(p.x,p.y,p.z) < 8) return@repeat
            plant.growth = (plant.growth + dt).coerceAtMost(TreeSpecies.duration(plant.species))
            if (plant.growth >= TreeSpecies.duration(plant.species)) grow(p,plant)
        }
    }

    /** The plants of a 2x2 square of same-species saplings containing [p], or just [p]. */
    private fun squareOf(p: Pos, plant: Plant): List<Pos> {
        if (TreeSpecies.types[plant.species] !in GrandTrees.widenable) return listOf(p)
        for (ox in -1..0) for (oz in -1..0) {
            val cells = listOf(Pos(p.x+ox,p.y,p.z+oz), Pos(p.x+ox+1,p.y,p.z+oz),
                Pos(p.x+ox,p.y,p.z+oz+1), Pos(p.x+ox+1,p.y,p.z+oz+1))
            if (cells.all { plants[it]?.species == plant.species }) return cells
        }
        return listOf(p)
    }

    private fun grow(p: Pos, plant: Plant) {
        val group = squareOf(p, plant)
        val thick = group.size == 4
        // A square waits until all four saplings have matured, so the tree is planted as one.
        if (group.any { (plants[it]?.growth ?: 0L) < TreeSpecies.duration(plant.species) }) return
        val ax = group.minOf { it.x }; val az = group.minOf { it.z }
        val blocks = linkedMapOf<Pos, Short>()
        val type = TreeSpecies.types[plant.species]
        TreeShape.generate(type, Random(plant.shapeSeed), thick) { dx,dy,dz,id,onlyAir ->
            val q = Pos(ax+dx,p.y-1+dy,az+dz)
            if (!onlyAir || q !in blocks) blocks[q] = id
        }
        // Validate the entire recipe before writing anything. Never grow into unknown chunks,
        // other plants, constructions or a cliff underneath a wide trunk.
        for ((q,id) in blocks) {
            val current = read(q.x,q.y,q.z) ?: return
            if (q !in group && current != AIR) return
            if (q.y == p.y && isWood(id)) {
                val ground = read(q.x,q.y-1,q.z) ?: return
                if (!soil(ground)) return
            }
        }
        for (cell in group) { plants.remove(cell); claims.remove(cell) }
        for (cell in group) write(cell.x,cell.y,cell.z,AIR,0) // A baobab's hollow centre stays empty.
        for ((q,id) in blocks) write(q.x,q.y,q.z,id, if (isLeaf(id)) TreeSpecies.leafMeta(type) else 0)
    }

    /** Call just after removing the log, before any leaf decay. Each path is at most six faces. */
    @Synchronized fun cut(x: Int, y: Int, z: Int, owner: String) {
        require(owner.isNotEmpty() && owner.none { it == '\t' || it == '\n' || it == '\r' })
        val origin = Pos(x,y,z)
        val search = Search(Claim(owner, ++serial), origin.neighbors().associateWith { 1 }.toMutableMap(), hashSetOf(origin))
        searches.add(search)
        resume(search)
        searches.removeAll { it.frontier.isEmpty() }
    }

    private fun resume(search: Search) {
        val queue = java.util.ArrayDeque(search.frontier.entries.map { it.key to it.value })
        search.frontier.clear()
        while (queue.isNotEmpty()) {
            val (p,distance) = queue.removeFirst()
            if (p in search.seen) continue
            val id = read(p.x,p.y,p.z)
            if (id == null) { search.frontier[p] = minOf(search.frontier[p] ?: 6, distance); continue }
            search.seen.add(p)
            if (!isLeaf(id)) continue
            if (!TreeSpecies.persistent(meta(p.x,p.y,p.z)) && TreeSpecies.fromLeaf(meta(p.x,p.y,p.z)) != null) {
                val support = LeafSupport.state(p.x,p.y,p.z,read)
                if (support != true && (claims[p]?.order ?: -1) < search.claim.order) claims[p] = search.claim
            }
            if (distance < 6) for (q in p.neighbors()) queue.add(q to distance + 1)
        }
    }

    @Synchronized fun resumeCuts() {
        searches.forEach(::resume)
        searches.removeAll { it.frontier.isEmpty() }
    }

    /** Fully supported leaves no longer belong to an earlier cut. Unknown support is deferred. */
    @Synchronized fun supportedLeaf(x: Int,y: Int,z: Int) { claims.remove(Pos(x,y,z)) }
    @Synchronized fun decayOwner(x: Int,y: Int,z: Int): String? = claims.remove(Pos(x,y,z))?.owner

    fun eligible(owner: String?, x: Int,y: Int,z: Int, playerId: String,
                 px: Double,py: Double,pz: Double): Boolean = owner != null && owner == playerId &&
        (px-x-.5)*(px-x-.5)+(py-y-.5)*(py-y-.5)+(pz-z-.5)*(pz-z-.5) <= TreeSpecies.DROP_REACH*TreeSpecies.DROP_REACH

    /** Versioned text inside the world checkpoint; no wall clock and no old-world conversion. */
    @Synchronized fun snapshot(): String = buildString {
        append("1\t$clock\t$serial\t$tick\t$cursor\n")
        for ((p,v) in plants) append("P\t${p.x}\t${p.y}\t${p.z}\t${v.species}\t${v.shapeSeed}\t${v.growth}\t${v.checked}\n")
        for ((p,v) in claims) append("C\t${p.x}\t${p.y}\t${p.z}\t${v.owner}\t${v.order}\n")
        for ((i,s) in searches.withIndex()) {
            append("S\t$i\t${s.claim.owner}\t${s.claim.order}\n")
            for ((p,d) in s.frontier) append("F\t$i\t${p.x}\t${p.y}\t${p.z}\t$d\n")
            for (p in s.seen) append("V\t$i\t${p.x}\t${p.y}\t${p.z}\n")
        }
    }

    @Synchronized fun restore(text: String) {
        plants.clear(); claims.clear(); searches.clear(); clock=0; serial=0; tick=0; cursor=0
        if (text.isBlank()) return
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        val header = lines.next().split('\t')
        require(header[0] == "1") { "Unknown sapling save version" }
        clock = header[1].toLong(); serial = header[2].toLong()
        tick = header[3].toLong().coerceIn(0,999); cursor = header[4].toInt().coerceAtLeast(0)
        for (line in lines) {
            val a = line.split('\t')
            fun pos(offset: Int) = Pos(a[offset].toInt(),a[offset+1].toInt(),a[offset+2].toInt())
            when (a[0]) {
                "P" -> {
                    val species = a[4].toInt(); require(species in TreeSpecies.types.indices)
                    plants[pos(1)] = Plant(species,a[5].toLong(),a[6].toLong().coerceIn(0,TreeSpecies.duration(species)),a[7].toLong())
                }
                "C" -> claims[pos(1)] = Claim(a[4],a[5].toLong())
                "S" -> { require(a[1].toInt() == searches.size); searches.add(Search(Claim(a[2],a[3].toLong()),linkedMapOf(),hashSetOf())) }
                "F" -> searches[a[1].toInt()].frontier[pos(2)] = a[5].toInt().also { require(it in 1..6) }
                "V" -> searches[a[1].toInt()].seen.add(pos(2))
                else -> error("Unknown sapling save entry")
            }
        }
    }
}
