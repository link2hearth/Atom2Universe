package com.Atom2Universe.app.games.motocross

import kotlin.math.*
import kotlin.random.Random

/** Mètres, Y vers le haut. Les mêmes segments servent au dessin et aux collisions. */
internal class MotocrossTrack(val seed: Int) {
    data class Point(val x: Float, val y: Float)
    data class Section(val start: Float, val end: Float, val kind: Kind, val difficulty: Int)
    enum class Kind { ROLLERS, TABLE, DOUBLE, VALLEY, STEPS, RIDGE, JUMP, REST, BRIDGE, LOOP, TRANSFER }
    /** État propre à la moto : une branche éloignée de la boucle est dans un autre plan. */
    class RouteState {
        val ignoredDecks = HashSet<Int>()
        val loopCursors = HashMap<Int, Int>()
        fun clear() { ignoredDecks.clear(); loopCursors.clear() }
        fun accepts(roadId: Int, segment: Int, loop: Boolean): Boolean = roadId !in ignoredDecks &&
            (loopCursors[roadId]?.let { abs(segment - it) <= 36 } ?: !loop)
    }
    private data class Module(val kind: Kind, val difficulty: Int, val knots: List<Point>, val launchXs: Set<Float>)

    val points = ArrayList<Point>()
    val sections = ArrayList<Section>()
    val checkpoints = ArrayList<Float>()
    val roads = ArrayList<MotocrossRoad>()
    private data class Rail(val a: Point, val b: Point, val roadId: Int, val segment: Int, val loop: Boolean) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length2 = dx * dx + dy * dy
        val nx = -dy / sqrt(length2)
        val ny = dx / sqrt(length2)
    }
    private val railBuckets = HashMap<Int, MutableList<Rail>>()
    val finishX: Float

    init {
        val random = Random(seed)
        points.add(Point(-20f, 0f))
        points.add(Point(0f, 0f))
        points.add(Point(12f, 0f))
        checkpoints.add(3f)
        var x = 12f
        val recent = ArrayList<Module>()
        val recentStructures = ArrayList<MotocrossStructures.Recipe>()
        repeat(36) { index ->
            val tier = when { index < 8 -> 0; index < 20 -> 1; else -> 2 }
            val candidates = when {
                index == 7 || index == 16 || index == 28 -> MotocrossStructures.transfers
                index % 9 == 5 -> MotocrossStructures.loops
                index % 3 == 1 -> MotocrossStructures.bridges
                else -> emptyList()
            }.filter { it.difficulty <= tier }
            // Garantir au moins un réseau à trois niveaux, puis un à quatre par circuit.
            val featured = when (index) {
                10 -> candidates.filter { it.levels == 3 }
                25 -> candidates.filter { it.levels == 4 }
                else -> emptyList()
            }
            val fresh = featured.ifEmpty { candidates.filter { it !in recentStructures }.ifEmpty { candidates } }
            val special = fresh.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
            if (special != null) {
                val end = x + special.ground.last().x
                val kind = when { special.transfer -> Kind.TRANSFER; special.loop -> Kind.LOOP; else -> Kind.BRIDGE }
                sections.add(Section(x, end, kind, special.difficulty))
                for (p in special.ground.drop(1)) points.add(Point(x + p.x, p.y))
                for ((id, deck) in special.decks.withIndex()) roads.add(MotocrossRoad(
                    deck.map { Point(x + it.x, it.y) }, special.loop || id in special.guidedDecks))
                recentStructures.add(special)
                if (recentStructures.size > 4) recentStructures.removeAt(0)
                checkpoints.add(end - 2f)
                x = end
                return@repeat
            }
            val module = if (index % 5 == 4) REST else {
                val pool = MODULES.filter { it.difficulty <= tier && it !in recent }
                val featuredKind = when (index) {
                    0 -> Kind.TABLE
                    3 -> Kind.VALLEY
                    12 -> Kind.STEPS
                    21 -> Kind.RIDGE
                    else -> if (index % 6 == 2) Kind.JUMP else null
                }
                val choices = pool.filter { it.kind == featuredKind }.ifEmpty { pool }
                choices[random.nextInt(choices.size)]
            }
            val end = x + module.knots.last().x
            sections.add(Section(x, end, module.kind, module.difficulty))
            // Raccords plats : aucune modification d'un obstacle lors de l'assemblage.
            val geometry = GEOMETRY.getValue(module)
            for (i in 1 until geometry.size) {
                val p = geometry[i]
                points.add(Point(x + p.x, p.y))
            }
            checkpoints.add(end - 2f)
            x = end
            recent.add(module)
            if (recent.size > 4) recent.removeAt(0)
        }
        finishX = x + 8f
        points.add(Point(finishX + 30f, 0f))
        for ((roadId, road) in roads.withIndex()) for ((segment, pair) in road.points.zipWithNext().withIndex()) {
            val (a, b) = pair
            val rail = Rail(a, b, roadId, segment, road.loop)
            require(rail.length2 > 0f)
            for (bucket in floor(min(a.x, b.x) / 8f).toInt()..floor(max(a.x, b.x) / 8f).toInt()) {
                railBuckets.getOrPut(bucket) { ArrayList() }.add(rail)
            }
        }
    }

    fun segmentAt(x: Float): Int {
        var lo = 0
        var hi = points.lastIndex - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (points[mid].x <= x) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun height(x: Float): Float {
        val i = segmentAt(x)
        val a = points[i]
        val b = points[i + 1]
        return a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x)).coerceIn(0f, 1f)
    }

    /** Point de contact le plus proche, normale sortante. Pas d'allocation par roue. */
    fun contact(x: Float, y: Float, mountX: Float, mountY: Float,
                upX: Float, upY: Float, route: RouteState, out: FloatArray) {
        var best = Float.POSITIVE_INFINITY
        var i = segmentAt(x - 1.2f)
        while (i < points.lastIndex && points[i].x <= x + 1.2f) {
            val a = points[i]
            val b = points[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length2 = dx * dx + dy * dy
            val t = (((x - a.x) * dx + (y - a.y) * dy) / length2).coerceIn(0f, 1f)
            val px = a.x + dx * t
            val py = a.y + dy * t
            val distance2 = (x - px).pow(2) + (y - py).pow(2)
            if (distance2 < best) {
                best = distance2
                val length = sqrt(length2)
                out[0] = px; out[1] = py
                out[2] = -dy / length; out[3] = dx / length
            }
            i++
        }
        for (bucket in floor((x - 1.2f) / 8f).toInt()..floor((x + 1.2f) / 8f).toInt()) {
            val rails = railBuckets[bucket] ?: continue
            for (rail in rails) {
                if (!route.accepts(rail.roadId, rail.segment, rail.loop)) continue
                if (rail.nx * upX + rail.ny * upY <= .2f) continue
                val mountSide = (mountX - rail.a.x) * rail.nx + (mountY - rail.a.y) * rail.ny
                if (mountSide <= .02f) continue // Aucun accrochage depuis le dessous d'un pont.
                val projection = ((x - rail.a.x) * rail.dx + (y - rail.a.y) * rail.dy) / rail.length2
                val t = projection.coerceIn(0f, 1f)
                val px = rail.a.x + rail.dx * t
                val py = rail.a.y + rail.dy * t
                val distance2 = (x - px).pow(2) + (y - py).pow(2)
                if (distance2 > 1.2f * 1.2f) continue
                var nx = rail.nx
                var ny = rail.ny
                if (projection < 0f || projection > 1f) {
                    // Un bout de tablier est un point, pas une droite infinie.
                    // Ne jamais prolonger son appui dans le vide entre deux ponts.
                    if (distance2 > MotocrossBike.RADIUS * MotocrossBike.RADIUS) continue
                    if (distance2 > .000001f) {
                        val distance = sqrt(distance2)
                        nx = (x - px) / distance
                        ny = (y - py) / distance
                        if (nx * upX + ny * upY <= .2f) continue
                    }
                }
                if (distance2 < best) {
                    best = distance2
                    out[0] = px; out[1] = py; out[2] = nx; out[3] = ny
                }
            }
        }
    }

    /** Collision avec les surfaces de la voie suivie, jamais le pont traversé dessous. */
    fun hitsBody(x: Float, y: Float, radius: Float, route: RouteState): Boolean {
        if (y - radius < height(x)) return true
        val reach = radius + .08f
        for (bucket in floor((x - reach) / 8f).toInt()..floor((x + reach) / 8f).toInt()) {
            for (rail in railBuckets[bucket] ?: continue) {
                if (!route.accepts(rail.roadId, rail.segment, rail.loop)) continue
                val t = (((x - rail.a.x) * rail.dx + (y - rail.a.y) * rail.dy) / rail.length2).coerceIn(0f, 1f)
                val dx = x - rail.a.x - t * rail.dx
                val dy = y - rail.a.y - t * rail.dy
                if (dx * dx + dy * dy < reach * reach) return true
            }
        }
        return false
    }

    /** Garder la voie basse jusqu'à la sortie complète du tablier, même quand
     * sa rampe de sortie finit par descendre sous le casque puis sous les roues. */
    fun updateUnderpasses(x: Float, y: Float, route: RouteState) {
        val ignoredDecks = route.ignoredDecks
        for ((id, road) in roads.withIndex()) {
            if (x < road.minX - 2f || x > road.maxX + 2f) {
                ignoredDecks.remove(id)
                route.loopCursors.remove(id)
                continue
            }
            if (id in ignoredDecks) continue
            if (road.loop) {
                val cursor = route.loopCursors[id]
                if (cursor == null) {
                    val entry = road.points.first()
                    if (x < entry.x - 1.4f) continue
                    if (x > entry.x + 2f || y < entry.y - .12f) {
                        ignoredDecks.add(id)
                        continue
                    }
                    route.loopCursors[id] = 0
                }
                val previous = route.loopCursors.getValue(id)
                var nearest = previous
                var distance = Float.POSITIVE_INFINITY
                for (i in max(0, previous - 24)..min(road.points.lastIndex, previous + 24)) {
                    val p = road.points[i]
                    val d = (x - p.x).pow(2) + (y - p.y).pow(2)
                    if (d < distance) { distance = d; nearest = i }
                }
                // En cas de chute dans le cercle, rejoindre la voie basse sans attraper
                // le mur de sortie. Freiner et reculer sur la branche reste possible.
                if (distance > 16f) {
                    ignoredDecks.add(id)
                    route.loopCursors.remove(id)
                } else route.loopCursors[id] = nearest
                continue
            }
            // Anticiper le nez de la moto avant que le châssis atteigne le bord.
            if (x < road.minX - 1.4f) continue
            val atX = x.coerceIn(road.minX, road.maxX)
            var lo = 0
            var hi = road.points.lastIndex - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (road.points[mid].x <= atX) lo = mid else hi = mid - 1
            }
            val a = road.points[lo]
            val b = road.points[lo + 1]
            val deckY = a.y + (b.y - a.y) * (atX - a.x) / (b.x - a.x)
            if (y < deckY - .12f) ignoredDecks.add(id)
        }
    }

    fun activeLoop(route: RouteState): MotocrossRoad? =
        route.loopCursors.keys.firstOrNull { it !in route.ignoredDecks }?.let { roads[it] }

    /** Sol visible sous la moto, sans sélectionner un étage situé au-dessus d'elle. */
    fun heightBelow(x: Float, y: Float): Float {
        var result = height(x)
        for (rail in railBuckets[floor(x / 8f).toInt()] ?: return result) {
            if (rail.dx <= 0f || x < rail.a.x || x > rail.b.x) continue
            val h = rail.a.y + rail.dy * (x - rail.a.x) / rail.dx
            if (h <= y && h > result) result = h
        }
        return result
    }

    companion object {
        private const val LENGTH_SCALE = 3.2f
        private const val HEIGHT_SCALE = 4.8f

        private fun module(kind: Kind, tier: Int, vararg xy: Float, launchX: Float? = null): Module {
            require(xy.size % 2 == 0)
            val knots = xy.toList().chunked(2).map { Point(it[0] * LENGTH_SCALE, it[1] * HEIGHT_SCALE) }
            require(knots.first() == Point(0f, 0f) && knots.last().y == 0f)
            require(knots.zipWithNext().all { (a, b) -> b.x > a.x })
            return Module(kind, tier, knots, launchX?.let { setOf(it * LENGTH_SCALE) } ?: emptySet())
        }

        /** Suites fixes : hauteurs et espacements sont conçus ensemble, puis précalculés. */
        private fun rhythm(
            kind: Kind, tier: Int, vararg heights: Float,
            stride: Float = 6f, launchEvery: Int = 0
        ): Module {
            require(heights.size >= 4)
            val knots = arrayListOf(Point(0f, 0f), Point(5f * LENGTH_SCALE, 0f))
            val launches = mutableSetOf<Float>()
            var x = 5f
            for ((index, height) in heights.withIndex()) {
                // Les moyennes bosses disposent d'une montée et d'une réception
                // plus longues. Aucun plat ne casse le rythme entre deux bosses.
                val width = stride + height * 1.4f
                val peakX = (x + width * .46f) * LENGTH_SCALE
                knots.add(Point(peakX, height * HEIGHT_SCALE))
                if (launchEvery > 0 && (index + 1) % launchEvery == 0 && height >= 1.25f) {
                    launches.add(peakX)
                }
                x += width
                knots.add(Point(x * LENGTH_SCALE, 0f))
            }
            knots.add(Point((x + 5f) * LENGTH_SCALE, 0f))
            return Module(kind, tier, knots, launches)
        }

        // Chaque portion comporte quatre à huit bosses, jamais un sommet isolé.
        // Les variantes restent fixes ; seul leur ordre change avec la graine.
        private val REST = module(Kind.REST, 0, 0f, 0f, 8f, 0f)
        private val MODULES = listOf(
            module(Kind.TABLE, 0, 0f,0f, 4f,0f, 8f,1.2f, 13f,1.2f, 17f,0f,
                21f,0f, 25f,1.6f, 31f,1.6f, 36f,0f, 41f,0f),
            module(Kind.VALLEY, 0, 0f,0f, 4f,0f, 10f,-1f, 16f,0f,
                22f,.9f, 28f,-1.2f, 35f,0f, 40f,0f),
            module(Kind.STEPS, 1, 0f,0f, 4f,0f, 8f,.7f, 11f,.7f, 15f,1.4f,
                18f,1.4f, 22f,2.1f, 26f,2.1f, 31f,1.2f, 35f,1.2f, 42f,0f, 47f,0f),
            module(Kind.RIDGE, 1, 0f,0f, 4f,0f, 12f,1.8f, 17f,1.1f,
                22f,2.2f, 27f,1.3f, 32f,2f, 41f,0f, 46f,0f),
            module(Kind.TABLE, 1, 0f,0f, 5f,0f, 10f,1.6f, 16f,1.6f, 21f,.5f,
                26f,2f, 32f,2f, 38f,0f, 43f,0f),
            module(Kind.VALLEY, 2, 0f,0f, 5f,0f, 12f,-1.5f, 18f,.8f,
                25f,-1.8f, 33f,1.6f, 40f,-1f, 47f,0f, 52f,0f),
            module(Kind.STEPS, 2, 0f,0f, 4f,0f, 9f,1f, 12f,1f, 17f,2f,
                21f,2f, 26f,3f, 30f,3f, 35f,2f, 39f,2f, 44f,1f, 48f,1f, 54f,0f, 59f,0f),
            module(Kind.RIDGE, 2, 0f,0f, 5f,0f, 14f,2.5f, 20f,1.4f,
                26f,2.8f, 33f,1.5f, 39f,2.3f, 49f,0f, 54f,0f),
            // Petites / moyennes, accessibles dès le départ.
            rhythm(Kind.ROLLERS, 0, .45f, 1.05f, .5f, .65f, 1.1f, .45f),
            rhythm(Kind.ROLLERS, 0, .4f, .6f, .85f, 1.1f, .8f, .55f, .4f),
            rhythm(Kind.DOUBLE, 0, .6f, .7f, 1.1f, 1f, .55f, .65f),
            rhythm(Kind.ROLLERS, 0, .5f, .5f, .55f, .5f, .65f, .5f, .5f, .45f, stride = 4.5f),
            rhythm(Kind.DOUBLE, 0, .45f, 1.2f, .5f, 1f, .45f, .8f),
            rhythm(Kind.JUMP, 0, .55f, 1.35f, .65f, .5f, 1.3f, .55f, stride = 7f, launchEvery = 1),
            rhythm(Kind.ROLLERS, 0, 1f, .7f, .4f, .65f, 1.05f, .5f),
            rhythm(Kind.DOUBLE, 0, .4f, .75f, .45f, 1.15f, .6f, .85f, .4f),
            // Rythmes irréguliers et doubles, toujours liés par des creux courts.
            rhythm(Kind.ROLLERS, 1, .6f, 1.5f, .65f, 1.1f, .5f, 1.4f, .6f),
            rhythm(Kind.DOUBLE, 1, .75f, .85f, 1.5f, 1.4f, .6f, .7f),
            rhythm(Kind.ROLLERS, 1, .5f, .8f, 1.15f, 1.6f, 1.2f, .75f, .5f),
            rhythm(Kind.ROLLERS, 1, .65f, 1.2f, .5f, .9f, 1.5f, .55f, 1f, .6f, stride = 5f),
            rhythm(Kind.JUMP, 1, .6f, 1.75f, .7f, 1.1f, 1.65f, .6f, stride = 7.5f, launchEvery = 1),
            rhythm(Kind.DOUBLE, 1, 1.4f, .6f, .7f, 1.5f, .65f, .55f),
            rhythm(Kind.JUMP, 1, .55f, 1.5f, .65f, 1.55f, .6f, 1.45f, stride = 7f, launchEvery = 2),
            rhythm(Kind.ROLLERS, 1, .8f, .55f, 1.3f, .75f, 1.6f, .55f, .85f),
            // Plus techniques : davantage de changements de rythme, pas de montagne.
            rhythm(Kind.ROLLERS, 2, .7f, 1.85f, .6f, 1.25f, .75f, 2f, .6f),
            rhythm(Kind.DOUBLE, 2, .8f, .9f, 1.8f, 1.7f, .7f, 1.1f, .6f),
            rhythm(Kind.ROLLERS, 2, .6f, 1f, 1.5f, 2.1f, 1.4f, .9f, .55f),
            rhythm(Kind.JUMP, 2, .65f, 2.2f, .75f, 1.25f, 2f, .65f, stride = 8f, launchEvery = 1),
            rhythm(Kind.ROLLERS, 2, .7f, 1.4f, .6f, 1.75f, .8f, 1.15f, .55f, 1.5f, stride = 5.5f),
            rhythm(Kind.DOUBLE, 2, 1.6f, .65f, .8f, 1.85f, .75f, .6f, 1.2f),
            rhythm(Kind.JUMP, 2, .7f, 1.8f, .65f, 2.1f, .8f, 1.75f, stride = 8f, launchEvery = 2),
            rhythm(Kind.ROLLERS, 2, .55f, 1.2f, .75f, 1.9f, .6f, 1.4f, .85f, .5f)
        )
        private val GEOMETRY = (MODULES + REST).associateWith { module ->
            buildList {
                add(module.knots.first())
                module.knots.zipWithNext().forEach { (a, b) ->
                    val samples = ceil((b.x - a.x) / .16f).toInt()
                    for (i in 1..samples) {
                        val t = i.toFloat() / samples
                        // La lèvre d'un tremplin garde une pente montante au départ
                        // du vol. L'aplatir supprimait l'impulsion verticale du saut.
                        val endSlope = if (b.x in module.launchXs) 1.15f else 0f
                        val blend = t * t * (3f - 2f * t) + (t * t * t - t * t) * endSlope
                        add(Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * blend))
                    }
                }
            }
        }
    }
}
