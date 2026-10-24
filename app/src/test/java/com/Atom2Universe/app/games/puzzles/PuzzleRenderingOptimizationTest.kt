package com.Atom2Universe.app.games.puzzles

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.puzzles.lightup.LightUpState
import com.Atom2Universe.app.games.puzzles.lightup.LightUpView
import com.Atom2Universe.app.games.puzzles.pegs.PegsState
import com.Atom2Universe.app.games.puzzles.pegs.PegsView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PuzzleRenderingOptimizationTest {
    /** Plusieurs pièces partagent un shader : sa matrice ne doit pas déplacer les autres reflets. */
    @Test fun lesShadersReutilisesGardentLesMemesRefletsApresRedimensionnementEtChangementDePalette() {
        val context = RuntimeEnvironment.getApplication()
        val bulbs = LightUpView(context)
        val pegs = PegsView(context)
        for (accent in intArrayOf(0xFF4FC3F7.toInt(), 0xFF81C784.toInt())) for (cell in intArrayOf(100, 180)) {
            val palette = KitPalette.artwork(accent)
            bulbs.palette = palette; pegs.palette = palette
            val area = RectF(0f, 0f, cell * 2f, cell.toFloat())
            val actual = Bitmap.createBitmap(cell * 2, cell, Bitmap.Config.ARGB_8888)
            val expected = Bitmap.createBitmap(cell * 2, cell, Bitmap.Config.ARGB_8888)
            try {
                val state = LightUpState(2, 1, IntArray(2) { LightUpState.OPEN }, intArrayOf(1, 1), IntArray(2))
                bulbs.drawBoard(Canvas(actual), state, area)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val reference = Canvas(expected)
                for (i in 0..1) {
                    val cx = (i + .5f) * cell; val cy = .5f * cell
                    paint.shader = RadialGradient(cx, cy - cell * .05f, cell * .32f,
                        palette.blend(palette.error, Color.WHITE, .55f), palette.error, Shader.TileMode.CLAMP)
                    reference.drawCircle(cx, cy - cell * .04f, cell * .26f, paint)
                }
                compareInteriors(actual, expected, cell)

                actual.eraseColor(Color.TRANSPARENT); expected.eraseColor(Color.TRANSPARENT)
                pegs.drawBoard(Canvas(actual), PegsState(2, 1, intArrayOf(PegsState.PEG, PegsState.PEG), 0), area)
                val base = palette.blend(palette.accent, palette.text, .25f)
                for (i in 0..1) {
                    val cx = (i + .5f) * cell; val cy = .5f * cell
                    paint.shader = RadialGradient(cx - cell * .1f, cy - cell * .12f, cell * .4f,
                        palette.blend(base, Color.WHITE, .45f), base, Shader.TileMode.CLAMP)
                    reference.drawCircle(cx, cy, cell * .33f, paint)
                }
                compareInteriors(actual, expected, cell)
            } finally { actual.recycle(); expected.recycle() }
        }
    }

    private fun compareInteriors(actual: Bitmap, expected: Bitmap, cell: Int) {
        for (piece in 0..1) for (dx in -2..2) for (dy in -2..2) {
            val x = (piece + .5f).times(cell).toInt() + dx * cell / 20
            val y = cell / 2 + dy * cell / 20
            val a = actual.getPixel(x, y); val b = expected.getPixel(x, y)
            assertTrue(abs(Color.red(a) - Color.red(b)) <= 2 && abs(Color.green(a) - Color.green(b)) <= 2 &&
                abs(Color.blue(a) - Color.blue(b)) <= 2)
        }
    }
}
