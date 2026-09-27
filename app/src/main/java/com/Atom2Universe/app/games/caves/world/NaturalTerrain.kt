package com.Atom2Universe.app.games.caves.world

import android.content.res.AssetManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import com.Atom2Universe.app.games.caves.node.MineralItems as M

internal data class NaturalBiomeProfile(val id: String, val base: Double, val amplitude: Double,
    val temperature: Double, val humidity: Double, val rarity: Double)

internal object NaturalTerrainSettings {
    var profiles: List<NaturalBiomeProfile> = emptyList()
        private set
    fun load(assets: AssetManager) {
        if (profiles.isNotEmpty()) return
        val root = JSONObject(assets.open("caves/natural_generation.json").bufferedReader().use { it.readText() })
        val a = root.getJSONArray("biomes")
        profiles = (0 until a.length()).map { i -> a.getJSONObject(i).let {
            NaturalBiomeProfile(it.getString("id"), it.getDouble("base"), it.getDouble("amplitude"),
                it.getDouble("temperature"), it.getDouble("humidity"), it.optDouble("rarity", 0.0))
        } }
        require(profiles.isNotEmpty() && profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.all { it.amplitude > 0 && it.temperature in 0.0..1.0 && it.humidity in 0.0..1.0 })
    }
}

/** A single surface with branching, world-coordinate cave networks. */
internal class NaturalTerrain(private val seed: Long, profiles: List<NaturalBiomeProfile> = NaturalTerrainSettings.profiles) {
    private val profiles = RegionalBiomes.profiles(profiles)
    companion object {
        const val SEA_LEVEL = 74; const val SURFACE_MAX_CY = 255
        private const val LAKE_CELL = 384.0; private const val POND_CELL = 112.0
        private const val LAKE_WOBBLE = .22; private const val LAKE_BERM = 2.0; private const val LAKE_FADE = 6.0
    }
    private val offset = ((seed xor (seed ushr 32)) and 0xFFFFFF).toDouble() * .013
    private data class Relief(val base: Double, val amplitude: Double)
    private val relief = ConcurrentHashMap<Long, Relief>()
    private val underground by lazy { UndergroundDecor(seed, this) }
    private fun n(x: Double, z: Double, scale: Double, salt: Double) =
        SimplexNoise.noise(x * scale + offset + salt, z * scale - offset)
    private fun smooth(v: Double) = v.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun temperature(x: Double, z: Double): Double {
        val px = x + n(x, z, .0007, 140.0) * 160
        val pz = z + n(x, z, .0007, 240.0) * 160
        return (.5 + n(px, pz, .00023, 410.0) * .85).coerceIn(0.0, 1.0)
    }
    fun humidity(x: Double, z: Double) = (.5 + n(x, z, .0008, 870.0) * .75).coerceIn(0.0, 1.0)

    /** Climate eligibility precedes local variety: a noise peak cannot put snow in the tropics. */
    private fun weights(x: Double, z: Double, out: DoubleArray): Int {
        val t = temperature(x, z); val h = humidity(x, z)
        val continent = n(x, z, .00045, 181.0)
        var best = -Double.MAX_VALUE; var dominant = 0
        for (i in profiles.indices) {
            val p = profiles[i]
            val ocean = p.id == "ocean" || p.id == "iceberg"
            val cold = p.temperature <= .30
            val hot = p.temperature >= .75
            val snowy = p.id == "tundra" || p.id == "snowy_taiga" || p.id == "frost_plains" || p.id == "iceberg"
            val allowed = (!cold || t < .39) && (!hot || t > .63) && (!snowy || t < .25)
            val special = when (p.id) {
                "ocean", "iceberg" -> (-continent - .30) * 3.2
                "mountains", "rocky", "volcanic" -> (n(x, z, .00085, 920.0) - .48) * .7
                else -> 0.0
            }
            val score = if (!allowed) -1e6 else
                -((t - p.temperature).pow(2) * 4.5 + (h - p.humidity).pow(2) * 2.8) +
                n(x, z, .0011, 2100.0 + i * 137) * .13 + special - p.rarity -
                (if (!ocean) max(0.0, -continent - .30) * 3.2 else 0.0)
            out[i] = score
            if (score > best) { best = score; dominant = i }
        }
        var sum = 0.0
        for (i in out.indices) { out[i] = max(0.0, out[i] - best + .12).pow(2); sum += out[i] }
        for (i in out.indices) out[i] /= sum
        return dominant
    }
    fun biomeIdAt(x: Double, z: Double): String {
        val w = DoubleArray(profiles.size)
        return profiles[weights(x, z, w)].id
    }
    /** Analytic ground lookup for cross-chunk plant support, before neighbours are loaded. */
    fun groundAt(x: Int, y: Int, z: Int): Short {
        val h = height(x.toDouble(), z.toDouble()).toInt()
        val waterLevel = waterLevelAt(x.toDouble(), z.toDouble())
        if (y > h) return if (y == waterLevel && temperature(x.toDouble(), z.toDouble()) < .18) ICE else if (y <= waterLevel) WATER else AIR
        if (caveAt(x, y, z)) return if (isFlooded(x, y, z, h, waterLevel)) WATER else AIR
        if (y < h) return STONE
        val id = biomeIdAt(x.toDouble(), z.toDouble())
        val biome = BiomeRegistry.surfaceBiomes.first { it.id == id }
        return topBlock(biome, x.toDouble(), z.toDouble(), h, waterLevel)
    }
    private fun reliefAt(x: Int, z: Int): Relief = relief.getOrPut(columnCacheKey(x, z)) {
        if (relief.size > 8192) relief.clear()
        val w = DoubleArray(profiles.size); weights(x * 192.0, z * 192.0, w)
        Relief(profiles.indices.sumOf { w[it] * profiles[it].base }, profiles.indices.sumOf { w[it] * profiles[it].amplitude })
    }
    /** Hauteur de la rive : le relief plein, sans le creux d'un lac ou d'un étang. */
    private fun rimHeight(x: Double, z: Double): Double {
        val gx = floor(x / 192).toInt(); val gz = floor(z / 192).toInt()
        val tx = smooth(x / 192 - gx); val tz = smooth(z / 192 - gz)
        val a = reliefAt(gx, gz); val b = reliefAt(gx + 1, gz); val c = reliefAt(gx, gz + 1); val d = reliefAt(gx + 1, gz + 1)
        val base = mix(mix(a.base, b.base, tx), mix(c.base, d.base, tx), tz)
        val amplitude = mix(mix(a.amplitude, b.amplitude, tx), mix(c.amplitude, d.amplitude, tx), tz)
        val broad = .5 + n(x, z, .00065, 31.0) * .5
        val peak = smooth((n(x, z, .0009, 121.0) - .45) / .35)
        val ridge = (1.0 - abs(n(x, z, .0010, 51.0))).pow(2) * peak
        val hills = n(x, z, .0025, 62.0) * .035 + n(x, z, .009, 92.0) * .006
        // Most normal relief stays below sea + 50. Very rare massifs have a broad, smooth apron.
        val massif = smooth((n(x, z, .00031, 6110.0) - .70) / .22) *
            smooth((base + 4) / 18) * (90 + 150 * ridge)
        val land = SEA_LEVEL + base + amplitude * (broad * .65 + ridge * .28 + hills) + massif
        val river = abs(n(x, z, .0012, 712.0))
        val riverStrength = 1 - smooth((land - 110) / 180)
        // Une large vallée rejoint des berges à quelques blocs de l'eau avant de creuser
        // le lit. Le raccord s'élargit avec le dénivelé pour éviter les longues tranchées.
        // L'eau reste au niveau de la mer : relever son niveau colonne par colonne ferait
        // des marches et des débordements, comme pour les lacs.
        val gorge = smooth((n(x, z, .00045, 1712.0) - .62) / .20)
        val valleyWidth = .24 + smooth((land - SEA_LEVEL - 12) / 60) * .16
        val floodplain = (1 - smooth((river - .075) / valleyWidth)) * riverStrength * (1 - gorge)
        val banks = mix(land, min(land, SEA_LEVEL + 3.0), floodplain)
        // Les rares pics du bruit régional conservent les gorges, avec une transition douce.
        val channel = (1 - smooth((river - .012) / .08)) * riverStrength
        return mix(banks, min(land, SEA_LEVEL - 3.0), channel)
    }
    private fun localAmplitude(x: Double, z: Double): Double {
        val gx = floor(x / 192).toInt(); val gz = floor(z / 192).toInt()
        val tx = smooth(x / 192 - gx); val tz = smooth(z / 192 - gz)
        val a = reliefAt(gx, gz); val b = reliefAt(gx + 1, gz); val c = reliefAt(gx, gz + 1); val d = reliefAt(gx + 1, gz + 1)
        return mix(mix(a.amplitude, b.amplitude, tx), mix(c.amplitude, d.amplitude, tx), tz)
    }
    /*
     * Lacs et étangs.
     *
     * Une surface d'eau doit être PLATE : un seul niveau [Lake.level] pour tout le plan d'eau,
     * jamais un niveau recalculé colonne par colonne (sinon la surface fait des marches d'un bloc
     * et l'eau déborde sur la rive). Chaque lac vit donc dans sa propre case d'une grille
     * (grande grille pour les lacs, petite pour les étangs), avec un centre, un rayon et un niveau
     * tirés une fois pour toutes.
     *
     * Règle anti-débordement : partout où il y a de l'eau (sol < niveau), la colonne voisine hors
     * du lac a un sol au moins au niveau de l'eau. On choisit le niveau sous la rive la plus basse
     * mesurée autour du lac, et une petite digue [LAKE_BERM] rattrape les rares creux entre deux mesures.
     */
    private class Lake(val cx: Double, val cz: Double, val radius: Double, val level: Int,
        val depth: Double, val wobbleScale: Double, val salt: Double)
    private val noLake = Lake(0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0)
    private val lakes = ConcurrentHashMap<Long, Lake>()
    private val ponds = ConcurrentHashMap<Long, Lake>()

    private fun cellRandom(cx: Int, cz: Int, salt: Long): Double {
        var k = columnCacheKey(cx, cz) xor (seed * -7046029254386353131L) xor (salt * -4658895280553007687L)
        k = (k xor (k ushr 33)) * -49064778989728563L
        k = (k xor (k ushr 29)) * -4265267296055464877L
        k = k xor (k ushr 32)
        return (k ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    /** Rayon réel du lac dans la direction de (x, z) : un cercle déformé par un bruit doux. */
    private fun lakeRadius(l: Lake, x: Double, z: Double) =
        l.radius * (1 + n(x, z, l.wobbleScale, l.salt) * LAKE_WOBBLE)
    private fun lakeReach(l: Lake) = l.radius * (1 + LAKE_WOBBLE) + LAKE_BERM + LAKE_FADE

    /** Tire le lac d'une case, ou [noLake]. [big] = grande grille des lacs, sinon étangs. */
    private fun buildLake(cellX: Int, cellZ: Int, big: Boolean): Lake {
        val size = if (big) LAKE_CELL else POND_CELL
        val salt = if (big) 11L else 23L
        if (cellRandom(cellX, cellZ, salt) > (if (big) .40 else .30)) return noLake
        val radius = if (big) 22 + cellRandom(cellX, cellZ, salt + 1) * 26 else 4 + cellRandom(cellX, cellZ, salt + 1) * 6
        // Le lac entier, digue et raccord compris, reste dans sa case : une colonne n'a jamais
        // besoin de consulter les cases voisines.
        val slack = size / 2 - (radius * (1 + LAKE_WOBBLE) + LAKE_BERM + LAKE_FADE + 2)
        if (slack <= 0) return noLake
        val cx = (cellX + .5) * size + (cellRandom(cellX, cellZ, salt + 2) * 2 - 1) * slack
        val cz = (cellZ + .5) * size + (cellRandom(cellX, cellZ, salt + 3) * 2 - 1) * slack
        if (localAmplitude(cx, cz) > 90) return noLake
        // Mesure la rive tout autour, de l'intérieur du lac jusqu'au bout du raccord.
        var low = Double.MAX_VALUE; var high = -Double.MAX_VALUE
        for (ring in doubleArrayOf(.75, 1.0, 1.25, 1.25 + (LAKE_BERM + LAKE_FADE) / radius)) for (i in 0 until 16) {
            val a = i * PI / 8
            val r = rimHeight(cx + cos(a) * radius * ring, cz + sin(a) * radius * ring)
            low = min(low, r); high = max(high, r)
        }
        val level = floor(low).toInt() - 1
        // Jamais au ras de la mer (le lac toucherait l'océan ou une rivière), ni sur une pente
        // trop raide (il faudrait un barrage visible).
        if (level < SEA_LEVEL + 2 || high - low > (if (big) 14.0 else 5.0)) return noLake
        val depth = if (big) 6 + cellRandom(cellX, cellZ, salt + 4) * 10 else 2 + cellRandom(cellX, cellZ, salt + 4) * 2
        return Lake(cx, cz, radius, level, depth, if (big) .02 else .06, 5300.0 + salt * 97)
    }

    private fun lakeIn(cellX: Int, cellZ: Int, big: Boolean): Lake {
        val map = if (big) lakes else ponds
        map[columnCacheKey(cellX, cellZ)]?.let { return it }
        if (map.size > 4096) map.clear()
        val built = buildLake(cellX, cellZ, big)
        // Un étang ne touche jamais un grand lac : deux niveaux d'eau voisins déborderaient l'un dans l'autre.
        val lake = if (!big && built !== noLake && nearBigLake(built)) noLake else built
        map[columnCacheKey(cellX, cellZ)] = lake
        return lake
    }

    private fun nearBigLake(pond: Lake): Boolean {
        val lx = floor(pond.cx / LAKE_CELL).toInt(); val lz = floor(pond.cz / LAKE_CELL).toInt()
        for (dz in -1..1) for (dx in -1..1) {
            val l = lakeIn(lx + dx, lz + dz, true)
            if (l !== noLake && hypot(pond.cx - l.cx, pond.cz - l.cz) < lakeReach(l) + lakeReach(pond) + 2) return true
        }
        return false
    }

    /** Le lac qui influence la colonne (x, z), ou [noLake]. */
    private fun lakeAt(x: Double, z: Double): Lake {
        val big = lakeIn(floor(x / LAKE_CELL).toInt(), floor(z / LAKE_CELL).toInt(), true)
        if (big !== noLake && hypot(x - big.cx, z - big.cz) < lakeReach(big)) return big
        return lakeIn(floor(x / POND_CELL).toInt(), floor(z / POND_CELL).toInt(), false)
    }

    /** Distance au bord de l'eau : négative dans le lac, positive sur la rive. */
    private fun lakeEdge(l: Lake, x: Double, z: Double) = hypot(x - l.cx, z - l.cz) - lakeRadius(l, x, z)

    fun height(x: Double, z: Double): Double {
        val rim = rimHeight(x, z)
        val lake = lakeAt(x, z)
        if (lake === noLake) return rim.coerceAtLeast(8.0)
        val edge = lakeEdge(lake, x, z)
        val level = lake.level.toDouble()
        val ground = if (edge >= 0) {
            // Rive : jamais plus basse que l'eau sur la digue, puis on rejoint le relief naturel.
            max(rim, mix(level, rim, smooth((edge - LAKE_BERM) / LAKE_FADE)))
        } else {
            // Dans le lac : la berge descend vers un fond creusé sous le niveau de l'eau.
            val radius = lakeRadius(lake, x, z)
            val shore = smooth(-edge / max(2.0, radius * .35))
            val bottom = level - 1 - lake.depth * smooth(-edge / (radius * .7))
            min(rim, mix(max(rim, level), bottom, shore))
        }
        return ground.coerceAtLeast(8.0)
    }

    /** SEA_LEVEL partout, sauf dans un lac ou un étang où c'est son niveau unique. */
    fun waterLevelAt(x: Double, z: Double): Int {
        val lake = lakeAt(x, z)
        return if (lake !== noLake && lakeEdge(lake, x, z) < 0) lake.level else SEA_LEVEL
    }
    /**
     * Poches d'eau souterraines : uniquement à l'intérieur d'une galerie déjà creusée, bien en
     * dessous du niveau d'eau de surface, dans des zones d'aquifère rares. Comme pour les lacs,
     * deux échelles de bruit donnent de grandes et de petites poches ; jamais un unique bloc isolé
     * puisque le remplissage suit la géométrie continue du réseau de grottes existant.
     */
    private fun aquiferWetness(x: Double, z: Double): Double {
        val big = smooth((n(x, z, .0016, 8100.0) - .42) / .09)
        val small = smooth((n(x, z, .009, 9200.0) - .46) / .08)
        return max(big, small)
    }
    private fun aquiferTable(x: Double, z: Double, h: Int): Double =
        h - 22.0 - (.5 + n(x, z, .005, 10300.0) * .5) * 55.0
    fun isFlooded(x: Int, y: Int, z: Int, h: Int, waterLevel: Int): Boolean {
        if (h <= waterLevel + 6) return false
        if (y >= aquiferTable(x.toDouble(), z.toDouble(), h)) return false
        return aquiferWetness(x.toDouble(), z.toDouble()) > 0.0
    }
    fun topBlock(b: SurfaceBiomeDef, x: Double, z: Double, h: Int, waterLevel: Int = SEA_LEVEL): Short {
        val patch = n(x, z, .035, 334.0)
        val wet = humidity(x, z)
        run {
            val t = temperature(x, z)
            if (t < .25 && b.id in setOf("tundra", "snowy_taiga", "frost_plains", "iceberg"))
                return if (h < waterLevel - 2) GRAVEL else if (patch > .2) SNOW else DIRT_SNOW
            if (h > SEA_LEVEL + 105 && t < .36) return if (patch > -.2) SNOW else STONE
            if (h > SEA_LEVEL + 65) return if (patch > .3) GRAVEL else STONE
            RegionalBiomes.variant(b.id)?.let { v ->
                if (h > waterLevel + 2) return when (b.id) {
                    "sandstone_badlands" -> if (patch > .25) 2308.toShort() else REDSAND // natural terracotta
                    "willow_marsh", "tropical_marsh" -> if (patch > .25) MOSS else MUD
                    else -> v.ground
                }
            }
        }
        if (h <= waterLevel + 2) {
            if (patch > .22 && h >= waterLevel - 16) return CLAY
            if (wet > .65 && h >= waterLevel - 2) return if (patch > -.15) MUD else CLAY
            return if (b.id == "red_desert") REDSAND else if (patch < -.45) GRAVEL else SAND
        }
        val snowLine = 420 + temperature(x, z) * 700
        if (h > snowLine || b.id == "tundra" || b.id == "iceberg") return if (patch > .2) SNOW else DIRT_SNOW
        return when (b.id) {
            "volcanic" -> BASALT
            "mountains", "rocky" -> if (patch > .36) GRAVEL else if (patch < -.40) GRANITE else STONE
            "desert" -> if (patch > .40) SANDSTONE else SAND
            "red_desert" -> if (patch > .40) 2308 else REDSAND
            "wetlands" -> if (patch > .20) MOSS else MUD
            else -> if (b.treeType != "none" && patch > -.12) { if (patch > .48) MOSS else FOREST_FLOOR } else GRASS
        }
    }

    /** V6: gently sloping galleries, occasional vaulted halls and sparse connecting shafts. */
    fun caveField(x: Double, y: Double, z: Double): Double {
        val depth = height(x, z) - y
        if (depth < -8) return -10.0
        fun noise(px: Double, py: Double, pz: Double, salt: Double) =
            SimplexNoise.noise(px + offset + salt, py, pz)
        val px = x + noise(x * .006, y * .004, z * .006, 131.0) * 20
        val pz = z + noise(x * .006, y * .004, z * .006, 317.0) * 20
        val region = noise(x * .0025, y * .003, z * .0025, 711.0)
        val deep = smooth((depth - 35) / 100)
        val spacious = smooth((region - .08) / .55) * deep
        // Two passage families share the same floor field, meeting at walkable junctions.
        val level = noise(px * .0045, y * .023, pz * .0045, 510.0)
        val main = noise(px * .014, y * .005, pz * .014, 950.0)
        val branch = noise(px * .024, y * .006, pz * .024, 1350.0)
        val gallery = min((.20 + spacious * .07 - abs(main)) * 36, (.23 - abs(level)) * 30)
        val branches = min((.15 - abs(branch)) * 32, (.20 - abs(level)) * 30)
        val chamber = noise(px * .008, y * .012, pz * .008, 180.0)
        // Vaulted rooms are concentrated in deep regions, with rock pillars left inside the largest ones.
        val room = min((chamber - mix(.70, .42, spacious)) * 40, (.43 - abs(level)) * 32)
        val pillar = max(abs(noise(x * .035, 0.0, z * .035, 2410.0)),
            abs(noise(x * .035, 0.0, z * .035, 2910.0)))
        val halls = min(room, (pillar - .075) * 45)
        fun join(a: Double, b: Double, radius: Double): Double {
            val t = max(radius - abs(a - b), 0.0) / radius
            return max(a, b) + t * t * radius * .25
        }
        val shaftA = noise(x * .013, y * .0015, z * .013, 3800.0)
        val shaftB = noise(x * .013, y * .0015, z * .013, 4800.0)
        val shafts = min((.105 - max(abs(shaftA), abs(shaftB))) * 34,
            (region - .44) * 24) - (1 - deep) * 8
        val network = join(join(gallery, branches, 1.2), max(halls, shafts), 2.0)
        // Selected mouths widen into the first gallery; elsewhere keep a substantial soil roof.
        val entrance = smooth((n(x, z, .006, 177.0) - .40) / .20)
        val cover = (1 - smooth(depth / 24)) * (1 - entrance) * 14
        return network - cover
    }
    fun caveAt(x: Int, y: Int, z: Int): Boolean {
        val h = height(x.toDouble(), z.toDouble()).toInt()
        val water = waterLevelAt(x.toDouble(), z.toDouble())
        if (y > h || (h <= water && h - y < 8)) return false
        val gx = Math.floorDiv(x, 4) * 4; val gy = Math.floorDiv(y, 4) * 4; val gz = Math.floorDiv(z, 4) * 4
        val tx = Math.floorMod(x, 4) / 4.0; val ty = Math.floorMod(y, 4) / 4.0; val tz = Math.floorMod(z, 4) / 4.0
        fun plane(dz: Int): Double {
            fun f(dx: Int, dy: Int) = caveField((gx + dx).toDouble(), (gy + dy).toDouble(), (gz + dz).toDouble())
            return mix(mix(f(0, 0), f(4, 0), tx), mix(f(0, 4), f(4, 4), tx), ty)
        }
        return mix(plane(0), plane(4), tz) > 0
    }

    private fun hash(x: Int, y: Int, z: Int): Long {
        var v = seed xor (x.toLong() * 341873128712L) xor (y.toLong() * 42317861L) xor (z.toLong() * 132897987541L)
        v = (v xor (v ushr 30)) * -4658895280553007687L
        return (v xor (v ushr 27)) and Long.MAX_VALUE
    }
    fun rockAt(x: Int, y: Int, z: Int, depth: Int): Short {
        val cell = hash(Math.floorDiv(x, 12), Math.floorDiv(y, 12), Math.floorDiv(z, 12))
        val dx = Math.floorMod(x, 12) - (3 + (cell % 6).toInt())
        val dy = Math.floorMod(y, 12) - (3 + (cell / 7 % 6).toInt())
        val dz = Math.floorMod(z, 12) - (3 + (cell / 43 % 6).toInt())
        if (depth > 2 && cell % 5 < 3 && dx * dx + dy * dy + dz * dz <= 3) {
            val category = (cell / 251 % 100).toInt()
            val rare = category >= 98 && depth >= 40
            // Previously radius²=7 produced 81 blocks. Preserve discovery frequency while
            // reducing the quantity: metals 7/19, coal 19/27, rare stones 1–3 connected blocks.
            val radiusSquared = (if (category < 30) 2 else 1) + (cell / 7919 % 2).toInt()
            val inVein = if (rare) {
                val length = 1 + (cell / 7919 % 3).toInt()
                when ((cell / 1543 % 3).toInt()) {
                    0 -> dy == 0 && dz == 0 && dx in -1 until length-1
                    1 -> dx == 0 && dz == 0 && dy in -1 until length-1
                    else -> dx == 0 && dy == 0 && dz in -1 until length-1
                }
            } else dx * dx + dy * dy + dz * dz <= radiusSquared
            if (inVein) {
                val centerY = Math.floorDiv(y,12)*12 + 3 + (cell / 7 % 6).toInt()
                val centerStage = MineralProgression.stage(centerY.toDouble())
                val localStage = MineralProgression.stage(y.toDouble())
                if (category < 30) return COAL
                if (category < 50) return COPPER
                // Metals stay within their cycle; independent resources never dilute with depth.
                if (MineralProgression.tier(centerStage) == MineralProgression.tier(localStage)) {
                    val metal = if(category < 72) 0 else MineralProgression.localMetal(centerStage,(cell / 1031 % 100).toInt())
                    val stage = (MineralProgression.tier(centerStage)-1)*MineralProgression.METALS + metal
                    if (stage <= localStage && (metal==0 || localStage-stage<=2)) {
                        if(category < 98 || depth < 40) return M.id(stage,M.Form.ORE)
                        // Diamond joins the rare-stone budget from the cobalt layer down;
                        // it never replaces a progression metal or enlarges a vein.
                        if(centerY <= -750 && y <= -750 && cell / 8123 % 8 == 0L) return M.DIAMOND_ORE
                        return when ((cell / 607 % 4).toInt()) { 0 -> REDSTONE; 1 -> RUBY; 2 -> EMERALD; else -> CRYSTAL }
                    }
                }
            }
        }
        val geology = SimplexNoise.noise(x * .009 + offset + 390, y * .013, z * .009)
        return when { geology > .44 -> BASALT; geology < -.45 -> 2201; geology > .23 -> GRANITE; geology < -.28 -> QUARTZ; else -> STONE }
    }

    // Données immuables après publication, partagées entre les étages d'une colonne.
    // LRU borné (~6 Mio), sans vider brutalement tout le cache pendant l'exploration.
    private class SurfaceColumn {
        val heights = IntArray(256)
        val tops = ShortArray(256)
        val indices = IntArray(256)
        val waterLevels = IntArray(256)
        val frozen = BooleanArray(256)
        val floodBelow = DoubleArray(256) { Double.NEGATIVE_INFINITY }
    }
    private val surfaceColumns = LinkedHashMap<Long, SurfaceColumn>(128, .75f, true)

    private fun surfaceColumn(cx: Int, cz: Int): SurfaceColumn {
        val key = columnCacheKey(cx, cz)
        synchronized(surfaceColumns) { surfaceColumns[key]?.let { return it } }
        // Les deux générateurs peuvent calculer des colonnes différentes en parallèle.
        val result = SurfaceColumn()
        val biomes = BiomeRegistry.surfaceBiomes
        for (z in 0..15) for (x in 0..15) {
            val wx = (cx * 16 + x).toDouble(); val wz = (cz * 16 + z).toDouble(); val i = z * 16 + x
            val h = height(wx, wz).toInt(); result.heights[i] = h
            val water = waterLevelAt(wx, wz); result.waterLevels[i] = water
            val id = biomeIdAt(wx.toDouble(), wz.toDouble())
            result.indices[i] = biomes.indexOfFirst { it.id == id }.coerceAtLeast(0)
            result.tops[i] = topBlock(biomes[result.indices[i]], wx, wz, h, water)
            result.frozen[i] = temperature(wx, wz) < .18
            if (h > water + 6 && aquiferWetness(wx, wz) > 0.0)
                result.floodBelow[i] = aquiferTable(wx, wz, h)
        }
        synchronized(surfaceColumns) {
            surfaceColumns[key]?.let { return it }
            surfaceColumns[key] = result
            if (surfaceColumns.size > 1024) {
                val oldest = surfaceColumns.entries.iterator()
                oldest.next(); oldest.remove()
            }
        }
        return result
    }

    @JvmOverloads
    fun generate(chunk: Chunk, landscape: CozyLandscape, decorateUnderground: Boolean = true) {
        val surface = surfaceColumn(chunk.cx, chunk.cz)
        val heights = surface.heights; val tops = surface.tops; val indices = surface.indices
        val waterLevels = surface.waterLevels
        if (chunk.worldY > max(SEA_LEVEL, heights.max()) + TreeShape.HEIGHT) return
        // Shared 4-block lattice: interpolation remains identical across chunk boundaries.
        val field = DoubleArray(125)
        for (z in 0..4) for (y in 0..4) for (x in 0..4)
            field[x + 5 * (y + 5 * z)] = caveField((chunk.worldX + x * 4).toDouble(), (chunk.worldY + y * 4).toDouble(), (chunk.worldZ + z * 4).toDouble())
        fun density(x: Int, y: Int, z: Int): Double {
            val gx = x / 4; val gy = y / 4; val gz = z / 4
            val tx = x % 4 / 4.0; val ty = y % 4 / 4.0; val tz = z % 4 / 4.0
            fun plane(dz: Int): Double {
                val i = gx + 5 * (gy + 5 * (gz + dz))
                return mix(mix(field[i], field[i + 1], tx), mix(field[i + 5], field[i + 6], tx), ty)
            }
            return mix(plane(0), plane(1), tz)
        }
        for (z in 0..15) for (x in 0..15) for (y in 0..15) {
            val wx = chunk.worldX + x; val wy = chunk.worldY + y; val wz = chunk.worldZ + z
            val i = z * 16 + x
            val h = heights[i]; val depth = h - wy; val top = tops[i]; val waterLevel = waterLevels[i]
            val block = when {
                wy > h -> if (wy == waterLevel && surface.frozen[i]) ICE else if (wy <= waterLevel) WATER else AIR
                // Keep a seabed/lakebed so a surface water body cannot flood entire cave networks on load.
                density(x, y, z) > 0 && !(h <= waterLevel && depth < 8) ->
                    if (wy < surface.floodBelow[i]) WATER else AIR
                depth == 0 -> top
                depth < 4 && top in shortArrayOf(SAND, REDSAND, CLAY, MUD, SANDSTONE) -> if (top == SAND || top == REDSAND) SANDSTONE else top
                depth < 4 && top in shortArrayOf(GRASS, FOREST_FLOOR, MOSS, DIRT_SNOW) -> DIRT
                else -> rockAt(wx, wy, wz, depth)
            }
            chunk.setBlock(x, y, z, block)
        }
        if (decorateUnderground) underground.decorate(chunk, heights, field)
        landscape.decorate(chunk, heights, tops, indices)
    }
}
