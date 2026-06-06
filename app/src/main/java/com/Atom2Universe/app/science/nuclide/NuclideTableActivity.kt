package com.Atom2Universe.app.science.nuclide

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        CoroutineScope(Dispatchers.IO).launch {
            NuclideRepository.load(this@NuclideTableActivity)
            val all = NuclideRepository.getAll()
            withContext(Dispatchers.Main) {
                chartView.loadNuclides(all)
            }
        }
    }

    private fun showDetail(n: Nuclide) {
        detailHint.visibility = View.GONE
        detailPanel.visibility = View.VISIBLE

        detailNotation.text = n.notation
        val elementName = NuclideRepository.getElementName(this, n.Z)
        detailName.text = if (elementName.isNotEmpty())
            "$elementName-${n.A}"
        else
            getString(R.string.nuclide_detail_element, n.symbol, n.A)
        detailStability.text = if (n.stable) getString(R.string.nuclide_stable)
                               else getString(R.string.nuclide_radioactive)
        detailStability.setTextColor(
            if (n.stable) getColor(R.color.nuclide_stable_text)
            else getColor(R.color.nuclide_radioactive_text)
        )
        detailHalfLife.text = if (n.stable) getString(R.string.nuclide_halflife_stable)
                              else getString(R.string.nuclide_halflife_val, n.halfLife ?: "?")
        detailDecay.text = if (n.stable) "—"
                           else n.decayModes.joinToString(", ").ifEmpty { "?" }
        detailSpin.text = n.spin
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
}
