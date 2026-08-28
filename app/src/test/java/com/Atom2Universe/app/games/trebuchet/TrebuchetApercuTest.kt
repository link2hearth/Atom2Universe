package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.Shape
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

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
            val c = couleur(b.material)
            for (p in b.parts) {
                val ca = cos(b.angle)
                val sa = sin(b.angle)
                val cx = b.x + p.localX * ca - p.localY * sa
                val cy = b.y + p.localX * sa + p.localY * ca
                if (p.shape == Shape.CIRCLE) {
                    sb.append(
                        """<circle cx="${px(cx)}" cy="${py(cy)}" r="${p.radius * ech}" """ +
                            """fill="$c" stroke="#0008" stroke-width="1"/>"""
                    )
                } else {
                    val deg = -(b.angle + p.localAngle) * 180f / Math.PI.toFloat()
                    sb.append(
                        """<g transform="translate(${px(cx)},${py(cy)}) rotate($deg)">""" +
                            """<rect x="${-p.halfW * ech}" y="${-p.halfH * ech}" """ +
                            """width="${2 * p.halfW * ech}" height="${2 * p.halfH * ech}" """ +
                            """fill="$c" stroke="#0009" stroke-width="1"/></g>"""
                    )
                }
            }
        }
        sb.append(
            """<text x="14" y="30" fill="#dfe8f0" font-family="sans-serif" font-size="22">""" +
                "graine ${lvl.seed} — ${TargetGenerator.label(lvl)} — " +
                "${lvl.structure.blocks.size} corps</text>"
        )
        sb.append("</svg>")
        file.writeText(sb.toString())
    }

    @Test
    fun apercu() {
        TargetRules.style = TargetStyle.ARCADE
        val dir = File(System.getProperty("apercu.dir") ?: "build/apercu")
        dir.mkdirs()
        for (seed in 1L..15L) {
            svg(TargetGenerator.generate(seed), File(dir, "niveau-$seed.svg"))
        }
        println("APERÇU écrit dans ${dir.absolutePath}")
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
