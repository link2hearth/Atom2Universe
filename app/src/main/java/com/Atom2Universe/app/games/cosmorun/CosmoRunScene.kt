package com.Atom2Universe.app.games.cosmorun

import kotlin.math.*

/** La scène en polygones : projection, éclairage et découpe au plan proche, en Kotlin pur.
 * Aucun `android.graphics` ici : le dessin (ciel, chemins, particules) est dans [CosmoRunRenderer],
 * et un test sur PC peut rastériser ces mêmes faces en PNG pour les regarder sans appareil. */
class CosmoRunScene {
    companion object {
        val SUIT_COLORS = intArrayOf(0xFF54E5E0.toInt(), 0xFFFFB85C.toInt(), 0xFFD09BFF.toInt())
        /** Couleur de chaque monde : jamais l'orange (sauter) ni le rose (glisser), réservés aux dangers. */
        val WORLD_ACCENT = intArrayOf(0xFF54E5E0.toInt(), 0xFF7F95FF.toInt(), 0xFFB48CFF.toInt())
        private const val GOLD = 0xFFFFE066.toInt()
        private const val MINT = 0xFF6EF0C0.toInt()
        private val BOX_FACES = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(4, 7, 6, 5), intArrayOf(0, 4, 5, 1),
            intArrayOf(3, 2, 6, 7), intArrayOf(0, 3, 7, 4), intArrayOf(1, 5, 6, 2)
        )
        private val LIGHT = floatArrayOf(.82f, .55f, 1.15f, .48f, .7f, .96f)
        const val INK = 0xFF091124.toInt()
        private const val WHITE = 0xFFE9F6FF.toInt()
        private const val ORANGE = 0xFFFFB45E.toInt()
        private const val PINK = 0xFFFF527D.toInt()
        private val SIDES = intArrayOf(-1, 1)
        private const val NEAR_Z = -7f
        private const val FAR_Z = 144f
        private const val PLAYER_RESERVE = 220
        private const val ROOF = CosmoRunGame.CONTAINER_HEIGHT
        private const val SLICE = 3f
    }

    class Face {
        // Un quadrilatère coupé au plan proche peut avoir cinq sommets.
        val xy = FloatArray(10)
        var count = 4
        var depth = 0f
        var color = 0
    }
    private val pool = Array(3200) { Face() }
    val visible = ArrayList<Face>(3200)
    private val farFirst = Comparator<Face> { a, b -> b.depth.compareTo(a.depth) }
    private var used = 0
    /** Les décors s'arrêtent avant la fin du pool : le personnage doit toujours pouvoir être émis. */
    private var limit = 0
    private var depthBias = 0f
    private var alphaScale = 1f
    val faceCount get() = visible.size
    private val vertices = FloatArray(24)
    private val polygon = FloatArray(12)
    private val clipped = FloatArray(15)
    var w = 1f; private set
    var h = 1f; private set
    var focal = 1f; private set
    var horizon = 1f; private set
    private var cameraY = 1f
    private var cameraX = 0f
    private var bend = 0f
    var accent = SUIT_COLORS[0]; private set
    var theme = 0; private set
    private var time = 0f
    var suit = 0
    var bottomInset = 0f

    fun scale(z: Float) = focal / (z + 9f).coerceAtLeast(1f)
    private fun px(x: Float, z: Float) = w * .5f + (x - cameraX + bend * z * z) * scale(z)
    private fun py(y: Float, z: Float) = horizon + (cameraY - y) * scale(z)
    fun playerScreenX(game: CosmoRunGame) = px(CosmoRunGame.laneX(game.laneF), 0f)
    fun playerScreenY(game: CosmoRunGame) = py(.9f + game.playerY, 0f)

    /** Place la caméra pour cette image. À appeler avant de construire quoi que ce soit. */
    fun frame(width: Int, height: Int, game: CosmoRunGame, clock: Float) {
        w = width.toFloat(); h = height.toFloat(); time = clock
        focal = min(w, h * .95f) * 1.06f
        horizon = h * (if (h > w) .21f else .29f)
        val feetY = min(h * .84f, h - bottomInset - h * .025f)
        cameraY = (feetY - horizon) * 9f / focal + game.cameraLift
        cameraX = CosmoRunGame.laneX(game.laneF) * .08f
        bend = sin(game.distance / 240f) * .00085f
        theme = game.sector % 3
        accent = WORLD_ACCENT[theme]
    }

    /** Vide la liste : chaque couche (sol, monde) se construit puis se dessine séparément. */
    fun begin() { used = 0; visible.clear(); limit = pool.size - PLAYER_RESERVE; depthBias = 0f; alphaScale = 1f }
    fun sortFarFirst() { visible.sortWith(farFirst) }

    fun buildWorld(game: CosmoRunGame, demo: Boolean, travel: Float) {
        buildArchitecture(travel)
        // Bornes : une arche dorée à la distance du record, un liseré rouge où l'on est tombé la dernière fois.
        if (!demo) {
            if (game.bestDistance > 0f) marker(game.bestDistance - game.distance, 0xFFFFCE7B.toInt(), tall = true)
            if (game.lastDeathDistance > 0f) marker(game.lastDeathDistance - game.distance, PINK, tall = false)
        }
        for (e in game.entities) {
            if (e.dead || e.z > 125f || e.z + e.length < -5f) continue
            entity(e)
        }
        // Le personnage passe devant tout ce qui est à moins de 2,6 m de lui, et a toujours de la place.
        limit = pool.size
        depthBias = -2.6f
        alphaScale = if (!demo && game.invincibleTime > 0f && (time * 18f).toInt() % 2 == 0) .4f else 1f
        astronaut(game, demo)
        depthBias = 0f; alphaScale = 1f
    }

    fun buildDeck(travel: Float) {
        val first = floor((travel + NEAR_Z - 3f) / 6f).toInt()
        val last = floor((travel + FAR_Z) / 6f).toInt()
        for (segment in first..last) {
            val worldZ = segment * 6f
            val z = worldZ - travel
            val segmentAccent = WORLD_ACCENT[sceneryTheme(worldZ)]
            box(0f, -.25f, z, 8.25f, .45f, 5.95f, if (segment % 2 == 0) 0xFF26384D.toInt() else 0xFF203145.toInt())
            for (side in SIDES) {
                box(side * 4.23f, .04f, z, .15f, .13f, 5.95f, segmentAccent)
                box(side * 4.6f, -.4f, z, .5f, .7f, 5.9f, 0xFF101F32.toInt())
                box(side * 4.58f, .23f, z, .13f, .13f, 3.5f, WHITE)
            }
        }
        // Joints de voie : quatre longs traits au lieu de vingt-six tranches. Ils sont peints après
        // le reste du sol (biais de profondeur), sans quoi le tri les cacherait derrière les dalles proches.
        depthBias = -400f
        var start = NEAR_Z
        while (start < FAR_Z) {
            val length = min(45f, FAR_Z - start)
            for (lane in 1..4) box((lane - 2.5f) * CosmoRunGame.LANE_WIDTH, .008f, start + length / 2f, .025f, .015f, length, 0xFF4B6578.toInt())
            start += length
        }
        depthBias = 0f
    }

    private fun sceneryTheme(worldZ: Float) = (worldZ.coerceAtLeast(0f).toInt() / 600) % 3

    private fun buildArchitecture(travel: Float) {
        val first = floor((travel + NEAR_Z - 5f - 3f) / 24f).toInt()
        val last = floor((travel + FAR_Z - 5f) / 24f).toInt()
        for (segment in first..last) {
            // L'identité dépend de la position absolue, jamais de l'indice dans la fenêtre
            // visible : une arche conserve son existence et sa forme jusqu'à son passage.
            val worldZ = segment * 24f + 5f
            val z = worldZ - travel
            val variant = Math.floorMod(segment, 4)
            val theme = sceneryTheme(worldZ)
            val segmentAccent = WORLD_ACCENT[theme]
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

    private fun marker(z: Float, color: Int, tall: Boolean) {
        if (z < -5f || z > 125f) return
        if (tall) {
            for (side in SIDES) box(side * 4.45f, 3.2f, z, .5f, 6.4f, .5f, 0xFF40536D.toInt())
            box(0f, 6.3f, z, 9.4f, .5f, .6f, color)
            box(0f, 5.9f, z - .32f, 8.6f, .1f, .05f, WHITE)
        } else {
            box(0f, .03f, z, 8.2f, .04f, .5f, color)
            for (side in SIDES) box(side * 4.3f, .7f, z, .3f, 1.4f, .3f, color)
        }
    }

    private fun entity(e: CosmoRunGame.Entity) {
        val x = e.x
        val base = e.baseY
        when (e.type) {
            CosmoRunGame.EntityType.CONTAINER -> container(e)
            CosmoRunGame.EntityType.RAMP -> ramp(e)
            CosmoRunGame.EntityType.HURDLE -> {
                val z = e.zCenter
                shadow(x, base, z, 1.6f, 1.0f)
                box(x, base + .42f, z, 1.43f, .84f, .42f, ORANGE)
                box(x, base + .44f, z - .23f, 1.25f, .38f, .025f, INK)
                for (i in -2..2) box(x + i * .24f, base + .44f, z - .25f, .1f, .32f, .025f, ORANGE, rz = -.4f)
                box(x, base + .88f, z, 1.46f, .08f, .48f, WHITE)
            }
            CosmoRunGame.EntityType.LASER -> {
                val z = e.zCenter
                shadow(x, base, z, 1.6f, .9f)
                for (side in SIDES) {
                    box(x + side * .68f, base + 1.4f, z, .12f, 2.6f, .27f, 0xFF6E597C.toInt())
                    box(x + side * .68f, base + 2.6f, z, .2f, .18f, .34f, PINK)
                }
                box(x, base + 1.35f, z, 1.3f, .32f, .24f, PINK)
                box(x, base + 1.35f, z - .13f, 1.3f, .08f, .02f, WHITE)
                box(x, base + 1.9f, z, 1.3f, .8f, .06f, 0x99FF527D.toInt())
            }
            CosmoRunGame.EntityType.METEOR -> meteor(e)
            CosmoRunGame.EntityType.DRONE -> {
                val z = e.zCenter
                val y = base + 1.65f + sin(time * 4f + e.variant) * .12f
                shadow(x, base, z, 1.5f, 1.2f)
                box(x, y, z, .78f, .55f, .65f, 0xFFB17B96.toInt(), rz = sin(time * 2f) * .08f)
                box(x, y, z - .36f, .45f, .17f, .07f, PINK)
                for (side in SIDES) {
                    box(x + side * .54f, y, z, .45f, .1f, .2f, 0xFF445770.toInt())
                    box(x + side * .57f, y + .1f, z, .44f, .05f, .44f, accent, ry = time * 12f)
                }
            }
            else -> pickup(e)
        }
    }

    /** Un rocher annoncé par une marque orange au sol ; il tombe du ciel et n'est solide qu'une fois posé. */
    private fun meteor(e: CosmoRunGame.Entity) {
        val x = e.x; val z = e.zCenter
        val rock = 0xFF8C6A5A.toInt()
        if (!e.landed) {
            val pulse = .55f + .45f * sin(time * 14f)
            box(x, .03f, z, 1.5f, .03f, 1.5f, tint(ORANGE, pulse))
            box(x, .04f, z, .9f, .03f, .9f, tint(ORANGE, pulse * .7f))
            if (e.drop < .999f) crystal(x, .5f + e.drop * 13f, z, .55f, 1.0f, rock, time * 3f)
        } else {
            shadow(x, 0f, z, 1.6f, 1.3f)
            crystal(x, .45f, z, .55f, .9f, rock, e.variant * .7f)
            box(x, .06f, z, 1.2f, .1f, 1.2f, tint(rock, .6f))
        }
    }

    /** Tache sombre nette au sol (ou sur le toit) sous un danger : elle dit où il est et à quelle hauteur on le survole. */
    private fun shadow(x: Float, base: Float, z: Float, width: Float, depth: Float) {
        // Peinte avant l'objet qui la projette, sans quoi le tri par profondeur la poserait sur son pied.
        val previous = depthBias
        depthBias = previous + .8f
        box(x, base + .015f, z, width, .02f, depth, 0xA0030814.toInt())
        depthBias = previous
    }

    private val containerColors = intArrayOf(0xFF566B82.toInt(), 0xFF8A6448.toInt(), 0xFF3F7078.toInt())

    /** Un conteneur en tranches de 3 m : le tri du peintre reste juste pour ce qui roule sur son toit. */
    private fun container(e: CosmoRunGame.Entity) {
        val x = e.x
        val body = containerColors[e.variant % 3]
        val slices = ceil(e.length / SLICE).toInt().coerceAtLeast(1)
        val step = e.length / slices
        for (i in 0 until slices) {
            val z = e.z + step * (i + .5f)
            box(x, ROOF / 2f, z, 1.36f, ROOF, step, body)
            box(x, ROOF - .05f, z, 1.44f, .1f, step, tint(body, 1.45f))
            for (side in SIDES) box(x + side * .69f, 1.3f, z, .03f, .16f, step * .9f, accent)
        }
        // Face avant : bandes et feux, pour lire de loin qu'on ne passe pas au travers.
        val front = e.z
        box(x, 1.05f, front - .01f, .9f, .5f, .04f, INK)
        box(x, 1.05f, front - .04f, .7f, .1f, .02f, WHITE)
        for (side in SIDES) box(x + side * .5f, 1.5f, front - .03f, .12f, 1.5f, .03f, 0xFFB8C4D0.toInt())
    }

    private fun ramp(e: CosmoRunGame.Entity) {
        val slices = ceil(e.length / SLICE).toInt().coerceAtLeast(1)
        val step = e.length / slices
        val color = 0xFF4F8F86.toInt()
        for (i in 0 until slices) {
            val z0 = e.z + step * i
            wedge(e.x, z0, z0 + step, ROOF * i / slices, ROOF * (i + 1) / slices, .69f, color)
        }
        // Chevrons de guidage sur la pente.
        for (i in 0 until slices) {
            val u = (i + .5f) / slices
            box(e.x, ROOF * u + .03f, e.z + e.length * u, 1.0f, .04f, .16f, MINT, rx = -atan2(ROOF, e.length))
        }
    }

    private fun pickup(e: CosmoRunGame.Entity) {
        val x = e.x; val z = e.zCenter
        val y = e.y + if (e.attracting) 0f else sin(time * 3f + z * .12f) * .1f
        if (e.type == CosmoRunGame.EntityType.ATOM || e.type == CosmoRunGame.EntityType.ATOM_HIGH) {
            crystal(x, y, z, .22f, .5f, GOLD, time * 2f)
            if (z < 45f) ring(x, y, z, .38f, .04f, GOLD, time)
            return
        }
        val color = when (e.type) {
            CosmoRunGame.EntityType.SHIELD -> SUIT_COLORS[0]
            CosmoRunGame.EntityType.MAGNET -> SUIT_COLORS[2]
            else -> PINK
        }
        box(x, y, z, .57f, .57f, .57f, color, ry = time, rz = .2f)
        // Symboles géométriques distincts : bouclier, aimant en U et éclair.
        when (e.type) {
            CosmoRunGame.EntityType.SHIELD -> crystal(x, y, z - .46f, .18f, .35f, WHITE, 0f)
            CosmoRunGame.EntityType.MAGNET -> {
                box(x, y - .13f, z - .46f, .32f, .09f, .03f, WHITE)
                for (side in SIDES) box(x + side * .12f, y, z - .46f, .08f, .27f, .03f, WHITE)
            }
            else -> box(x, y, z - .46f, .1f, .4f, .04f, WHITE, rz = -.45f)
        }
        ring(x, y, z, .6f, .04f, color, -time)
    }

    private fun astronaut(game: CosmoRunGame, demo: Boolean) {
        val x = CosmoRunGame.laneX(game.laneF)
        val moving = game.isRunning || demo
        val phase = if (moving) time * 15f else 0f
        val airborne = game.isJumping
        val stride = if (airborne || game.isSliding) 0f else sin(phase) * .62f
        val jump = if (demo) cameraY - (h * .28f - horizon) / scale(0f) + sin(time * 1.7f) * .08f else game.playerY
        val crouch = if (game.isSliding) .66f else 0f
        val y = jump - crouch
        val lean = (game.laneTarget - game.laneF) * -.22f
        val color = SUIT_COLORS[suit.coerceIn(0, 2)]
        // Ombre sur ce qui porte le joueur (sol ou toit), plus petite quand il s'en éloigne.
        if (!demo) {
            val floor = game.supportY
            box(x, floor + .025f, .1f, (.95f - (jump - floor) * .14f).coerceAtLeast(.4f), .025f, .6f, 0x60030814)
        }
        // Bottes, tibias, épaules et bras indépendants : véritable silhouette articulée.
        for (side in SIDES) {
            val leg = if (game.isSliding) -.9f else side * stride
            box(x + side * .2f, .44f + jump - crouch * .32f, if (game.isSliding) -.32f else 0f,
                .26f, .72f, .29f, WHITE, rx = leg)
            box(x + side * .2f, .12f + jump - crouch * .1f + abs(sin(leg)) * .15f, -sin(leg) * .35f - .08f,
                .31f, .22f, .46f, 0xFF31455F.toInt(), rx = leg * .4f)
            box(x + side * .45f, 1.08f + y, .02f, .23f, .63f, .27f, color,
                rx = if (airborne) -1f else -side * stride, rz = side * .15f + lean)
            box(x + side * .48f, .77f + y, sin(side * stride) * .22f, .24f, .22f, .27f, WHITE)
        }
        box(x, 1.05f + y, 0f, .68f, .72f, .46f, WHITE, rz = lean)
        box(x, .76f + y, -.015f, .7f, .14f, .49f, 0xFF273C56.toInt())
        // Sac dorsal orienté vers la caméra avec deux propulseurs.
        box(x, 1.12f + y, -.33f, .53f, .59f, .28f, 0xFF334A65.toInt(), rz = lean)
        box(x, 1.18f + y, -.49f, .3f, .27f, .035f, color)
        for (side in SIDES) {
            box(x + side * .22f, .83f + y, -.38f, .17f, .22f, .22f, 0xFF7890A8.toInt())
            if (airborne || game.boostTime > 0f || demo) {
                crystal(x + side * .22f, .51f + y, -.4f, .09f, .4f + sin(time * 40f) * .09f, color, 0f)
            }
        }
        box(x, 1.63f + y, .02f, .75f, .63f, .62f, WHITE, rz = lean)
        box(x, 1.61f + y, -.31f, .61f, .33f, .055f, 0xFF122A44.toInt(), rz = lean)
        box(x, 1.71f + y, -.347f, .46f, .045f, .02f, color)
        for (side in SIDES) box(x + side * .39f, 1.62f + y, -.01f, .09f, .26f, .3f, color)
        box(x + .29f, 2.03f + y, .04f, .045f, .27f, .045f, 0xFF879BAF.toInt())
        box(x + .29f, 2.18f + y, .04f, .075f, .07f, .075f, ORANGE)
    }

    /** Coin de pente : dessus incliné et flanc du côté de la caméra. */
    private fun wedge(x: Float, z0: Float, z1: Float, h0: Float, h1: Float, halfWidth: Float, color: Int) {
        if (z1 < NEAR_Z) return
        fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
                 cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, c: Int) {
            polygon[0] = ax; polygon[1] = ay; polygon[2] = az; polygon[3] = bx; polygon[4] = by; polygon[5] = bz
            polygon[6] = cx; polygon[7] = cy; polygon[8] = cz; polygon[9] = dx; polygon[10] = dy; polygon[11] = dz
            emitFace(4, c)
        }
        quad(x - halfWidth, h0, z0, x + halfWidth, h0, z0, x + halfWidth, h1, z1, x - halfWidth, h1, z1,
            tint(color, LIGHT[2]))
        if (cameraX < x - halfWidth) {
            quad(x - halfWidth, 0f, z0, x - halfWidth, h0, z0, x - halfWidth, h1, z1, x - halfWidth, 0f, z1, tint(color, LIGHT[4]))
        } else if (cameraX > x + halfWidth) {
            quad(x + halfWidth, 0f, z0, x + halfWidth, h0, z0, x + halfWidth, h1, z1, x + halfWidth, 0f, z1, tint(color, LIGHT[5]))
        }
        // Face avant de la tranche : visible tant que la pente monte vers la caméra.
        if (h0 > .01f) quad(x - halfWidth, 0f, z0, x + halfWidth, 0f, z0, x + halfWidth, h0, z0, x - halfWidth, h0, z0, tint(color, LIGHT[0]))
    }

    private fun ring(x: Float, y: Float, z: Float, radius: Float, thick: Float, color: Int, rotation: Float) {
        for (i in 0..3) {
            val a = i * PI.toFloat() / 2f + rotation
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
        if (used >= limit) return
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
        face.depth = depth / clippedCount + depthBias
        face.color = fog(color, face.depth - depthBias)
        visible.add(face)
    }

    fun tint(color: Int, amount: Float): Int {
        val r = ((color shr 16 and 255) * amount).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 255) * amount).toInt().coerceIn(0, 255)
        val b = ((color and 255) * amount).toInt().coerceIn(0, 255)
        return (color ushr 24 shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun fog(color: Int, z: Float): Int {
        val f = (z / 145f).coerceIn(0f, .82f)
        val visibility = ((FAR_Z - z) / 24f).coerceIn(0f, 1f)
        val a = ((color ushr 24) * visibility * alphaScale).toInt()
        val r = ((color shr 16 and 255) * (1f - f) + 23f * f).toInt()
        val g = ((color shr 8 and 255) * (1f - f) + 37f * f).toInt()
        val b = ((color and 255) * (1f - f) + 57f * f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
