package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.input.MidiKeyboardInfo

/**
 * Dialog to display MIDI keyboard information and settings.
 *
 * Shows:
 * - Keyboard name, manufacturer, connection type, ports
 * - LED channel selection (1-16)
 * - LED octave offset (to match keyboard range)
 */
class MidiKeyboardSettingsDialog : DialogFragment() {

    interface OnSettingsChangedListener {
        fun onLedChannelChanged(channel: Int)
        fun onOctaveOffsetChanged(offset: Int)
    }

    private var keyboardInfo: MidiKeyboardInfo? = null
    private var currentLedChannel: Int = 0
    private var currentOctaveOffset: Int = 0
    private var listener: OnSettingsChangedListener? = null

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_MANUFACTURER = "manufacturer"
        private const val ARG_PRODUCT = "product"
        private const val ARG_TYPE = "type"
        private const val ARG_INPUT_PORTS = "input_ports"
        private const val ARG_OUTPUT_PORTS = "output_ports"
        private const val ARG_LED_CHANNEL = "led_channel"
        private const val ARG_OCTAVE_OFFSET = "octave_offset"

        // Limits: -4 to +4 octaves
        private const val MIN_OCTAVE_OFFSET = -4
        private const val MAX_OCTAVE_OFFSET = 4

        fun newInstance(
            keyboardInfo: MidiKeyboardInfo,
            currentLedChannel: Int,
            currentOctaveOffset: Int = 0
        ): MidiKeyboardSettingsDialog {
            return MidiKeyboardSettingsDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, keyboardInfo.name)
                    putString(ARG_MANUFACTURER, keyboardInfo.manufacturer)
                    putString(ARG_PRODUCT, keyboardInfo.product)
                    putInt(ARG_TYPE, keyboardInfo.type)
                    putInt(ARG_INPUT_PORTS, keyboardInfo.inputPortCount)
                    putInt(ARG_OUTPUT_PORTS, keyboardInfo.outputPortCount)
                    putInt(ARG_LED_CHANNEL, currentLedChannel)
                    putInt(ARG_OCTAVE_OFFSET, currentOctaveOffset)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Try to get listener from parent fragment first, then activity
        listener = parentFragment as? OnSettingsChangedListener
            ?: context as? OnSettingsChangedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments ?: throw IllegalStateException("Arguments required")

        keyboardInfo = MidiKeyboardInfo(
            name = args.getString(ARG_NAME) ?: "",
            manufacturer = args.getString(ARG_MANUFACTURER) ?: "",
            product = args.getString(ARG_PRODUCT) ?: "",
            type = args.getInt(ARG_TYPE),
            inputPortCount = args.getInt(ARG_INPUT_PORTS),
            outputPortCount = args.getInt(ARG_OUTPUT_PORTS)
        )
        currentLedChannel = args.getInt(ARG_LED_CHANNEL, 0)
        currentOctaveOffset = args.getInt(ARG_OCTAVE_OFFSET, 0)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_midi_keyboard_settings, null)

        // Populate keyboard info
        view.findViewById<TextView>(R.id.txt_keyboard_name).text =
            keyboardInfo?.name?.ifEmpty { getString(R.string.gm_instrument_unknown) }

        view.findViewById<TextView>(R.id.txt_keyboard_manufacturer).text =
            keyboardInfo?.manufacturer?.ifEmpty { getString(R.string.gm_instrument_unknown) }

        view.findViewById<TextView>(R.id.txt_keyboard_connection).text =
            when (keyboardInfo?.type) {
                MidiDeviceInfo.TYPE_USB -> getString(R.string.midi_keyboard_connection_usb)
                MidiDeviceInfo.TYPE_BLUETOOTH -> getString(R.string.midi_keyboard_connection_bluetooth)
                MidiDeviceInfo.TYPE_VIRTUAL -> getString(R.string.midi_keyboard_connection_virtual)
                else -> getString(R.string.gm_instrument_unknown)
            }

        view.findViewById<TextView>(R.id.txt_keyboard_ports).text =
            getString(R.string.midi_keyboard_ports_format,
                keyboardInfo?.inputPortCount ?: 0,
                keyboardInfo?.outputPortCount ?: 0)

        // Setup LED channel spinner (1-16)
        val spinnerLedChannel = view.findViewById<Spinner>(R.id.spinner_led_channel)
        val channels = (1..16).map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, channels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLedChannel.adapter = adapter
        // Use post to ensure selection is set after adapter is fully loaded
        android.util.Log.d("MidiKeyboardSettings", "Setting spinner to channel index=$currentLedChannel")
        spinnerLedChannel.post {
            spinnerLedChannel.setSelection(currentLedChannel)
        }

        // Setup octave offset controls
        val txtOctaveOffset = view.findViewById<TextView>(R.id.txt_octave_offset)
        val btnOctaveMinus = view.findViewById<ImageButton>(R.id.btn_octave_minus)
        val btnOctavePlus = view.findViewById<ImageButton>(R.id.btn_octave_plus)

        var octaveOffset = currentOctaveOffset

        fun updateOctaveDisplay() {
            val displayText = when {
                octaveOffset > 0 -> "+$octaveOffset"
                else -> octaveOffset.toString()
            }
            txtOctaveOffset.text = displayText
            // Disable buttons at limits
            btnOctaveMinus.isEnabled = octaveOffset > MIN_OCTAVE_OFFSET
            btnOctavePlus.isEnabled = octaveOffset < MAX_OCTAVE_OFFSET
            btnOctaveMinus.alpha = if (btnOctaveMinus.isEnabled) 1.0f else 0.3f
            btnOctavePlus.alpha = if (btnOctavePlus.isEnabled) 1.0f else 0.3f
        }

        updateOctaveDisplay()

        btnOctaveMinus.setOnClickListener {
            if (octaveOffset > MIN_OCTAVE_OFFSET) {
                octaveOffset--
                updateOctaveDisplay()
            }
        }

        btnOctavePlus.setOnClickListener {
            if (octaveOffset < MAX_OCTAVE_OFFSET) {
                octaveOffset++
                updateOctaveDisplay()
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.midi_keyboard_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newChannel = spinnerLedChannel.selectedItemPosition

                android.util.Log.d("MidiKeyboardSettings", "OK clicked: newChannel=$newChannel octaveOffset=$octaveOffset")

                // Always notify the listener to update the channel
                listener?.onLedChannelChanged(newChannel)

                if (octaveOffset != currentOctaveOffset) {
                    listener?.onOctaveOffsetChanged(octaveOffset)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
