package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.SimpleColorPickerDialog
import com.Atom2Universe.app.midi.practice.ColorSettingsManager
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import kotlinx.coroutines.launch

/**
 * Dialog for customizing MIDI colors:
 * - Channel colors (16)
 * - Instrument colors (128, optional override)
 */
class ColorSettingsDialog : DialogFragment() {

    companion object {
        const val TAG = "ColorSettingsDialog"

        fun newInstance() = ColorSettingsDialog()
    }

    // Section expansion state
    private var channelsExpanded = true
    private var instrumentsExpanded = false

    // Views
    private lateinit var channelRow1: LinearLayout
    private lateinit var channelRow2: LinearLayout
    private lateinit var recyclerInstruments: RecyclerView
    private lateinit var layoutChannelsContent: LinearLayout
    private lateinit var iconExpandChannels: ImageView
    private lateinit var iconExpandInstruments: ImageView
    private lateinit var textChannelsCount: TextView
    private lateinit var textInstrumentsCount: TextView

    // Callback when colors change (to refresh the practice view)
    var onColorsChanged: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_color_settings, null)
        setupViews(view)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.color_settings_title)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        // Initialize ColorSettingsManager and load colors
        ColorSettingsManager.init(requireContext())
        lifecycleScope.launch {
            ColorSettingsManager.loadAllColors()
            updateAllViews()
        }
    }

    private fun setupViews(view: View) {
        // Channel section
        channelRow1 = view.findViewById(R.id.row_channels_0_7)
        channelRow2 = view.findViewById(R.id.row_channels_8_15)
        layoutChannelsContent = view.findViewById(R.id.layout_channels_content)
        iconExpandChannels = view.findViewById(R.id.icon_expand_channels)
        textChannelsCount = view.findViewById(R.id.text_channels_count)

        // Instruments section
        recyclerInstruments = view.findViewById(R.id.recycler_instruments)
        iconExpandInstruments = view.findViewById(R.id.icon_expand_instruments)
        textInstrumentsCount = view.findViewById(R.id.text_instruments_count)

        // Setup section headers
        view.findViewById<View>(R.id.section_channels_header).setOnClickListener {
            channelsExpanded = !channelsExpanded
            updateSectionVisibility()
        }

        view.findViewById<View>(R.id.section_instruments_header).setOnClickListener {
            instrumentsExpanded = !instrumentsExpanded
            updateSectionVisibility()
        }

        // Setup channel buttons
        setupChannelButtons()

        // Setup instruments RecyclerView
        setupInstrumentsRecyclerView()

        // Reset all button
        view.findViewById<View>(R.id.button_reset_all).setOnClickListener {
            showResetAllConfirmation()
        }

        // Initial section visibility
        updateSectionVisibility()
    }

    private fun setupChannelButtons() {
        val density = resources.displayMetrics.density
        val buttonSize = (36 * density).toInt()
        val margin = (4 * density).toInt()

        // Row 1: Channels 0-7
        channelRow1.removeAllViews()
        for (channel in 0..7) {
            channelRow1.addView(createChannelButton(channel, buttonSize, margin))
        }

        // Row 2: Channels 8-15
        channelRow2.removeAllViews()
        for (channel in 8..15) {
            channelRow2.addView(createChannelButton(channel, buttonSize, margin))
        }
    }

    private fun createChannelButton(channel: Int, size: Int, margin: Int): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, margin, margin, margin)
            }
        }

        val colorView = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            tag = "channel_$channel"
            setOnClickListener {
                showChannelColorPicker(channel)
            }
        }

        val label = TextView(requireContext()).apply {
            text = channel.toString()
            textSize = 10f
            setTextColor(resources.getColor(R.color.midi_text_secondary, null))
            gravity = android.view.Gravity.CENTER
        }

        layout.addView(colorView)
        layout.addView(label)

        return layout
    }

    private fun updateChannelButton(channel: Int) {
        val color = ColorSettingsManager.getChannelColor(channel)
        val hasCustom = ColorSettingsManager.hasCustomChannelColor(channel)

        // Find the button in the appropriate row
        val row = if (channel < 8) channelRow1 else channelRow2
        val index = channel % 8
        val layout = row.getChildAt(index) as? LinearLayout
        val colorView = layout?.getChildAt(0)

        colorView?.let {
            val density = resources.displayMetrics.density
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f * density
                setColor(color)
                if (hasCustom) {
                    setStroke((2 * density).toInt(), Color.WHITE)
                } else {
                    setStroke((1 * density).toInt(), Color.parseColor("#40000000"))
                }
            }
            it.background = drawable
        }
    }

    private fun setupInstrumentsRecyclerView() {
        recyclerInstruments.layoutManager = LinearLayoutManager(context)
        recyclerInstruments.adapter = InstrumentFamilyAdapter()
    }

    private fun updateSectionVisibility() {
        layoutChannelsContent.visibility = if (channelsExpanded) View.VISIBLE else View.GONE
        iconExpandChannels.rotation = if (channelsExpanded) 0f else -90f

        recyclerInstruments.visibility = if (instrumentsExpanded) View.VISIBLE else View.GONE
        iconExpandInstruments.rotation = if (instrumentsExpanded) 0f else -90f
    }

    private fun updateAllViews() {
        // Update channel buttons
        for (channel in 0..15) {
            updateChannelButton(channel)
        }

        // Update channel count
        val customChannelCount = ColorSettingsManager.getCustomChannelColorCount()
        textChannelsCount.text = if (customChannelCount > 0) "$customChannelCount/16" else ""
        textChannelsCount.visibility = if (customChannelCount > 0) View.VISIBLE else View.GONE

        // Update instrument count
        val customInstrumentCount = ColorSettingsManager.getCustomInstrumentColorCount()
        textInstrumentsCount.text = if (customInstrumentCount > 0) "$customInstrumentCount/128" else ""
        textInstrumentsCount.visibility = if (customInstrumentCount > 0) View.VISIBLE else View.GONE

        // Update instruments adapter
        (recyclerInstruments.adapter as? InstrumentFamilyAdapter)?.notifyDataSetChanged()
    }

    private fun showChannelColorPicker(channel: Int) {
        val currentColor = ColorSettingsManager.getChannelColor(channel)
        val colorHex = String.format("#%06X", 0xFFFFFF and currentColor)

        SimpleColorPickerDialog(
            context = requireContext(),
            currentColorHex = colorHex,
            currentTextColorMode = "auto",
            showAlpha = false,
            showTextMode = false
        ) { newColorHex, _ ->
            lifecycleScope.launch {
                val newColor = Color.parseColor(newColorHex)
                ColorSettingsManager.setChannelColor(channel, newColor)
                updateChannelButton(channel)
                updateAllViews()
                onColorsChanged?.invoke()
            }
        }.show()
    }

    private fun showInstrumentColorPicker(program: Int) {
        val currentColor = ColorSettingsManager.getInstrumentColor(program)
            ?: ColorSettingsManager.DEFAULT_CHANNEL_COLORS[0]
        val colorHex = String.format("#%06X", 0xFFFFFF and currentColor)

        SimpleColorPickerDialog(
            context = requireContext(),
            currentColorHex = colorHex,
            currentTextColorMode = "auto",
            showAlpha = false,
            showTextMode = false
        ) { newColorHex, _ ->
            lifecycleScope.launch {
                val newColor = Color.parseColor(newColorHex)
                ColorSettingsManager.setInstrumentColor(program, newColor)
                updateAllViews()
                onColorsChanged?.invoke()
            }
        }.show()
    }

    private fun showResetAllConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.color_settings_reset_all)
            .setMessage(R.string.color_settings_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    ColorSettingsManager.resetToDefaults()
                    updateAllViews()
                    onColorsChanged?.invoke()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Adapter for instrument families with expandable items
     */
    inner class InstrumentFamilyAdapter : RecyclerView.Adapter<InstrumentFamilyAdapter.FamilyViewHolder>() {

        private val families = GeneralMidiInstruments.Category.values()
        private val expandedFamilies = mutableSetOf<Int>()

        inner class FamilyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val headerLayout: View = itemView.findViewById(R.id.layout_family_header)
            val iconExpand: ImageView = itemView.findViewById(R.id.icon_expand)
            val checkboxFamily: View? = itemView.findViewById(R.id.checkbox_family)
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

            // Hide checkbox (used by HybridProgramSelectionDialog, not here)
            holder.checkboxFamily?.visibility = View.GONE

            // Family name
            holder.textFamilyName.text = family.getDisplayName(requireContext())

            // Count of custom colors in this family
            val customCount = family.range.count { ColorSettingsManager.hasCustomInstrumentColor(it) }
            holder.textSelectedCount.text = if (customCount > 0) "$customCount/8" else ""
            holder.textSelectedCount.visibility = if (customCount > 0) View.VISIBLE else View.GONE

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
                for (program in family.range) {
                    val programView = createProgramRow(program)
                    holder.programsContainer.addView(programView)
                }
            }
        }

        override fun getItemCount(): Int = families.size

        private fun createProgramRow(program: Int): View {
            val density = resources.displayMetrics.density
            val programView = LayoutInflater.from(context)
                .inflate(R.layout.item_instrument_program, null, false)

            // Hide checkbox (used by HybridProgramSelectionDialog)
            programView.findViewById<View>(R.id.checkbox_program)?.visibility = View.GONE

            val textNumber = programView.findViewById<TextView>(R.id.text_program_number)
            val textName = programView.findViewById<TextView>(R.id.text_program_name)
            val colorView = programView.findViewById<View>(R.id.color_preview)
            val btnReset = programView.findViewById<ImageButton>(R.id.btn_reset)

            // Show color preview
            colorView.visibility = View.VISIBLE

            textNumber.text = program.toString()
            textName.text = GeneralMidiInstruments.getName(requireContext(), program)

            // Update color preview
            val hasCustom = ColorSettingsManager.hasCustomInstrumentColor(program)
            val color = if (hasCustom) {
                ColorSettingsManager.getInstrumentColor(program)!!
            } else {
                // Show default channel 0 color with lower opacity
                Color.argb(100, 128, 128, 128)
            }

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f * density
                setColor(color)
                if (hasCustom) {
                    setStroke((2 * density).toInt(), Color.WHITE)
                } else {
                    setStroke((1 * density).toInt(), Color.parseColor("#40000000"))
                }
            }
            colorView.background = drawable

            btnReset.visibility = if (hasCustom) View.VISIBLE else View.GONE

            // Click to edit
            programView.setOnClickListener {
                showInstrumentColorPicker(program)
            }

            // Reset button
            btnReset.setOnClickListener {
                lifecycleScope.launch {
                    ColorSettingsManager.setInstrumentColor(program, null)
                    updateAllViews()
                    onColorsChanged?.invoke()
                }
            }

            return programView
        }
    }
}
