package com.Atom2Universe.app.games.puzzles.blackbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

class BlackBoxActivity : PuzzleActivity<BlackBoxState>() {
    override val gameKey = "blackbox"
    override val titleRes = R.string.blackbox_title
    override val rulesRes = R.string.blackbox_rules
    override val presets = listOf(
        Preset(R.string.blackbox_preset, 5, 5, 3),
        Preset(R.string.blackbox_preset, 8, 8, 4),
        Preset(R.string.blackbox_preset, 8, 8, 5),
        Preset(R.string.blackbox_preset, 10, 10, 6),
    )
    override val defaultPreset = 1
    private var checkButton: MaterialButton? = null

    override fun createBoard() = BlackBoxView(this)
    override fun generate(preset: Int, random: Random): BlackBoxState {
        val (size, balls) = listOf(5 to 3, 8 to 4, 8 to 5, 10 to 6)[preset]
        return BlackBoxState.generate(size, size, balls, random)
    }
    override fun encode(state: BlackBoxState) = state.encode()
    override fun decode(text: String) = BlackBoxState.decode(text)
    override fun isSolved(state: BlackBoxState) = state.isSolved
    override val hasHints = true
    override fun hint(state: BlackBoxState) = state.hint()
    override fun isLost(state: BlackBoxState) = state.isLost
    override fun statusText(state: BlackBoxState) = getString(R.string.blackbox_status, state.guesses.count { it }, state.ballCount)

    override fun createControls(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        val b = actionButton(R.string.blackbox_check, R.drawable.ic_check, primary = true) { current?.let { play(it.check()) } }
        checkButton = b
        addView(b, LinearLayout.LayoutParams((140 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onStateShown(state: BlackBoxState) {
        checkButton?.let {
            it.isEnabled = !state.checked && state.guesses.count { g -> g } == state.ballCount
            it.alpha = if (it.isEnabled) 1f else 0.4f
        }
    }
}

/**
 * La Boîte noire de Tatham habillée en expérience de Rutherford : la boîte est la chambre à vide,
 * avec son réseau d'atomes d'or en filigrane ; les noyaux cachés dévient ou arrêtent les
 * particules alpha. Les bords portent les détecteurs : en toucher un y tire une particule, et les
 * détecteurs qui la reçoivent s'allument d'un éclat de scintillation (vert phosphore). Un point
 * barré : particule absorbée ; une flèche en demi-tour : particule renvoyée ; un nombre : les
 * deux détecteurs reliés par la même trajectoire.
 *
 * Toucher l'intérieur pose ou retire un noyau supposé. Après la vérification, les vrais noyaux
 * apparaissent : cerclés de vert si on les avait trouvés, en fantôme cerclé de rouge sinon.
 */
class BlackBoxView(context: Context) : PuzzleView<BlackBoxState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val path = Path()

    /** Les détecteurs qui viennent de s'allumer, et quand. */
    private val flashes = HashMap<Int, Long>()
    private var previous: IntArray? = null

    override fun onStateChanged() {
        val s = state ?: return
        val before = previous
        if (before != null && before.size == s.results.size) {
            val now = SystemClock.uptimeMillis()
            for (p in s.results.indices) {
                if (before[p] == BlackBoxState.UNFIRED && s.results[p] != BlackBoxState.UNFIRED) flashes[p] = now
            }
        }
        previous = s.results.copyOf()
    }

    private fun portCell(state: BlackBoxState, port: Int): Pair<Int, Int> {
        val w = state.w; val h = state.h
        return when {
            port < w -> port to -1
            port < w + h -> w to port - w
            port < 2 * w + h -> (w - 1 - (port - w - h)) to h
            else -> -1 to (h - 1 - (port - 2 * w - h))
        }
    }

    override fun drawBoard(canvas: Canvas, state: BlackBoxState, area: RectF) {
        grid.fit(area, state.w, state.h, 1f, 1f, 1f, 1f)
        val cell = grid.cell
        val now = SystemClock.uptimeMillis()

        // La chambre à vide : sombre dans les deux thèmes, c'est un instrument.
        fill.color = palette.blend(VACUUM, palette.accent, 0.06f)
        canvas.drawRoundRect(grid.left, grid.top, grid.right, grid.bottom, cell * 0.2f, cell * 0.2f, fill)
        stroke.color = palette.withAlpha(GOLD, 0.08f); stroke.strokeWidth = cell * 0.02f
        for (k in 1 until state.w) canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        for (k in 1 until state.h) canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        // Le réseau d'atomes d'or de la feuille, en filigrane.
        fill.color = palette.withAlpha(GOLD, 0.22f)
        for (i in 0 until state.w * state.h) canvas.drawCircle(grid.cx(i % state.w), grid.cy(i / state.w), cell * 0.045f, fill)
        stroke.color = palette.withAlpha(GOLD, 0.35f); stroke.strokeWidth = cell * 0.04f
        canvas.drawRoundRect(grid.left, grid.top, grid.right, grid.bottom, cell * 0.2f, cell * 0.2f, stroke)

        // Les détecteurs.
        var animating = false
        val litFill = palette.blend(palette.raised, PHOSPHOR, if (palette.isLight) 0.22f else 0.16f)
        val ink = KitPalette.ensureContrast(PHOSPHOR_INK, litFill, 3.0)
        for (port in 0 until state.ports) {
            val (x, y) = portCell(state, port)
            val cx = grid.left + (x + 0.5f) * cell; val cy = grid.top + (y + 0.5f) * cell
            val res = state.results[port]
            r.set(cx - cell * 0.36f, cy - cell * 0.36f, cx + cell * 0.36f, cy + cell * 0.36f)
            val lit = res != BlackBoxState.UNFIRED
            fill.color = if (lit) litFill else palette.raised
            canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, fill)
            stroke.color = if (lit) palette.withAlpha(PHOSPHOR, 0.8f) else palette.outline
            stroke.strokeWidth = cell * 0.03f
            canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, stroke)
            when (res) {
                BlackBoxState.UNFIRED -> chevron(canvas, cx, cy, x, y, state, cell)
                BlackBoxState.HIT -> {
                    // Absorbée : la particule s'arrête net, un point barré.
                    fill.color = ink
                    canvas.drawCircle(cx, cy, cell * 0.1f, fill)
                    stroke.color = ink; stroke.strokeWidth = cell * 0.045f
                    canvas.drawLine(cx - cell * 0.2f, cy + cell * 0.2f, cx + cell * 0.2f, cy - cell * 0.2f, stroke)
                }
                BlackBoxState.REFLECT -> {
                    // Renvoyée : une flèche qui fait demi-tour.
                    stroke.color = ink; stroke.strokeWidth = cell * 0.05f
                    path.reset()
                    path.moveTo(cx - cell * 0.16f, cy + cell * 0.18f)
                    path.lineTo(cx - cell * 0.16f, cy - cell * 0.03f)
                    path.arcTo(cx - cell * 0.16f, cy - cell * 0.19f, cx + cell * 0.16f, cy + cell * 0.13f, 180f, 180f, false)
                    path.lineTo(cx + cell * 0.16f, cy + cell * 0.16f)
                    canvas.drawPath(path, stroke)
                    path.reset()
                    path.moveTo(cx + cell * 0.07f, cy + cell * 0.07f)
                    path.lineTo(cx + cell * 0.16f, cy + cell * 0.18f)
                    path.lineTo(cx + cell * 0.25f, cy + cell * 0.07f)
                    canvas.drawPath(path, stroke)
                }
                else -> canvas.centeredText(res.toString(), cx, cy, cell * 0.4f,
                    KitPalette.ensureContrast(palette.piece(res * 2), litFill, 3.0), bold = true)
            }
            // L'éclat de scintillation, quand le détecteur vient de recevoir la particule.
            val t = flashes[port]?.let { (now - it) / FLASH_MS.toFloat() }
            if (t != null && t < 1f) {
                animating = true
                stroke.color = palette.withAlpha(PHOSPHOR, (1f - t) * 0.9f)
                stroke.strokeWidth = cell * 0.06f * (1f - t) + 1f
                canvas.drawCircle(cx, cy, cell * (0.3f + 0.5f * t), stroke)
                fill.color = palette.withAlpha(PHOSPHOR, (1f - t) * 0.35f)
                canvas.drawCircle(cx, cy, cell * (0.3f + 0.2f * t), fill)
            } else if (t != null) flashes.remove(port)
        }

        // Les noyaux : supposés, puis révélés après la vérification.
        for (i in 0 until state.w * state.h) {
            grid.rect(i % state.w, i / state.w, r)
            val real = state.balls[i]
            val guess = state.guesses[i]
            if (guess) nucleus(canvas, r.centerX(), r.centerY(), cell, if (state.checked && !real) 0.45f else 1f)
            else if (state.checked && real) nucleus(canvas, r.centerX(), r.centerY(), cell, 0.35f)
            if (state.checked && (real || guess)) {
                stroke.color = if (real && guess) palette.success else palette.error
                stroke.strokeWidth = cell * 0.06f
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.4f, stroke)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    /** Un petit chevron tourné vers la chambre : ce détecteur peut encore tirer. */
    private fun chevron(canvas: Canvas, cx: Float, cy: Float, x: Int, y: Int, state: BlackBoxState, cell: Float) {
        val dx = when { x < 0 -> 1f; x >= state.w -> -1f; else -> 0f }
        val dy = when { y < 0 -> 1f; y >= state.h -> -1f; else -> 0f }
        val k = cell * 0.1f
        stroke.color = palette.withAlpha(palette.text, 0.35f); stroke.strokeWidth = cell * 0.04f
        path.reset()
        path.moveTo(cx - dx * k - dy * k, cy - dy * k - dx * k)
        path.lineTo(cx + dx * k, cy + dy * k)
        path.lineTo(cx - dx * k + dy * k, cy - dy * k + dx * k)
        canvas.drawPath(path, stroke)
    }

    /** Un noyau d'or : une grappe de nucléons, protons dorés et neutrons ambrés. */
    private fun nucleus(canvas: Canvas, cx: Float, cy: Float, cell: Float, alpha: Float) {
        val n = cell * 0.11f
        fill.color = palette.withAlpha(GOLD, 0.2f * alpha)
        canvas.drawCircle(cx, cy, cell * 0.36f, fill)
        for ((k, pos) in NUCLEONS.withIndex()) {
            val px = cx + pos.first * n; val py = cy + pos.second * n
            val base = if (k % 2 == 0) GOLD else AMBER
            fill.shader = RadialGradient(px - n * 0.35f, py - n * 0.35f, n * 1.2f,
                palette.withAlpha(palette.blend(base, 0xFFFFFFFF.toInt(), 0.55f), alpha), palette.withAlpha(base, alpha),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, n, fill)
            fill.shader = null
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i >= 0) { s.toggleGuess(i)?.let { tick(); move(it) }; return }
        for (port in 0 until s.ports) {
            val (px, py) = portCell(s, port)
            r.set(grid.left + px * grid.cell, grid.top + py * grid.cell, grid.left + (px + 1) * grid.cell, grid.top + (py + 1) * grid.cell)
            if (r.contains(x, y)) { s.fire(port)?.let { tick(); move(it) }; return }
        }
    }

    private companion object {
        const val FLASH_MS = 700L
        // Les couleurs de l'expérience : un instrument, les mêmes dans les deux thèmes.
        const val VACUUM = 0xFF0E1424.toInt()
        const val GOLD = 0xFFFFC107.toInt()
        const val AMBER = 0xFFFF8F00.toInt()
        const val PHOSPHOR = 0xFF69F0AE.toInt()
        const val PHOSPHOR_INK = 0xFF00C853.toInt()
        /** Les sept nucléons d'une grappe, en rayons de nucléon autour du centre. */
        val NUCLEONS = listOf(0f to 0f, -1.6f to -0.6f, 1.6f to -0.6f, 0f to -1.7f, -1f to 1.4f, 1f to 1.4f, 1.9f to 0.9f)
    }
}

class BlackBoxArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFC107.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        var s = BlackBoxState.generate(6, 6, 3, Random(22))
        for (p in listOf(1, 4, 8, 13, 17, 22)) s = s.fire(p) ?: s
        val g = s.balls.copyOf()
        drawWith(BlackBoxView(context), BlackBoxState(6, 6, s.balls, g, s.results, s.pairs, false), canvas, boardRect(w, h, 1.05f), palette)
    }
}
