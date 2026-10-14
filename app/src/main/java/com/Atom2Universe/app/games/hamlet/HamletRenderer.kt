package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Un habitant qui se promène sur les chemins. [gift] : il tend une pièce qu'on peut cueillir en le touchant. */
class Villager(var x: Float, var y: Float, val cloth: Int, val skin: Int, val speed: Float) {
    var role = 0
    var nameIndex = 0
    var tx = x
    var ty = y
    var gift = false
    var faceRight = true
    var pause = 0f
}

/** Du texte qui monte et s'efface au-dessus d'un point du village. */
class Floater(val x: Float, val y: Float, val z: Float, val parts: List<Part>, val t0: Long) {
    class Part(val glyph: String?, val text: String, val color: Int)
}

/**
 * Le dessin du village : sol, objets triés en profondeur, habitants, bulles, lumière du soir.
 * Aucune dépendance à une vue, pour qu'un test puisse en tirer une image.
 */
class HamletRenderer(val game: HamletGame, val art: HamletArt) {

    var camX = 0f
    var camY = 0f
    var zoom = 0.9f
    var density = 2.5f
    var time = 0f
    var nightEnabled = true
    var effectsEnabled = true
    var daylight = 1f
    /** Photo lighting is presentation only; it never advances the simulation clock. */
    var daylightOverride: Float? = null
    var photoMode = false
    var width = 1
    var height = 1

    var selected: Obj? = null
    /** Objet temporairement masqué pendant son déplacement. */
    var hidden: Obj? = null
    var ghostDef: Def? = null
    var ghostX = 0
    var ghostY = 0
    var ghostFlip = false
    var ghostOk = true
    var ghostCrop: String? = null

    /** Tuiles à surligner (pinceau en cours). */
    var brushTile: IntArray? = null

    val villagers = ArrayList<Villager>()
    val floaters = ArrayList<Floater>()

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val mat = Matrix()
    private val path = Path()
    private val rect = RectF()
    private val objectBounds = RectF()
    private val lightMatrix = Matrix()
    private val warmLight = RadialGradient(0f, 0f, 1f, 0xBEFFC86E.toInt(), 0x00FFB450, Shader.TileMode.CLAMP)
    private val orderedVillagers = ArrayList<Villager>()
    private val villagerDepth = compareBy<Villager> { it.x + it.y }
    private val walkDirections = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    private val walkOptions = IntArray(4)
    private var roadCache: List<IntArray> = emptyList()
    private var roadRev = -1
    private var roadOrigin = -1
    private var roadSize = -1
    private var daylightCheckedAt = Long.MIN_VALUE
    private var ambientDaylight = 1f
    private var buildingIso: Iso? = null

    private var sorted: List<Obj> = emptyList()
    private var sortedRev = -1
    private var waterTiles = IntArray(0)
    private var waterRev = -1
    private var lights: List<FloatArray> = emptyList()
    private val stewardHomes = arrayOfNulls<Obj>(3)

    // ------------------------------------------------------------------ passage écran / monde

    fun sx(wx: Float) = (wx - camX) * zoom + width / 2f
    fun sy(wy: Float) = (wy - camY) * zoom + height / 2f

    /** Coordonnées de tuile (flottantes) sous un point de l'écran, sur le sol. */
    fun tileAt(px: Float, py: Float): FloatArray {
        val wx = (px - width / 2f) / zoom + camX
        val wy = (py - height / 2f) / zoom + camY
        val sum = wy / 16f
        val diff = wx / 32f
        return floatArrayOf((sum + diff) / 2f, (sum - diff) / 2f)
    }

    fun centerOn(tileX: Float, tileY: Float) {
        camX = (tileX - tileY) * 32f
        camY = (tileX + tileY) * 16f
    }

    /** Garde le centre de la vue dans les limites du royaume, avec un peu de marge. */
    fun clampCamera() {
        val o = game.realmOrigin().toFloat()
        val r = game.realmSize().toFloat()
        val m = 3f
        camX = camX.coerceIn(-(r + m * 2f) * 32f, (r + m * 2f) * 32f)
        camY = camY.coerceIn((o - m) * 32f, (o + r + m) * 32f)
    }

    // ------------------------------------------------------------------ habitants

    fun syncVillagers(rng: Random) {
        val want = min(36, game.population() + 2)
        val roads = roadTiles()
        if (roads.isEmpty()) { villagers.clear(); return }
        while (villagers.size > want) villagers.removeAt(villagers.size - 1)
        while (villagers.size < want) {
            val t = roads[rng.nextInt(roads.size)]
            val v = Villager(
                t[0] + 0.5f, t[1] + 0.5f,
                intArrayOf(0xC8392E, 0x3A6AC8, 0x3F9A4A, 0x8A3AB8, 0xE8A020, 0x2F8A8A, 0xA85A2A)[rng.nextInt(7)],
                intArrayOf(0xF6D2A8, 0xE8B888, 0xC8946A, 0xF8E0C0, 0xA87850)[rng.nextInt(5)],
                0.5f + rng.nextFloat() * 0.5f,
            )
            v.nameIndex = villagers.size % Hamlet.visitors.size
            v.role = villagers.size % 5
            HamletCharacterArt.prewarmResident(v.nameIndex)
            villagers.add(v)
        }
    }

    private fun roadTiles(): List<IntArray> {
        val o = game.realmOrigin(); val r = game.realmSize()
        if (roadRev == game.groundRev && roadOrigin == o && roadSize == r) return roadCache
        val out = ArrayList<IntArray>()
        for (y in o until o + r) for (x in o until o + r) if (Hamlet.walkable(game.pathAt(x, y)) || game.pathAt(x, y) == Hamlet.PATH_BRIDGE) out.add(intArrayOf(x, y))
        roadRev = game.groundRev; roadOrigin = o; roadSize = r; roadCache = out
        return out
    }

    private fun walkableAt(x: Int, y: Int): Boolean {
        val c = game.pathAt(x, y)
        return (Hamlet.walkable(c) || c == Hamlet.PATH_BRIDGE) && game.inRealm(x, y)
    }

    fun update(dt: Float, rng: Random) {
        time += dt
        for (v in villagers) {
            if (v.pause > 0) { v.pause -= dt; continue }
            val dx = v.tx - v.x; val dy = v.ty - v.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < 0.05f) {
                // choisir une tuile voisine marchable, de préférence en continuant tout droit
                val cx = floor(v.x).toInt(); val cy = floor(v.y).toInt()
                var optionCount = 0
                for (i in walkDirections.indices) {
                    val dir = walkDirections[i]
                    if (walkableAt(cx + dir[0], cy + dir[1])) walkOptions[optionCount++] = i
                }
                if (optionCount == 0) {
                    if (!walkableAt(cx, cy)) { respawn(v, rng) }
                    v.pause = 1.5f
                } else {
                    val pick = walkDirections[walkOptions[rng.nextInt(optionCount)]]
                    v.tx = cx + pick[0] + 0.3f + rng.nextFloat() * 0.4f
                    v.ty = cy + pick[1] + 0.3f + rng.nextFloat() * 0.4f
                    if (rng.nextFloat() < 0.12f) v.pause = 0.5f + rng.nextFloat() * 2f
                }
            } else {
                val step = min(dist, v.speed * dt)
                v.x += dx / dist * step
                v.y += dy / dist * step
                v.faceRight = dx - dy > 0
            }
        }
        // de temps en temps, un habitant tend une pièce
        if (villagers.isNotEmpty() && villagers.none { it.gift } && rng.nextFloat() < dt / 70f) villagers[rng.nextInt(villagers.size)].gift = true
        val now = game.now()
        floaters.removeAll { now - it.t0 > 1500 }
        if (daylightCheckedAt == Long.MIN_VALUE || now - daylightCheckedAt >= 10_000L || now < daylightCheckedAt) {
            ambientDaylight = daylightNow(); daylightCheckedAt = now
        }
        daylight = daylightOverride ?: if (nightEnabled) ambientDaylight else 1f
    }

    private fun respawn(v: Villager, rng: Random) {
        val roads = roadTiles()
        if (roads.isEmpty()) return
        val t = roads[rng.nextInt(roads.size)]
        v.x = t[0] + 0.5f; v.y = t[1] + 0.5f; v.tx = v.x; v.ty = v.y
    }

    private fun daylightNow(): Float {
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        return when {
            h < 5f -> 0f
            h < 7f -> (h - 5f) / 2f
            h < 19f -> 1f
            h < 21f -> 1f - (h - 19f) / 2f
            else -> 0f
        }
    }

    fun floatText(x: Float, y: Float, z: Float, parts: List<Floater.Part>) { floaters.add(Floater(x, y, z, parts, game.now())) }

    // ------------------------------------------------------------------ dessin

    private fun ensureSorted() {
        if (sortedRev == game.rev) return
        sortedRev = game.rev
        sorted = game.objs.values.sortedWith(compareBy<Obj>({ it.x + it.w + it.y + it.d }, { it.x }))
        stewardHomes.fill(null)
        for (o in sorted) {
            if (game.buildLeft(o) > 0L) continue
            val post = when (o.def.cat) { Cat.FIELD, Cat.RANCH -> 0; Cat.SHOP -> 1; Cat.HOME -> 2; else -> -1 }
            if (post >= 0 && stewardHomes[post] == null) stewardHomes[post] = o
        }
        lights = ArrayList<FloatArray>().also { list ->
            for (o in sorted) {
                val k = o.def.look.kind
                val glow = when (k) {
                    "d_lamp" -> 1.2f
                    "d_torch" -> 0.9f
                    "d_fire" -> 1.6f
                    "d_stage", "d_pavilion" -> 1.2f
                    "asset" -> when (o.def.look.n) { 0, 5 -> .8f; 1 -> .65f; 2 -> 1.1f; else -> 0f }
                    else -> 0f
                }
                if (glow > 0f && game.buildLeft(o) == 0L) list.add(floatArrayOf(o.x + o.w / 2f, o.y + o.d / 2f, if (k == "d_lamp" || k == "d_torch") 1.2f else 0.5f, glow))
                else if (o.def.cat == Cat.HOME && game.buildLeft(o) == 0L) list.add(floatArrayOf(o.x + o.w / 2f, o.y + o.d / 2f, 0.55f, 0.9f))
            }
        }
    }

    private fun ensureWater() {
        if (waterRev == game.groundRev) return
        waterRev = game.groundRev
        val l = ArrayList<Int>()
        for (i in game.paths.indices) if (game.paths[i] == Hamlet.PATH_WATER) l.add(i)
        waterTiles = l.toIntArray()
    }

    fun draw(c: Canvas) {
        ensureSorted()
        ensureWater()
        c.drawColor(0xFF2C4A2C.toInt())
        drawGround(c)
        drawWater(c)
        drawObjects(c)
        if (!photoMode) {
            drawSelection(c)
            drawGhost(c)
            drawBrush(c)
        }
        drawNight(c)
        if (effectsEnabled) drawAtmosphere(c)
        if (!photoMode) drawFloaters(c)
    }

    private fun visibleChunks(): IntArray {
        // coins de l'écran en tuiles
        val a = tileAt(0f, 0f); val b = tileAt(width.toFloat(), 0f); val d = tileAt(0f, height.toFloat()); val e = tileAt(width.toFloat(), height.toFloat())
        val minX = minOf(a[0], b[0], d[0], e[0]); val maxX = maxOf(a[0], b[0], d[0], e[0])
        val minY = minOf(a[1], b[1], d[1], e[1]); val maxY = maxOf(a[1], b[1], d[1], e[1])
        val n = HamletArt.CHUNK
        val last = Hamlet.GRID / n - 1
        return intArrayOf(
            floor(minX / n).toInt().coerceIn(0, last), floor(maxX / n).toInt().coerceIn(0, last),
            floor(minY / n).toInt().coerceIn(0, last), floor(maxY / n).toInt().coerceIn(0, last),
        )
    }

    private fun drawGround(c: Canvas) {
        val v = visibleChunks()
        for (cy in v[2]..v[3]) for (cx in v[0]..v[1]) {
            val bmp = art.chunk(cx, cy, game)
            mat.reset()
            mat.postScale(zoom, zoom)
            mat.postTranslate(sx(HamletGround.left(cx, cy)), sy(HamletGround.top(cx, cy)))
            c.drawBitmap(bmp, mat, bmpPaint)
        }
    }

    private fun drawWater(c: Canvas) {
        if (waterTiles.isEmpty()) return
        strokeP.strokeWidth = max(1f, 1.4f * zoom)
        strokeP.color = Color.argb(110, 255, 255, 255)
        strokeP.strokeCap = Paint.Cap.ROUND
        for (i in waterTiles) {
            val x = i % Hamlet.GRID; val y = i / Hamlet.GRID
            val px = sx(HamletGround.worldX(x.toFloat(), y.toFloat()))
            val py = sy(HamletGround.worldY(x.toFloat(), y.toFloat()) + 16f)
            if (px < -70 * zoom || px > width + 70 * zoom || py < -40 * zoom || py > height + 40 * zoom) continue
            val h = ((x * 73856093) xor (y * 19349663)) and 0xFF
            if (h > 110) continue
            val ph = (if (effectsEnabled) time * 0.8f else 0f) + h * 0.1f
            val off = sin(ph) * 7f * zoom
            val a = (0.5f + 0.5f * sin(ph * 1.3f))
            strokeP.alpha = (a * 130).toInt()
            c.drawLine(px - 9f * zoom + off, py - 2f * zoom, px + 9f * zoom + off, py - 2f * zoom, strokeP)
        }
    }

    private fun spriteBox(o: Obj, s: Sprite): RectF {
        val k = zoom / HamletArt.S
        val wx = (o.x - o.y) * 32f
        val wy = (o.x + o.y) * 16f
        val dx = sx(wx) - s.ox * k
        val dy = sy(wy) - s.oy * k
        objectBounds.set(dx, dy, dx + s.bmp.width * k, dy + s.bmp.height * k)
        return objectBounds
    }

    private fun drawObjects(c: Canvas) {
        orderedVillagers.clear(); orderedVillagers.addAll(villagers); orderedVillagers.sortWith(villagerDepth)
        val vs = orderedVillagers
        var vi = 0
        val k = zoom / HamletArt.S
        for (o in sorted) {
            if (o === hidden) continue
            val key = (o.x + o.y + o.w + o.d).toFloat() - 1f
            while (vi < vs.size && vs[vi].x + vs[vi].y < key - 0.5f) { drawVillager(c, vs[vi]); vi++ }
            val s = art.forObj(o, game)
            val b = spriteBox(o, s)
            if (b.right < 0 || b.bottom < 0 || b.left > width || b.top > height) continue
            mat.reset()
            mat.postScale(k, k)
            mat.postTranslate(b.left, b.top)
            c.drawBitmap(s.bmp, mat, bmpPaint)
            if (o.def.cat == Cat.RANCH && game.buildLeft(o) == 0L) drawAnimals(c, o)
            drawObjectEffects(c, o)
            drawSteward(c, o)
            if (!photoMode) drawBubble(c, o, b, s)
        }
        while (vi < vs.size) { drawVillager(c, vs[vi]); vi++ }
    }

    private fun drawAnimals(c: Canvas, o: Obj) {
        val save = c.save()
        c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
        c.scale(zoom, zoom)
        val iso = Iso(c, 0f, 0f)
        for (i in 0..2) {
            val phase = (if (effectsEnabled) time * 0.65f else 0f) + o.id * 1.3f + i * 2.1f
            val x = o.w * 0.49f + sin(phase) * 0.2f + (i - 1) * 0.28f
            val y = o.d * 0.62f + cos(phase * 0.8f) * 0.15f + (i - 1) * 0.13f
            val bob = if (effectsEnabled) abs(sin(phase * 3)) * 0.025f else 0f
            HamletArtAnimals.animal(iso, o.def.look.n, x, y, bob)
        }
        c.restoreToCount(save)
    }

    private fun drawObjectEffects(c: Canvas, o: Obj) {
        if (game.buildLeft(o) > 0) return
        val px = sx((o.x + o.w / 2f - o.y - o.d / 2f) * 32f)
        val py = sy((o.x + o.w / 2f + o.y + o.d / 2f) * 16f)
        val kind = o.def.look.kind
        fillP.shader = null
        if (HamletArtCoastal.handles(o.def) || HamletArtWildlife.handles(o.def)) {
            val save = c.save()
            c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
            c.scale(if (o.flip) -zoom else zoom, zoom)
            val iso = buildingIso?.takeIf { it.c === c } ?: Iso(c, 0f, 0f).also { buildingIso = it }
            if (HamletArtCoastal.handles(o.def)) HamletArtCoastal.animate(iso, o.def, time + o.id * .37f, effectsEnabled)
            else HamletArtWildlife.draw(iso, o.def, o.def.w.toFloat(), o.def.d.toFloat(), if (effectsEnabled) time + o.id * .37f else 0f)
            c.restoreToCount(save)
        }
        if (kind == "asset") {
            val save = c.save()
            c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
            c.scale(if (o.flip) -zoom else zoom, zoom)
            val iso = buildingIso?.takeIf { it.c === c } ?: Iso(c, 0f, 0f).also { buildingIso = it }
            HamletArtAdventure.animate(iso, o.def.look.n, time + o.id * .37f, daylight, effectsEnabled)
            drawGarrison(iso, o)
            c.restoreToCount(save)
        }
        if (effectsEnabled && (kind == "h_cot" || kind == "h_farm" || kind == "s_shop")) {
            // Chimney coordinates match gableHouse: the plumes follow the building when rotated.
            val save = c.save()
            c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
            c.scale(if (o.flip) -zoom else zoom, zoom)
            val iso = buildingIso?.takeIf { it.c === c } ?: Iso(c, 0f, 0f).also { buildingIso = it }
            val wall = when (kind) { "h_farm" -> .95f; "s_shop" -> .85f; else -> .8f }
            for (i in 0..2) {
                val age = (time * .22f + o.id * .13f + i / 3f) % 1f
                iso.oval(.14f + (o.def.w - .28f) * .28f + age * .13f,
                    .14f + (o.def.d - .28f) * .3f - age * .08f,
                    .07f + wall + .6f + .28f + age * .65f,
                    1.5f + age * 5f, 1f + age * 3f, Iso.withAlpha(0xF1E7D3, ((1f - age) * .35f).coerceAtLeast(.008f)))
            }
            c.restoreToCount(save)
        }
        if (effectsEnabled && (kind == "d_fire" || kind == "d_torch")) {
            for (i in 0..3) {
                val a = (time * 0.7f + i * 0.27f + o.id * 0.13f) % 1f
                fillP.color = Color.argb(((1 - a) * 180).toInt(), 255, 195, 65)
                c.drawCircle(px + sin(time * 2f + i) * 6f * zoom, py - (15 + a * 28) * zoom, (2 - a) * zoom, fillP)
            }
        }
        if (kind == "s_mill" && o.def.look.n == 0) {
            val save = c.save()
            c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
            c.scale(if (o.flip) -zoom else zoom, zoom)
            HamletArtHomes.millWings(Iso(c, 0f, 0f), o.def.w.toFloat(), o.def.d.toFloat(), if (effectsEnabled) time * 0.45f else 0.35f)
            c.restoreToCount(save)
        }
        if (o.tier > 1) {
            fillP.color = Iso.opaque(0xF5D379)
            for (i in 1 until o.tier) c.drawCircle(px - 6 * zoom + i * 5 * zoom, py - 5 * zoom, 2 * zoom, fillP)
        }
    }

    /** The selected party lives in the village; these are presentation-only courtyard poses. */
    private fun drawGarrison(iso: Iso, o: Obj) {
        val slot = when (o.def.look.n) { 0 -> 2; 4 -> 1; 5 -> 0; else -> return }
        val adventure = game.adventure
        val heroId = adventure.teams[adventure.selectedTeam].getOrNull(slot) ?: return
        if (heroId in game.stewardship.posts) return
        val hero = adventure.heroes.getOrNull(heroId)?.takeIf { it.owned } ?: return
        val x = when (o.def.look.n) { 0 -> .88f; 4 -> 2.56f; else -> 1.5f }
        val y = if (o.def.look.n == 0) 2.86f else 2.57f
        iso.oval(x, y, .14f, 7f, 2.5f, Iso.withAlpha(0x293D36, .25f))
        HamletCharacterArt.draw(iso.c, true, hero.id, iso.px(x, y), iso.py(x, y, .14f), 32f,
            if (effectsEnabled) time + hero.id * .37f else 0f,
            if (o.def.look.n == 4 && effectsEnabled) HamletCharacterArt.Pose.ATTACK else HamletCharacterArt.Pose.IDLE)
    }

    private fun drawSteward(c: Canvas, o: Obj) {
        val post = stewardHomes.indexOfFirst { it === o }
        if (post < 0) return
        val heroId = game.stewardship.posts[post]
        val hero = game.adventure.heroes.getOrNull(heroId)?.takeIf { it.owned } ?: return
        val save = c.save()
        c.translate(sx((o.x - o.y) * 32f), sy((o.x + o.y) * 16f))
        c.scale(if (o.flip) -zoom else zoom, zoom)
        val iso = buildingIso?.takeIf { it.c === c } ?: Iso(c, 0f, 0f).also { buildingIso = it }
        val x = o.def.w * .72f; val y = o.def.d + .08f
        iso.oval(x, y, 0f, 7f, 2.5f, Iso.withAlpha(0x293D36, .25f))
        HamletCharacterArt.draw(c, true, hero.id, iso.px(x, y), iso.py(x, y, 0f), 32f,
            if (effectsEnabled) time + hero.id * .37f else 0f,
            if (effectsEnabled && post == 0) HamletCharacterArt.Pose.CAST else HamletCharacterArt.Pose.IDLE)
        c.restoreToCount(save)
    }

    private fun drawAtmosphere(c: Canvas) {
        fillP.shader = null
        // Particules déterministes, sans allocation ni RNG dans la boucle de dessin.
        for (i in 0 until 24) {
            val px = ((i * 137.5f + time * (2 + i % 3)) % (width + 40)) - 20
            val py = height * ((i * 0.173f) % 1f) + sin(time * 0.7f + i) * 14
            val alpha = ((0.5f + sin(time * 1.5f + i) * 0.5f) * if (daylight < 0.5f) 150 else 50).toInt()
            fillP.color = Color.argb(alpha, 244, 238, 153)
            c.drawCircle(px, py, (if (daylight < 0.5f) 1.4f else 0.7f) * density, fillP)
        }
    }

    // ------------------------------------------------------------------ habitants

    private fun drawVillager(c: Canvas, v: Villager) {
        val px = sx((v.x - v.y) * 32f)
        val py = sy((v.x + v.y) * 16f)
        if (px < -30 * zoom || px > width + 30 * zoom || py < -50 * zoom || py > height + 30 * zoom) return
        val z = zoom
        val phase = if (effectsEnabled) time * v.speed + v.nameIndex * .37f else 0f
        fillP.shader = null
        fillP.color = Color.argb(60, 0, 0, 0)
        rect.set(px - 7f * z, py - 2.5f * z, px + 7f * z, py + 2.5f * z)
        c.drawOval(rect, fillP)
        HamletCharacterArt.drawResident(c, v.nameIndex, px, py + 2f * z, 38f * z, phase,
            walking = effectsEnabled && v.pause <= 0,
            working = effectsEnabled && v.pause > 0 && v.role != 0,
            mirrored = !v.faceRight)
        if (v.gift && !photoMode) {
            val g = py - 40f * z - if (effectsEnabled) abs(sin(time * 3f)) * 3f * z else 0f
            fillP.color = Iso.opaque(0xE0A81A)
            c.drawCircle(px, g, 6f * z, fillP)
            fillP.color = Iso.opaque(0xF2C230)
            c.drawCircle(px, g, 4.6f * z, fillP)
            HamletGlyphs.draw(c, "coin", px, g, 9f * z)
        }
    }

    fun villagerAt(px: Float, py: Float): Villager? {
        for (v in villagers) {
            val vx = sx((v.x - v.y) * 32f); val vy = sy((v.x + v.y) * 16f) - 18f * zoom
            if (abs(px - vx) < 26f * zoom && abs(py - vy) < 32f * zoom) return v
        }
        return null
    }

    // ------------------------------------------------------------------ bulles

    private fun bubbleVisible(o: Obj, status: Status): Boolean = when (status) {
        Status.NONE -> o.def.look.kind == "asset" && game.buildLeft(o) == 0L
        Status.ORDER_BUSY, Status.GROWING, Status.PRODUCING -> false
        else -> true
    }

    private fun bubbleY(o: Obj, top: Float): Float =
        top - 16f * zoom + if (effectsEnabled) sin(time * 3.2f + o.id) * 3f * zoom else 0f

    private fun bubbleRadius() = 17f * zoom.coerceIn(0.6f, 1.6f)

    private fun drawBubble(c: Canvas, o: Obj, box: RectF, s: Sprite) {
        val st = game.status(o)
        if (!bubbleVisible(o, st)) return
        val def = o.def
        val cxw = (o.x + o.w / 2f - (o.y + o.d / 2f)) * 32f
        val bx = sx(cxw)
        val top = box.top + s.topRow * (zoom / HamletArt.S)
        val by = bubbleY(o, top)
        val r = bubbleRadius()
        val gold = st == Status.TAX || st == Status.ORDER_DONE || st == Status.RIPE || st == Status.GIFT || st == Status.PRODUCT
        val back = when {
            st == Status.WITHERED -> 0xFFA8A090.toInt()
            gold -> 0xFFF6D45A.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        // pointe vers l'objet
        path.reset()
        path.moveTo(bx - r * 0.35f, by + r * 0.8f); path.lineTo(bx + r * 0.35f, by + r * 0.8f); path.lineTo(bx, by + r * 1.45f); path.close()
        fillP.shader = null
        fillP.color = 0xFF7C7060.toInt()
        c.drawPath(path, fillP)
        fillP.color = 0xFF7C7060.toInt()
        c.drawCircle(bx, by, r + 2f * zoom, fillP)
        fillP.color = back
        c.drawCircle(bx, by, r, fillP)
        when (st) {
            Status.RIPE -> {
                val crop = game.cropOf(o)
                if (crop != null) {
                    fillP.color = Iso.opaque(crop.leaf)
                    c.drawCircle(bx, by + r * 0.1f, r * 0.62f, fillP)
                    fillP.color = Iso.opaque(crop.fruit)
                    c.drawCircle(bx - r * 0.06f, by, r * 0.5f, fillP)
                    fillP.color = Color.argb(120, 255, 255, 255)
                    c.drawCircle(bx - r * 0.22f, by - r * 0.2f, r * 0.14f, fillP)
                }
            }
            Status.WITHERED -> HamletGlyphs.draw(c, "leaf", bx, by, r * 1.3f, 0x7A6A3A)
            Status.TAX -> HamletGlyphs.draw(c, "coin", bx, by, r * 1.4f)
            Status.GIFT -> HamletGlyphs.draw(c, "gift", bx, by, r * 1.35f, 0xC8392E)
            Status.ORDER_NEW -> HamletGlyphs.draw(c, def.glyph, bx, by, r * 1.3f, 0x3A7AD8)
            Status.ORDER_DONE -> HamletGlyphs.draw(c, def.glyph, bx, by, r * 1.3f, 0xC8392E)
            Status.FEED -> HamletGlyphs.draw(c, "leaf", bx, by, r * 1.3f, 0x609445)
            Status.PRODUCT -> HamletGlyphs.draw(c, HamletLife.produce.getValue(o.t).glyph, bx, by, r * 1.3f)
            Status.NONE -> HamletGlyphs.draw(c, def.glyph, bx, by, r * 1.3f, 0x397F83)
            Status.BUILDING -> HamletGlyphs.draw(c, "hourglass", bx, by, r * 1.3f, 0x866A47)
            else -> {}
        }
    }

    // ------------------------------------------------------------------ sélection, fantôme, pinceau

    private fun diamondPath(x: Float, y: Float, w: Float, d: Float, z: Float = 0f) {
        path.reset()
        fun pt(i: Float, j: Float, first: Boolean) {
            val px = sx((i - j) * 32f); val py = sy((i + j) * 16f - z * Iso.ZH)
            if (first) path.moveTo(px, py) else path.lineTo(px, py)
        }
        pt(x, y, true); pt(x + w, y, false); pt(x + w, y + d, false); pt(x, y + d, false)
        path.close()
    }

    private fun drawSelection(c: Canvas) {
        val o = selected ?: return
        if (game.objs[o.id] !== o) return
        diamondPath(o.x.toFloat(), o.y.toFloat(), o.w.toFloat(), o.d.toFloat())
        fillP.shader = null
        fillP.color = Color.argb((40 + if (effectsEnabled) 25 * sin(time * 5f) else 0f).toInt(), 255, 240, 120)
        c.drawPath(path, fillP)
        strokeP.color = Color.argb(230, 255, 240, 120)
        strokeP.strokeWidth = 2.6f * density / 2.5f
        c.drawPath(path, strokeP)
    }

    private fun drawGhost(c: Canvas) {
        val def = ghostDef ?: return
        if (def.isPath) return
        val w = if (ghostFlip) def.d else def.w
        val d = if (ghostFlip) def.w else def.d
        diamondPath(ghostX.toFloat(), ghostY.toFloat(), w.toFloat(), d.toFloat())
        fillP.shader = null
        fillP.color = if (ghostOk) Color.argb(110, 90, 220, 90) else Color.argb(120, 230, 70, 60)
        c.drawPath(path, fillP)
        strokeP.color = if (ghostOk) Color.argb(240, 200, 255, 200) else Color.argb(240, 255, 190, 180)
        strokeP.strokeWidth = 2.6f * density / 2.5f
        c.drawPath(path, strokeP)
        val s = art.forDef(def, ghostFlip, ghostCrop, if (ghostCrop != null) 2 else -1)
        val k = zoom / HamletArt.S
        val wx = (ghostX - ghostY) * 32f; val wy = (ghostX + ghostY) * 16f
        mat.reset()
        mat.postScale(k, k)
        mat.postTranslate(sx(wx) - s.ox * k, sy(wy) - s.oy * k)
        bmpPaint.alpha = if (ghostOk) 215 else 150
        c.drawBitmap(s.bmp, mat, bmpPaint)
        bmpPaint.alpha = 255
    }

    private fun drawBrush(c: Canvas) {
        val t = brushTile ?: return
        diamondPath(t[0].toFloat(), t[1].toFloat(), 1f, 1f)
        fillP.shader = null
        fillP.color = Color.argb(90, 255, 255, 255)
        c.drawPath(path, fillP)
        strokeP.color = Color.argb(230, 255, 255, 255)
        strokeP.strokeWidth = 2.2f * density / 2.5f
        c.drawPath(path, strokeP)
    }

    // ------------------------------------------------------------------ nuit

    private fun drawNight(c: Canvas) {
        val dark = 1f - daylight
        if (dark <= 0.01f) return
        c.drawColor(Color.argb((dark * 120).toInt(), 12, 22, 70))
        if (dark < 0.15f) return
        for (l in lights) {
            val px = sx((l[0] - l[1]) * 32f); val py = sy((l[0] + l[1]) * 16f - l[2] * Iso.ZH)
            val r = 70f * zoom * l[3]
            if (px < -r || px > width + r || py < -r || py > height + r) continue
            lightMatrix.setScale(r, r); lightMatrix.postTranslate(px, py)
            warmLight.setLocalMatrix(lightMatrix)
            glowP.shader = warmLight; glowP.alpha = (dark * 255).toInt()
            c.drawCircle(px, py, r, glowP)
        }
        glowP.shader = null
        glowP.alpha = 255
    }

    // ------------------------------------------------------------------ texte flottant

    private fun drawFloaters(c: Canvas) {
        val now = game.now()
        for (f in floaters) {
            val age = (now - f.t0) / 1500f
            if (age < 0 || age > 1) continue
            val px = sx((f.x - f.y) * 32f)
            val py = sy((f.x + f.y) * 16f - f.z * Iso.ZH) - age * 46f * density / 2.5f
            val alpha = if (age < 0.7f) 255 else ((1f - age) / 0.3f * 255).toInt()
            val scale = if (age < 0.12f) 0.6f + age / 0.12f * 0.55f else 1.1f - (age - 0.12f).coerceAtMost(0.2f) * 0.5f
            val size = 17f * density * scale
            var total = 0f
            textP.textSize = size
            for (p in f.parts) total += textP.measureText(p.text) + size * 1.15f
            var x = px - total / 2
            for (p in f.parts) {
                if (p.glyph != null) {
                    HamletGlyphs.draw(c, p.glyph, x + size * 0.5f, py, size * 1.05f)
                    x += size * 1.15f
                }
                val tw = textP.measureText(p.text)
                textP.style = Paint.Style.STROKE
                textP.strokeWidth = size * 0.22f
                textP.color = Color.argb(alpha, 58, 42, 16)
                textP.textAlign = Paint.Align.LEFT
                c.drawText(p.text, x, py + size * 0.35f, textP)
                textP.style = Paint.Style.FILL
                textP.color = (p.color and 0xFFFFFF) or (alpha shl 24)
                c.drawText(p.text, x, py + size * 0.35f, textP)
                x += tw + size * 0.25f
            }
        }
        textP.textAlign = Paint.Align.CENTER
    }

    // ------------------------------------------------------------------ toucher

    /** Action markers take priority over silhouettes and passing villagers. */
    fun actionAt(px: Float, py: Float): Obj? {
        ensureSorted()
        val k = zoom / HamletArt.S
        var nearest: Obj? = null
        var nearestDistance = Float.POSITIVE_INFINITY
        // The action marker is part of the building's touch target, even above its roof.
        if (!photoMode) for (i in sorted.indices.reversed()) {
            val o = sorted[i]
            if (o === hidden || !bubbleVisible(o, game.status(o))) continue
            val s = art.forObj(o, game)
            val b = spriteBox(o, s)
            val bx = sx((o.x + o.w / 2f - o.y - o.d / 2f) * 32f)
            val by = bubbleY(o, b.top + s.topRow * k)
            val r = max(bubbleRadius(), 20f * density)
            val distance = (px - bx) * (px - bx) + (py - by) * (py - by)
            if (distance <= r * r && distance < nearestDistance) { nearest = o; nearestDistance = distance }
        }
        return nearest
    }

    /** L'objet visible sous un doigt : on teste les pixels du dessin, pas seulement l'empreinte au sol. */
    fun objectAt(px: Float, py: Float): Obj? {
        actionAt(px, py)?.let { return it }
        val k = zoom / HamletArt.S
        for (i in sorted.indices.reversed()) {
            val o = sorted[i]
            if (o === hidden) continue
            // Wildlife is animated directly in the scene; use its resting silhouette for touch picking.
            val s = if (HamletArtWildlife.handles(o.def) && game.buildLeft(o) == 0L) art.forDef(o.def, o.flip) else art.forObj(o, game)
            val b = spriteBox(o, s)
            if (!b.contains(px, py)) continue
            val ix = ((px - b.left) / k).toInt().coerceIn(0, s.bmp.width - 1)
            val iy = ((py - b.top) / k).toInt().coerceIn(0, s.bmp.height - 1)
            if (Color.alpha(s.bmp.getPixel(ix, iy)) > 40) return o
        }
        val t = tileAt(px, py)
        return game.objAt(floor(t[0]).toInt(), floor(t[1]).toInt())
    }
}
