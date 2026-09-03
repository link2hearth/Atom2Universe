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
        "#54606B".toColorInt()  // fer
    ).map { c -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c } }

    private val targetEdges = intArrayOf(
        "#9B8038".toColorInt(),
        "#7FB3C6".toColorInt(),
        "#8D6B4A".toColorInt(),
        "#584734".toColorInt(),
        "#5F3F28".toColorInt(),
        "#6E767D".toColorInt(),
        "#A8874F".toColorInt(),
        "#39424A".toColorInt()
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
    private val pDecorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pDecorLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
            for (k in b.parts.indices) appendPart(path, b, k)
            targetUsed[i] = true
            if (p.crackLevel > 0) {
                appendCracks(crackPath, b, p.crackLevel)
                cracked = true
            }
        }

        for (i in targetPaths.indices) {
            if (!targetUsed[i]) continue
            canvas.drawPath(targetPaths[i], targetFills[i])
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

    /**
     * Une petite scène peinte sur le rectangle d'un bloc à [Decor] : fenêtre,
     * meurtrière, escalier, rambarde, puits, abri.
     *
     * **C'est de la peinture, pas de la géométrie.** Le bloc reste un seul corps
     * plein aux yeux du moteur ; tout ce qui suit ne fait que dessiner par-dessus
     * son rectangle, dans son propre repère local (X à droite, Y vers le haut,
     * comme partout ailleurs dans la génération) avant de le tourner et de le
     * poser à l'écran — exactement la même transformation que [appendPart] fait
     * pour une boîte, appliquée ici à des traits plutôt qu'à un seul rectangle.
     */
    private fun appendDecor(canvas: Canvas, piece: TargetPiece) {
        val b = piece.body
        val part = b.parts.getOrNull(0) ?: return
        if (part.shape == Shape.CIRCLE) return
        val hw = part.halfW
        val hh = part.halfH
        if (hw * camScale < 4f * dp || hh * camScale < 4f * dp) return
        val ca = cos(b.angle)
        val sa = sin(b.angle)
        fun sxd(lx: Float, ly: Float) = sx(b.x + lx * ca - ly * sa)
        fun syd(lx: Float, ly: Float) = sy(b.y + lx * sa + ly * ca)
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
            Decor.WINDOW -> {
                val fw = hw * 0.55f
                val fh = hh * 0.55f
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

            Decor.ARROW_SLIT -> {
                poly(
                    listOf(
                        -hw * 0.28f to -hh * 0.7f, hw * 0.28f to -hh * 0.7f,
                        hw * 0.28f to hh * 0.7f, -hw * 0.28f to hh * 0.7f
                    ),
                    "#0B0B0B".toColorInt()
                )
                poly(
                    listOf(
                        -hw * 0.22f to hh * 0.05f, hw * 0.22f to hh * 0.05f,
                        hw * 0.22f to hh * 0.18f, -hw * 0.22f to hh * 0.18f
                    ),
                    "#000000".toColorInt()
                )
            }

            Decor.STAIRCASE -> {
                val steps = 6
                var lastX = -hw * 0.85f
                var lastY = -hh * 0.85f
                for (k in 0 until steps) {
                    val t = (k + 1) / steps.toFloat()
                    val nx = -hw * 0.85f + t * hw * 1.7f
                    val ny = -hh * 0.85f + t * hh * 1.7f
                    line(lastX, ny, nx, ny, "#5A5A5A".toColorInt(), 2.2f)
                    line(nx, lastY, nx, ny, "#5A5A5A".toColorInt(), 2.2f)
                    lastX = nx
                    lastY = ny
                }
                line(-hw * 0.85f, -hh * 0.7f, hw * 0.85f, hh * 1f, "#8A8A8A".toColorInt(), 1.2f)
                for (k in 0..steps) {
                    val t = k / steps.toFloat()
                    val bx = -hw * 0.85f + t * hw * 1.7f
                    val by = -hh * 0.7f + t * hh * 1.7f
                    line(bx, by, bx, by + hh * 0.3f, "#8A8A8A".toColorInt(), 1.2f)
                }
            }

            Decor.RAILING -> {
                val top = hh * 0.6f
                val bottom = -hh * 0.6f
                line(-hw * 0.92f, top, hw * 0.92f, top, "#241608".toColorInt(), 2.4f)
                line(-hw * 0.92f, bottom, hw * 0.92f, bottom, "#241608".toColorInt(), 1.6f)
                val posts = 7
                for (k in 0..posts) {
                    val x = -hw * 0.92f + hw * 1.84f * k / posts
                    line(x, bottom, x, top, "#241608".toColorInt(), 1.6f)
                }
            }

            Decor.WELL -> {
                val rimY = -hh * 0.35f
                val rimR = hh * 0.5f
                pDecorFill.color = "#8B8B93".toColorInt()
                canvas.drawCircle(sxd(0f, rimY), syd(0f, rimY), rimR * camScale, pDecorFill)
                pDecorLine.color = "#3A3A40".toColorInt()
                pDecorLine.strokeWidth = 1.6f * dp
                canvas.drawCircle(sxd(0f, rimY), syd(0f, rimY), rimR * camScale, pDecorLine)
                val postY0 = rimY + rimR * 0.4f
                val postY1 = hh * 0.35f
                line(-hw * 0.5f, postY0, -hw * 0.5f, postY1, "#4A3A28".toColorInt(), 2.2f)
                line(hw * 0.5f, postY0, hw * 0.5f, postY1, "#4A3A28".toColorInt(), 2.2f)
                poly(
                    listOf(-hw * 0.75f to postY1, hw * 0.75f to postY1, 0f to hh * 0.95f),
                    "#7A4A2A".toColorInt()
                )
                line(0f, rimY + rimR * 0.3f, 0f, rimY, "#2A2A2A".toColorInt(), 1f)
            }

            Decor.SHELTER -> {
                val eaveY = hh * 0.3f
                poly(
                    listOf(-hw * 0.95f to eaveY, hw * 0.95f to eaveY, 0f to hh * 0.95f),
                    "#A85A3A".toColorInt()
                )
                line(-hw * 0.95f, eaveY, hw * 0.95f, eaveY, "#241608".toColorInt(), 1.6f)
                line(-hw * 0.6f, -hh * 0.9f, -hw * 0.6f, eaveY, "#241608".toColorInt(), 3f)
                line(hw * 0.6f, -hh * 0.9f, hw * 0.6f, eaveY, "#241608".toColorInt(), 3f)
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
