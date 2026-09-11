package com.Atom2Universe.app.games.survivor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*

/** Dessin vectoriel natif : aucun bitmap ni flou par créature dans la boucle de jeu. */
internal class SurvivorArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val ink = Color.rgb(12, 28, 36)
    private val mint = Color.rgb(132, 244, 207)
    private val bone = Color.rgb(235, 229, 193)
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        fill.color = color
        c.drawOval(x - rx, y - ry, x + rx, y + ry, fill)
    }
    private fun stroke(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, w: Float) {
        line.color = color; line.strokeWidth = w
        c.drawLine(x, y, xx, yy, line)
    }

    // Hash spatial stable : les détails restent ancrés au sol, même aux coordonnées négatives.
    private fun hash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and Int.MAX_VALUE
    }

    private val terrainPaint = Paint() // Pixels nets, indépendant des peintures des personnages.

    fun terrain(c: Canvas, cameraX: Float, cameraY: Float, time: Float) {
        c.drawColor(0xFF192D2C.toInt())
        val cell = 240f
        val left = cameraX - c.width / 2f
        val top = cameraY - c.height / 2f
        // Marge : un détail n'apparaît/disparaît que lorsqu'il est entièrement hors champ.
        val minX = floor((left - 32f) / cell).toInt()
        val maxX = floor((left + c.width + 32f) / cell).toInt()
        val minY = floor((top - 32f) / cell).toInt()
        val maxY = floor((top + c.height + 32f) / cell).toInt()
        for (gy in minY..maxY) for (gx in minX..maxX) {
            val seed = hash(gx, gy)
            val x = gx * cell + 35f + seed % 170 - left
            val y = gy * cell + 35f + (seed / 173) % 170 - top
            // Beaucoup d'espace vide. Aucun motif dessiné aux limites de la grille.
            if (seed % 5 <= 1) drawGrass(c, x, y, seed, time)
            if (seed % 29 == 0) drawPebble(c, x + 22f, y + 12f, seed)
        }
    }

    private fun pixel(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        terrainPaint.color = color
        c.drawRect(x, y, x + w, y + h, terrainPaint)
    }

    private fun drawGrass(c: Canvas, x: Float, y: Float, seed: Int, time: Float) {
        val unit = 2f + seed % 3 * 0.5f
        val phase = seed % 101 * 0.17f
        val wind = sin(time * 1.7f + phase) * unit * 0.7f
        // Trois petits brins, chacun en quelques pixels ; la base reste immobile.
        for (blade in 0..2) {
            val bx = x + (blade - 1) * unit * 2f
            val tall = 2 + (seed / (blade + 1)) % 3
            for (step in 0 until tall) {
                val bend = step.toFloat() / tall
                val lean = (blade - 1) * unit * bend + wind * bend * bend
                val color = if (step == tall - 1) 0xFF648565.toInt() else 0xFF3D624D.toInt()
                pixel(c, bx + lean, y - step * unit, unit, unit, color)
            }
        }
        pixel(c, x - unit, y + unit, unit * 2f, unit, 0xFF294735.toInt())
    }

    private fun drawPebble(c: Canvas, x: Float, y: Float, seed: Int) {
        val unit = 2f + seed % 3
        val wide = 3f + (seed / 7) % 3
        val tall = 2f + (seed / 13) % 2
        pixel(c, x - unit, y + unit * (tall - 1f), unit * (wide + 1f), unit, 0xFF122421.toInt())
        pixel(c, x, y, unit * wide, unit * tall, 0xFF4C5950.toInt())
        pixel(c, x + unit, y - unit, unit * (wide - 2f), unit, 0xFF4C5950.toInt())
        pixel(c, x + unit, y - unit, unit * (wide - 2f), unit * 0.65f, 0xFF718072.toInt())
        pixel(c, x + unit * (wide - 1f), y + unit, unit, unit * (tall - 1f), 0xFF36493F.toInt())
    }
    fun enemy(c: Canvas, e: SEnemy, x: Float, y: Float, time: Float, playerX: Float, playerY: Float) {
        val r = e.radius
        oval(c, x, y + r * 0.65f, r * 0.95f, r * 0.42f, 0x55081017)
        c.save(); c.translate(x, y); c.scale(r, r)
        val t = time * (if (e.type == EnemyType.FAST) 13f else 6f) + e.visualSeed
        val gait = sin(t)
        // Les appendices sont décoratifs ; le corps plein épouse le disque de collision.
        when (e.type) {
            EnemyType.ZOMBIE -> {
                for (side in -1..1 step 2) {
                    val s = side.toFloat()
                    stroke(c, s * 0.45f, 0.35f, s * 0.58f, 0.85f + gait * s * 0.1f, ink, 0.3f)
                    stroke(c, s * 0.62f, -0.12f, s * 0.88f, 0.35f + gait * s * 0.13f, 0xFF75A779.toInt(), 0.3f)
                }
                oval(c, 0f, 0.04f, 0.73f, 0.86f, 0xFF48745F.toInt())
                oval(c, -0.16f, -0.12f, 0.49f, 0.58f, 0xFF8CB582.toInt())
                oval(c, 0f, -0.42f, 0.52f, 0.44f, 0xFFBBC695.toInt())
                oval(c, -0.2f, -0.43f, 0.12f, 0.13f, ink)
                oval(c, 0.2f, -0.43f, 0.12f, 0.13f, ink)
                oval(c, -0.2f, -0.43f, 0.05f, 0.06f, mint)
                oval(c, 0.2f, -0.43f, 0.05f, 0.06f, mint)
                stroke(c, -0.17f, -0.16f, 0.16f, -0.12f, ink, 0.08f)
                oval(c, 0.28f, 0.34f, 0.17f, 0.21f, 0xFFBCD88B.toInt())
            }
            EnemyType.FAST -> {
                c.rotate(atan2(playerY - e.y, playerX - e.x) * 180f / PI.toFloat() + 90f)
                for (side in -1..1 step 2) for (i in 0..2) {
                    val s = side.toFloat(); val yy = i * 0.38f - 0.35f
                    val bend = sin(t + i) * 0.16f
                    stroke(c, s * 0.42f, yy, s * (0.82f + bend), yy + 0.22f, ink, 0.16f)
                    stroke(c, s * (0.82f + bend), yy + 0.22f, s * 0.96f, yy - 0.08f, 0xFFE49575.toInt(), 0.08f)
                }
                oval(c, 0f, 0.08f, 0.59f, 0.84f, 0xFF923F49.toInt())
                oval(c, -0.15f, 0.04f, 0.31f, 0.68f, 0xFFDD705F.toInt())
                stroke(c, 0f, -0.55f, 0f, 0.71f, ink, 0.06f)
                oval(c, 0f, -0.6f, 0.36f, 0.32f, ink)
                for (s in -1..1 step 2) {
                    oval(c, s * 0.18f, -0.65f, 0.085f, 0.11f, 0xFFFFDB91.toInt())
                    stroke(c, s * 0.23f, -0.78f, s * 0.37f, -1.02f, bone, 0.09f)
                }
            }
            EnemyType.ERRATIC -> {
                // Méduse : cloche souple et cinq tentacules à phases décalées.
                for (i in 0..4) {
                    val xx = (i - 2) * 0.27f
                    path.reset(); path.moveTo(xx, 0.12f)
                    path.cubicTo(xx + sin(t + i) * 0.35f, 0.5f, xx - 0.24f, 0.75f, xx + sin(t + i) * 0.2f, 1.1f)
                    line.color = 0xFF78BBD4.toInt(); line.strokeWidth = 0.1f; c.drawPath(path, line)
                }
                oval(c, 0f, -0.17f, 0.91f, 0.66f + gait * 0.035f, 0xFF4A719D.toInt())
                oval(c, -0.13f, -0.3f, 0.64f, 0.44f, 0xFF9AB8DD.toInt())
                oval(c, 0f, -0.1f, 0.34f, 0.28f, ink)
                oval(c, 0f, -0.1f, 0.15f, 0.2f, 0xFFF1D8FF.toInt())
            }
            EnemyType.ORBITER -> {
                c.rotate(time * 35f + e.visualSeed * 30f)
                for (i in 0..5) {
                    c.save(); c.rotate(i * 60f + gait * 4f)
                    oval(c, 0f, -0.58f, 0.28f, 0.5f, 0xFFAD8046.toInt())
                    oval(c, -0.06f, -0.66f, 0.13f, 0.34f, 0xFFF0C578.toInt())
                    c.restore()
                }
                oval(c, 0f, 0f, 0.62f, 0.62f, ink)
                oval(c, 0f, 0f, 0.44f, 0.44f, 0xFFD6945A.toInt())
                oval(c, 0f, 0f, 0.25f + gait * 0.02f, 0.25f, bone)
                oval(c, 0f, 0f, 0.08f, 0.2f, ink)
            }
            EnemyType.SHOOTER -> {
                for (i in -1..1) {
                    stroke(c, i * 0.35f, 0.2f, i * 0.72f + sin(t + i) * 0.12f, 0.85f, 0xFF9B75AE.toInt(), 0.19f)
                }
                oval(c, 0f, 0.2f, 0.6f, 0.66f, 0xFF77648E.toInt())
                oval(c, 0f, -0.3f, 0.96f, 0.57f, 0xFF684E82.toInt())
                oval(c, -0.12f, -0.43f, 0.74f, 0.37f, 0xFFB691C3.toInt())
                for (i in -1..1) oval(c, i * 0.42f, -0.43f + abs(i) * 0.1f, 0.12f, 0.08f, bone)
                val charge = (1f - e.shootCd.coerceIn(0f, 1f))
                oval(c, 0f, 0.12f, 0.29f, 0.28f, ink)
                oval(c, 0f, 0.12f, 0.12f + charge * 0.1f, 0.12f + charge * 0.1f, 0xFFFFADD8.toInt())
            }
            EnemyType.MINI_BOSS -> {
                for (s in -1..1 step 2) {
                    stroke(c, s * 0.46f, 0.36f, s * 0.57f, 0.88f + gait * s * 0.07f, ink, 0.38f)
                    oval(c, s * 0.72f, 0.03f + gait * s * 0.08f, 0.33f, 0.51f, 0xFFA86154.toInt())
                    stroke(c, s * 0.68f, -0.4f, s * 0.85f, -0.98f, bone, 0.18f)
                    stroke(c, s * 0.85f, -0.98f, s * 0.5f, -0.78f, bone, 0.1f)
                }
                oval(c, 0f, 0f, 0.74f, 0.87f, 0xFF633F49.toInt())
                oval(c, 0f, -0.2f, 0.59f, 0.63f, 0xFFC38969.toInt())
                oval(c, 0f, -0.52f, 0.4f, 0.4f, bone)
                for (s in -1..1 step 2) oval(c, s * 0.18f, -0.51f, 0.12f, 0.09f, 0xFF822F43.toInt())
                for (i in 0..2) stroke(c, -0.38f, i * 0.18f, 0.38f, i * 0.18f, ink, 0.055f)
                oval(c, 0f, 0.16f, 0.17f, 0.25f, 0xFFFFB880.toInt())
            }
        }
        c.restore()
        if (e.hpRatio < 0.99f) {
            stroke(c, x - r * 0.7f, y - r - 7f, x + r * 0.7f, y - r - 7f, ink, 3f)
            stroke(c, x - r * 0.7f, y - r - 7f, x - r * 0.7f + r * 1.4f * e.hpRatio, y - r - 7f, bone, 3f)
        }
    }

    fun player(c: Canvas, x: Float, y: Float, radius: Float, time: Float, moving: Boolean) {
        oval(c, x, y + radius * 0.7f, radius, radius * 0.4f, 0x77081017)
        c.save(); c.translate(x, y); c.scale(radius, radius)
        val gait = if (moving) sin(time * 16f) * 0.16f else 0f
        // Cape, bottes, épaulières et casque du gardien.
        path.reset(); path.moveTo(-0.55f, -0.3f); path.lineTo(0.55f, -0.3f)
        path.lineTo(0.85f + gait, 0.9f); path.quadTo(0f, 0.7f, -0.85f + gait, 0.9f); path.close()
        fill.color = 0xFF287B80.toInt(); c.drawPath(path, fill)
        for (s in -1..1 step 2) {
            oval(c, s * 0.33f, 0.64f + gait * s, 0.23f, 0.29f, ink)
            oval(c, s * 0.64f, -0.02f, 0.27f, 0.35f, bone)
        }
        oval(c, 0f, 0.12f, 0.55f, 0.58f, 0xFFABBAB0.toInt())
        oval(c, 0f, -0.38f, 0.58f, 0.53f, bone)
        oval(c, 0f, -0.35f, 0.44f, 0.22f, ink)
        stroke(c, -0.28f, -0.37f, 0.28f, -0.37f, mint, 0.09f)
        oval(c, 0f, 0.23f, 0.15f, 0.19f, mint)
        c.restore()
    }
}
