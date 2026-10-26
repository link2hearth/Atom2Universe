package com.Atom2Universe.app.games.mochi

import kotlin.math.*
import kotlin.random.Random

/** Grammaire de contraintes géométriques validées par la physique, sans catalogue de salles fixes. */
internal class MochiGarden(seed: Long) {
    data class Section(val next: MochiGame.Flower, val branch: MochiGame.Flower?, val walls: List<MochiWall>,
        val sweets: List<MochiGame.Sweet>, val hazards: List<MochiGame.Hazard>)
    data class Shot(val velocity: MochiGame.Point, val duration: Float, val points: List<MochiGame.Point>, val bounces: Int,
        val clearancePoints: List<MochiGame.Point> = points)
    private data class Corridor(val points: List<MochiGame.Point>, val margin: Float)
    private enum class Constraint { SPUR, AWNING, GATE, CHICANE, BANK, ISLAND }
    private val random = Random(seed)
    private val recent = ArrayDeque<Constraint>()
    private val corridors = ArrayList<Corridor>()
    private var wallId = 1

    fun next(previous: MochiGame.Flower, existingWalls: List<MochiWall>, existingFlowers: List<MochiGame.Flower>): Section {
        corridors.removeAll { corridor -> corridor.points.maxOf { it.y } < previous.y - 330f }
        val id = previous.id + 1
        val kind = when {
            MochiGame.isSanctuaryRow(id) -> MochiGame.FlowerKind.SANCTUARY
            id <= 3 -> MochiGame.FlowerKind.NORMAL
            id % 8 == 0 -> MochiGame.FlowerKind.FRAGILE
            id % 12 == 5 || id >= 12 && id % 5 == 2 -> MochiGame.FlowerKind.DRIFT
            id % 11 == 7 -> MochiGame.FlowerKind.SPRING
            else -> MochiGame.FlowerKind.NORMAL
        }
        var target = target(previous, id, kind)
        var main = solveLaunch(previous, target, existingWalls, flowers = existingFlowers + target)
        var attempts = 0
        while (main == null && attempts++ < 12) {
            target = target(previous, id, kind)
            main = solveLaunch(previous, target, existingWalls, flowers = existingFlowers + target)
        }
        if (main == null) {
            // Chaque ouvrage se termine sous sa fleur de sortie : une sortie verticale reste libre.
            target = MochiGame.Flower(id, previous.x, previous.y + 128f,
                kind = if (kind == MochiGame.FlowerKind.DRIFT) MochiGame.FlowerKind.NORMAL else kind)
            main = requireNotNull(solveLaunch(previous, target, existingWalls, flowers = existingFlowers + target))
        }
        var additions = emptyList<MochiWall>()
        if (id > 3 && kind != MochiGame.FlowerKind.SANCTUARY) {
            for (constraint in Constraint.entries.filter { it !in recent }.shuffled(random)) {
                val candidate = geometry(constraint, previous, target)
                if (candidate.isEmpty() || !canPlace(candidate, existingFlowers + target)) continue
                val proof = certify(previous, target, existingWalls + candidate, existingFlowers + target) ?: continue
                additions = candidate; main = proof
                recent.addLast(constraint)
                if (recent.size > 2) recent.removeFirst()
                break
            }
        }
        var walls = existingWalls + additions
        if (id >= 14 && additions.isNotEmpty() && random.nextFloat() < .32f) {
            val extra = geometry(Constraint.AWNING, previous, target)
            if (canPlace(extra, existingFlowers + target) && !overlapWalls(extra, additions)) {
                val proof = certify(previous, target, walls + extra, existingFlowers + target)
                if (proof != null) { additions = additions + extra; walls = walls + extra; main = proof }
            }
        }
        var branch: MochiGame.Flower? = null
        var branchIn: Shot? = null
        var branchOut: Shot? = null
        if (id >= 3 && kind != MochiGame.FlowerKind.SANCTUARY && (id == 3 || random.nextFloat() < .72f)) {
            val direction = if (target.x > previous.x) 1f else -1f
            val desired = listOf(previous.x - direction * between(65f, 125f),
                previous.x + direction * between(22f, 60f),
                if (direction > 0) between(55f, 140f) else between(280f, 365f))
            for (needBank in listOf(true, false)) {
                for (bx in desired) {
                    if (bx !in 50f..370f || abs(bx - target.x) < 65f) continue
                    val candidate = MochiGame.Flower(-id, bx, target.y - between(35f, 52f),
                        kind = if (needBank) MochiGame.FlowerKind.NORMAL else when (random.nextInt(3)) {
                            0 -> MochiGame.FlowerKind.SPRING
                            1 -> MochiGame.FlowerKind.FRAGILE
                            else -> MochiGame.FlowerKind.NORMAL
                        })
                    if (walls.any { closeToFlower(it, candidate) }) continue
                    val allFlowers = existingFlowers + target + candidate
                    val entrance = solveLaunch(previous, candidate, walls, flowers = allFlowers,
                        requireInteriorBounce = needBank) ?: continue
                    val exit = certify(candidate, target, walls, allFlowers) ?: continue
                    val mainWithBranch = certify(previous, target, walls, allFlowers) ?: continue
                    branch = candidate; branchIn = entrance; branchOut = exit; main = mainWithBranch
                    break
                }
                if (branch != null) break
            }
        }
        val proof = requireNotNull(main)
        val fruits = ArrayList<MochiGame.Sweet>()
        val regular = proof.points[(proof.points.size * .57f).toInt().coerceAtMost(proof.points.lastIndex)]
        val fruitY = regular.y + if (id <= 2) 0f else between(25f, 43f)
        if (walls.none { MochiFlight.overlaps(it, regular.x, fruitY, 14f) }) fruits += MochiGame.Sweet(regular.x, fruitY)
        if (branchIn != null) {
            val p = branchIn.points[(branchIn.points.size * .63f).toInt().coerceAtMost(branchIn.points.lastIndex)]
            if (walls.none { MochiFlight.overlaps(it, p.x, p.y, 13f) }) fruits += MochiGame.Sweet(p.x, p.y, golden = true)
        }
        val hazard = hazard(previous, target, branch, proof, branchIn, branchOut, walls)
        remember(previous, target, proof)
        if (branch != null && branchIn != null && branchOut != null) {
            remember(previous, branch, branchIn); remember(branch, target, branchOut)
        }
        return Section(target, branch, additions, fruits, listOfNotNull(hazard))
    }

    private fun between(min: Float, max: Float) = min + random.nextFloat() * (max - min)

    private fun target(from: MochiGame.Flower, id: Int, kind: MochiGame.FlowerKind): MochiGame.Flower {
        repeat(20) {
            val x = if (id == 1) 290f else between(65f, 355f)
            val y = from.y + if (id <= 3) between(125f, 145f) else between(138f, 180f)
            val candidate = MochiGame.Flower(id, x, y, kind = kind, offset = between(0f, 6.28f))
            val v = MochiGame.routeVelocity(from.copy(x = 0f),
                candidate.copy(x = abs(x - from.x) + from.travel + candidate.travel))
            if (abs(x - from.x) >= 65f && hypot(v.x, v.y) <= 610f) return candidate
        }
        return MochiGame.Flower(id, if (from.x < 210f) from.x + 110f else from.x - 110f,
            from.y + 140f, kind = kind, offset = between(0f, 6.28f))
    }

    private fun geometry(type: Constraint, from: MochiGame.Flower, to: MochiGame.Flower): List<MochiWall> {
        val dir = if (to.x > from.x) 1f else -1f
        val velocity = MochiGame.routeVelocity(from, to)
        fun risingX(height: Float): Float {
            val t = (velocity.y - sqrt(max(0f, velocity.y * velocity.y - 2f * MochiGame.GRAVITY * height))) / MochiGame.GRAVITY
            return (from.x + velocity.x * t).coerceIn(65f, 355f)
        }
        fun wall(left: Float, bottom: Float, right: Float, top: Float, rubber: Boolean = false): MochiWall =
            MochiWall(wallId++, min(left, right), bottom, max(left, right), top,
                if (rubber) MochiWall.Material.RUBBER else MochiWall.Material.STONE)
        fun gate(y: Float, gap: Float): List<MochiWall> {
            val center = (risingX(y - from.y) + between(-8f, 8f)).coerceIn(gap / 2 + 20, 400f - gap / 2)
            val thick = between(12f, 20f)
            return listOf(wall(0f, y, center - gap / 2, y + thick), wall(center + gap / 2, y, 420f, y + thick))
        }
        return when (type) {
            Constraint.SPUR, Constraint.BANK -> {
                val center = from.x + dir * between(49f, 78f)
                val thickness = between(13f, 23f)
                if (center !in 35f..385f) emptyList() else listOf(wall(center - thickness / 2,
                    from.y + between(38f, 49f), center + thickness / 2, to.y - between(40f, 49f),
                    type == Constraint.BANK || random.nextFloat() < .3f))
            }
            Constraint.AWNING -> {
                val y = from.y + between(66f, 88f)
                val edge = risingX(y - from.y) - dir * between(24f, 35f)
                listOf(wall(if (dir > 0) 0f else edge, y, if (dir > 0) edge else 420f, y + between(12f, 20f)))
            }
            Constraint.GATE -> gate(from.y + between(57f, 83f), between(76f, 110f))
            Constraint.CHICANE -> {
                val lower = from.y + between(43f, 57f)
                val upper = min(to.y - 58f, lower + between(35f, 52f))
                val gap = between(72f, 106f)
                val a = risingX(lower - from.y); val b = risingX(upper - from.y)
                listOf(wall(0f, lower, max(18f, a - gap / 2), lower + 13f),
                    wall(min(402f, b + gap / 2), upper, 420f, upper + 13f))
            }
            Constraint.ISLAND -> {
                val center = from.x + (to.x - from.x) * between(.35f, .55f)
                val half = between(17f, 31f)
                listOf(wall(center - half, from.y + between(40f, 55f), center + half,
                    to.y - between(40f, 58f), rubber = random.nextBoolean()))
            }
        }
    }

    private fun canPlace(walls: List<MochiWall>, flowers: List<MochiGame.Flower>) = walls.all { wall ->
        wall.right > wall.left + 5f && wall.top > wall.bottom + 5f && flowers.none { closeToFlower(wall, it) } &&
            corridors.none { corridor -> corridor.points.any { MochiFlight.overlaps(wall, it.x, it.y, corridor.margin) } }
    }
    private fun overlapWalls(a: List<MochiWall>, b: List<MochiWall>) = a.any { x -> b.any { y ->
        x.left < y.right + 3 && x.right > y.left - 3 && x.bottom < y.top + 3 && x.top > y.bottom - 3
    } }
    private fun closeToFlower(wall: MochiWall, flower: MochiGame.Flower) =
        MochiFlight.overlaps(wall, flower.x, flower.y, MochiGame.RADIUS + flower.travel + 9f)
    private fun certify(from: MochiGame.Flower, to: MochiGame.Flower, walls: List<MochiWall>, flowers: List<MochiGame.Flower>): Shot? {
        val first = solveLaunch(from, to, walls, flowers = flowers) ?: return null
        val coverage = ArrayList(first.points)
        if (from.travel > 0 || to.travel > 0) for (phase in listOf(1.5f, 3f)) {
            val alternate = solveLaunch(from, to, walls, phase, flowers) ?: return null
            coverage += alternate.points
        }
        return first.copy(clearancePoints = coverage)
    }
    private fun remember(from: MochiGame.Flower, to: MochiGame.Flower, shot: Shot) {
        corridors += Corridor(shot.clearancePoints, MochiGame.RADIUS + 8f + from.travel + to.travel)
    }

    private fun hazard(from: MochiGame.Flower, to: MochiGame.Flower, branch: MochiGame.Flower?,
        main: Shot, entrance: Shot?, exit: Shot?, walls: List<MochiWall>): MochiGame.Hazard? {
        if (to.id < 6 || to.kind == MochiGame.FlowerKind.SANCTUARY || to.id % 4 == 0) return null
        val kind = if ((to.id % 7 == 6 || to.id % 11 == 2) && from.kind != MochiGame.FlowerKind.FRAGILE &&
            branch?.kind != MochiGame.FlowerKind.FRAGILE) MochiGame.Danger.BEE else MochiGame.Danger.THORN
        // L'accroche peut écourter le vol : chercher une place libre avec une fenêtre de passage.
        val positions = if (kind == MochiGame.Danger.BEE) listOf(.27f, .4f, .5f, .6f, .7f) else listOf(.27f)
        val phase = between(0f, 6.28f)
        for (position in positions) {
            val p = main.points[(main.points.size * position).toInt().coerceAtMost(main.points.lastIndex)]
            val x = p.x + if (kind == MochiGame.Danger.BEE) 0f else if (to.x > from.x) -58f else 58f
            val h = MochiGame.Hazard(x, p.y, kind, phase)
            if (x - h.travel < 30f || x + h.travel > 390f) continue
            if (walls.any { wall -> (0..4).any {
                    MochiFlight.overlaps(wall, x - h.travel + it * h.travel / 2, p.y, h.radius + 15f) } }) continue
            if (listOfNotNull(from, to, branch).any { abs(it.y - p.y) < h.radius + MochiGame.RADIUS + 16f &&
                    abs(it.x - x) < h.radius + h.travel + it.travel + MochiGame.RADIUS + 16f }) continue
            fun hits(points: List<MochiGame.Point>, travel: Float) = points.any {
                hypot(max(0f, abs(it.x - x) - travel), it.y - p.y) < h.radius + MochiGame.RADIUS + 9f
            }
            if (kind == MochiGame.Danger.THORN && hits(main.points, 0f)) continue
            if (listOfNotNull(entrance, exit).any { hits(it.points, h.travel) }) continue
            if (corridors.any { hits(it.points, h.travel) }) continue
            if (kind == MochiGame.Danger.BEE && (0..35).none { delay ->
                main.points.withIndex().all { (index, point) ->
                    val time = delay * .1f + min(index * 2 * MochiGame.STEP, main.duration)
                    hypot(point.x - h.at(time), point.y - h.y) > h.radius + MochiGame.RADIUS + 4f
                }
            }) continue
            return h
        }
        return null
    }

    companion object {
        internal fun solveLaunch(from: MochiGame.Flower, to: MochiGame.Flower, walls: List<MochiWall>,
            time: Float = 0f, flowers: List<MochiGame.Flower> = listOf(from, to), requireInteriorBounce: Boolean = false,
            catchRadius: Float = MochiGame.CATCH_RADIUS): Shot? {
            val startX = from.at(time)
            val maxSpeed = MochiGame.MAX_SPEED * if (from.kind == MochiGame.FlowerKind.SPRING) 1.2f else 1f
            val ceiling = from.y + maxSpeed * maxSpeed / (2f * MochiGame.GRAVITY) + MochiGame.RADIUS
            val relevantWalls = walls.filter { it.top >= from.y - 90f && it.bottom <= ceiling }
            val relevantFlowers = flowers.filter { it.y >= from.y - 75f && it.y <= ceiling }
            val faces = ArrayList<Pair<Float, Float>>()
            for (wall in relevantWalls) {
                // Un ricochet latéral demande une face assez longue ; ignorer les corniches déjà sous le départ.
                if (wall.top < from.y + 20f || wall.top - wall.bottom < 30f) continue
                val restitution = if (wall.material == MochiWall.Material.RUBBER) .94f else .72f
                if (startX < wall.left - MochiGame.RADIUS) faces += (wall.left - MochiGame.RADIUS) to restitution
                if (startX > wall.right + MochiGame.RADIUS) faces += (wall.right + MochiGame.RADIUS) to restitution
            }
            if (!requireInteriorBounce) { faces += MochiGame.RADIUS to .8f; faces += (MochiGame.WIDTH - MochiGame.RADIUS) to .8f }
            val durations = (0..13).map { .64f + it * .05f }
            for (bank in listOf(false, true)) {
                if (!bank && requireInteriorBounce) continue
                for (duration in durations) {
                    val targetX = to.at(time + duration)
                    val vy = (to.y - from.y) / duration + .5f * MochiGame.GRAVITY * duration
                    if (vy - MochiGame.GRAVITY * duration > -28f) continue
                    val candidates = if (!bank) listOf((targetX - startX) / duration) else faces.mapNotNull { (face, restitution) ->
                        if ((startX - face) * (targetX - face) <= 0f) null else
                            sign(face - startX) * (abs(face - startX) + abs(targetX - face) / restitution) / duration
                    }
                    for (vx in candidates) {
                        if (hypot(vx, vy) > maxSpeed - 2f) continue
                        val shot = trace(from, to, MochiGame.Point(vx, vy), relevantWalls, relevantFlowers, time, catchRadius)
                        if (shot != null && (!requireInteriorBounce || shot.bounces > 0)) return shot
                    }
                }
            }
            return null
        }
        private fun trace(from: MochiGame.Flower, to: MochiGame.Flower, velocity: MochiGame.Point,
            walls: List<MochiWall>, flowers: List<MochiGame.Flower>, time: Float, catchRadius: Float): Shot? {
            var state = MochiFlight.State(from.at(time), from.y, velocity.x, velocity.y)
            val points = ArrayList<MochiGame.Point>(90)
            points += MochiGame.Point(state.x, state.y)
            var bounces = 0
            for (step in 1..210) {
                val result = MochiFlight.step(state, MochiGame.STEP, walls)
                val thornContact = result.hits.firstOrNull { it.ny > .99f && it.speed < 65f }
                for (segment in result.segments) {
                    val landing = flowers.asSequence().filter {
                        it.alive && (it.id != from.id || step > 30)
                    }.mapNotNull { flower ->
                        val radius = if (flower.id == to.id) { if (flower.bonus) 13f else 15f } else catchRadius + when {
                            flower.kind == MochiGame.FlowerKind.SANCTUARY -> 4f
                            flower.bonus -> -2f
                            else -> 0f
                        }
                        MochiGame.flowerContact(flower, segment.ax, segment.ay, segment.bx, segment.by,
                            time + (step - 1 + segment.startFraction) * MochiGame.STEP,
                            time + (step - 1 + segment.endFraction) * MochiGame.STEP, radius)
                            ?.let { flower to it.fraction }
                    }.minByOrNull { it.second }
                    if (landing != null) {
                        if (landing.first.id != to.id) return null
                        val fraction = segment.startFraction + (segment.endFraction - segment.startFraction) * landing.second
                        if (thornContact != null && thornContact.fraction <= fraction) return null
                        bounces += result.hits.count { it.wallId >= 0 && it.speed >= 25f && it.fraction < fraction - .00001f }
                        points += MochiGame.Point(to.at(time + step * MochiGame.STEP), to.y)
                        return Shot(velocity, step * MochiGame.STEP, points, bounces)
                    }
                }
                if (thornContact != null) return null
                bounces += result.hits.count { it.wallId >= 0 && it.speed >= 25f }
                state = result.state
                if (step % 2 == 0) points += MochiGame.Point(state.x, state.y)
                if (state.y < from.y - 70f) return null
            }
            return null
        }
    }
}
