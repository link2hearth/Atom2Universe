package com.Atom2Universe.app.science.cosmicscale

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Comparaison de la taille de deux astres côte à côte (paysage), toujours à l'échelle réelle.
 * On parcourt l'échelle triée par taille au swipe (gauche = plus petit, droite = le suivant),
 * et les menus déroulants permettent de sauter sur n'importe quel astre.
 */
class CosmicScaleActivity : ThemedActivity() {

    private lateinit var glView: CosmicScaleGLView
    private lateinit var btnLeft: TextView
    private lateinit var btnRight: TextView
    private lateinit var btnSwap: ImageButton
    private lateinit var tvRatio: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnModeScroll: TextView
    private lateinit var btnModeFree: TextView

    private lateinit var leftName: TextView
    private lateinit var leftType: TextView
    private lateinit var leftRadius: TextView
    private lateinit var leftFact: TextView
    private lateinit var rightName: TextView
    private lateinit var rightType: TextView
    private lateinit var rightRadius: TextView
    private lateinit var rightFact: TextView

    /** Astres triés par rayon croissant — l'ordre fixe du mode défilement. */
    private val sorted: List<CosmicBody> = CosmicScaleData.bodies.sortedBy { it.radiusKm }

    private var scrollMode = true                 // true = défilement (ordre fixe, swipe) ; false = libre
    private var scrollIndex = 0                    // position de la fenêtre dans [sorted] (mode défilement)
    private var freeLeft: CosmicBody = CosmicScaleData.byId("sun")   // sélection du mode libre
    private var freeRight: CosmicBody = CosmicScaleData.byId("earth")

    // Astres réellement affichés (dérivés du mode courant).
    private val left: CosmicBody get() = if (scrollMode) sorted[scrollIndex] else freeLeft
    private val right: CosmicBody get() = if (scrollMode) sorted[scrollIndex + 1] else freeRight

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scrollMode = prefs.getBoolean(KEY_MODE_SCROLL, true)
        scrollIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, sorted.size - 2)
        freeLeft = CosmicScaleData.byId(prefs.getString(KEY_LEFT, "sun") ?: "sun")
        freeRight = CosmicScaleData.byId(prefs.getString(KEY_RIGHT, "earth") ?: "earth")

        buildUI()
        enableImmersiveMode()
        applyState()
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        glView = CosmicScaleGLView(this)
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        glView.renderer.leftBody = left
        glView.renderer.rightBody = right
        glView.onSwipe = { dir -> advance(dir) }

        // ── Barre haute : retour, sélecteurs, swap ──────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(0xAA000000.toInt())
        }
        val btnBack = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(Color.WHITE)
            background = null
            setOnClickListener { finish() }
        }
        btnLeft = selectorChip().apply { setOnClickListener { showSelector(this, isLeft = true) } }
        btnRight = selectorChip().apply { setOnClickListener { showSelector(this, isLeft = false) } }
        btnSwap = ImageButton(this).apply {
            setImageResource(R.drawable.ic_restart)
            setColorFilter(Color.WHITE)
            background = null
            contentDescription = getString(R.string.cosmic_swap)
            setOnClickListener { swap() }
        }

        topBar.addView(btnBack, LinearLayout.LayoutParams(dp(40), dp(40)))
        topBar.addView(btnLeft, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply { leftMargin = dp(8) })
        topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        topBar.addView(btnSwap, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) })
        topBar.addView(btnRight, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)))
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        // ── Ruban central : ratio de taille ─────────────────────────
        tvRatio = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFE9B0.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setBackgroundColor(0x66000000)
            setOnLongClickListener {
                Toast.makeText(this@CosmicScaleActivity, R.string.cosmic_credits, Toast.LENGTH_LONG).show()
                true
            }
        }
        root.addView(tvRatio, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = dp(52) })

        // ── Cartes info gauche / droite ─────────────────────────────
        val leftCard = buildInfoCard(true)
        val rightCard = buildInfoCard(false)
        root.addView(leftCard, FrameLayout.LayoutParams(
            dp(200), FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; leftMargin = dp(12); bottomMargin = dp(12) })
        root.addView(rightCard, FrameLayout.LayoutParams(
            dp(200), FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; rightMargin = dp(12); bottomMargin = dp(12) })

        // ── Bas centre : indice de swipe + bascule de mode ──────────
        val bottomCenter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        tvHint = TextView(this).apply {
            text = getString(R.string.cosmic_hint_swipe)
            textSize = 11f
            setTextColor(0xFF99A6BF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(0x66000000)
        }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x88000000.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        btnModeScroll = modeChip(getString(R.string.cosmic_mode_scroll)) { setMode(scroll = true) }
        btnModeFree = modeChip(getString(R.string.cosmic_mode_free)) { setMode(scroll = false) }
        modeRow.addView(btnModeScroll, LinearLayout.LayoutParams(dp(110), dp(32)).apply { rightMargin = dp(4) })
        modeRow.addView(btnModeFree, LinearLayout.LayoutParams(dp(96), dp(32)))

        bottomCenter.addView(tvHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
        bottomCenter.addView(modeRow)
        root.addView(bottomCenter, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })

        setContentView(root)
    }

    private fun modeChip(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun buildInfoCard(isLeft: Boolean): CardView {
        val card = CardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(6).toFloat()
            setCardBackgroundColor(0xCC0E0E22.toInt())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val name = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE) }
        val type = TextView(this).apply { textSize = 12f; setTextColor(0xFF8FA8C8.toInt()) }
        val radius = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFE6E6E6.toInt()); setPadding(0, dp(6), 0, 0)
        }
        val fact = TextView(this).apply { textSize = 12f; setTextColor(0xFFB9B9B9.toInt()) }
        content.addView(name); content.addView(type); content.addView(radius); content.addView(fact)
        card.addView(content)
        if (isLeft) {
            leftName = name; leftType = type; leftRadius = radius; leftFact = fact
        } else {
            rightName = name; rightType = type; rightRadius = radius; rightFact = fact
        }
        return card
    }

    // ── Interactions ────────────────────────────────────────────────
    private fun showSelector(anchor: View, isLeft: Boolean) {
        val popup = PopupMenu(this, anchor)
        val planets = popup.menu.addSubMenu(getString(R.string.cosmic_cat_planets))
        val stars = popup.menu.addSubMenu(getString(R.string.cosmic_cat_stars))
        val holes = popup.menu.addSubMenu(getString(R.string.cosmic_cat_blackholes))
        CosmicScaleData.bodies.forEach { b ->
            val sub = when (b.category) {
                CosmicCategory.PLANET -> planets
                CosmicCategory.STAR -> stars
                CosmicCategory.BLACK_HOLE -> holes
            }
            sub.add(getString(b.nameRes)).setOnMenuItemClickListener {
                if (isLeft) freeLeft = b else freeRight = b
                applyState(); true
            }
        }
        popup.show()
    }

    private fun swap() {
        val t = freeLeft; freeLeft = freeRight; freeRight = t
        applyState()
    }

    /** Mode défilement : avance (dir>0) ou recule (dir<0) la fenêtre dans l'échelle triée. */
    private fun advance(dir: Int) {
        if (!scrollMode) return
        val ni = scrollIndex + dir
        if (ni in 0..sorted.size - 2) {
            scrollIndex = ni
            applyState()
        }
    }

    private fun setMode(scroll: Boolean) {
        scrollMode = scroll
        applyState()
    }

    private fun applyState() {
        val l = left; val r = right
        glView.renderer.leftBody = l
        glView.renderer.rightBody = r

        val arrow = if (scrollMode) "" else " ▾"
        btnLeft.text = "${getString(l.nameRes)}$arrow"
        btnRight.text = "${getString(r.nameRes)}$arrow"
        btnLeft.isClickable = !scrollMode
        btnRight.isClickable = !scrollMode
        btnSwap.visibility = if (scrollMode) View.GONE else View.VISIBLE
        tvHint.visibility = if (scrollMode) View.VISIBLE else View.GONE

        val on = 0xFF1565C0.toInt(); val off = 0xFF333333.toInt()
        btnModeScroll.setBackgroundColor(if (scrollMode) on else off)
        btnModeFree.setBackgroundColor(if (scrollMode) off else on)

        fillCard(l, leftName, leftType, leftRadius, leftFact)
        fillCard(r, rightName, rightType, rightRadius, rightFact)
        updateRatio()

        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_MODE_SCROLL, scrollMode)
            putInt(KEY_INDEX, scrollIndex)
            putString(KEY_LEFT, freeLeft.id)
            putString(KEY_RIGHT, freeRight.id)
        }
    }

    private fun fillCard(b: CosmicBody, name: TextView, type: TextView, radius: TextView, fact: TextView) {
        name.text = getString(b.nameRes)
        type.text = getString(typeRes(b))
        radius.text = getString(R.string.cosmic_label_radius, radiusValue(b))
        val f = factValue(b)
        fact.visibility = if (f == null) View.GONE else View.VISIBLE
        if (f != null) fact.text = f
    }

    private fun updateRatio() {
        val larger = if (left.radiusKm >= right.radiusKm) left else right
        val smaller = if (larger === left) right else left
        val ratio = larger.radiusKm / smaller.radiusKm
        tvRatio.text = if (ratio < 1.02) getString(R.string.cosmic_ratio_equal)
        else getString(R.string.cosmic_ratio_radius,
            getString(larger.nameRes), ratioStr(ratio), getString(smaller.nameRes)) +
            "\n" + getString(R.string.cosmic_ratio_volume, ratioStr(ratio.pow(3.0)))
    }

    private fun typeRes(b: CosmicBody): Int = when {
        b.id == "moon" -> R.string.cosmic_type_moon
        b.kind == BodyKind.ROCKY -> R.string.cosmic_type_rocky
        b.kind == BodyKind.GAS -> R.string.cosmic_type_gas
        b.kind == BodyKind.STAR -> R.string.cosmic_type_star
        else -> R.string.cosmic_type_blackhole
    }

    private fun factValue(b: CosmicBody): String? = when (b.kind) {
        BodyKind.STAR -> "${b.spectralType} · ${b.temperatureK} K"
        BodyKind.BLACK_HOLE -> b.massSolar?.let { "≈ ${massStr(it)} M☉" }
        else -> null
    }

    // ── Helpers UI ──────────────────────────────────────────────────
    private fun selectorChip(): TextView = TextView(this).apply {
        textSize = 13f
        setTextColor(0xFFFFDD88.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setBackgroundColor(0x33FFFFFF)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); glView.onResume() }
    override fun onPause() { super.onPause(); glView.onPause() }

    companion object {
        private const val PREFS = "cosmic_scale_prefs"
        private const val KEY_MODE_SCROLL = "scroll_mode"
        private const val KEY_INDEX = "scroll_index"
        private const val KEY_LEFT = "left_id"
        private const val KEY_RIGHT = "right_id"
        private const val AU_KM = 1.495978707e8
    }

    // ── Formatage numérique (symboles d'unités universels) ──────────
    private fun radiusValue(b: CosmicBody): String = when (b.kind) {
        BodyKind.STAR -> "${decSmart(b.radiusInSuns)} R☉ · ${sci(b.radiusKm)} km"
        BodyKind.BLACK_HOLE -> {
            val au = b.radiusKm / AU_KM
            if (au >= 0.01) "${decSmart(au)} UA · ${sci(b.radiusKm)} km"
            else "${sci(b.radiusKm)} km"
        }
        else -> "${sci(b.radiusKm)} km · ${decSmart(b.radiusInEarths)} R⊕"
    }

    private fun decSmart(x: Double): String = when {
        x >= 100 -> String.format("%,.0f", x)
        x >= 10 -> String.format("%.1f", x)
        x >= 1 -> String.format("%.2f", x)
        else -> String.format("%.3f", x)
    }

    private fun sci(x: Double): String {
        if (x <= 0.0) return "0"
        val exp = floor(log10(x)).toInt()
        return if (exp in -1..5) String.format("%,.0f", x)
        else String.format("%.2f×10%s", x / 10.0.pow(exp), superscript(exp))
    }

    private fun ratioStr(x: Double): String = when {
        x < 10 -> String.format("%.1f", x)
        x <= 999_999 -> String.format("%,.0f", x)
        else -> sci(x)
    }

    private fun massStr(m: Double): String = when {
        m < 100 -> if (m == floor(m)) String.format("%.0f", m) else String.format("%.1f", m)
        m < 1000 -> String.format("%,.0f", m)
        else -> sci(m)
    }

    private fun superscript(n: Int): String {
        val sup = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻')
        return n.toString().map { sup[it] ?: it }.joinToString("")
    }
}
