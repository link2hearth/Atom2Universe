package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.Shape
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * L'outil qui **montre** un niveau, faute de pouvoir lancer l'application.
 *
 * Tous les autres tests du dossier mesurent ; celui-ci dessine. Il écrit une quinzaine
 * de niveaux en SVG dans `app/build/apercu/`, à ouvrir dans un navigateur. C'est la
 * seule façon de répondre à des questions qui n'ont pas de chiffre — un aqueduc
 * ressemble-t-il à un aqueduc, une terrasse se lit-elle comme une terrasse, une
 * colline est-elle plantée au bon endroit — et il a servi à trouver deux défauts que
 * rien d'autre n'aurait dénoncés : un plateau qui n'était pas plat, et un aqueduc
 * qu'on prenait pour une colonnade.
 *
 * Il ne vérifie rien et n'échoue jamais. C'est voulu : ce qu'il produit se juge à
 * l'œil.
 */
class TrebuchetApercuTest {

    private fun couleur(m: Material) = when (m) {
        Material.THATCH -> "#C9A84C"
        Material.ICE -> "#A8D8E8"
        Material.COB -> "#B8916A"
        Material.EARTH -> "#7A6247"
        Material.WOOD -> "#8B5E3C"
        Material.STONE -> "#9AA3AB"
        Material.SANDSTONE -> "#D3B076"
        Material.IRON -> "#54606B"
        Material.CARDBOARD -> "#D9B26A"
    }

    /** Palette des surfaces : l'aperçu garde ainsi la matière des blocs composés. */
    private fun couleur(b: Block, p: Piece): String {
        val s = if (p.surface != Surface.AUTO) p.surface else b.surface
        return when (s) {
            Surface.THATCH -> "#C9A84C"
            Surface.SHINGLES -> "#81522F"
            Surface.TILES -> "#B85C3D"
            Surface.SLATE -> "#58636E"
            Surface.PLANKS -> "#8B5E3C"
            Surface.TIMBER_FRAME -> "#C6A879"
            Surface.BRICK -> "#A94F37"
            Surface.FIELDSTONE -> "#92999C"
            Surface.CUT_STONE -> "#A4A9AA"
            Surface.CARDBOARD -> "#D9B26A"
            Surface.AUTO -> couleur(b.material)
        }
    }

    private fun svg(lvl: TargetLevel, file: File) {
        val marge = 25f
        val x0 = lvl.structure.left - 90f
        val x1 = lvl.structure.right + marge
        val yMin = minOf(lvl.terrain.lowest, 0f) - 8f
        val yMax = maxOf(lvl.structure.baseHeight, lvl.terrain.highest) + 12f
        val w = x1 - x0
        val h = yMax - yMin
        val ech = 1600f / w
        val sb = StringBuilder()
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${(w * ech).toInt()}" """ +
                """height="${(h * ech).toInt()}" viewBox="0 0 ${w * ech} ${h * ech}">"""
        )
        sb.append("""<rect width="100%" height="100%" fill="#16202c"/>""")
        fun px(x: Float) = (x - x0) * ech
        fun py(y: Float) = (yMax - y) * ech

        // le sol
        val pts = StringBuilder()
        pts.append("${px(x0)},${py(lvl.terrain.heightAt(x0))} ")
        for (n in lvl.terrain.nodes) {
            if (n.x < x0 || n.x > x1) continue
            pts.append("${px(n.x)},${py(n.y)} ")
        }
        pts.append("${px(x1)},${py(lvl.terrain.heightAt(x1))} ")
        pts.append("${px(x1)},${py(yMin)} ${px(x0)},${py(yMin)}")
        sb.append("""<polygon points="$pts" fill="#1B2A1E" stroke="#4E7A3A" stroke-width="2"/>""")

        for (b in lvl.structure.blocks) {
            appendBlockSvg(sb, b, ::px, ::py, ech, 1f)
            appendDecorSvg(sb, b, ::px, ::py)
        }
        sb.append(
            """<text x="14" y="30" fill="#dfe8f0" font-family="sans-serif" font-size="22">""" +
                "graine ${lvl.seed} — ${TargetGenerator.label(lvl)} — " +
                "${lvl.structure.blocks.size} corps</text>"
        )
        sb.append("</svg>")
        file.writeText(sb.toString())
    }

    /** Dessine le même contour que le jeu, notamment le triangle plein des toitures. */
    private fun appendBlockSvg(
        sb: StringBuilder,
        b: Block,
        px: (Float) -> Float,
        py: (Float) -> Float,
        scale: Float,
        strokeWidth: Float
    ) {
        if (b.silhouette == Silhouette.GABLE_ROOF) {
            val vertices = ArrayList<Pair<Float, Float>>()
            for (p in b.parts) {
                if (p.shape != Shape.BOX) continue
                val c = cos(p.localAngle)
                val s = sin(p.localAngle)
                for ((dx, dy) in arrayOf(
                    -p.halfW to -p.halfH, p.halfW to -p.halfH,
                    p.halfW to p.halfH, -p.halfW to p.halfH
                )) {
                    vertices += (p.localX + dx * c - dy * s) to (p.localY + dx * s + dy * c)
                }
            }
            if (vertices.isEmpty()) return
            val minX = vertices.minOf { it.first }
            val maxX = vertices.maxOf { it.first }
            val minY = vertices.minOf { it.second }
            val maxY = vertices.maxOf { it.second }
            val tolerance = (maxY - minY) * 0.08f
            val apexX = vertices.filter { maxY - it.second <= tolerance }.map { it.first }.average().toFloat()
            val ca = cos(b.angle)
            val sa = sin(b.angle)
            fun screen(x: Float, y: Float): String {
                val wx = b.x + x * ca - y * sa
                val wy = b.y + x * sa + y * ca
                return "${px(wx)},${py(wy)}"
            }
            val points = listOf(screen(minX, minY), screen(apexX, maxY), screen(maxX, minY)).joinToString(" ")
            sb.append(
                """<polygon points="$points" fill="${couleur(b, b.parts.first())}" """ +
                    """stroke="#0009" stroke-width="$strokeWidth"/>"""
            )
            return
        }

        for (p in b.parts) {
            val color = couleur(b, p)
            val ca = cos(b.angle)
            val sa = sin(b.angle)
            val cx = b.x + p.localX * ca - p.localY * sa
            val cy = b.y + p.localX * sa + p.localY * ca
            if (p.shape == Shape.CIRCLE) {
                sb.append(
                    """<circle cx="${px(cx)}" cy="${py(cy)}" r="${p.radius * scale}" """ +
                        """fill="$color" stroke="#0009" stroke-width="$strokeWidth"/>"""
                )
            } else {
                val deg = -(b.angle + p.localAngle) * 180f / Math.PI.toFloat()
                sb.append(
                    """<g transform="translate(${px(cx)},${py(cy)}) rotate($deg)">""" +
                        """<rect x="${-p.halfW * scale}" y="${-p.halfH * scale}" """ +
                        """width="${2f * p.halfW * scale}" height="${2f * p.halfH * scale}" """ +
                        """fill="$color" stroke="#0009" stroke-width="$strokeWidth"/></g>"""
                )
            }
        }
    }

    /**
     * Le dessin d'un [Decor] : une petite scène peinte sur le rectangle du bloc,
     * dans son propre repère local (X à droite, Y vers le haut, comme partout
     * ailleurs dans la génération), tournée et posée comme n'importe quelle pièce.
     *
     * Volontairement séparé du remplissage uni des blocs juste au-dessus : c'est
     * exactement le même partage que dans le vrai jeu (`LandScene.appendDecor`),
     * pour que cet aperçu montre ce que le joueur verra et pas autre chose.
     */
    private fun appendDecorSvg(sb: StringBuilder, b: Block, px: (Float) -> Float, py: (Float) -> Float) {
        if (b.decor == Decor.NONE) return
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in b.parts) {
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
        val ca = cos(b.angle)
        val sa = sin(b.angle)
        fun sxy(lx: Float, ly: Float): Pair<Float, Float> {
            val wx = b.x + (anchorX + lx) * ca - (anchorY + ly) * sa
            val wy = b.y + (anchorX + lx) * sa + (anchorY + ly) * ca
            return px(wx) to py(wy)
        }
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: String, w: Float = 2f) {
            val (sx0, sy0) = sxy(x0, y0)
            val (sx1, sy1) = sxy(x1, y1)
            sb.append(
                """<line x1="$sx0" y1="$sy0" x2="$sx1" y2="$sy1" stroke="$color" """ +
                    """stroke-width="$w" stroke-linecap="round"/>"""
            )
        }
        fun poly(pts: List<Pair<Float, Float>>, fill: String, stroke: String = "none") {
            val d = pts.joinToString(" ") { (lx, ly) -> val (x, y) = sxy(lx, ly); "$x,$y" }
            sb.append("""<polygon points="$d" fill="$fill" stroke="$stroke" stroke-width="1.5"/>""")
        }
        when (b.decor) {
            Decor.WINDOW_SHUTTERS -> {
                val fw = hw * 0.55f
                val fh = hh * 0.55f
                poly(
                    listOf(-fw to -fh, fw to -fh, fw to fh, -fw to fh),
                    "#BFE0EE", "#3A2A1A"
                )
                line(0f, -fh, 0f, fh, "#3A2A1A", 1.5f)
                line(-fw, 0f, fw, 0f, "#3A2A1A", 1.5f)
                for (side in listOf(-1f, 1f)) {
                    val x0 = side * fw
                    val x1 = side * hw * 0.92f
                    poly(
                        listOf(x0 to -fh, x1 to -fh, x1 to fh, x0 to fh),
                        "#5B3A22", "#2A1A0E"
                    )
                    for (k in 1..2) {
                        val lx = x0 + (x1 - x0) * k / 3f
                        line(lx, -fh * 0.9f, lx, fh * 0.9f, "#2A1A0E", 1f)
                    }
                }
            }

            Decor.WINDOW_ARCHED -> {
                val fw = hw * 0.48f
                val bottom = -hh * 0.62f
                val shoulder = hh * 0.12f
                // L'aperçu SVG se contente d'un arc polygonal : le jeu dessine la
                // même ouverture avec une courbe de Bézier lissée.
                poly(
                    listOf(
                        -fw to bottom, fw to bottom, fw to shoulder,
                        fw * 0.7f to hh * 0.55f, 0f to hh * 0.78f,
                        -fw * 0.7f to hh * 0.55f, -fw to shoulder
                    ),
                    "#8FC4D8", "#3A2A1A"
                )
                line(0f, bottom, 0f, hh * 0.68f, "#3A2A1A", 1.5f)
                line(-fw, -hh * 0.02f, fw, -hh * 0.02f, "#3A2A1A", 1.5f)
            }

            Decor.DOOR -> {
                val dw = hw * 0.58f
                val bottom = -hh * 0.96f
                val top = hh * 0.62f
                poly(listOf(-dw to bottom, dw to bottom, dw to top, -dw to top), "#59371F", "#2D1A0F")
                for (k in -1..1) {
                    val x = dw * k / 1.5f
                    line(x, bottom, x, top, "#2D1A0F", 1f)
                }
                line(-dw, -hh * 0.1f, dw, -hh * 0.1f, "#25282A", 2.5f)
            }

            Decor.ARROW_SLIT -> {
                poly(
                    listOf(
                        -hw * 0.28f to -hh * 0.7f, hw * 0.28f to -hh * 0.7f,
                        hw * 0.28f to hh * 0.7f, -hw * 0.28f to hh * 0.7f
                    ),
                    "#0B0B0B", "#000"
                )
                poly(
                    listOf(
                        -hw * 0.07f to -hh * 0.62f, hw * 0.07f to -hh * 0.62f,
                        hw * 0.07f to hh * 0.62f, -hw * 0.07f to hh * 0.62f
                    ),
                    "#000"
                )
                poly(
                    listOf(
                        -hw * 0.22f to hh * 0.05f, hw * 0.22f to hh * 0.05f,
                        hw * 0.22f to hh * 0.18f, -hw * 0.22f to hh * 0.18f
                    ),
                    "#000"
                )
            }

            Decor.BELL -> {
                val ow = hw * 0.55f
                val oh = hh * 0.72f
                poly(
                    listOf(
                        -ow to -oh, ow to -oh, ow to oh * 0.25f,
                        ow * 0.65f to oh * 0.72f, 0f to oh,
                        -ow * 0.65f to oh * 0.72f, -ow to oh * 0.25f
                    ),
                    "#171512", "#080706"
                )
                poly(
                    listOf(
                        -ow * 0.2f to oh * 0.35f, ow * 0.2f to oh * 0.35f,
                        ow * 0.58f to -oh * 0.38f, -ow * 0.58f to -oh * 0.38f
                    ),
                    "#C28B32", "#6E451B"
                )
            }

            Decor.NONE -> Unit
        }
    }

    @Test
    fun apercu() {
        TargetRules.style = TargetStyle.ARCADE
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        for (seed in 1L..20L) {
            svg(TargetGenerator.generate(seed), File(dir, "niveau-$seed.svg"))
        }
        println("APERÇU écrit dans ${dir.absolutePath}")
    }

    /** Les six pièces du catalogue de détails, posées côte à côte pour les voir sans niveau. */
    @Test
    fun apercuDetails() {
        TargetRules.style = TargetStyle.ARCADE
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        val blocks = ArrayList<Block>()
        var x = 0f
        fun pose(piece: Block, gap: Float = 2f) {
            blocks += piece
            x = Structure(listOf(piece)).right + gap
        }
        pose(Masonry.windowedWall(Material.STONE, x, 0f, 5f, 4f))
        pose(Masonry.arrowSlitWall(Material.STONE, x, 0f, 2.5f, 5f))
        pose(Masonry.staircase(Material.STONE, x, 0f, 4f, 3f))
        pose(Masonry.railing(Material.WOOD, x, 0f, 4f))
        pose(TargetModules.well(x + 1f, 0f))
        pose(TargetModules.shelter(x, 4f, 3.5f))
        val structure = Structure(blocks, "détails")
        val lvl = TargetLevel(0L, SiteKind.HAMEAU, 0f, Wind.CALM, structure, Terrain.FLAT, TerrainShape.PLAINE)
        svg(lvl, File(dir, "details.svg"))
        println("APERÇU détails écrit dans ${dir.absolutePath}")
    }

    /** Une pièce seule, en gros plan, pour juger un décor sans avoir à zoomer. */
    private fun closeup(b: Block, file: File) {
        val span = b.halfSpan()
        val halfH = maxOf(b.top() - b.y, b.y - b.bottom())
        val margin = maxOf(span, halfH) * 0.35f
        val w = 2f * span + 2f * margin
        val h = 2f * halfH + 2f * margin
        val ech = 500f / maxOf(w, h)
        fun px(x: Float) = (x - b.x + w / 2f) * ech
        fun py(y: Float) = (h / 2f - (y - b.y)) * ech
        val sb = StringBuilder()
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${(w * ech).toInt()}" """ +
                """height="${(h * ech).toInt()}" viewBox="0 0 ${w * ech} ${h * ech}">"""
        )
        sb.append("""<rect width="100%" height="100%" fill="#3a4a3a"/>""")
        appendBlockSvg(sb, b, ::px, ::py, ech, 2f)
        appendDecorSvg(sb, b, ::px, ::py)
        sb.append("</svg>")
        file.writeText(sb.toString())
    }

    @Test
    fun apercuDetailsZoom() {
        TargetRules.style = TargetStyle.ARCADE
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        closeup(Masonry.windowedWall(Material.STONE, 0f, 0f, 5f, 4f), File(dir, "detail-fenetre.svg"))
        closeup(Masonry.arrowSlitWall(Material.STONE, 0f, 0f, 2.5f, 5f), File(dir, "detail-meurtriere.svg"))
        closeup(Masonry.staircase(Material.STONE, 0f, 0f, 4f, 3f), File(dir, "detail-escalier.svg"))
        closeup(Masonry.railing(Material.WOOD, 0f, 0f, 4f), File(dir, "detail-rambarde.svg"))
        closeup(TargetModules.well(0f, 0f), File(dir, "detail-puits.svg"))
        closeup(TargetModules.shelter(0f, 4f, 3.5f), File(dir, "detail-abri.svg"))
        println("APERÇU détails (gros plan) écrit dans ${dir.absolutePath}")
    }

    @Test
    fun apercuNouveauxBatiments() {
        TargetRules.style = TargetStyle.ARCADE
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        val modules = listOf(
            "batiment-porte-fortifiee.svg" to TargetModules.gatehouse(
                Random(41), 0f, TargetRules.site(7f), TargetRules.site(8f)
            ),
            "batiment-chapelle.svg" to TargetModules.chapel(
                Random(42), 0f, TargetRules.site(6f), TargetRules.site(11f)
            ),
            "batiment-maison-tour.svg" to TargetModules.towerHouse(
                Random(43), 0f, TargetRules.site(5f), TargetRules.site(13f)
            )
        )
        for ((name, blocks) in modules) {
            val structure = Structure(blocks, name)
            svg(
                TargetLevel(
                    0L, SiteKind.VILLAGE, 0f, Wind.CALM,
                    structure, Terrain.FLAT, TerrainShape.PLAINE
                ),
                File(dir, name)
            )
        }
        println("APERÇU nouveaux bâtiments écrit dans ${dir.absolutePath}")
    }

    /** Dessine une bande de ciels : une colonne par heure, plus une éclipse. */
    @Test
    fun apercuDuCiel() {
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        val minuit = 1767225600000L
        val heure = 3_600_000L

        val larg = 150
        val haut = 260
        val sb = StringBuilder()
        val cols = 25
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${larg * cols}" """ +
                """height="${haut + 40}" viewBox="0 0 ${larg * cols} ${haut + 40}">"""
        )

        // Vingt-quatre heures, plus l'instant le plus éclipsé qu'on trouve en trois ans.
        var eclipseAt = minuit
        var pire = 0f
        var t = minuit
        while (t < minuit + 3L * 365 * 24 * heure) {
            val c = SkyState().apply { update(t) }
            if (c.eclipse > pire && c.sunAltitude > 0.3f) { pire = c.eclipse; eclipseAt = t }
            t += heure / 2
        }

        for (col in 0 until cols) {
            val instant = if (col < 24) minuit + col * heure else eclipseAt
            val etiquette = if (col < 24) "${col}h" else "éclipse"
            val c = SkyState().apply { update(instant) }
            val x0 = col * larg
            val id = "g$col"
            sb.append(
                """<defs><linearGradient id="$id" x1="0" y1="0" x2="0" y2="1">""" +
                    """<stop offset="0" stop-color="${hex(c.zenith)}"/>""" +
                    """<stop offset="1" stop-color="${hex(c.horizon)}"/></linearGradient></defs>"""
            )
            sb.append("""<rect x="$x0" y="0" width="$larg" height="$haut" fill="url(#$id)"/>""")

            // Les étoiles.
            if (c.starAlpha > 0.02f) {
                val r = kotlin.random.Random(col * 7919)
                repeat(18) {
                    val sx = x0 + r.nextFloat() * larg
                    val sy = r.nextFloat() * haut * 0.55f
                    sb.append(
                        """<circle cx="$sx" cy="$sy" r="1.3" fill="#fff" """ +
                            """opacity="${"%.2f".format(c.starAlpha * 0.7f)}"/>"""
                    )
                }
            }
            // La Lune, avec sa phase, puis le Soleil.
            if (c.moonAltitude > -0.12f) {
                val mx = x0 + (0.08f + 0.84f * c.moonX) * larg
                val my = haut * 0.62f - c.moonAltitude.coerceIn(-0.2f, 1f) * haut * 0.62f * 0.86f
                sb.append("""<circle cx="$mx" cy="$my" r="13" fill="#F2EFE2"/>""")
                if (c.moonPhase < 0.99f) {
                    val d = 26f * (1f - c.moonPhase) * (if (c.moonWaxing) -1f else 1f)
                    sb.append(
                        """<circle cx="${mx + d}" cy="$my" r="13" fill="${hex(c.zenith)}"/>"""
                    )
                }
            }
            if (c.sunAltitude > -0.12f) {
                val sx2 = x0 + (0.08f + 0.84f * c.sunX) * larg
                val sy2 = haut * 0.62f - c.sunAltitude.coerceIn(-0.2f, 1f) * haut * 0.62f * 0.86f
                sb.append("""<circle cx="$sx2" cy="$sy2" r="34" fill="#FFF3C4" opacity="0.25"/>""")
                sb.append("""<circle cx="$sx2" cy="$sy2" r="11" fill="#FFE9A8"/>""")
                if (c.eclipse > 0f) {
                    val d = 22f * (1f - c.eclipse)
                    sb.append("""<circle cx="${sx2 + d}" cy="$sy2" r="11" fill="#0A0A12"/>""")
                }
            }
            // Le sol, pour l'échelle.
            sb.append("""<rect x="$x0" y="${haut - 26}" width="$larg" height="26" fill="#1B2A1E"/>""")
            sb.append(
                """<text x="${x0 + 6}" y="${haut + 22}" fill="#dfe8f0" """ +
                    """font-family="sans-serif" font-size="15">$etiquette · """ +
                    """${(c.light * 100).toInt()}%</text>"""
            )
        }
        sb.append("</svg>")
        File(dir, "ciel.svg").writeText(sb.toString())
        println("APERÇU du ciel écrit dans ${dir.absolutePath}")
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)
}
