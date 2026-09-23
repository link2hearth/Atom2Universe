package com.Atom2Universe.app.games.cosmorun

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/** Petits modèles polygonaux éclairés et projetés en perspective, sans textures ni PNG.
 * Les faces et les chemins sont réutilisés : aucune allocation de maillage par image. */
class CosmoRunRenderer {
    companion object {
        val SUIT_COLORS = intArrayOf(0xFF54E5E0.toInt(), 0xFFFFB85C.toInt(), 0xFFD09BFF.toInt())
        private val BOX_FACES = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(4, 7, 6, 5), intArrayOf(0, 4, 5, 1),
            intArrayOf(3, 2, 6, 7), intArrayOf(0, 3, 7, 4), intArrayOf(1, 5, 6, 2)
        )
        private val LIGHT = floatArrayOf(.82f, .55f, 1.15f, .48f, .7f, .96f)
        private const val INK = 0xFF091124.toInt()
        private const val WHITE = 0xFFE9F6FF.toInt()
        private const val ORANGE = 0xFFFFB45E.toInt()
        private const val PINK = 0xFFFF527D.toInt()
        private val SIDES = intArrayOf(-1, 1)
        private const val NEAR_Z = -7f
        private const val FAR_Z = 144f
    }

    private class Face {
        // Un quadrilatère coupé au plan proche peut avoir cinq sommets.
        val xy = FloatArray(10)
        var count = 4
        var depth = 0f
        var color = 0
    }
    private val pool = Array(2600) { Face() }
    private val visible = ArrayList<Face>(2600)
    private val farFirst = Comparator<Face> { a, b -> b.depth.compareTo(a.depth) }
    private var used = 0
    private val vertices = FloatArray(24)
    private val polygon = FloatArray(12)
    private val clipped = FloatArray(15)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val stars = FloatArray(240).also { a ->
        val r = Random(71)
        for (i in a.indices) a[i] = r.nextFloat()
    }
    private var w = 1f
    private var h = 1f
    private var focal = 1f
    private var horizon = 1f
    private var cameraY = 1f
    private var cameraX = 0f
    private var bend = 0f
    private var accent = SUIT_COLORS[0]
    private var sky: Shader? = null
    private var glow: Shader? = null
    private var lastTheme = -1
    private var lastWidth = 0
    private var lastHeight = 0
    private var time = 0f
    var suit = 0
    var bottomInset = 0f

    private fun scale(z: Float) = focal / (z + 9f).coerceAtLeast(1f)
    private fun px(x: Float, z: Float) = w * .5f + (x - cameraX + bend * z * z) * scale(z)
    private fun py(y: Float, z: Float) = horizon + (cameraY - y) * scale(z)
    fun playerScreenX(game: CosmoRunGame) = px(CosmoRunGame.laneX(game.laneF), 0f)
    fun playerScreenY(game: CosmoRunGame) = py(.9f + game.jumpHeight, 0f)

    fun draw(canvas: Canvas, width: Int, height: Int, game: CosmoRunGame, clock: Float, demo: Boolean) {
        w = width.toFloat(); h = height.toFloat(); time = clock
        focal = min(w, h * .95f) * 1.06f
        horizon = h * .29f
        val feetY = min(h * .84f, h - bottomInset - h * .025f)
        cameraY = (feetY - horizon) * 9f / focal
        cameraX = CosmoRunGame.laneX(game.laneF) * .08f
        bend = sin(game.distance / 240f) * .00085f
        val theme = game.sector % 3
        accent = SUIT_COLORS[theme]
        if (width != lastWidth || height != lastHeight || theme != lastTheme) {
            lastWidth = width; lastHeight = height; lastTheme = theme
            val top = when (theme) { 1 -> 0xFF21182E.toInt(); 2 -> 0xFF191637.toInt(); else -> 0xFF071729.toInt() }
            sky = LinearGradient(0f, 0f, 0f, h, intArrayOf(INK, top, 0xFF25314A.toInt(), INK),
                floatArrayOf(0f, .25f, .48f, 1f), Shader.TileMode.CLAMP)
            glow = RadialGradient(w * .57f, horizon, w * .7f,
                intArrayOf(tint(accent, .55f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        paint.shader = sky; canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = glow; canvas.drawRect(0f, 0f, w, h * .8f, paint); paint.shader = null
        drawSpace(canvas, theme)
        used = 0; visible.clear()
        val travel = if (demo) clock * 10f else game.distance
        // Le sol forme une couche dédiée : un grand panneau proche ne doit pas recouvrir
        // le personnage à cause du tri par profondeur moyenne de ses sommets.
        drawDeck(travel)
        drawFaces(canvas)
        used = 0; visible.clear()
        drawArchitecture(travel)
        for (e in game.entities) if (e.z > -5f && e.z < 125f && !(e.collectible && e.resolved)) entity(e)
        astronaut(game, demo)
        drawFaces(canvas)
        // Visière de protection : anneau léger, le personnage reste lisible.
        if (!demo && (game.shield || game.boostTime > 0f)) {
            val x = playerScreenX(game); val y = playerScreenY(game)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1.5f, w * .004f)
            paint.color = if (game.boostTime > 0f) ORANGE else 0x7054E5E0
            canvas.drawOval(x - scale(0f) * .65f, y - scale(0f) * 1.02f,
                x + scale(0f) * .65f, y + scale(0f) * .92f, paint)
            paint.style = Paint.Style.FILL
        }
        if (game.boostTime > 0f) {
            paint.color = 0x70FFCE88; paint.strokeWidth = 2f
            for (i in 0..15) {
                val f = ((clock * 1.8f + i * .071f) % 1f)
                val side = if (i % 2 == 0) -1f else 1f
                val x = w * .5f + side * w * (.3f + f * .3f)
                canvas.drawLine(x, horizon + f * h, x + side * w * .06f, horizon + f * h + h * .09f, paint)
            }
        }
    }

    private fun drawSpace(c: Canvas, theme: Int) {
        for (i in 0 until 80) {
            val x = stars[i * 3] * w
            val y = stars[i * 3 + 1] * h * .52f
            paint.color = Color.argb((100 + 100 * stars[i * 3 + 2]).toInt(), 201, 223, 255)
            c.drawCircle(x, y, .8f + stars[i * 3 + 2] * w * .003f, paint)
        }
        val radius = min(w * .22f, h * .18f)
        val cx = w * .77f; val cy = horizon * .66f
        paint.color = when (theme) { 1 -> 0xFFB47A67.toInt(); 2 -> 0xFF8276B2.toInt(); else -> 0xFF457A96.toInt() }
        c.drawCircle(cx, cy, radius, paint)
        c.save(); path.rewind(); path.addCircle(cx, cy, radius, Path.Direction.CW); c.clipPath(path)
        paint.color = 0x284CE1E0
        for (i in 0..6) c.drawOval(cx - radius * 1.4f, cy - radius + i * radius * .32f,
            cx + radius * 1.4f, cy - radius * .7f + i * radius * .32f, paint)
        paint.color = 0xA0081028.toInt()
        c.drawCircle(cx - radius * .5f, cy - radius * .14f, radius * 1.1f, paint)
        c.restore()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .065f; paint.color = 0x607AD6E8
        c.save(); c.rotate(-24f, cx, cy)
        c.drawOval(cx - radius * 1.5f, cy - radius * .26f, cx + radius * 1.5f, cy + radius * .26f, paint)
        c.restore(); paint.style = Paint.Style.FILL
    }

    private fun drawFaces(canvas: Canvas) {
        visible.sortWith(farFirst)
        for (face in visible) {
            path.rewind(); path.moveTo(face.xy[0], face.xy[1])
            for (i in 1 until face.count) path.lineTo(face.xy[i * 2], face.xy[i * 2 + 1])
            path.close()
            paint.color = face.color; paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
    }

    private fun drawDeck(travel: Float) {
        val first = floor((travel + NEAR_Z - 3f) / 6f).toInt()
        val last = floor((travel + FAR_Z) / 6f).toInt()
        for (segment in first..last) {
            val worldZ = segment * 6f
            val z = worldZ - travel
            val segmentAccent = SUIT_COLORS[sceneryTheme(worldZ)]
            box(0f, -.25f, z, 8.25f, .45f, 5.95f, if (segment % 2 == 0) 0xFF26384D.toInt() else 0xFF203145.toInt())
            // Joints longitudinaux, panneaux de guidage et rails latéraux lumineux.
            for (lane in 0..5) {
                val x = (lane - 2.5f) * CosmoRunGame.LANE_WIDTH
                box(x, .008f, z, .025f, .015f, 5.8f, 0xFF4B6578.toInt())
            }
            for (side in SIDES) {
                box(side * 4.23f, .04f, z, .15f, .13f, 5.95f, segmentAccent)
                box(side * 4.6f, -.4f, z, .5f, .7f, 5.9f, 0xFF101F32.toInt())
                box(side * 4.58f, .23f, z, .13f, .13f, 3.5f, WHITE)
            }
        }
    }

    private fun sceneryTheme(worldZ: Float) = (worldZ.coerceAtLeast(0f).toInt() / 600) % 3

    private fun drawArchitecture(travel: Float) {
        val first = floor((travel + NEAR_Z - 5f - 3f) / 24f).toInt()
        val last = floor((travel + FAR_Z - 5f) / 24f).toInt()
        for (segment in first..last) {
            // L'identité dépend de la position absolue, jamais de l'indice dans la fenêtre
            // visible : une arche conserve son existence et sa forme jusqu'à son passage.
            val worldZ = segment * 24f + 5f
            val z = worldZ - travel
            val variant = Math.floorMod(segment, 4)
            val theme = sceneryTheme(worldZ)
            val segmentAccent = SUIT_COLORS[theme]
            for (side in SIDES) {
                val x = side * (7.8f + variant % 2 * 2f)
                val height = 5f + (variant * 7 % 4) * 2f
                if (theme == 1) {
                    crystal(x, height * .35f, z, 2.1f, height * .7f, 0xFF765375.toInt(), .3f * variant)
                    crystal(x + side * 2f, 2.5f, z + 5f, 1.3f, 5f, segmentAccent, .7f)
                } else {
                    box(x, height * .5f - 2f, z, 2.7f, height, 3.8f, 0xFF203249.toInt())
                    box(x, height - 1.9f, z, 2.9f, .18f, 4f, segmentAccent)
                    for (floor in 0..3) box(x - side * 1.37f, floor * 1.6f, z, .04f, .3f, 2.5f, 0xFF5596AF.toInt())
                }
                box(side * 4.65f, 1.3f, z, .24f, 2.6f, .35f, 0xFF597087.toInt())
                box(side * 4.65f, 2.7f, z, .35f, .3f, .5f, segmentAccent)
            }
            if (theme == 2 || segment % 2 == 0) {
                for (side in SIDES) box(side * 5f, 3.3f, z, .42f, 6.6f, .6f, 0xFF40536D.toInt())
                box(0f, 6.5f, z, 10.4f, .42f, .7f, 0xFF40536D.toInt())
                box(0f, 6.25f, z - .38f, 9.6f, .075f, .05f, segmentAccent)
            }
        }
    }

    private fun entity(e: CosmoRunGame.Entity) {
        val x = e.x; val z = e.z
        when (e.type) {
            CosmoRunGame.EntityType.CARGO -> {
                box(x, .95f, z, 1.3f, 1.9f, 1.5f, 0xFF566B82.toInt())
                box(x, 1.9f, z, 1.38f, .12f, 1.58f, 0xFF91A3B4.toInt())
                box(x, .12f, z, 1.38f, .2f, 1.58f, 0xFF17263B.toInt())
                for (side in intArrayOf(-1, 1)) {
                    box(x + side * .44f, .95f, z - .77f, .12f, 1.6f, .035f, ORANGE)
                }
                box(x, .95f, z - .8f, .34f, .44f, .06f, INK)
                box(x, 1.02f, z - .84f, .18f, .07f, .02f, PINK)
            }
            CosmoRunGame.EntityType.HURDLE -> {
                box(x, .42f, z, 1.43f, .84f, .42f, ORANGE)
                box(x, .44f, z - .23f, 1.25f, .38f, .025f, INK)
                for (i in -2..2) box(x + i * .24f, .44f, z - .25f, .1f, .32f, .025f, ORANGE, rz = -.4f)
                box(x, .88f, z, 1.46f, .08f, .48f, WHITE)
            }
            CosmoRunGame.EntityType.LASER -> {
                for (side in intArrayOf(-1, 1)) {
                    box(x + side * .68f, 1.1f, z, .12f, 2.2f, .27f, 0xFF6E597C.toInt())
                    box(x + side * .68f, 2.14f, z, .2f, .18f, .34f, PINK)
                }
                box(x, 1.35f, z, 1.3f, .18f, .2f, PINK)
                box(x, 1.35f, z - .11f, 1.3f, .055f, .02f, WHITE)
                box(x, 1.72f, z, 1.3f, .42f, .06f, 0x65FF527D)
            }
            CosmoRunGame.EntityType.GAP -> {
                box(x, .015f, z, 1.5f, .025f, 2.6f, 0xFF020713.toInt())
                for (side in intArrayOf(-1, 1)) {
                    box(x + side * .73f, .045f, z, .07f, .05f, 2.65f, PINK)
                    box(x, .045f, z + side * 1.3f, 1.5f, .05f, .08f, ORANGE)
                }
                for (i in -1..1) box(x + i * .35f, .055f, z - 1.8f, .13f, .02f, .4f, ORANGE)
            }
            CosmoRunGame.EntityType.DRONE -> {
                val y = 1.65f + sin(time * 4f + e.variant) * .12f
                box(x, y, z, .78f, .55f, .65f, 0xFFB17B96.toInt(), rz = sin(time * 2f) * .08f)
                box(x, y, z - .36f, .45f, .17f, .07f, PINK)
                for (side in intArrayOf(-1, 1)) {
                    box(x + side * .54f, y, z, .45f, .1f, .2f, 0xFF445770.toInt())
                    box(x + side * .57f, y + .1f, z, .44f, .05f, .44f, accent, ry = time * 12f)
                }
                box(x, .02f, z, 1.2f, .02f, .9f, 0x30FF527D)
            }
            else -> {
                val y = e.y + if (e.attracting) 0f else sin(time * 3f + z * .12f) * .12f
                val color = when (e.type) {
                    CosmoRunGame.EntityType.ATOM -> ORANGE
                    CosmoRunGame.EntityType.SHIELD -> SUIT_COLORS[0]
                    CosmoRunGame.EntityType.MAGNET -> SUIT_COLORS[2]
                    else -> PINK
                }
                if (e.type == CosmoRunGame.EntityType.ATOM) {
                    crystal(x, y, z, .22f, .5f, color, time * 2f)
                    ring(x, y, z, .4f, .035f, color, time)
                } else {
                    box(x, y, z, .57f, .57f, .57f, color, ry = time, rz = .2f)
                    // Symboles géométriques distincts : bouclier, aimant en U et éclair.
                    when (e.type) {
                        CosmoRunGame.EntityType.SHIELD -> crystal(x, y, z - .46f, .18f, .35f, WHITE, 0f)
                        CosmoRunGame.EntityType.MAGNET -> {
                            box(x, y - .13f, z - .46f, .32f, .09f, .03f, WHITE)
                            for (side in intArrayOf(-1, 1)) box(x + side * .12f, y, z - .46f, .08f, .27f, .03f, WHITE)
                        }
                        else -> box(x, y, z - .46f, .1f, .4f, .04f, WHITE, rz = -.45f)
                    }
                    ring(x, y, z, .6f, .04f, color, -time)
                }
            }
        }
    }

    private fun astronaut(game: CosmoRunGame, demo: Boolean) {
        val x = CosmoRunGame.laneX(game.laneF)
        val moving = game.isRunning || demo
        val phase = if (moving) time * 15f else 0f
        val stride = if (game.isJumping || game.isSliding) 0f else sin(phase) * .62f
        val jump = if (demo) cameraY - (h * .28f - horizon) / scale(0f) + sin(time * 1.7f) * .08f else game.jumpHeight
        val crouch = if (game.isSliding) .66f else 0f
        val y = jump - crouch
        val lean = (game.laneTarget - game.laneF) * -.22f
        val color = SUIT_COLORS[suit.coerceIn(0, 2)]
        if (!demo) box(x, .025f, .1f, .95f - jump * .14f, .025f, .6f, 0x60030814)
        // Bottes, tibias, épaules et bras indépendants : véritable silhouette articulée.
        for (side in intArrayOf(-1, 1)) {
            val leg = if (game.isSliding) -.9f else side * stride
            box(x + side * .2f, .44f + jump - crouch * .32f, if (game.isSliding) -.32f else 0f,
                .26f, .72f, .29f, WHITE, rx = leg)
            box(x + side * .2f, .12f + jump - crouch * .1f + abs(sin(leg)) * .15f, -sin(leg) * .35f - .08f,
                .31f, .22f, .46f, 0xFF31455F.toInt(), rx = leg * .4f)
            box(x + side * .45f, 1.08f + y, .02f, .23f, .63f, .27f, color,
                rx = if (game.isJumping) -1f else -side * stride, rz = side * .15f + lean)
            box(x + side * .48f, .77f + y, sin(side * stride) * .22f, .24f, .22f, .27f, WHITE)
        }
        box(x, 1.05f + y, 0f, .68f, .72f, .46f, WHITE, rz = lean)
        box(x, .76f + y, -.015f, .7f, .14f, .49f, 0xFF273C56.toInt())
        // Sac dorsal orienté vers la caméra avec deux propulseurs.
        box(x, 1.12f + y, -.33f, .53f, .59f, .28f, 0xFF334A65.toInt(), rz = lean)
        box(x, 1.18f + y, -.49f, .3f, .27f, .035f, color)
        for (side in intArrayOf(-1, 1)) {
            box(x + side * .22f, .83f + y, -.38f, .17f, .22f, .22f, 0xFF7890A8.toInt())
            if (game.isJumping || game.boostTime > 0f || demo) {
                crystal(x + side * .22f, .51f + y, -.4f, .09f, .4f + sin(time * 40f) * .09f, color, 0f)
            }
        }
        box(x, 1.63f + y, .02f, .75f, .63f, .62f, WHITE, rz = lean)
        box(x, 1.61f + y, -.31f, .61f, .33f, .055f, 0xFF122A44.toInt(), rz = lean)
        box(x, 1.71f + y, -.347f, .46f, .045f, .02f, color)
        for (side in intArrayOf(-1, 1)) box(x + side * .39f, 1.62f + y, -.01f, .09f, .26f, .3f, color)
        box(x + .29f, 2.03f + y, .04f, .045f, .27f, .045f, 0xFF879BAF.toInt())
        box(x + .29f, 2.18f + y, .04f, .075f, .07f, .075f, ORANGE)
    }

    private fun ring(x: Float, y: Float, z: Float, radius: Float, thick: Float, color: Int, rotation: Float) {
        for (i in 0..7) {
            val a = i * PI.toFloat() / 4f + rotation
            box(x + cos(a) * radius, y + sin(a) * radius * .6f, z + sin(a) * radius * .35f,
                thick, thick, thick, color)
        }
    }

    private fun crystal(x: Float, y: Float, z: Float, radius: Float, height: Float, color: Int, rotation: Float) {
        for (i in 0..3) {
            val a = rotation + i * PI.toFloat() / 2f
            val b = a + PI.toFloat() / 2f
            triangle(x, y + height / 2f, z, x + cos(a) * radius, y, z + sin(a) * radius,
                x + cos(b) * radius, y, z + sin(b) * radius, tint(color, .65f + i * .14f))
            triangle(x, y - height / 2f, z, x + cos(b) * radius, y, z + sin(b) * radius,
                x + cos(a) * radius, y, z + sin(a) * radius, tint(color, .5f + i * .12f))
        }
    }

    private fun box(x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float, color: Int,
                    rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
        // Ne pas supprimer un bloc entier dès que son premier sommet sort du champ.
        if (z + (width + height + depth) * .5f < NEAR_Z) return
        val cx = cos(rx); val sx = sin(rx); val cy = cos(ry); val sy = sin(ry); val cz = cos(rz); val sz = sin(rz)
        for (i in 0..7) {
            val vx = (if (i == 0 || i == 3 || i == 4 || i == 7) -.5f else .5f) * width
            val vy = (if (i == 0 || i == 1 || i == 4 || i == 5) .5f else -.5f) * height
            val vz = (if (i < 4) -.5f else .5f) * depth
            val y1 = vy * cx - vz * sx; val z1 = vy * sx + vz * cx
            val x2 = vx * cy + z1 * sy; val z2 = -vx * sy + z1 * cy
            vertices[i * 3] = x + x2 * cz - y1 * sz
            vertices[i * 3 + 1] = y + x2 * sz + y1 * cz
            vertices[i * 3 + 2] = z + z2
        }
        for (f in BOX_FACES.indices) {
            // Faces arrière et inférieures d'un bloc non tourné sont invisibles depuis la caméra.
            if (rx == 0f && ry == 0f && rz == 0f) {
                if (f == 1 || f == 3 && cameraY >= y - height / 2f ||
                    f == 2 && cameraY <= y + height / 2f ||
                    f == 4 && cameraX >= x - width / 2f || f == 5 && cameraX <= x + width / 2f) continue
            }
            for (j in 0..3) {
                val vi = BOX_FACES[f][j] * 3
                polygon[j * 3] = vertices[vi]
                polygon[j * 3 + 1] = vertices[vi + 1]
                polygon[j * 3 + 2] = vertices[vi + 2]
            }
            emitFace(4, tint(color, LIGHT[f]))
        }
    }

    private fun triangle(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                         x3: Float, y3: Float, z3: Float, color: Int) {
        polygon[0] = x1; polygon[1] = y1; polygon[2] = z1
        polygon[3] = x2; polygon[4] = y2; polygon[5] = z2
        polygon[6] = x3; polygon[7] = y3; polygon[8] = z3
        emitFace(3, color)
    }

    /** Découpe au plan proche avant projection, avec des buffers réutilisés. */
    private fun emitFace(count: Int, color: Int) {
        if (used == pool.size) return
        var clippedCount = 0
        var previous = (count - 1) * 3
        for (i in 0 until count) {
            val current = i * 3
            val previousZ = polygon[previous + 2]
            val currentZ = polygon[current + 2]
            val previousInside = previousZ >= NEAR_Z
            val currentInside = currentZ >= NEAR_Z
            if (previousInside != currentInside) {
                val t = (NEAR_Z - previousZ) / (currentZ - previousZ)
                val out = clippedCount++ * 3
                clipped[out] = polygon[previous] + (polygon[current] - polygon[previous]) * t
                clipped[out + 1] = polygon[previous + 1] + (polygon[current + 1] - polygon[previous + 1]) * t
                clipped[out + 2] = NEAR_Z
            }
            if (currentInside) {
                val out = clippedCount++ * 3
                clipped[out] = polygon[current]
                clipped[out + 1] = polygon[current + 1]
                clipped[out + 2] = currentZ
            }
            previous = current
        }
        if (clippedCount < 3) return
        val face = pool[used++]
        face.count = clippedCount
        var depth = 0f
        for (i in 0 until clippedCount) {
            val x = clipped[i * 3]; val y = clipped[i * 3 + 1]; val z = clipped[i * 3 + 2]
            face.xy[i * 2] = px(x, z); face.xy[i * 2 + 1] = py(y, z)
            depth += z - y * .002f
        }
        face.depth = depth / clippedCount
        face.color = fog(color, face.depth)
        visible.add(face)
    }

    private fun tint(color: Int, amount: Float) = Color.argb(Color.alpha(color),
        (Color.red(color) * amount).toInt().coerceIn(0, 255),
        (Color.green(color) * amount).toInt().coerceIn(0, 255),
        (Color.blue(color) * amount).toInt().coerceIn(0, 255))

    private fun fog(color: Int, z: Float): Int {
        val f = (z / 145f).coerceIn(0f, .82f)
        val visibility = ((FAR_Z - z) / 24f).coerceIn(0f, 1f)
        return Color.argb((Color.alpha(color) * visibility).toInt(), (Color.red(color) * (1f - f) + 23f * f).toInt(),
            (Color.green(color) * (1f - f) + 37f * f).toInt(), (Color.blue(color) * (1f - f) + 57f * f).toInt())
    }
}
