package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le paysage : **le sol, les constructions, la verdure, les habitants et leurs feux**.
 *
 * C'est le pendant de [SkyBackdrop] pour tout ce qui est sous l'horizon. Un relief avec
 * des collines, un site de pierres qu'on peut abattre, des arbres qui se balancent au
 * vent, des villageois qui detalent quand un mur cede, et des torches qui s'eteignent
 * avec la pierre qui les portait — cela n'a aucune raison d'appartenir a une machine
 * plutot qu'a une autre. Le trebuchet et l'atelier d'engrenages peignent le meme, et
 * les machines a venir aussi.
 *
 * **La classe ne possede ni le relief ni les cibles** : ce sont des objets du jeu, qui
 * vivent dans le monde physique et se font casser. On les lui tend a chaque image, avec
 * la camera qui dit ou les poser. Elle ne possede que ce qui n'est que decor — la
 * vegetation, les fuyards — et les pinceaux.
 *
 * La nuit se pose ici aussi, et seulement sur le paysage : le ciel a deja ses couleurs
 * d'heure, et la machine doit rester lisible a trois heures du matin.
 */
class LandScene(context: Context) {

    private val dp = context.resources.displayMetrics.density

    /**
     * D'ou partent les graduations au sol, en metres.
     *
     * C'est la ligne de tir : le pied de la machine au trebuchet, le lanceur a
     * l'atelier. Les bornes se comptent a partir de la, et suivent le relief.
     */
    var firingLine = 0f

    // ── Ce que la vue prete a chaque image ──────────────────────────────────
    // Des champs plutot que des parametres partout : le paysage se peint en une
    // douzaine de methodes qui appellent toutes `sx`/`sy`, et les traverser de six
    // arguments de camera rendrait chaque signature illisible pour rien.
    private var camX = 0f
    private var camY = 0f
    private var camScale = 1f
    private var viewW = 0f
    private var viewH = 0f
    private var terrain: Terrain = Terrain.FLAT
    private var targets: TargetField = TargetField(com.Atom2Universe.app.games.physics.PhysWorld())
    private var light = 1f
    private var windVx = 0f
    private var ambientClock = 0f

    /** Deux brouillons de geometrie, pour ne pas allouer a chaque pierre dessinee. */
    private val partPose = FloatArray(3)
    private val gablePose = FloatArray(5)
    private val corners = FloatArray(8)

    private fun sx(x: Float) = (x - camX) * camScale + viewW / 2f
    private fun sy(y: Float) = viewH / 2f - (y - camY) * camScale

    private val pGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#1B2A1E".toColorInt() }
    private val pGrass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#3E6B43".toColorInt()
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(90, 180, 220, 190)
    }
    private val pTickLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(150, 200, 230, 205)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    /**
     * Les peintures de la cible, une par matériau, plus leur trait de contour.
     *
     * Elles sont dans le même ordre que [Material], ce qui permet d'aller les chercher
     * par l'ordinal sans table intermédiaire.
     */
    private val targetFills = intArrayOf(
        "#C9A84C".toColorInt(), // chaume
        "#A8D8E8".toColorInt(), // glace
        "#B8916A".toColorInt(), // torchis
        "#7A6247".toColorInt(), // terre
        "#8B5E3C".toColorInt(), // bois
        "#9AA3AB".toColorInt(), // pierre
        "#D3B076".toColorInt(), // grès
        "#54606B".toColorInt(), // fer
        "#D9B26A".toColorInt(), // carton
        "#B9BCB8".toColorInt(), // béton
        "#9FD4E4".toColorInt(), // verre
        "#7E8896".toColorInt()  // acier
    ).map { c -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c } }

    private val targetEdges = intArrayOf(
        "#9B8038".toColorInt(),
        "#7FB3C6".toColorInt(),
        "#8D6B4A".toColorInt(),
        "#584734".toColorInt(),
        "#5F3F28".toColorInt(),
        "#6E767D".toColorInt(),
        "#A8874F".toColorInt(),
        "#39424A".toColorInt(),
        "#A77C3F".toColorInt(),
        "#7C817E".toColorInt(),
        "#5E93A6".toColorInt(),
        "#4A525C".toColorInt()
    ).map { c ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
        }
    }

    /** Les fêlures : des traits sombres, d'autant plus nombreux que la pierre a pris. */
    private val pCrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 20, 16, 12)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
    }

    private val pFoliage = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#2E5233".toColorInt() }
    private val pFoliageLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#3E6B43".toColorInt() }
    private val pTrunk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * dp
        strokeCap = Paint.Cap.ROUND
        color = "#4E342E".toColorInt()
    }
    private val pTuft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
        color = "#3E6B43".toColorInt()
    }
    private val pVillager = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(215, 40, 32, 28)
    }

    private val pFlame = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flameTint = 0
    private var flameShaderR = 0f

    /**
     * Les deux pinceaux du **décor peint** : une petite scène — fenêtre, escalier,
     * puits — sur le rectangle d'un bloc à [Decor], plutôt qu'une vraie géométrie.
     *
     * Un remplissage et un trait suffisent à tout le catalogue ; la couleur change
     * par appel plutôt que d'avoir un pinceau par teinte, parce qu'il n'y a jamais
     * plus qu'une poignée de blocs décorés sur un site — rien à voir avec les cent
     * trente corps que [targetFills] doit tenir en deux tracés.
     */
    private val pDecorFill = Paint().apply { style = Paint.Style.FILL }
    private val pDecorLine = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }
    private val pSurfaceFill = Paint().apply { style = Paint.Style.FILL }
    private val pSurfaceDark = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }
    private val pSurfaceLight = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }
    private val decorPath = Path()

    /** Le profil du sol, refait à chaque image : il ne dépend que du cadrage. */
    private val groundPath = Path()

    /** Un chemin par matériau, rempli à neuf à chaque image. */
    private val targetPaths = Array(targetFills.size) { Path() }
    private val targetUsed = BooleanArray(targetFills.size)
    private val crackPath = Path()

    /**
     * Les peintures du **décor**, celles que la nuit assombrit.
     *
     * La machine n'en fait pas partie, et c'est un choix : c'est la pièce que le joueur
     * manipule, elle doit rester lisible à trois heures du matin. Le sol, les
     * constructions et leurs fêlures, eux, s'éteignent avec le jour.
     *
     * La liste se construit une fois. Un filtre posé sur une peinture oubliée donnerait
     * un château dans le noir avec un toit en plein soleil, ce qui se remarque
     * beaucoup plus qu'une nuit un peu claire.
     */
    private val worldPaints: List<Paint> by lazy {
        ArrayList<Paint>().apply {
            add(pGround); add(pGrass); add(pCrack)
            addAll(targetFills); addAll(targetEdges)
            add(pDecorFill); add(pDecorLine)
            add(pSurfaceFill); add(pSurfaceDark); add(pSurfaceLight)
            // La végétation s'éteint avec le jour comme le reste du sol. Les oiseaux
            // et les fuyards, eux, restent des silhouettes nues : une teinte de nuit
            // sur un contour déjà sombre ne changerait rien qu'on puisse voir.
            add(pFoliage); add(pFoliageLight); add(pTrunk); add(pTuft)
        }
    }

    private var tintLevel = -1f
    private var tintFilter: LightingColorFilter? = null

    /**
     * Le décor et ses habitants — arbres, buissons, touffes d'herbe, et les gens
     * qui fuient quand on démolit leur maison. Reconstruits à chaque nouveau site,
     * voir [updateDecor].
     */
    private var vegetation = VegetationField(1L, Terrain.FLAT, 0f, 0f)
    private val villagers = VillagerField()
    private var decorReady = false
    private var lastLevelForDecor: TargetLevel? = null
    private var lastPieceBroken = 0
    private var villagerCooldown = 0f


    /**
     * Peint tout le paysage, de la terre aux torches.
     *
     * L'ordre n'est pas negociable : le sol, puis ce qui pousse dessus, puis ce qu'on
     * y a bati, puis ceux qui l'habitent, puis les feux — une torche doit poser sa
     * flaque de lumiere sur le mur qui la porte, pas derriere lui.
     */
    fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        camX: Float,
        camY: Float,
        camScale: Float,
        terrain: Terrain,
        targets: TargetField,
        light: Float,
        windVx: Float,
        ambientClock: Float
    ) {
        this.viewW = w
        this.viewH = h
        this.camX = camX
        this.camY = camY
        this.camScale = camScale
        this.terrain = terrain
        this.targets = targets
        this.light = light
        this.windVx = windVx
        this.ambientClock = ambientClock

        applyNight()
        drawGround(canvas, w, h)
        drawVegetation(canvas, w, h)
        drawTargets(canvas, w)
        drawVillagers(canvas, w, h)
        drawLights(canvas, w)
        clearNight()
    }

    /**
     * Le sol, du bord gauche de l'écran au bord droit.
     *
     * Sur un terrain plat, c'est le rectangle d'avant et rien d'autre. Sur un relief, on
     * suit la ligne brisée du profil — **et seulement les nœuds visibles** : un terrain
     * fait au plus une vingtaine de sommets, mais aucun n'a besoin d'être tracé s'il
     * est à trois cents mètres hors du cadre. La ligne d'herbe se dessine par-dessus le
     * même chemin, ce qui garantit qu'elle ne s'en décolle jamais.
     */
    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        val viewWidth = w / camScale
        val leftWorld = camX - viewWidth / 2f
        val rightWorld = camX + viewWidth / 2f

        if (terrain.flat) {
            val groundY = sy(terrain.heightAt(camX))
            if (groundY < h) {
                canvas.drawRect(0f, groundY, w, h, pGround)
                canvas.drawLine(0f, groundY, w, groundY, pGrass)
            }
        } else {
            groundPath.reset()
            groundPath.moveTo(0f, sy(terrain.heightAt(leftWorld)))
            for (n in terrain.nodes) {
                if (n.x <= leftWorld || n.x >= rightWorld) continue
                groundPath.lineTo(sx(n.x), sy(n.y))
            }
            groundPath.lineTo(w, sy(terrain.heightAt(rightWorld)))
            // L'herbe d'abord, sur la ligne seule ; puis on referme le chemin vers le
            // bas de l'écran pour le remplissage. Dans l'autre ordre, le trait d'herbe
            // ferait le tour du remplissage et soulignerait les bords de l'écran.
            canvas.drawPath(groundPath, pGrass)
            groundPath.lineTo(w, h)
            groundPath.lineTo(0f, h)
            groundPath.close()
            canvas.drawPath(groundPath, pGround)
            canvas.drawPath(groundPath, pGrass)
        }

        // Graduations à partir du pied de la machine. Le pas s'élargit quand on
        // recule : à trois cents mètres, une borne tous les dix mètres serait une
        // bouillie de traits.
        pTickLabel.textSize = 11f * dp
        val step = when {
            viewWidth > 180f -> 50f
            viewWidth > 70f -> 25f
            else -> 10f
        }
        var d = 0f
        while (firingLine + d < rightWorld + step) {
            val x = firingLine + d
            if (x > leftWorld - step) {
                val px = sx(x)
                // Les bornes suivent le terrain : plantée à l'altitude zéro, une borne
                // de trois cents mètres flotterait au milieu du flanc d'une colline.
                val py = sy(terrain.heightAt(x))
                canvas.drawLine(px, py, px, py + 10f * dp, pTick)
                if (d > 0f) {
                    canvas.drawText("${d.toInt()} m", px, py + 24f * dp, pTickLabel)
                }
            }
            d += step
        }
    }

    /**
     * La végétation : des touffes, des buissons et des arbres, plantés une fois
     * pour toutes par [updateDecor] et rejoués ici à chaque image.
     *
     * Elle balance avec **le** vent du jeu et pas avec une horloge à elle : par vent
     * calme, rien ne bouge, et c'est ce même vent — [TrebuchetGame.wind] — qui fait
     * déjà dériver les nuages et la traînée du boulet. Une seule brise pour tout le
     * monde plutôt qu'une deuxième inventée pour l'occasion.
     */
    private fun drawVegetation(canvas: Canvas, w: Float, h: Float) {
        val windSway = (windVx / 12f).coerceIn(-1f, 1f)
        for (p in vegetation.plants) {
            val px = sx(p.x)
            if (px < -80f || px > w + 80f) continue
            val groundPy = sy(p.groundY)
            if (groundPy < -40f || groundPy > h + 200f) continue

            val sway = sin(ambientClock * 0.7f + p.swayPhase) * windSway
            when (p.kind) {
                VegetationField.Kind.TUFT -> drawTuft(canvas, p, px, groundPy, sway)
                VegetationField.Kind.BUSH -> drawBush(canvas, p, px, groundPy, sway)
                VegetationField.Kind.TREE -> drawTree(canvas, p, px, groundPy, sway)
            }
        }
    }

    /** Trois brins qui penchent chacun un peu différemment, et tous un peu plus au vent. */
    private fun drawTuft(
        canvas: Canvas, p: VegetationField.Plant, px: Float, groundPy: Float, sway: Float
    ) {
        val lenPx = p.size * camScale
        if (lenPx < 1.2f) return
        for (i in 0 until 3) {
            val lean = (p.puffs[i] * 0.6f + sway * 1.3f).coerceIn(-1.6f, 1.6f)
            val bx = (i - 1) * lenPx * 0.28f
            canvas.drawLine(
                px + bx, groundPy,
                px + bx + lean * lenPx * 0.55f, groundPy - lenPx,
                pTuft
            )
        }
    }

    /** Un tas de boules posé au sol, comme un petit nuage qui aurait pris racine. */
    private fun drawBush(
        canvas: Canvas, p: VegetationField.Plant, px: Float, groundPy: Float, sway: Float
    ) {
        val sizePx = p.size * camScale
        if (sizePx < 1.5f) return
        val cx = px + sway * sizePx * 0.15f
        val cy = groundPy - sizePx * 0.5f
        var i = 0
        while (i < p.puffs.size) {
            val paint = if (i == 0) pFoliageLight else pFoliage
            canvas.drawCircle(cx + p.puffs[i] * sizePx, cy + p.puffs[i + 1] * sizePx, p.puffs[i + 2] * sizePx, paint)
            i += 3
        }
    }

    /** Un tronc, et son feuillage qui balance un peu plus que lui : la cime bouge, pas les racines. */
    private fun drawTree(
        canvas: Canvas, p: VegetationField.Plant, px: Float, groundPy: Float, sway: Float
    ) {
        val stemPx = p.stem * camScale
        val sizePx = p.size * camScale
        if (stemPx + sizePx < 2f) return
        val topX = px + sway * stemPx * 0.12f
        val topY = groundPy - stemPx
        canvas.drawLine(px, groundPy, topX, topY, pTrunk)
        val canopyCx = topX + sway * sizePx * 0.25f
        val canopyCy = topY - sizePx * 0.35f
        var i = 0
        while (i < p.puffs.size) {
            val paint = if (i == 0) pFoliageLight else pFoliage
            canvas.drawCircle(
                canopyCx + p.puffs[i] * sizePx, canopyCy + p.puffs[i + 1] * sizePx, p.puffs[i + 2] * sizePx, paint
            )
            i += 3
        }
    }

    /**
     * La cible : chaque pierre de sa couleur, fêlée selon ce qu'elle a encaissé, et la
     * ligne de ruine en travers.
     *
     * On ne dessine que ce qui traverse l'écran : un site fait cent corps, et le joueur
     * passe le plus clair de son temps à regarder sa machine, trois cents mètres plus
     * loin.
     */
    private fun drawTargets(canvas: Canvas, w: Float) {
        val field = targets
        if (field.pieces.isEmpty()) return

        val viewWidth = w / camScale
        val leftWorld = camX - viewWidth / 2f - 5f
        val rightWorld = camX + viewWidth / 2f + 5f

        // Toutes les pierres d'un même matériau dans un seul chemin.
        //
        // Un site fait jusqu'à cent trente corps, souvent en plusieurs morceaux : les
        // dessiner un par un, c'était trois cents appels de tracé par image, chacun
        // avec sa mise en place d'anticrénelage, pour une surface totale qui tient
        // dans un coin de l'écran. Regroupés par matériau, il en reste deux par
        // matériau présent — le remplissage et le contour — et le dessin ne dépend
        // plus du nombre de pierres mais de ce qu'elles couvrent.
        for (path in targetPaths) path.reset()
        java.util.Arrays.fill(targetUsed, false)
        crackPath.reset()
        var cracked = false

        for (p in field.pieces) {
            val b = p.body
            if (b.x + b.boundingRadius < leftWorld || b.x - b.boundingRadius > rightWorld) continue
            val i = p.material.ordinal
            val path = targetPaths[i]
            if (p.block.silhouette == Silhouette.GABLE_ROOF) {
                appendGable(path, p)
            } else {
                for (k in b.parts.indices) appendPart(path, b, k)
            }
            targetUsed[i] = true
            if (p.crackLevel > 0) {
                appendCracks(crackPath, b, p.crackLevel)
                cracked = true
            }
        }

        for (i in targetPaths.indices) {
            if (!targetUsed[i]) continue
            canvas.drawPath(targetPaths[i], targetFills[i])
        }
        // Les matières sont peintes dans le repère de chaque pierre : les tuiles et
        // les planches suivent donc un toit qui tombe au lieu de glisser à sa surface.
        for (p in field.pieces) {
            val b = p.body
            if (b.x + b.boundingRadius < leftWorld || b.x - b.boundingRadius > rightWorld) continue
            drawSurface(canvas, p)
        }
        for (i in targetPaths.indices) {
            if (!targetUsed[i]) continue
            canvas.drawPath(targetPaths[i], targetEdges[i])
        }
        // Le décor peint par-dessus, après le remplissage et avant les fêlures : une
        // fenêtre doit se voir sur son mur, et une pierre fêlée doit garder sa fêlure
        // visible même là où elle recouvre un décor.
        for (p in field.pieces) {
            if (p.decor == Decor.NONE) continue
            val b = p.body
            if (b.x + b.boundingRadius < leftWorld || b.x - b.boundingRadius > rightWorld) continue
            appendDecor(canvas, p)
        }
        if (cracked) canvas.drawPath(crackPath, pCrack)

    }

    /** Peint les joints, fibres et rangées qui font lire la matière au premier coup d'œil. */
    private fun drawSurface(canvas: Canvas, piece: TargetPiece) {
        if (piece.block.silhouette == Silhouette.GABLE_ROOF) {
            drawGableSurface(canvas, piece)
            return
        }
        val body = piece.body
        for (i in body.parts.indices) {
            val part = body.parts[i]
            val partSurface = piece.block.parts.getOrNull(i)?.surface ?: Surface.AUTO
            val requested = if (partSurface != Surface.AUTO) partSurface else piece.surface
            val surface = if (requested != Surface.AUTO) requested else when (piece.material) {
                Material.THATCH -> Surface.THATCH
                Material.WOOD -> Surface.PLANKS
                Material.STONE -> Surface.FIELDSTONE
                Material.SANDSTONE -> Surface.CUT_STONE
                Material.CARDBOARD -> Surface.CARDBOARD
                else -> Surface.AUTO
            }
            body.partWorld(i, partPose)
            val hw = part.halfW * camScale
            val hh = part.halfH * camScale
            if (minOf(hw, hh) < 2.2f * dp) continue

            canvas.save()
            canvas.translate(sx(partPose[0]), sy(partPose[1]))
            canvas.rotate(-partPose[2] * 180f / kotlin.math.PI.toFloat())
            decorPath.reset()
            if (part.shape == Shape.CIRCLE) {
                decorPath.addCircle(0f, 0f, hw, Path.Direction.CW)
            } else {
                decorPath.addRect(-hw, -hh, hw, hh, Path.Direction.CW)
            }
            canvas.clipPath(decorPath)

            val colors = when (surface) {
                Surface.THATCH -> intArrayOf(0xFFC9A84C.toInt(), 0xFF806329.toInt(), 0xFFE1C66E.toInt())
                Surface.SHINGLES -> intArrayOf(0xFF81522F.toInt(), 0xFF4D2D19.toInt(), 0xFFB27A48.toInt())
                Surface.TILES -> intArrayOf(0xFFB85C3D.toInt(), 0xFF713323.toInt(), 0xFFD9845E.toInt())
                Surface.SLATE -> intArrayOf(0xFF58636E.toInt(), 0xFF303943.toInt(), 0xFF89949E.toInt())
                Surface.PLANKS -> intArrayOf(0xFF8B5E3C.toInt(), 0xFF50331F.toInt(), 0xFFC08A58.toInt())
                Surface.TIMBER_FRAME -> intArrayOf(0xFFC6A879.toInt(), 0xFF4B2D19.toInt(), 0xFFE0C99E.toInt())
                Surface.BRICK -> intArrayOf(0xFFA94F37.toInt(), 0xFF663426.toInt(), 0xFFD07A5B.toInt())
                Surface.FIELDSTONE -> intArrayOf(0xFF92999C.toInt(), 0xFF596064.toInt(), 0xFFC0C3BE.toInt())
                Surface.CUT_STONE -> intArrayOf(0xFFA4A9AA.toInt(), 0xFF666D70.toInt(), 0xFFCED0CB.toInt())
                Surface.CARDBOARD -> intArrayOf(0xFFD9B26A.toInt(), 0xFF9B7139.toInt(), 0xFFF0D18C.toInt())
                Surface.CONCRETE -> intArrayOf(0xFFB9BCB8.toInt(), 0xFF7C817E.toInt(), 0xFFDCDED9.toInt())
                Surface.GLASS_WALL -> intArrayOf(0xFF6FA8BD.toInt(), 0xFF3C5F70.toInt(), 0xFFCDEAF5.toInt())
                Surface.CLADDING -> intArrayOf(0xFF8E97A0.toInt(), 0xFF515A63.toInt(), 0xFFC3CBD2.toInt())
                Surface.AUTO -> when (piece.material) {
                    Material.ICE -> intArrayOf(0xFFA8D8E8.toInt(), 0xFF6EAABD.toInt(), 0xFFD9F4FA.toInt())
                    Material.CONCRETE -> intArrayOf(0xFFB9BCB8.toInt(), 0xFF7C817E.toInt(), 0xFFDCDED9.toInt())
                    Material.GLASS -> intArrayOf(0xFF6FA8BD.toInt(), 0xFF3C5F70.toInt(), 0xFFCDEAF5.toInt())
                    Material.STEEL -> intArrayOf(0xFF7E8896.toInt(), 0xFF4A525C.toInt(), 0xFFB4BCC6.toInt())
                    Material.COB -> intArrayOf(0xFFB8916A.toInt(), 0xFF806044.toInt(), 0xFFD4B38C.toInt())
                    Material.EARTH -> intArrayOf(0xFF7A6247.toInt(), 0xFF4E3C2C.toInt(), 0xFFA48A69.toInt())
                    Material.IRON -> intArrayOf(0xFF54606B.toInt(), 0xFF303942.toInt(), 0xFF87939D.toInt())
                    else -> intArrayOf(targetFills[piece.material.ordinal].color, targetEdges[piece.material.ordinal].color, Color.WHITE)
                }
            }
            pSurfaceFill.color = colors[0]
            canvas.drawPath(decorPath, pSurfaceFill)
            pSurfaceDark.color = colors[1]
            pSurfaceDark.strokeWidth = maxOf(0.8f * dp, minOf(hw, hh) * 0.055f)
            pSurfaceLight.color = colors[2]
            pSurfaceLight.strokeWidth = maxOf(0.55f * dp, pSurfaceDark.strokeWidth * 0.55f)

            when (surface) {
                Surface.THATCH -> {
                    val gap = maxOf(3f * dp, hh / 4f)
                    var y = -hh + gap
                    while (y < hh) {
                        canvas.drawLine(-hw, y, hw, y + gap * 0.18f, pSurfaceDark)
                        y += gap
                    }
                    val strands = (hw / (5f * dp)).toInt().coerceIn(2, 14)
                    for (k in 0..strands) {
                        val x = -hw + 2f * hw * k / strands
                        canvas.drawLine(x, -hh, x + ((k + piece.visualVariant) % 3 - 1) * dp, hh, pSurfaceLight)
                    }
                }
                Surface.SHINGLES, Surface.TILES, Surface.SLATE -> {
                    val rows = 3
                    for (row in 1 until rows) {
                        val y = -hh + 2f * hh * row / rows
                        canvas.drawLine(-hw, y, hw, y, pSurfaceDark)
                    }
                    val cell = maxOf(7f * dp, hw / 5f)
                    for (row in 0 until rows) {
                        val y0 = -hh + 2f * hh * row / rows
                        val y1 = -hh + 2f * hh * (row + 1) / rows
                        var x = -hw + if ((row + piece.visualVariant) % 2 == 0) 0f else cell / 2f
                        while (x < hw) {
                            canvas.drawLine(x, y0, x, y1, pSurfaceDark)
                            x += cell
                        }
                        canvas.drawLine(-hw, y0 + pSurfaceLight.strokeWidth, hw, y0 + pSurfaceLight.strokeWidth, pSurfaceLight)
                    }
                }
                Surface.PLANKS -> {
                    val alongX = hw >= hh
                    val count = ((if (alongX) hh else hw) / (7f * dp)).toInt().coerceIn(2, 8)
                    for (k in 1 until count) {
                        val t = k / count.toFloat()
                        if (alongX) {
                            val y = -hh + 2f * hh * t
                            canvas.drawLine(-hw, y, hw, y, pSurfaceDark)
                            canvas.drawLine(-hw, y + dp, hw, y + dp, pSurfaceLight)
                        } else {
                            val x = -hw + 2f * hw * t
                            canvas.drawLine(x, -hh, x, hh, pSurfaceDark)
                            canvas.drawLine(x + dp, -hh, x + dp, hh, pSurfaceLight)
                        }
                    }
                }
                Surface.TIMBER_FRAME -> {
                    pSurfaceDark.strokeWidth = maxOf(2f * dp, minOf(hw, hh) * 0.13f)
                    canvas.drawRect(-hw, -hh, hw, hh, pSurfaceDark)
                    canvas.drawLine(-hw, -hh, hw, hh, pSurfaceDark)
                    canvas.drawLine(hw, -hh, -hw, hh, pSurfaceDark)
                    canvas.drawLine(-hw, 0f, hw, 0f, pSurfaceDark)
                }
                Surface.BRICK, Surface.FIELDSTONE, Surface.CUT_STONE -> {
                    val rows = (hh / (7f * dp)).toInt().coerceIn(2, 7)
                    val cell = maxOf(10f * dp, hw / 3.5f)
                    for (row in 1 until rows) {
                        val y = -hh + 2f * hh * row / rows
                        canvas.drawLine(-hw, y, hw, y, pSurfaceDark)
                    }
                    for (row in 0 until rows) {
                        val y0 = -hh + 2f * hh * row / rows
                        val y1 = -hh + 2f * hh * (row + 1) / rows
                        var x = -hw + if ((row + piece.visualVariant) % 2 == 0) cell / 2f else cell
                        while (x < hw) {
                            val wobble = if (surface == Surface.FIELDSTONE) ((row + x.toInt()) % 3 - 1) * dp else 0f
                            canvas.drawLine(x + wobble, y0, x - wobble, y1, pSurfaceDark)
                            x += cell
                        }
                    }
                }
                Surface.CARDBOARD -> {
                    canvas.drawRect(-hw + 2f * dp, -hh + 2f * dp, hw - 2f * dp, hh - 2f * dp, pSurfaceDark)
                    canvas.drawLine(-hw, 0f, hw, 0f, pSurfaceLight)
                }
                Surface.CONCRETE -> {
                    // Du béton banché : de grands panneaux lisses, leurs joints creux, et
                    // les trous de banche qui donnent l'échelle. Rien de plus — c'est une
                    // matière qui se reconnaît à ce qu'elle n'a presque pas de dessin.
                    val panneau = maxOf(14f * dp, hw / 1.6f)
                    var x = -hw + panneau
                    while (x < hw) {
                        canvas.drawLine(x, -hh, x, hh, pSurfaceDark)
                        x += panneau
                    }
                    if (hh > 10f * dp) {
                        val y = -hh + 2f * hh * 0.5f
                        canvas.drawLine(-hw, y, hw, y, pSurfaceDark)
                        var tx = -hw + panneau / 2f
                        while (tx < hw) {
                            canvas.drawCircle(tx, y - 3f * dp, 1.1f * dp, pSurfaceDark)
                            tx += panneau
                        }
                    }
                    canvas.drawLine(-hw, -hh + dp, hw, -hh + dp, pSurfaceLight)
                }
                Surface.GLASS_WALL -> {
                    // Le mur-rideau : une trame de meneaux, et deux reflets en biais qui
                    // barrent la baie. Ce sont les reflets qui font lire « vitre » ; sans
                    // eux, une trame sur du bleu n'est qu'un carrelage.
                    val cell = maxOf(6f * dp, minOf(hw, hh) / 2.2f)
                    var x = -hw + cell
                    while (x < hw) {
                        canvas.drawLine(x, -hh, x, hh, pSurfaceDark)
                        x += cell
                    }
                    var y = -hh + cell
                    while (y < hh) {
                        canvas.drawLine(-hw, y, hw, y, pSurfaceDark)
                        y += cell
                    }
                    pSurfaceLight.strokeWidth = maxOf(1.6f * dp, minOf(hw, hh) * 0.09f)
                    val biais = minOf(hw, hh) * 1.1f
                    canvas.drawLine(-hw * 0.55f, hh, -hw * 0.55f + biais, hh - biais, pSurfaceLight)
                    canvas.drawLine(hw * 0.1f, hh, hw * 0.1f + biais * 0.6f, hh - biais * 0.6f, pSurfaceLight)
                }
                Surface.CLADDING -> {
                    // La tôle nervurée : des nervures serrées, et rien d'autre.
                    val nervure = maxOf(4f * dp, minOf(hw, hh) / 5f)
                    var x = -hw + nervure
                    while (x < hw) {
                        canvas.drawLine(x, -hh, x, hh, pSurfaceDark)
                        canvas.drawLine(x + dp, -hh, x + dp, hh, pSurfaceLight)
                        x += nervure
                    }
                }
                Surface.AUTO -> {
                    when (piece.material) {
                        Material.ICE -> canvas.drawLine(-hw * 0.75f, hh * 0.55f, hw * 0.55f, -hh * 0.7f, pSurfaceLight)
                        Material.COB, Material.EARTH -> {
                            canvas.drawLine(-hw, -hh * 0.35f, hw, -hh * 0.18f, pSurfaceDark)
                            canvas.drawLine(-hw, hh * 0.42f, hw, hh * 0.28f, pSurfaceLight)
                        }
                        Material.IRON -> {
                            canvas.drawCircle(-hw * 0.72f, -hh * 0.65f, 1.4f * dp, pSurfaceDark)
                            canvas.drawCircle(hw * 0.72f, hh * 0.65f, 1.4f * dp, pSurfaceDark)
                        }
                        else -> Unit
                    }
                }
            }
            canvas.restore()
        }
    }

    /** Un vrai pignon pixel-art par-dessus les deux versants qui assurent la collision. */
    private fun drawGableSurface(canvas: Canvas, piece: TargetPiece) {
        val b = piece.body
        if (!gableBounds(piece.block, gablePose)) return
        val bounds = gablePose
        val minX = bounds[0] * camScale
        val maxX = bounds[1] * camScale
        val bottom = -bounds[2] * camScale
        val apex = -bounds[3] * camScale
        val apexX = bounds[4] * camScale
        if (maxX - minX < 5f * dp || bottom - apex < 4f * dp) return

        val surface = if (piece.surface != Surface.AUTO) piece.surface else when (piece.material) {
            Material.THATCH -> Surface.THATCH
            Material.STONE -> Surface.SLATE
            Material.SANDSTONE -> Surface.TILES
            else -> Surface.SHINGLES
        }
        val colors = when (surface) {
            Surface.THATCH -> intArrayOf(0xFFC9A84C.toInt(), 0xFF806329.toInt(), 0xFFE1C66E.toInt())
            Surface.TILES -> intArrayOf(0xFFB85C3D.toInt(), 0xFF713323.toInt(), 0xFFD9845E.toInt())
            Surface.SLATE -> intArrayOf(0xFF58636E.toInt(), 0xFF303943.toInt(), 0xFF89949E.toInt())
            else -> intArrayOf(0xFF81522F.toInt(), 0xFF4D2D19.toInt(), 0xFFB27A48.toInt())
        }

        canvas.save()
        canvas.translate(sx(b.x), sy(b.y))
        canvas.rotate(-b.angle * 180f / kotlin.math.PI.toFloat())
        decorPath.reset()
        decorPath.moveTo(minX, bottom)
        decorPath.lineTo(apexX, apex)
        decorPath.lineTo(maxX, bottom)
        decorPath.close()
        canvas.clipPath(decorPath)
        pSurfaceFill.color = colors[0]
        canvas.drawPath(decorPath, pSurfaceFill)
        pSurfaceDark.color = colors[1]
        pSurfaceDark.strokeWidth = maxOf(dp, 1.5f * dp)
        pSurfaceLight.color = colors[2]
        pSurfaceLight.strokeWidth = dp

        val row = maxOf(5f * dp, (bottom - apex) / 5f)
        var y = apex + row
        var rowIndex = 0
        while (y < bottom) {
            canvas.drawLine(minX, y, maxX, y, pSurfaceDark)
            if (surface == Surface.THATCH) {
                val strand = maxOf(6f * dp, (maxX - minX) / 9f)
                var x = minX + (rowIndex % 2) * strand / 2f
                while (x < maxX) {
                    canvas.drawLine(x, y - row, x + ((rowIndex + piece.visualVariant) % 3 - 1) * dp, y, pSurfaceLight)
                    x += strand
                }
            } else {
                val cell = maxOf(8f * dp, (maxX - minX) / 7f)
                var x = minX + if ((rowIndex + piece.visualVariant) % 2 == 0) 0f else cell / 2f
                while (x < maxX) {
                    canvas.drawLine(x, y - row, x, y, pSurfaceDark)
                    x += cell
                }
                canvas.drawLine(minX, y + dp, maxX, y + dp, pSurfaceLight)
            }
            y += row
            rowIndex++
        }
        // Faîtage épais et clair : même réduit, le toit reste immédiatement lisible.
        canvas.drawLine(apexX, apex, minX, bottom, pSurfaceLight)
        canvas.drawLine(apexX, apex, maxX, bottom, pSurfaceLight)
        canvas.restore()
    }

    /** Les ouvertures restent des détails de façade ; toutes les autres formes sont physiques. */
    private fun appendDecor(canvas: Canvas, piece: TargetPiece) {
        val b = piece.body
        val part = b.parts.getOrNull(piece.block.decorPart) ?: return
        if (part.shape == Shape.CIRCLE) return
        // Une façade composée de plusieurs assises reçoit une seule baie cadrée sur
        // leur enveloppe commune, pas une petite fenêtre écrasée sur l'assise n° 0.
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in piece.block.parts) {
            if (p.shape != Shape.BOX || kotlin.math.abs(p.localAngle) > 0.001f) continue
            minX = minOf(minX, p.localX - p.halfW)
            maxX = maxOf(maxX, p.localX + p.halfW)
            minY = minOf(minY, p.localY - p.halfH)
            maxY = maxOf(maxY, p.localY + p.halfH)
        }
        if (minX == Float.MAX_VALUE) return
        val anchorX = (minX + maxX) / 2f
        val anchorY = (minY + maxY) / 2f
        val hw = (maxX - minX) / 2f
        val hh = (maxY - minY) / 2f
        if (hw * camScale < 4f * dp || hh * camScale < 4f * dp) return
        val ca = cos(b.angle)
        val sa = sin(b.angle)
        fun sxd(lx: Float, ly: Float) = sx(b.x + (anchorX + lx) * ca - (anchorY + ly) * sa)
        fun syd(lx: Float, ly: Float) = sy(b.y + (anchorX + lx) * sa + (anchorY + ly) * ca)
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float) {
            pDecorLine.color = color
            pDecorLine.strokeWidth = width * dp
            canvas.drawLine(sxd(x0, y0), syd(x0, y0), sxd(x1, y1), syd(x1, y1), pDecorLine)
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: Int) {
            pDecorFill.color = fill
            decorPath.reset()
            decorPath.moveTo(sxd(pts[0].first, pts[0].second), syd(pts[0].first, pts[0].second))
            for (i in 1 until pts.size) {
                decorPath.lineTo(sxd(pts[i].first, pts[i].second), syd(pts[i].first, pts[i].second))
            }
            decorPath.close()
            canvas.drawPath(decorPath, pDecorFill)
        }
        when (piece.decor) {
            Decor.WINDOW_SHUTTERS -> {
                val fw = minOf(hw * 0.55f, TargetRules.detail(0.62f))
                val fh = minOf(hh * 0.55f, TargetRules.detail(0.62f))
                poly(listOf(-fw to -fh, fw to -fh, fw to fh, -fw to fh), "#BFE0EE".toColorInt())
                line(0f, -fh, 0f, fh, "#3A2A1A".toColorInt(), 1.2f)
                line(-fw, 0f, fw, 0f, "#3A2A1A".toColorInt(), 1.2f)
                for (side in floatArrayOf(-1f, 1f)) {
                    val x0 = side * fw
                    val x1 = side * hw * 0.92f
                    poly(listOf(x0 to -fh, x1 to -fh, x1 to fh, x0 to fh), "#5B3A22".toColorInt())
                    for (k in 1..2) {
                        val lx = x0 + (x1 - x0) * k / 3f
                        line(lx, -fh * 0.9f, lx, fh * 0.9f, "#2A1A0E".toColorInt(), 1f)
                    }
                }
            }

            Decor.WINDOW_ARCHED -> {
                val fw = minOf(hw * 0.48f, TargetRules.detail(0.58f))
                val fh = minOf(hh * 0.68f, TargetRules.detail(0.78f))
                val bottom = -fh
                val shoulder = fh * 0.15f
                pDecorFill.color = "#8FC4D8".toColorInt()
                decorPath.reset()
                decorPath.moveTo(sxd(-fw, bottom), syd(-fw, bottom))
                decorPath.lineTo(sxd(-fw, shoulder), syd(-fw, shoulder))
                decorPath.quadTo(sxd(0f, fh), syd(0f, fh), sxd(fw, shoulder), syd(fw, shoulder))
                decorPath.lineTo(sxd(fw, bottom), syd(fw, bottom))
                decorPath.close()
                canvas.drawPath(decorPath, pDecorFill)
                line(0f, bottom, 0f, fh * 0.78f, "#3A2A1A".toColorInt(), 1.2f)
                line(-fw, 0f, fw, 0f, "#3A2A1A".toColorInt(), 1.2f)
            }

            Decor.DOOR -> {
                val dw = minOf(hw * 0.58f, TargetRules.detail(0.62f))
                val bottom = -hh * 0.96f
                val top = minOf(hh * 0.62f, bottom + TargetRules.detail(2.05f))
                poly(listOf(-dw to bottom, dw to bottom, dw to top, -dw to top), "#59371F".toColorInt())
                for (k in -1..1) {
                    val x = dw * k / 1.5f
                    line(x, bottom, x, top, "#2D1A0F".toColorInt(), 1f)
                }
                line(-dw, -hh * 0.1f, dw, -hh * 0.1f, "#25282A".toColorInt(), 2.2f)
                pDecorFill.color = "#C89B45".toColorInt()
                canvas.drawCircle(sxd(dw * 0.62f, -hh * 0.12f), syd(dw * 0.62f, -hh * 0.12f), 2f * dp, pDecorFill)
            }

            Decor.ARROW_SLIT -> {
                val slitW = minOf(hw * 0.18f, TargetRules.detail(0.14f))
                val slitH = minOf(hh * 0.68f, TargetRules.detail(0.58f))
                val crossW = minOf(hw * 0.36f, TargetRules.detail(0.34f))
                poly(
                    listOf(
                        -slitW to -slitH, slitW to -slitH,
                        slitW to slitH, -slitW to slitH
                    ),
                    "#0B0B0B".toColorInt()
                )
                poly(
                    listOf(
                        -crossW to slitH * 0.05f, crossW to slitH * 0.05f,
                        crossW to slitH * 0.2f, -crossW to slitH * 0.2f
                    ),
                    "#000000".toColorInt()
                )
            }

            Decor.BELL -> {
                val openingW = minOf(hw * 0.55f, TargetRules.detail(0.65f))
                val openingH = minOf(hh * 0.72f, TargetRules.detail(0.82f))
                poly(
                    listOf(
                        -openingW to -openingH,
                        openingW to -openingH,
                        openingW to openingH * 0.25f,
                        openingW * 0.65f to openingH * 0.72f,
                        0f to openingH,
                        -openingW * 0.65f to openingH * 0.72f,
                        -openingW to openingH * 0.25f
                    ),
                    "#171512".toColorInt()
                )
                val bellW = openingW * 0.58f
                val bellTop = openingH * 0.35f
                val bellBottom = -openingH * 0.38f
                poly(
                    listOf(
                        -bellW * 0.35f to bellTop,
                        bellW * 0.35f to bellTop,
                        bellW to bellBottom,
                        -bellW to bellBottom
                    ),
                    "#C28B32".toColorInt()
                )
                line(-bellW, bellBottom, bellW, bellBottom, "#6E451B".toColorInt(), 2f)
                line(0f, bellBottom, 0f, -openingH * 0.62f, "#6E451B".toColorInt(), 1.4f)
            }

            Decor.WINDOW_BAND -> {
                // Le bandeau de fenêtres d'un étage moderne : une bande vitrée d'un bout
                // à l'autre de la façade, refendue par ses meneaux.
                //
                // On en dessine **autant qu'il y a d'étages dans le corps**, et non un
                // seul étiré : quand le budget serre, un corps porte deux ou trois
                // étages, et une baie unique de six mètres de haut trahirait le
                // regroupement — c'est justement ce que le regroupement doit cacher.
                val etage = TargetRules.site(3.1f)
                val niveaux = ((2f * hh) / etage).toInt().coerceIn(1, 6)
                val pas = 2f * hh / niveaux
                val bandeH = minOf(pas * 0.36f, TargetRules.detail(0.75f))
                val bandeW = hw * 0.86f
                for (k in 0 until niveaux) {
                    val cy = -hh + pas * (k + 0.62f)
                    poly(
                        listOf(
                            -bandeW to cy - bandeH,
                            bandeW to cy - bandeH,
                            bandeW to cy + bandeH,
                            -bandeW to cy + bandeH
                        ),
                        "#3E5F6E".toColorInt()
                    )
                    val meneaux = (bandeW / TargetRules.detail(0.6f)).toInt().coerceIn(2, 7)
                    for (m in 1 until meneaux) {
                        val x = -bandeW + 2f * bandeW * m / meneaux
                        line(x, cy - bandeH, x, cy + bandeH, "#B9BCB8".toColorInt(), 1.1f)
                    }
                    // Un reflet en biais dans une travée sur deux : c'est ce qui fait
                    // qu'une tour de béton se lit comme du verre et pas comme un trou.
                    if ((k + piece.visualVariant) % 2 == 0) {
                        line(
                            -bandeW * 0.55f, cy - bandeH, -bandeW * 0.1f, cy + bandeH,
                            "#9FD4E4".toColorInt(), 1.6f
                        )
                    }
                }
            }

            Decor.SHOPFRONT -> {
                // La vitrine : une baie qui descend au sol, son auvent, et l'enseigne.
                val baieW = hw * 0.9f
                val baieH = hh * 0.72f
                poly(
                    listOf(
                        -baieW to -hh * 0.92f,
                        baieW to -hh * 0.92f,
                        baieW to baieH * 0.55f,
                        -baieW to baieH * 0.55f
                    ),
                    "#31505C".toColorInt()
                )
                val travees = (baieW / TargetRules.detail(0.8f)).toInt().coerceIn(2, 5)
                for (m in 1 until travees) {
                    val x = -baieW + 2f * baieW * m / travees
                    line(x, -hh * 0.92f, x, baieH * 0.55f, "#C6CBC8".toColorInt(), 1.2f)
                }
                line(
                    -baieW * 0.7f, -hh * 0.5f, -baieW * 0.15f, baieH * 0.45f,
                    "#BFE6F2".toColorInt(), 2f
                )
                // L'auvent, incliné, et l'enseigne au-dessus.
                poly(
                    listOf(
                        -baieW to baieH * 0.58f,
                        baieW to baieH * 0.58f,
                        baieW * 0.9f to baieH * 0.86f,
                        -baieW * 0.9f to baieH * 0.86f
                    ),
                    if (piece.visualVariant % 2 == 0) "#B4462F".toColorInt() else "#2F6B57".toColorInt()
                )
                line(-baieW * 0.75f, hh * 0.78f, baieW * 0.75f, hh * 0.78f, "#E8D9A8".toColorInt(), 2.4f)
            }

            Decor.NONE -> Unit
        }
    }

    /**
     * Les fêlures d'une pierre entamée : un trait au premier palier, une croix au
     * deuxième, une étoile au troisième.
     *
     * C'est le seul retour que le joueur ait sur un coup qui a porté sans casser, et
     * sans lui un tir qui enlève la moitié de la vie d'une assise ressemble exactement
     * à un tir qui n'a rien fait.
     */
    private fun appendCracks(path: Path, b: PhysBody, level: Int) {
        for (i in b.parts.indices) {
            val part = b.parts[i]
            if (part.shape == Shape.CIRCLE) continue
            b.partWorld(i, partPose)
            val cx = sx(partPose[0])
            val cy = sy(partPose[1])
            val hw = part.halfW * camScale * 0.8f
            val hh = part.halfH * camScale * 0.8f
            if (hw < 2f * dp || hh < 2f * dp) continue
            // La rotation se fait à la main plutôt qu'avec la toile : c'est le prix à
            // payer pour que toutes les fêlures tiennent dans un seul chemin, et donc
            // dans un seul tracé.
            val a = -partPose[2]
            val ca = cos(a)
            val sa = sin(a)
            crackLine(path, cx, cy, ca, sa, -hw, -hh * 0.4f, hw * 0.2f, hh)
            if (level >= 2) crackLine(path, cx, cy, ca, sa, hw, -hh, -hw * 0.3f, hh * 0.5f)
            if (level >= 3) {
                crackLine(path, cx, cy, ca, sa, -hw, hh * 0.6f, hw, hh * 0.2f)
                crackLine(path, cx, cy, ca, sa, -hw * 0.2f, -hh, hw * 0.4f, hh * 0.3f)
            }
        }
    }

    /** Un trait de fêlure, exprimé dans le repère de la pierre puis tourné. */
    private fun crackLine(
        path: Path, cx: Float, cy: Float, ca: Float, sa: Float,
        x0: Float, y0: Float, x1: Float, y1: Float
    ) {
        path.moveTo(cx + x0 * ca - y0 * sa, cy + x0 * sa + y0 * ca)
        path.lineTo(cx + x1 * ca - y1 * sa, cy + x1 * sa + y1 * ca)
    }

    /**
     * Verse une forme dans un chemin, sans la dessiner.
     *
     * C'est ce qui permet de regrouper des dizaines de pierres en un tracé unique.
     * Un segment n'a pas d'aire : le rayon d'un galet, ajouté au même chemin, ne
     * change rien au remplissage et se voit au contour, exactement comme avant.
     */
    private fun appendPart(path: Path, b: PhysBody, part: Int) {
        val p = b.parts[part]
        if (p.shape == Shape.CIRCLE) {
            b.partWorld(part, partPose)
            val cx = sx(partPose[0])
            val cy = sy(partPose[1])
            val r = p.radius * camScale
            path.addCircle(cx, cy, r, Path.Direction.CW)
            path.moveTo(cx, cy)
            path.lineTo(cx + r * cos(partPose[2]), cy - r * sin(partPose[2]))
            return
        }
        b.partCorners(part, corners)
        path.moveTo(sx(corners[0]), sy(corners[1]))
        for (i in 1 until 4) path.lineTo(sx(corners[i * 2]), sy(corners[i * 2 + 1]))
        path.close()
    }

    /**
     * Enveloppe locale des deux versants : gauche, droite, égout, faîtage, axe du
     * faîtage. Les coins tiennent compte de l'épaisseur réelle des versants.
     */
    private fun gableBounds(block: Block, out: FloatArray): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (part in block.parts) {
            if (part.shape != Shape.BOX) continue
            val c = cos(part.localAngle)
            val s = sin(part.localAngle)
            for (ix in 0..1) {
                val dx = if (ix == 0) -part.halfW else part.halfW
                for (iy in 0..1) {
                    val dy = if (iy == 0) -part.halfH else part.halfH
                    val x = part.localX + dx * c - dy * s
                    val y = part.localY + dx * s + dy * c
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        if (minX == Float.MAX_VALUE) return false
        val tolerance = maxOf(TargetRules.MIN_HALF_THICKNESS, (maxY - minY) * 0.08f)
        var apexSum = 0f
        var apexCount = 0
        for (part in block.parts) {
            if (part.shape != Shape.BOX) continue
            val c = cos(part.localAngle)
            val s = sin(part.localAngle)
            for (ix in 0..1) {
                val dx = if (ix == 0) -part.halfW else part.halfW
                for (iy in 0..1) {
                    val dy = if (iy == 0) -part.halfH else part.halfH
                    val x = part.localX + dx * c - dy * s
                    val y = part.localY + dx * s + dy * c
                    if (maxY - y <= tolerance) {
                        apexSum += x
                        apexCount++
                    }
                }
            }
        }
        out[0] = minX
        out[1] = maxX
        out[2] = minY
        out[3] = maxY
        out[4] = if (apexCount == 0) (minX + maxX) / 2f else apexSum / apexCount
        return true
    }

    /** Ajoute le triangle visuel d'un toit au chemin groupé de son matériau. */
    private fun appendGable(path: Path, piece: TargetPiece) {
        if (!gableBounds(piece.block, gablePose)) return
        val bounds = gablePose
        val b = piece.body
        val c = cos(b.angle)
        val s = sin(b.angle)
        fun wx(x: Float, y: Float) = b.x + x * c - y * s
        fun wy(x: Float, y: Float) = b.y + x * s + y * c
        val leftX = bounds[0]
        val rightX = bounds[1]
        val bottom = bounds[2]
        val apexX = bounds[4]
        val apexY = bounds[3]
        path.moveTo(sx(wx(leftX, bottom)), sy(wy(leftX, bottom)))
        path.lineTo(sx(wx(apexX, apexY)), sy(wy(apexX, apexY)))
        path.lineTo(sx(wx(rightX, bottom)), sy(wy(rightX, bottom)))
        path.close()
    }

    /**
     * Les fuyards : de petites silhouettes qui courent, jambes en ciseaux.
     *
     * Ils gardent leur taille d'humain quel que soit le mode — voir la note de
     * [VillagerField] — donc ils paraissent minuscules à côté d'un château arcade
     * deux fois trop grand. C'est voulu : ce n'est pas eux qui ont changé de taille.
     */
    private fun drawVillagers(canvas: Canvas, w: Float, h: Float) {
        val list = villagers.list
        if (list.isEmpty()) return
        for (v in list) {
            val px = sx(v.x)
            if (px < -40f || px > w + 40f) continue
            val groundPy = sy(villagers.groundY(v.x))
            if (groundPy < -40f || groundPy > h + 40f) continue
            val hpx = v.height * camScale
            // Trop petit pour qu'un trait de plus se voie : inutile d'insister.
            if (hpx < 3f) continue

            // Penchés en avant, dans le sens où ils fuient — toujours vers la droite,
            // loin de la machine, voir [VillagerField].
            val lean = hpx * 0.16f
            val headX = px + lean
            val headY = groundPy - hpx
            val hipY = groundPy - hpx * 0.52f

            canvas.drawCircle(headX, headY + hpx * 0.08f, hpx * 0.08f, pVillager)
            canvas.drawLine(headX, headY + hpx * 0.16f, px, hipY, pVillager)

            val stride = sin(ambientClock * 10f + v.legPhase) * hpx * 0.24f
            canvas.drawLine(px, hipY, px + stride, groundPy, pVillager)
            canvas.drawLine(px, hipY, px - stride * 0.7f, groundPy, pVillager)
        }
    }

    /**
     * Pose la teinte de nuit sur les peintures du décor.
     *
     * Un multiplicateur et non un voile : un rectangle sombre par-dessus tout aurait
     * aussi couvert le ciel, dont les couleurs d'heure sont justement ce qu'on est allé
     * chercher. Multiplier les couleurs du sol et des pierres donne la même obscurité
     * sans toucher au reste, et coûte un seul filtre partagé.
     *
     * Le plancher est haut — on ne descend jamais sous le tiers — parce qu'un jeu où le
     * joueur ne voit plus sa cible n'est pas un jeu d'ambiance, c'est un jeu cassé. Le
     * bleu est un peu moins multiplié que le rouge : une nuit est froide.
     */
    private fun applyNight() {
        val niveau = 0.34f + 0.66f * light
        if (niveau > 0.995f) {
            clearNight()
            return
        }
        if (abs(niveau - tintLevel) > 0.008f) {
            val m = (niveau * 255f).toInt().coerceIn(0, 255)
            val b = (niveau * 1.14f * 255f).toInt().coerceIn(0, 255)
            tintFilter = LightingColorFilter(Color.rgb(m, m, b), 0)
            tintLevel = niveau
        }
        for (p in worldPaints) p.colorFilter = tintFilter
    }

    private fun clearNight() {
        for (p in worldPaints) p.colorFilter = null
    }

    /**
     * Les feux du site : torches, bougies, fenêtres allumées.
     *
     * Chaque flamme appartient à une pierre — voir [TargetPiece.lightRadius] — donc elle
     * suit ce que la pierre subit. Un mur qu'on renverse emporte ses torches en
     * tournant, et un village rasé s'éteint. Il n'y a aucun code pour ça : c'est ce que
     * signifie accrocher la lumière à la matière plutôt qu'à des coordonnées.
     *
     * On ne les dessine que quand il fait assez sombre pour qu'elles se voient : en
     * plein jour, une torche est un rond orange sur un mur gris.
     */
    private fun drawLights(canvas: Canvas, w: Float) {
        val nuit = (1f - light).coerceIn(0f, 1f)
        if (nuit < 0.12f) return
        val field = targets
        if (field.pieces.isEmpty()) return

        val vue = w / camScale
        val gauche = camX - vue / 2f - 10f
        val droite = camX + vue / 2f + 10f

        for (p in field.pieces) {
            if (p.lightRadius <= 0f) continue
            val b = p.body
            if (b.x < gauche || b.x > droite) continue
            val r = p.lightRadius * camScale
            if (r < 2f * dp) continue

            // Le nuancier radial se refabrique quand la teinte ou le rayon changent, et
            // pas à chaque flamme : sur un château il y en a une douzaine, toutes de la
            // même couleur, et bâtir douze nuanciers par image serait douze allocations
            // par image pour un résultat identique.
            if (p.lightTint != flameTint || abs(r - flameShaderR) > 0.5f) {
                flameTint = p.lightTint
                flameShaderR = r
                pFlame.shader = RadialGradient(
                    0f, 0f, r,
                    intArrayOf(flameTint, flameTint and 0x00FFFFFF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            pFlame.alpha = (nuit * 200f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(sx(b.x), sy(b.y))
            canvas.drawCircle(0f, 0f, r, pFlame)
            canvas.restore()
        }
    }

    // ── Décor et fuyards ─────────────────────────────────────────────────────

    /**
     * Reconstruit la végétation et repart d'un terrain neuf quand le site change,
     * puis fait déguerpir quelques habitants si une pierre vient de céder.
     *
     * La comparaison se fait sur **l'identité** du niveau, pas sur sa graine : le
     * bac à sable n'en a aucune, et deux parties de bac à sable qui se suivent
     * doivent recevoir un décor tout aussi neuf que deux niveaux différents.
     */
    fun updateDecor(
        dt: Float,
        level: TargetLevel?,
        terrain: Terrain,
        targets: TargetField,
        alarmX: Float,
        onPanic: () -> Unit
    ) {
        if (!decorReady || level !== lastLevelForDecor) {
            lastLevelForDecor = level
            decorReady = true
            val seed = level?.seed ?: SANDBOX_DECOR_SEED
            vegetation = VegetationField(seed, terrain, targets.left, targets.right)
            villagers.reset(seed, terrain, targets.left, targets.right)
            lastPieceBroken = 0
            villagerCooldown = 0f
        }

        // Une pierre de plus a cédé depuis la dernière image : quelqu'un panique.
        // La vague se limite dans le temps — sans quoi un mur qui s'effondre pierre
        // par pierre viderait tout le village d'un coup, dix habitants pour un seul
        // choc.
        val broken = targets.pieceBroken
        if (broken > lastPieceBroken && villagerCooldown <= 0f) {
            val alarmX = alarmX.coerceIn(targets.left, targets.right)
            villagers.panic(alarmX)
            onPanic()
            villagerCooldown = VILLAGER_PANIC_COOLDOWN
        }
        lastPieceBroken = broken
        villagerCooldown = (villagerCooldown - dt).coerceAtLeast(0f)
        villagers.update(dt)
    }


    companion object {
        /**
         * La graine du decor de bac a sable.
         *
         * Un site sans niveau n'a pas de graine a lui ; sans celle-ci, la vegetation
         * serait tiree au sort a chaque retour au bac a sable, et les arbres
         * changeraient de place sous les yeux du joueur.
         */
        const val SANDBOX_DECOR_SEED = 424242L

        /**
         * Delai entre deux paniques de villageois, en secondes.
         *
         * Sans lui, un mur qui s'effondre pierre par pierre viderait tout le village
         * d'un coup : dix habitants pour un seul choc.
         */
        const val VILLAGER_PANIC_COOLDOWN = 1.1f
    }
}
