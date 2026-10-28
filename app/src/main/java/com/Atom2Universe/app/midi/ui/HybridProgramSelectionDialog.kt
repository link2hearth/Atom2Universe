package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import kotlinx.coroutines.launch

/**
 * Dialog for selecting which MIDI programs (instruments) use SF2 synthesis
 * in hybrid mode. Programs not selected will use Sonivox (built-in GM sounds).
 *
 * Displays instruments grouped by GM family:
 * - Piano (0-7)
 * - Chromatic Percussion (8-15)
 * - Organ (16-23)
 * - Guitar (24-31)
 * - Bass (32-39)
 * - Strings (40-47)
 * - Ensemble (48-55)
 * - Brass (56-63)
 * - Reed (64-71)
 * - Pipe (72-79)
 * - Synth Lead (80-87)
 * - Synth Pad (88-95)
 * - Synth Effects (96-103)
 * - Ethnic (104-111)
 * - Percussive (112-119)
 * - Sound Effects (120-127)
 */
class HybridProgramSelectionDialog : DialogFragment() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var adapter: InstrumentFamilyAdapter

    // Selected programs (0-127)
    private val selectedPrograms = mutableSetOf<Int>()
    private var useSf2ForDrums = false

    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var checkboxDrums: CheckBox
    private lateinit var textSummary: TextView

    // Callback when selection changes
    var onSelectionChanged: ((programs: Set<Int>, useSf2ForDrums: Boolean) -> Unit)? = null
    // Callback when user cancels (dismiss hybrid mode)
    var onCancelled: (() -> Unit)? = null

    companion object {
        const val TAG = "HybridProgramSelectionDialog"

        fun newInstance() = HybridProgramSelectionDialog()
    }

    /** A General MIDI family with its localized name and the localized names of its programs. */
    data class InstrumentFamily(
        val name: String,
        val programRange: IntRange,
        val programNames: List<String>
    )

    private fun buildFamilies(): List<InstrumentFamily> {
        val context = requireContext()
        val instrumentNames = context.resources.getStringArray(R.array.gm_instruments)
        return GeneralMidiInstruments.Category.entries.map { category ->
            InstrumentFamily(
                name = category.getDisplayName(context),
                programRange = category.range,
                programNames = category.range.map { program ->
                    instrumentNames.getOrElse(program) { context.getString(R.string.gm_instrument_unknown) }
                }
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        settingsRepository = SettingsRepository(requireContext())

        val view = layoutInflater.inflate(R.layout.dialog_hybrid_program_selection, null)
        setupViews(view)

        return com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(requireContext())
            .setTitle(R.string.hybrid_program_selection_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                saveSelection()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onCancelled?.invoke()
            }
            .create()
    }

    override fun onStart() {
        super.onStart()
        loadCurrentSelection()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_instruments)
        checkboxDrums = view.findViewById(R.id.checkbox_drums_sf2)
        textSummary = view.findViewById(R.id.text_selection_summary)

        val buttonSelectAll = view.findViewById<View>(R.id.button_select_all)
        val buttonSelectNone = view.findViewById<View>(R.id.button_select_none)

        // Setup RecyclerView
        adapter = InstrumentFamilyAdapter(
            families = buildFamilies(),
            selectedPrograms = selectedPrograms,
            onProgramSelectionChanged = { updateSummary() }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Drums checkbox
        checkboxDrums.setOnCheckedChangeListener { _, isChecked ->
            useSf2ForDrums = isChecked
            updateSummary()
        }

        // Quick actions
        buttonSelectAll.setOnClickListener {
            selectAllPrograms()
        }

        buttonSelectNone.setOnClickListener {
            selectNoPrograms()
        }
    }

    private fun loadCurrentSelection() {
        lifecycleScope.launch {
            val savedPrograms = settingsRepository.getHybridSf2Programs()
            val savedDrums = settingsRepository.isHybridUseSf2ForDrums()

            selectedPrograms.clear()
            selectedPrograms.addAll(savedPrograms)
            useSf2ForDrums = savedDrums

            checkboxDrums.isChecked = useSf2ForDrums
            adapter.notifyDataSetChanged()
            updateSummary()
        }
    }

    private fun saveSelection() {
        // Call callback FIRST (synchronously) before dialog is dismissed
        // The callback will handle saving settings and activating hybrid mode
        val programsCopy = selectedPrograms.toSet()
        val drumsCopy = useSf2ForDrums

        // Save settings in background (fire and forget)
        lifecycleScope.launch {
            settingsRepository.saveHybridSf2Programs(programsCopy)
            settingsRepository.setHybridUseSf2ForDrums(drumsCopy)
        }

        // Call callback synchronously so it executes before dialog dismisses
        onSelectionChanged?.invoke(programsCopy, drumsCopy)
    }

    private fun selectAllPrograms() {
        selectedPrograms.clear()
        for (i in 0..127) {
            selectedPrograms.add(i)
        }
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun selectNoPrograms() {
        selectedPrograms.clear()
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun updateSummary() {
        val count = selectedPrograms.size
        val summaryRes = if (useSf2ForDrums) R.plurals.midi_ui_hybrid_summary_drums else R.plurals.midi_ui_hybrid_summary
        textSummary.text = resources.getQuantityString(summaryRes, count, count)
    }

    /**
     * Adapter for the expandable instrument families list
     */
    inner class InstrumentFamilyAdapter(
        private val families: List<InstrumentFamily>,
        private val selectedPrograms: MutableSet<Int>,
        private val onProgramSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<InstrumentFamilyAdapter.FamilyViewHolder>() {

        private val expandedFamilies = mutableSetOf<Int>()

        inner class FamilyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val headerLayout: View = itemView.findViewById(R.id.layout_family_header)
            val iconExpand: ImageView = itemView.findViewById(R.id.icon_expand)
            val checkboxFamily: CheckBox = itemView.findViewById(R.id.checkbox_family)
            val textFamilyName: TextView = itemView.findViewById(R.id.text_family_name)
            val textSelectedCount: TextView = itemView.findViewById(R.id.text_selected_count)
            val programsContainer: LinearLayout = itemView.findViewById(R.id.layout_programs_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FamilyViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_instrument_family, parent, false)
            return FamilyViewHolder(view)
        }

        override fun onBindViewHolder(holder: FamilyViewHolder, position: Int) {
            val family = families[position]
            val isExpanded = expandedFamilies.contains(position)

            // Family name
            holder.textFamilyName.text = family.name

            // Selected count in this family
            val selectedInFamily = family.programRange.count { selectedPrograms.contains(it) }
            holder.textSelectedCount.text = getString(
                R.string.midi_ui_count_of, selectedInFamily, family.programRange.count()
            )
            holder.textSelectedCount.visibility = if (selectedInFamily > 0) View.VISIBLE else View.GONE

            // Family checkbox state
            holder.checkboxFamily.setOnCheckedChangeListener(null) // Remove listener before setting state
            holder.checkboxFamily.isChecked = selectedInFamily == 8
            holder.checkboxFamily.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Select all in family
                    for (prog in family.programRange) {
                        selectedPrograms.add(prog)
                    }
                } else {
                    // Deselect all in family
                    for (prog in family.programRange) {
                        selectedPrograms.remove(prog)
                    }
                }
                notifyItemChanged(position)
                onProgramSelectionChanged()
            }

            // Expand/collapse state
            holder.iconExpand.rotation = if (isExpanded) 180f else 0f
            holder.programsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Header click to expand/collapse
            holder.headerLayout.setOnClickListener {
                if (expandedFamilies.contains(position)) {
                    expandedFamilies.remove(position)
                } else {
                    expandedFamilies.add(position)
                }
                notifyItemChanged(position)
            }

            // Populate programs if expanded
            if (isExpanded) {
                holder.programsContainer.removeAllViews()
                for ((index, programNumber) in family.programRange.withIndex()) {
                    val programView = LayoutInflater.from(holder.itemView.context)
                        .inflate(R.layout.item_instrument_program, holder.programsContainer, false)

                    val checkboxProgram = programView.findViewById<CheckBox>(R.id.checkbox_program)
                    val textNumber = programView.findViewById<TextView>(R.id.text_program_number)
                    val textName = programView.findViewById<TextView>(R.id.text_program_name)

                    textNumber.text = programNumber.toString()
                    textName.text = family.programNames.getOrElse(index) { getString(R.string.gm_instrument_unknown) }

                    checkboxProgram.setOnCheckedChangeListener(null)
                    checkboxProgram.isChecked = selectedPrograms.contains(programNumber)
                    checkboxProgram.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedPrograms.add(programNumber)
                        } else {
                            selectedPrograms.remove(programNumber)
                        }
                        // Update family checkbox and count
                        notifyItemChanged(position)
                        onProgramSelectionChanged()
                    }

                    // Click on entire row toggles checkbox
                    programView.setOnClickListener {
                        checkboxProgram.isChecked = !checkboxProgram.isChecked
                    }

                    holder.programsContainer.addView(programView)
                }
            }
        }

        override fun getItemCount(): Int = families.size
    }
}
