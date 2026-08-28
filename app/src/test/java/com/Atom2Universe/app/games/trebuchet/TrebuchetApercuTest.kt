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
}
