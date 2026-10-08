package com.Atom2Universe.app.science.nuclide

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormatSymbols

class NuclideTableActivity : ThemedActivity() {

    private val palette by lazy { SciencePalette(this) }

    private lateinit var chartView: NuclideChartView
    private lateinit var detailPanel: LinearLayout

    private lateinit var detailNotation: TextView
    private lateinit var detailName: TextView
    private lateinit var detailStability: TextView
    private lateinit var detailHalfLife: TextView
    private lateinit var detailDecay: TextView
    private lateinit var detailSpin: TextView
    private lateinit var detailBE: TextView
    private lateinit var detailZN: TextView
    private lateinit var detailHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nuclide_table)
        enableImmersiveMode()

        chartView = findViewById(R.id.nuclide_chart)
        detailPanel = findViewById(R.id.nuclide_detail_panel)
        detailNotation = findViewById(R.id.nuclide_detail_notation)
        detailName = findViewById(R.id.nuclide_detail_name)
        detailStability = findViewById(R.id.nuclide_detail_stability)
        detailHalfLife = findViewById(R.id.nuclide_detail_halflife)
        detailDecay = findViewById(R.id.nuclide_detail_decay)
        detailSpin = findViewById(R.id.nuclide_detail_spin)
        detailBE = findViewById(R.id.nuclide_detail_be)
        detailZN = findViewById(R.id.nuclide_detail_zn)
        detailHint = findViewById(R.id.nuclide_hint)

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }

        chartView.onNuclideSelected = { nuclide ->
            if (nuclide == null) {
                detailPanel.visibility = View.GONE
                detailHint.visibility = View.VISIBLE
            } else {
                showDetail(nuclide)
            }
        }

        // Lié au cycle de vie : l'ancien scope non rattaché retenait l'activité et une erreur de
        // lecture des données faisait planter l'application.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { NuclideRepository.load(applicationContext) }.isSuccess
            }
            if (loaded) chartView.loadNuclides(NuclideRepository.getAll())
        }
    }

    private fun showDetail(n: Nuclide) {
        detailHint.visibility = View.GONE
        detailPanel.visibility = View.VISIBLE

        detailNotation.text = n.notation
        val elementName = NuclideRepository.getElementName(this, n.Z)
        detailName.text = getString(R.string.nuclide_detail_element, elementName.ifEmpty { n.symbol }, n.A)
        detailStability.text = if (n.stable) getString(R.string.nuclide_stable)
                               else getString(R.string.nuclide_radioactive)
        detailStability.setTextColor(
            palette.ink(getColor(if (n.stable) R.color.nuclide_stable_text
            else R.color.nuclide_radioactive_text))
        )
        val unknown = getString(R.string.nuclide_unknown)
        detailHalfLife.text = when {
            n.stable -> getString(R.string.nuclide_halflife_stable)
            n.halfLifeValue == null -> unknown
            else -> getString(R.string.nuclide_halflife_val,
                (n.halfLifeOperator?.let { "$it " } ?: "") + formatHalfLife(n.halfLifeValue, n.halfLifeUnit))
        }
        // Les parts ne sont utiles que s'il y a plusieurs voies.
        val showPercent = n.decayModes.size > 1
        detailDecay.text = if (n.stable) "—"
                           else n.decayModes.joinToString(", ") { m ->
                               val label = decayModeLabel(m.mode)
                               if (showPercent && m.percent != null)
                                   getString(R.string.nuclide_mode_percent, label, formatPercent(m.percent))
                               else label
                           }.ifEmpty { unknown }
        detailSpin.text = n.spin ?: unknown
        detailBE.text = if (n.bindingEnergyPerNucleon > 0.0)
            getString(R.string.nuclide_be_val, n.bindingEnergyPerNucleon)
        else "—"
        detailZN.text = getString(R.string.nuclide_zn_val, n.Z, n.N)

        val colorRes = when (n.decayType) {
            DecayType.STABLE -> R.color.nuclide_stable
            DecayType.ALPHA -> R.color.nuclide_alpha
            DecayType.BETA_MINUS -> R.color.nuclide_beta_minus
            DecayType.BETA_PLUS -> R.color.nuclide_beta_plus
            DecayType.FISSION -> R.color.nuclide_fission
            DecayType.OTHER -> R.color.nuclide_other
        }
        // Keep decay colors in the chart; their text equivalents need contrast on the panel.
        detailNotation.setTextColor(if (n.stable) palette.text else palette.ink(getColor(colorRes)))
    }

    /** 97.91 → « 97,91 » ; les parts infimes (0.0000545) en puissance de dix : « 5,5 × 10⁻⁵ ». */
    private fun formatPercent(p: Double): String {
        val locale = resources.configuration.locales[0]
        if (p >= 0.01 || p == 0.0) {
            return java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(p)
        }
        val exponent = kotlin.math.floor(kotlin.math.log10(p)).toInt()
        val mantissa = p / Math.pow(10.0, exponent.toDouble())
        val m = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(mantissa)
        return "$m × 10${Nuclide.superscript(exponent)}"
    }

    /** « 1.248e9 » + « a » → « 1,248 × 10⁹ a » (séparateur décimal et unité de la langue). */
    private fun formatHalfLife(value: String, unit: String?): String {
        val decimal = DecimalFormatSymbols.getInstance(resources.configuration.locales[0]).decimalSeparator
        val mantissa = value.substringBefore('e').substringBefore('E').replace('.', decimal)
        val exponent = value.substringAfter('e', value.substringAfter('E', "")).toIntOrNull()
        val number = if (exponent == null) mantissa else "$mantissa × 10${Nuclide.superscript(exponent)}"
        return if (unit == null) number else "$number ${unitLabel(unit)}"
    }

    private fun unitLabel(unit: String): String = when (unit) {
        "a" -> getString(R.string.nuclide_unit_year)
        "d" -> getString(R.string.nuclide_unit_day)
        "h" -> getString(R.string.nuclide_unit_hour)
        "min" -> getString(R.string.nuclide_unit_minute)
        "s" -> getString(R.string.nuclide_unit_second)
        "ms" -> getString(R.string.nuclide_unit_ms)
        "μs", "us" -> getString(R.string.nuclide_unit_us)
        "ns" -> getString(R.string.nuclide_unit_ns)
        else -> unit
    }

    /**
     * EC et SF sont des abréviations anglaises (CE et FS en français), y compris dans les modes
     * composés (« β+/EC », « ECp ») ; β- s'écrit avec un vrai signe moins.
     */
    private fun decayModeLabel(mode: String): String = mode
        .replace("EC", getString(R.string.nuclide_mode_ec))
        .replace("SF", getString(R.string.nuclide_mode_sf))
        .replace("β-", "β−")
}
