package com.Atom2Universe.app.clickerstats

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.ClickerRepository
import com.Atom2Universe.app.crypto.clicker.GachaTicketRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import com.Atom2Universe.app.crypto.gacha.rollGacha
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.periodic.getPeriodicElements
import com.Atom2Universe.app.periodic.localizedName
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch

class DevCheatActivity : ThemedActivity() {

    private lateinit var collectionStore: PeriodicCollectionStore
    private lateinit var ticketRepo: GachaTicketRepository
    private lateinit var neutrinoRepo: NeutrinoRepository

    private lateinit var ticketCountText: TextView
    private lateinit var neutrinoCountText: TextView
    private lateinit var atomAmountLabel: TextView
    private lateinit var atomSnapshotText: TextView
    private lateinit var neutrinoAmountLabel: TextView
    private lateinit var resultsContainer: LinearLayout

    private var atomAmount: LayeredNumber = LayeredNumber.one()
    private var neutrinoAmount: Long = 10L

    private val elementsByNumber by lazy { getPeriodicElements().associateBy { it.atomicNumber } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_cheat)
        enableImmersiveMode()

        collectionStore = PeriodicCollectionStore(this)
        ticketRepo      = GachaTicketRepository(this)
        neutrinoRepo    = NeutrinoRepository(this)

        ticketCountText      = findViewById(R.id.dev_ticket_count)
        neutrinoCountText    = findViewById(R.id.dev_neutrino_count)
        atomAmountLabel      = findViewById(R.id.dev_atom_amount_label)
        atomSnapshotText     = findViewById(R.id.dev_atom_snapshot)
        neutrinoAmountLabel  = findViewById(R.id.dev_neutrino_amount_label)
        resultsContainer     = findViewById(R.id.dev_results_container)

        findViewById<ImageButton>(R.id.dev_back).setOnClickListener { finish() }

        // Gacha
        findViewById<Button>(R.id.dev_roll_1).setOnClickListener  { rollAndAdd(1)  }
        findViewById<Button>(R.id.dev_roll_10).setOnClickListener { rollAndAdd(10) }
        findViewById<Button>(R.id.dev_roll_50).setOnClickListener { rollAndAdd(50) }

        // Atomes
        findViewById<Button>(R.id.dev_atoms_div10).setOnClickListener {
            val divided = atomAmount.multiplyNumber(0.1)
            atomAmount = if (divided.toNumber() < 1.0) LayeredNumber.one() else divided
            refreshAtomAmountLabel()
        }
        findViewById<Button>(R.id.dev_atoms_mul10).setOnClickListener {
            atomAmount = atomAmount.multiplyNumber(10.0)
            refreshAtomAmountLabel()
        }
        findViewById<Button>(R.id.dev_atoms_add).setOnClickListener {
            getSharedPreferences("dev_ops_prefs", Context.MODE_PRIVATE)
                .edit { putString("atoms_op", "add_${atomAmount.toNumber()}") }
        }

        // Neutrinos
        findViewById<Button>(R.id.dev_neutrinos_div10).setOnClickListener {
            if (neutrinoAmount > 1L) neutrinoAmount = (neutrinoAmount / 10L).coerceAtLeast(1L)
            refreshNeutrinoAmountLabel()
        }
        findViewById<Button>(R.id.dev_neutrinos_mul10).setOnClickListener {
            neutrinoAmount *= 10L
            refreshNeutrinoAmountLabel()
        }
        findViewById<Button>(R.id.dev_neutrinos_add).setOnClickListener {
            neutrinoRepo.addBalance(neutrinoAmount.toInt())
            getSharedPreferences("dev_ops_prefs", Context.MODE_PRIVATE)
                .edit { putString("neutrinos_refresh", "1") }
            refreshNeutrinoCount()
        }

        // Tickets
        findViewById<Button>(R.id.dev_tickets_10).setOnClickListener  { addTickets(10)  }
        findViewById<Button>(R.id.dev_tickets_100).setOnClickListener { addTickets(100) }

        // Éléments
        findViewById<Button>(R.id.dev_all_elements).setOnClickListener { addAllElements() }
        findViewById<Button>(R.id.dev_add_element_btn).setOnClickListener {
            val raw = findViewById<EditText>(R.id.dev_element_input).text.toString().trim()
            val num = raw.toIntOrNull()
            if (num != null && num in 1..118) {
                repeat(5) { collectionStore.addCopy(num) }
                val elem = elementsByNumber[num]
                Toast.makeText(this, "+5 × ${elem?.symbol ?: "Z=$num"}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.dev_element_range_error), Toast.LENGTH_SHORT).show()
            }
        }

        refreshAtomAmountLabel()
        refreshNeutrinoAmountLabel()
        refreshTicketCount()
        refreshNeutrinoCount()
        refreshAtomSnapshot()
    }

    private fun rollAndAdd(count: Int) {
        resultsContainer.removeAllViews()
        repeat(count) {
            val (element, rarity) = rollGacha()
            collectionStore.addCopy(element.atomicNumber)
            val tv = TextView(this)
            tv.setTextColor(rarity.color)
            tv.textSize = 11f
            tv.typeface = android.graphics.Typeface.MONOSPACE
            tv.setPadding(0, 3, 0, 3)
            tv.text = "  ${element.symbol}  ${element.localizedName(this)}  [${rarity.label}]"
            resultsContainer.addView(tv)
        }
    }

    private fun refreshAtomSnapshot() {
        lifecycleScope.launch {
            val state = ClickerRepository(this@DevCheatActivity).load()
            atomSnapshotText.text = state.atoms.toString()
        }
    }

    private fun refreshAtomAmountLabel() {
        atomAmountLabel.text = atomAmount.toString()
    }

    private fun refreshNeutrinoAmountLabel() {
        neutrinoAmountLabel.text = formatAmount(neutrinoAmount)
    }

    private fun refreshNeutrinoCount() {
        neutrinoCountText.text = "ν  ${neutrinoRepo.getBalance()}"
    }

    private fun refreshTicketCount() {
        lifecycleScope.launch {
            val state = ticketRepo.load()
            ticketCountText.text = "🏟 ${state.totalTickets}"
        }
    }

    private fun addTickets(count: Int) {
        lifecycleScope.launch {
            val current = ticketRepo.load()
            ticketRepo.save(current.copy(totalTickets = current.totalTickets + count))
            refreshTicketCount()
        }
    }

    private fun addAllElements() {
        for (num in 1..118) collectionStore.addCopy(num)
        Toast.makeText(this, getString(R.string.dev_all_elements_done), Toast.LENGTH_SHORT).show()
    }

    private fun formatAmount(n: Long): String = when {
        n >= 1_000_000_000L -> "${n / 1_000_000_000L}G"
        n >= 1_000_000L     -> "${n / 1_000_000L}M"
        n >= 1_000L         -> "${n / 1_000L}K"
        else                -> "$n"
    }
}
