package com.Atom2Universe.app.crypto.clicker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

/** Atome décoratif, sans prétention de représenter les orbitales quantiques. */
class AnimatedAtomRenderer {
    private data class Nucleon(val x: Float, val y: Float, val z: Float, val proton: Boolean)
    private class Atom(val protons: Int, val neutrons: Int, val shells: IntArray) {
        val nucleons: List<Nucleon>
        val beadRadius: Float

        init {
            require(shells.sum() == protons)
            val count = protons + neutrons
            // Empilement compact de sphères (maille cubique à faces centrées).
            // On prélève les sites les plus proches du centre pour former une boule.
            val sites = buildList {
                for (x in -4..4) for (y in -4..4) for (z in -4..4) {
                    if ((x + y + z) % 2 == 0) add(Triple(x.toFloat(), y.toFloat(), z.toFloat()))
                }
            }.sortedWith(compareBy<Triple<Float, Float, Float>> { it.first * it.first + it.second * it.second + it.third * it.third }
                .thenBy { it.third }.thenBy { it.second }.thenBy { it.first }).take(count)
            val mx = sites.sumOf { it.first.toDouble() }.toFloat() / count
            val my = sites.sumOf { it.second.toDouble() }.toFloat() / count
            val mz = sites.sumOf { it.third.toDouble() }.toFloat() / count
            val extent = sites.maxOf { sqrt((it.first - mx).pow(2) + (it.second - my).pow(2) + (it.third - mz).pow(2)) }
            val scale = (if (count <= 4) 0.18f else 0.28f) / (extent + 0.74f)
            beadRadius = 0.74f * scale // Léger recouvrement : aucun interstice dans le noyau.
            nucleons = sites.mapIndexed { i, site ->
                val x = (site.first - mx) * scale
                val y = (site.second - my) * scale
                val z = (site.third - mz) * scale
                // Vue oblique fixe, pour éviter d'aligner les billes de la maille.
                val rx = x * cos(0.57f) + z * sin(0.57f)
                val rz = -x * sin(0.57f) + z * cos(0.57f)
                Nucleon(rx, y * cos(0.41f) - rz * sin(0.41f),
                    y * sin(0.41f) + rz * cos(0.41f),
                    (i + 1) * protons / count > i * protons / count)
            }.sortedBy { it.z }
        }
    }

    companion object {
        const val VARIANT_COUNT = 14
        // Atomes neutres, isotopes précis ; ordre conservé pour les indices sauvegardés.
        private val atoms = listOf(
            Atom(1, 0, intArrayOf(1)),          // Hydrogène-1
            Atom(1, 1, intArrayOf(1)),          // Deutérium (hydrogène-2)
            Atom(2, 2, intArrayOf(2)),          // Hélium-4
            Atom(3, 4, intArrayOf(2, 1)),       // Lithium-7
            Atom(6, 6, intArrayOf(2, 4)),       // Carbone-12
            Atom(7, 7, intArrayOf(2, 5)),       // Azote-14
            Atom(8, 8, intArrayOf(2, 6)),       // Oxygène-16
            Atom(10, 10, intArrayOf(2, 8)),     // Néon-20
            Atom(11, 12, intArrayOf(2, 8, 1)),  // Sodium-23
            Atom(12, 12, intArrayOf(2, 8, 2)),  // Magnésium-24
            Atom(14, 14, intArrayOf(2, 8, 4)),  // Silicium-28
            Atom(16, 16, intArrayOf(2, 8, 6)),  // Soufre-32
            Atom(18, 22, intArrayOf(2, 8, 8)),  // Argon-40
            Atom(20, 20, intArrayOf(2, 8, 8, 2)) // Calcium-40
        )
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(0f, 0.65f, 1f)

    private fun color(hue: Float, alpha: Int = 255): Int {
        hsv[0] = ((hue % 360f) + 360f) % 360f
        return Color.HSVToColor(alpha, hsv)
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, variant: Int,
             seconds: Float, energy: Float = 0f) {
        if (size <= 0f) return
        val v = Math.floorMod(variant, VARIANT_COUNT)
        val hue = v * 27f + sin(seconds * 0.22f) * 32f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(size / 2f, size / 2f)
        paint.style = Paint.Style.FILL
        // Halos concentriques doux, sans bitmap ni filtre flou coûteux.
        for (i in 8 downTo 1) {
            paint.color = color(hue, 3)
            canvas.drawCircle(0f, 0f, 0.24f + i * 0.055f + energy * 0.025f, paint)
        }
        val atom = atoms[v]
        val electrons = atom.protons
        // Deux passes : les arcs et électrons éloignés sont masqués par le noyau.
        for (pass in 0..1) {
            val front = pass == 1
            for (e in 0 until electrons) {
                var shell = 0
                var slot = e
                while (slot >= atom.shells[shell]) { slot -= atom.shells[shell]; shell++ }
                val rotation = shell * 0.85f + v * 0.17f
                val radius = if (atom.shells.size == 1) 0.76f else
                    0.54f + shell * (0.32f / (atom.shells.size - 1))
                val phase = seconds * (0.85f - shell * 0.12f) +
                    slot * (2f * PI.toFloat() / atom.shells[shell]) + shell * 0.6f
                val cr = cos(rotation)
                val sr = sin(rotation)
                fun x(a: Float) = radius * (cos(a) * cr - sin(a) * 0.36f * sr)
                fun y(a: Float) = radius * (cos(a) * sr + sin(a) * 0.36f * cr)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                // Seulement une portion de l'orbite : la queue s'efface progressivement.
                for (segment in 0 until 24) {
                    val a = phase - (24 - segment) * 0.065f
                    if ((sin(a) >= 0f) != front) continue
                    val fade = (segment + 1) / 24f
                    paint.color = color(hue + e * 13f, (fade * fade * 165).toInt())
                    paint.strokeWidth = 0.006f + fade * 0.008f
                    canvas.drawLine(x(a), y(a), x(a + 0.065f), y(a + 0.065f), paint)
                }
                paint.style = Paint.Style.FILL
                if ((sin(phase) >= 0f) == front) {
                    val r = 0.034f + (sin(phase) + 1f) * 0.008f
                    bead(canvas, x(phase), y(phase), r, hue + e * 13f)
                }
            }
            if (!front) {
                canvas.save()
                canvas.rotate(sin(seconds * 0.4f) * 4f)
                for (n in atom.nucleons) {
                    // Deux familles stables : protons corail, neutrons bleu-lavande.
                    val particleHue = (if (n.proton) 12f else 225f) + sin(seconds * 0.22f) * 8f
                    bead(canvas, n.x, n.y, atom.beadRadius, particleHue)
                }
                canvas.restore()
            }
        }
        // Quelques scintillements orbitaux discrets.
        repeat(3) { i ->
            val a = seconds * 0.18f + i * 2.094f + v
            val sparkle = ((sin(seconds * 1.7f + i * 2f) + 1f) * 0.5f)
            paint.color = color(hue + 40f, (sparkle * 120).toInt())
            canvas.drawCircle(cos(a) * 0.92f, sin(a) * 0.92f, 0.008f + sparkle * 0.006f, paint)
        }
        canvas.restore()
    }

    private fun bead(canvas: Canvas, x: Float, y: Float, r: Float, hue: Float) {
        paint.color = color(hue, 22)
        canvas.drawCircle(x, y, r * 1.8f, paint)
        paint.color = color(hue)
        canvas.drawCircle(x, y, r, paint)
        paint.color = Color.argb(165, 255, 255, 255)
        canvas.drawCircle(x - r * 0.28f, y - r * 0.30f, r * 0.32f, paint)
    }
}
