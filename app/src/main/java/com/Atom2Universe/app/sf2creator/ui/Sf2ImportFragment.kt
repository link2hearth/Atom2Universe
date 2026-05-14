package com.Atom2Universe.app.sf2creator.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedInstrument
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPreset
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fragment for importing presets from an SF2 file.
 * Shows a tabbed interface like Polyphone with:
 * - Presets tab: List of presets with their instruments
 * - Instruments tab: List of instruments with their zones
 * - Samples tab: List of all samples in the file
 */
class Sf2ImportFragment : Fragment() {

    private lateinit var sf2NameText: TextView
    private lateinit var sf2InfoText: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var emptyState: LinearLayout
    private lateinit var selectAllButton: Button
    private lateinit var selectNoneButton: Button
    private lateinit var selectionCountText: TextView
    private lateinit var importButton: Button
    private lateinit var progressOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private lateinit var importer: Sf2Importer
    private var parseResult: Sf2ParseResult? = null
    private var targetProjectId: Long = -1

    // Selection state for presets (by index)
    private val selectedPresets = mutableSetOf<Int>()

    // Tab adapters
    private var presetsAdapter: PresetsTabAdapter? = null
    private var instrumentsAdapter: InstrumentsTabAdapter? = null
    private var samplesAdapter: SamplesTabAdapter? = null

    // Callbacks
    var onImportComplete: ((Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sf2_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importer = Sf2Importer(requireContext())
        findViews(view)
        setupButtons()

        // If parse result was set before view created, display it
        parseResult?.let { displayParseResult(it) }
    }

    private fun findViews(view: View) {
        sf2NameText = view.findViewById(R.id.sf2_name_text)
        sf2InfoText = view.findViewById(R.id.sf2_info_text)
        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)
        emptyState = view.findViewById(R.id.empty_state)
        selectAllButton = view.findViewById(R.id.select_all_button)
        selectNoneButton = view.findViewById(R.id.select_none_button)
        selectionCountText = view.findViewById(R.id.selection_count_text)
        importButton = view.findViewById(R.id.import_button)
        progressOverlay = view.findViewById(R.id.progress_overlay)
        progressBar = view.findViewById(R.id.progress_bar)
        progressText = view.findViewById(R.id.progress_text)
    }

    private fun setupButtons() {
        selectAllButton.setOnClickListener {
            parseResult?.let { result ->
                selectedPresets.clear()
                result.presets.forEachIndexed { index, _ ->
                    selectedPresets.add(index)
                }
                presetsAdapter?.notifyDataSetChanged()
                updateSelectionCount()
            }
        }

        selectNoneButton.setOnClickListener {
            selectedPresets.clear()
            presetsAdapter?.notifyDataSetChanged()
            updateSelectionCount()
        }

        importButton.setOnClickListener {
            startImport()
        }
    }

    /**
     * Set the SF2 file to import from.
     */
    fun setSf2File(file: File, projectId: Long) {
        targetProjectId = projectId

        viewLifecycleOwner.lifecycleScope.launch {
            val result = importer.parseFile(file)
            if (result != null) {
                parseResult = result
                displayParseResult(result)
            } else {
                Toast.makeText(requireContext(), R.string.sf2_import_failed, Toast.LENGTH_SHORT).show()
                onCancel?.invoke()
            }
        }
    }

    /**
     * Set pre-parsed result (for when already parsed).
     */
    fun setParseResult(result: Sf2ParseResult, projectId: Long) {
        parseResult = result
        targetProjectId = projectId
        if (view != null) {
            displayParseResult(result)
        }
    }

    private fun displayParseResult(result: Sf2ParseResult) {
        sf2NameText.text = result.info.name.ifEmpty { getString(R.string.sf2_unnamed) }

        // Format file info with more details
        val presetCount = result.presets.size
        val instrumentCount = result.instruments.size
        val sampleCount = result.samples.size
        val sizeBytes = result.getEstimatedAudioSize()
        val sizeStr = formatSize(sizeBytes)
        sf2InfoText.text = getString(R.string.sf2_import_file_info_full, presetCount, instrumentCount, sampleCount, sizeStr)

        if (result.presets.isEmpty() && result.samples.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            viewPager.visibility = View.GONE
            tabLayout.visibility = View.GONE
            importButton.isEnabled = false
        } else {
            emptyState.visibility = View.GONE
            viewPager.visibility = View.VISIBLE
            tabLayout.visibility = View.VISIBLE

            // Select all presets by default
            result.presets.forEachIndexed { index, _ ->
                selectedPresets.add(index)
            }

            setupTabs(result)
            updateSelectionCount()
        }
    }

    private fun setupTabs(result: Sf2ParseResult) {
        // Create adapters
        presetsAdapter = PresetsTabAdapter(result)
        instrumentsAdapter = InstrumentsTabAdapter(result)
        samplesAdapter = SamplesTabAdapter(result)

        // Setup ViewPager adapter
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> TabContentFragment.newInstance(TAB_PRESETS)
                    1 -> TabContentFragment.newInstance(TAB_INSTRUMENTS)
                    2 -> TabContentFragment.newInstance(TAB_SAMPLES)
                    else -> throw IllegalArgumentException("Invalid position: $position")
                }
            }
        }

        // Connect TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.sf2_tab_presets, result.presets.size)
                1 -> getString(R.string.sf2_tab_instruments, result.instruments.size)
                2 -> getString(R.string.sf2_tab_samples, result.samples.size)
                else -> ""
            }
        }.attach()
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            getString(R.string.sf2_import_size_gb, mb / 1024.0)
        } else {
            getString(R.string.sf2_import_size_mb, mb)
        }
    }

    private fun updateSelectionCount() {
        val total = parseResult?.presets?.size ?: 0
        selectionCountText.text = getString(R.string.sf2_import_selection, selectedPresets.size, total)
        importButton.isEnabled = selectedPresets.isNotEmpty()
        importButton.alpha = if (selectedPresets.isNotEmpty()) 1f else 0.5f
    }

    private fun startImport() {
        val result = parseResult ?: return
        if (selectedPresets.isEmpty()) return

        showProgress(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val importedCount = importer.importPresets(
                parseResult = result,
                presetIndices = selectedPresets.toList(),
                targetProjectId = targetProjectId,
                progressCallback = { progress ->
                    requireActivity().runOnUiThread {
                        progressBar.progress = (progress * 100).toInt()
                    }
                }
            )

            showProgress(false)

            if (importedCount > 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sf2_import_success, importedCount),
                    Toast.LENGTH_SHORT
                ).show()
                onImportComplete?.invoke(importedCount)
            } else {
                Toast.makeText(requireContext(), R.string.sf2_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
        importButton.isEnabled = !show
    }

    // ==================== Tab Content Fragment ====================

    class TabContentFragment : Fragment() {
        private var tabType: Int = TAB_PRESETS

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            tabType = arguments?.getInt(ARG_TAB_TYPE, TAB_PRESETS) ?: TAB_PRESETS
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val recyclerView = RecyclerView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(context)
                setPadding(0, 8, 0, 8)
                clipToPadding = false
            }

            // Get adapter from parent fragment
            val parentFragment = parentFragment as? Sf2ImportFragment
            recyclerView.adapter = when (tabType) {
                TAB_PRESETS -> parentFragment?.presetsAdapter
                TAB_INSTRUMENTS -> parentFragment?.instrumentsAdapter
                TAB_SAMPLES -> parentFragment?.samplesAdapter
                else -> null
            }

            return recyclerView
        }

        companion object {
            private const val ARG_TAB_TYPE = "tab_type"

            fun newInstance(tabType: Int): TabContentFragment {
                return TabContentFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_TAB_TYPE, tabType)
                    }
                }
            }
        }
    }

    // ==================== Presets Tab Adapter ====================

    inner class PresetsTabAdapter(
        private val result: Sf2ParseResult
    ) : RecyclerView.Adapter<PresetsTabAdapter.ViewHolder>() {

        private val expandedItems = mutableSetOf<Int>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.preset_card)
            val checkbox: CheckBox = view.findViewById(R.id.preset_checkbox)
            val presetName: TextView = view.findViewById(R.id.preset_name)
            val presetInfo: TextView = view.findViewById(R.id.preset_info)
            val expandButton: ImageButton = view.findViewById(R.id.expand_button)
            val samplesContainer: LinearLayout = view.findViewById(R.id.samples_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_import_preset, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val preset = result.presets[position]
            val isSelected = selectedPresets.contains(position)
            val isExpanded = expandedItems.contains(position)

            holder.presetName.text = preset.name.ifEmpty { getString(R.string.sf2_unnamed_preset, position) }

            // Show bank:program and instrument/sample count
            val bankProgram = String.format("%03d:%03d", preset.bankNumber, preset.programNumber)
            val instrumentCount = preset.getInstrumentCount()
            val sampleCount = preset.getSampleCount()
            val instrumentInfo = if (instrumentCount == 1) {
                preset.instrument?.name ?: getString(R.string.sf2_no_instrument)
            } else {
                getString(R.string.sf2_program_instruments, instrumentCount)
            }
            holder.presetInfo.text = getString(R.string.sf2_preset_detail, bankProgram, instrumentInfo, sampleCount)

            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = isSelected
            holder.card.isChecked = isSelected

            // Expand button
            holder.expandButton.rotation = if (isExpanded) 180f else 0f
            holder.samplesContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Populate instrument zones if expanded
            if (isExpanded) {
                populateZones(holder.samplesContainer, preset)
            }

            // Click handlers
            holder.checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedPresets.add(position)
                } else {
                    selectedPresets.remove(position)
                }
                holder.card.isChecked = checked
                updateSelectionCount()
            }

            holder.card.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }

            holder.expandButton.setOnClickListener {
                if (isExpanded) {
                    expandedItems.remove(position)
                } else {
                    expandedItems.add(position)
                }
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = result.presets.size

        private fun populateZones(container: LinearLayout, preset: Sf2ParsedPreset) {
            container.removeAllViews()

            if (preset.instruments.isEmpty()) return
            val inflater = LayoutInflater.from(container.context)

            // Show ALL instruments and their zones
            for (instrument in preset.instruments) {
                // Show instrument header
                val headerView = inflater.inflate(R.layout.item_sf2_zone, container, false)
                val headerIcon = headerView.findViewById<ImageView>(R.id.zone_icon)
                val headerTitle = headerView.findViewById<TextView>(R.id.zone_title)
                val headerInfo = headerView.findViewById<TextView>(R.id.zone_info)
                headerIcon.setImageResource(R.drawable.ic_piano)
                headerTitle.text = instrument.name.ifEmpty { getString(R.string.sf2_unnamed_instrument) }
                headerInfo.text = getString(R.string.sf2_zones_count, instrument.zones.size)
                container.addView(headerView)

                // Show each zone for this instrument
                for (zone in instrument.zones) {
                    val sample = result.samples.getOrNull(zone.sampleIndex) ?: continue

                    val zoneView = inflater.inflate(R.layout.item_sf2_zone, container, false)
                    val zoneIcon = zoneView.findViewById<ImageView>(R.id.zone_icon)
                    val zoneTitle = zoneView.findViewById<TextView>(R.id.zone_title)
                    val zoneInfo = zoneView.findViewById<TextView>(R.id.zone_info)

                    zoneIcon.setImageResource(R.drawable.ic_music_note)
                    zoneTitle.text = sample.name.ifEmpty { getString(R.string.sf2_unnamed_sample) }

                    val keyRange = "${noteToName(zone.keyRangeLow)} - ${noteToName(zone.keyRangeHigh)}"
                    val rootNote = noteToName(zone.getRootKey() ?: sample.originalPitch)
                    val duration = String.format("%.2fs", sample.getDurationSeconds())
                    zoneInfo.text = getString(R.string.sf2_zone_detail, keyRange, rootNote, duration)

                    // Indent zone view
                    (zoneView.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = 32

                    container.addView(zoneView)
                }
            }
        }
    }

    // ==================== Instruments Tab Adapter ====================

    inner class InstrumentsTabAdapter(
        private val result: Sf2ParseResult
    ) : RecyclerView.Adapter<InstrumentsTabAdapter.ViewHolder>() {

        private val expandedItems = mutableSetOf<Int>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.item_card)
            val icon: ImageView = view.findViewById(R.id.item_icon)
            val title: TextView = view.findViewById(R.id.item_title)
            val info: TextView = view.findViewById(R.id.item_info)
            val expandButton: ImageButton = view.findViewById(R.id.expand_button)
            val detailContainer: LinearLayout = view.findViewById(R.id.detail_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sf2_expandable, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val instrument = result.instruments[position]
            val isExpanded = expandedItems.contains(position)

            holder.icon.setImageResource(R.drawable.ic_piano)
            holder.title.text = instrument.name.ifEmpty { getString(R.string.sf2_unnamed_instrument) }
            holder.info.text = getString(R.string.sf2_zones_count, instrument.zones.size)

            holder.expandButton.rotation = if (isExpanded) 180f else 0f
            holder.detailContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                populateInstrumentZones(holder.detailContainer, instrument)
            }

            holder.expandButton.setOnClickListener {
                if (isExpanded) {
                    expandedItems.remove(position)
                } else {
                    expandedItems.add(position)
                }
                notifyItemChanged(position)
            }

            holder.card.setOnClickListener {
                holder.expandButton.performClick()
            }
        }

        override fun getItemCount() = result.instruments.size

        private fun populateInstrumentZones(container: LinearLayout, instrument: Sf2ParsedInstrument) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)

            for (zone in instrument.zones) {
                val sample = result.samples.getOrNull(zone.sampleIndex) ?: continue

                val zoneView = inflater.inflate(R.layout.item_sf2_zone, container, false)
                val zoneIcon = zoneView.findViewById<ImageView>(R.id.zone_icon)
                val zoneTitle = zoneView.findViewById<TextView>(R.id.zone_title)
                val zoneInfo = zoneView.findViewById<TextView>(R.id.zone_info)

                zoneIcon.setImageResource(R.drawable.ic_music_note)
                zoneTitle.text = sample.name.ifEmpty { getString(R.string.sf2_unnamed_sample) }

                val keyRange = "${noteToName(zone.keyRangeLow)} - ${noteToName(zone.keyRangeHigh)}"
                val rootNote = noteToName(zone.getRootKey() ?: sample.originalPitch)
                val duration = String.format("%.2fs", sample.getDurationSeconds())
                zoneInfo.text = getString(R.string.sf2_zone_detail, keyRange, rootNote, duration)

                container.addView(zoneView)
            }
        }
    }

    // ==================== Samples Tab Adapter ====================

    inner class SamplesTabAdapter(
        private val result: Sf2ParseResult
    ) : RecyclerView.Adapter<SamplesTabAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.item_card)
            val icon: ImageView = view.findViewById(R.id.item_icon)
            val title: TextView = view.findViewById(R.id.item_title)
            val info: TextView = view.findViewById(R.id.item_info)
            val expandButton: ImageButton = view.findViewById(R.id.expand_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sf2_expandable, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sample = result.samples[position]

            holder.icon.setImageResource(R.drawable.ic_music_note)
            holder.title.text = sample.name.ifEmpty { getString(R.string.sf2_unnamed_sample) }

            // Show sample details
            val rootNote = noteToName(sample.originalPitch)
            val duration = String.format("%.2fs", sample.getDurationSeconds())
            val sampleRate = "${sample.sampleRate} Hz"
            val loopInfo = if (sample.hasLoop()) getString(R.string.sf2_has_loop) else ""
            holder.info.text = getString(R.string.sf2_sample_detail, rootNote, duration, sampleRate, loopInfo)

            // Hide expand button for samples (no children)
            holder.expandButton.visibility = View.GONE
        }

        override fun getItemCount() = result.samples.size
    }

    // ==================== Helper functions ====================

    private fun noteToName(midiNote: Int): String {
        return SampleData.midiNoteToName(midiNote)
    }

    companion object {
        private const val TAB_PRESETS = 0
        private const val TAB_INSTRUMENTS = 1
        private const val TAB_SAMPLES = 2

        fun newInstance(projectId: Long): Sf2ImportFragment {
            return Sf2ImportFragment().apply {
                this.targetProjectId = projectId
            }
        }
    }
}
