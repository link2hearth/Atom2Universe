package com.Atom2Universe.app.science.nuclide

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormatSymbols

class NuclideTableActivity : ThemedActivity() {

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
            if (n.stable) getColor(R.color.nuclide_stable_text)
            else getColor(R.color.nuclide_radioactive_text)
        )
        val unknown = getString(R.string.nuclide_unknown)
        detailHalfLife.text = when {
            n.stable -> getString(R.string.nuclide_halflife_stable)
            n.halfLifeValue == null -> unknown
            else -> getString(R.string.nuclide_halflife_val, formatHalfLife(n.halfLifeValue, n.halfLifeUnit))
        }
        detailDecay.text = if (n.stable) "—"
                           else n.decayModes.joinToString(", ") { decayModeLabel(it) }.ifEmpty { unknown }
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
        detailNotation.setTextColor(getColor(colorRes))
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

    /** EC et SF sont des abréviations anglaises (CE et FS en français) ; β- s'écrit avec un vrai signe moins. */
    private fun decayModeLabel(mode: String): String = when (mode) {
        "EC" -> getString(R.string.nuclide_mode_ec)
        "SF" -> getString(R.string.nuclide_mode_sf)
        else -> mode.replace("β-", "β−")
    }
}
