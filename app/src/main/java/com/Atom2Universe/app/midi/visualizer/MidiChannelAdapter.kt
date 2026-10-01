package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.ColorSettingsManager
import com.Atom2Universe.app.midi.service.MidiAudioMixer

/**
 * Liste des canaux du morceau, chacun avec son clavier animé.
 *
 * Ce que l'adaptateur sait : QUELS canaux afficher et l'état choisi par l'utilisateur (volume,
 * mute, clavier replié). Ce qui sonne en ce moment, il ne le sait pas : les claviers et
 * indicateurs le lisent eux-mêmes dans [MidiLiveState]. Aucune note ne passe donc par ici.
 */
class MidiChannelAdapter(private val context: Context) :
    RecyclerView.Adapter<MidiChannelAdapter.ViewHolder>(), MidiFrameClock.Tickable {

    /** Un canal tel que l'analyse du fichier l'a décrit (ne change pas pendant la lecture). */
    data class Row(
        val trackIndex: Int,
        val channel: Int,
        val program: Int,            // Programme de départ, selon l'analyse
        val trackName: String,
        val isDrumTrack: Boolean,
        val noteRangeMin: Int,
        val noteRangeMax: Int,
        val programCount: Int,
        val allPrograms: List<Int>
    )

    /**
     * Seul réglage purement visuel : le clavier replié. Le mute et le volume, eux, ne sont pas
     * copiés ici : on les relit dans MidiEventDispatcher / MidiAudioMixer, qui font foi.
     */
    private class ChannelUi(var keyboardVisible: Boolean = true)

    var onMuteChanged: ((channel: Int, isMuted: Boolean) -> Unit)? = null
    var onVolumeChanged: ((channel: Int, volume: Float) -> Unit)? = null
    var onPracticeClick: ((trackIndex: Int, channel: Int, noteRangeMin: Int, noteRangeMax: Int, instrumentName: String, programNumber: Int) -> Unit)? = null

    private var rows: List<Row> = emptyList()
    private val channelUi = SparseArray<ChannelUi>()
    private var seenProgramsVersion = -1

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val header: View = itemView.findViewById(R.id.channel_header)
        val channelNumber: TextView = itemView.findViewById(R.id.channel_number)
        val instrumentName: TextView = itemView.findViewById(R.id.instrument_name)
        val multiProgramBadge: TextView = itemView.findViewById(R.id.multi_program_badge)
        val volumeSlider: SeekBar = itemView.findViewById(R.id.volume_slider)
        val activity: ChannelActivityView = itemView.findViewById(R.id.channel_activity)
        val btnPractice: ImageButton = itemView.findViewById(R.id.btn_practice)
        val btnMute: ImageButton = itemView.findViewById(R.id.btn_mute)
        val btnToggleKeyboard: ImageButton = itemView.findViewById(R.id.btn_toggle_keyboard)
        val keyboardWell: View = itemView.findViewById(R.id.piano_scroll)
        val keyboard: PianoKeyboardView = itemView.findViewById(R.id.piano_keyboard)
    }

    // === Données ===

    /**
     * Remplace la liste des canaux. Les réglages de l'utilisateur sont gardés : le fragment
     * rappelle cette méthode à chaque retour sur l'onglet, sans que le mute ou le volume ne sautent.
     */
    fun setRows(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    /** Nouveau fichier : les claviers repliés sont oubliés. */
    fun clearChannelStates() {
        channelUi.clear()
    }

    private fun uiFor(channel: Int): ChannelUi =
        channelUi.get(channel) ?: ChannelUi().also { channelUi.put(channel, it) }

    private fun currentProgram(row: Row): Int =
        MidiLiveState.program(row.channel).takeIf { it >= 0 } ?: row.program

    private fun instrumentLabel(row: Row): String {
        if (row.isDrumTrack) return context.getString(R.string.gm_drum_channel)
        val program = currentProgram(row)
        return "$program: ${GeneralMidiInstruments.getName(context, program)}"
    }

    // === RecyclerView.Adapter ===

    override fun getItemCount() = rows.size

    override fun getItemId(position: Int): Long = rows[position].trackIndex.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_midi_channel_with_piano, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val row = rows[position]
        when {
            payloads.contains(PAYLOAD_PROGRAM) -> bindInstrument(holder, row)
            payloads.contains(PAYLOAD_UI) -> bindUserState(holder, row)
            else -> onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]

        bindInstrument(holder, row)
        holder.channelNumber.text = if (row.isDrumTrack) "Dr" else "${row.channel + 1}"

        // Le clavier et l'indicateur se branchent sur le canal puis se débrouillent seuls
        holder.keyboard.bindChannel(row.channel, row.noteRangeMin, row.noteRangeMax, row.program)
        holder.activity.bindChannel(
            row.channel,
            row.noteRangeMin,
            row.noteRangeMax,
            ColorSettingsManager.getNoteColor(row.channel, currentProgram(row))
        )

        holder.header.setOnClickListener { toggleKeyboard(holder) }
        holder.btnToggleKeyboard.setOnClickListener { toggleKeyboard(holder) }
        holder.btnMute.setOnClickListener { toggleMute(holder) }
        holder.btnPractice.setOnClickListener {
            val r = rows.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
            onPracticeClick?.invoke(
                r.trackIndex, r.channel, r.noteRangeMin, r.noteRangeMax,
                instrumentLabel(r), currentProgram(r)
            )
        }

        holder.volumeSlider.setOnSeekBarChangeListener(null)
        holder.volumeSlider.progress = (MidiAudioMixer.getChannelVolume(row.channel) * 100).toInt()
        holder.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val r = rows.getOrNull(holder.bindingAdapterPosition) ?: return
                val volume = progress / 100f
                onVolumeChanged?.invoke(r.channel, volume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bindUserState(holder, row)
    }

    /** Nom de l'instrument, couleur de la puce : ce qui peut changer au fil des Program Change. */
    private fun bindInstrument(holder: ViewHolder, row: Row) {
        holder.instrumentName.text = instrumentLabel(row)

        val color = ColorSettingsManager.getNoteColor(row.channel, currentProgram(row))
        holder.channelNumber.backgroundTintList = ColorStateList.valueOf(color)
        holder.activity.setColor(color)
        holder.channelNumber.setTextColor(
            if (ColorUtils.calculateLuminance(color) > 0.45) Color.BLACK else Color.WHITE
        )

        if (row.programCount > 1) {
            holder.multiProgramBadge.visibility = View.VISIBLE
            holder.multiProgramBadge.text = "x${row.programCount}"
        } else {
            holder.multiProgramBadge.visibility = View.GONE
        }
    }

    /** Mute et clavier replié : ce que l'utilisateur a choisi. */
    private fun bindUserState(holder: ViewHolder, row: Row) {
        val ui = uiFor(row.channel)
        val muted = MidiEventDispatcher.isChannelMuted(row.channel)

        holder.keyboardWell.visibility = if (ui.keyboardVisible) View.VISIBLE else View.GONE
        holder.btnToggleKeyboard.animate().rotation(if (ui.keyboardVisible) 180f else 0f).setDuration(150).start()

        if (muted) {
            holder.btnMute.setImageResource(R.drawable.ic_volume_off)
            holder.btnMute.imageTintList = ColorStateList.valueOf(MUTED_TINT)
            holder.btnMute.contentDescription = context.getString(R.string.midi_unmute_track)
        } else {
            holder.btnMute.setImageResource(R.drawable.ic_volume_up_24)
            holder.btnMute.imageTintList = ColorStateList.valueOf(context.getColor(R.color.audio_text_secondary))
            holder.btnMute.contentDescription = context.getString(R.string.midi_mute_track)
        }

        holder.itemView.alpha = if (muted) 0.45f else 1f
        holder.instrumentName.paintFlags = if (muted) {
            holder.instrumentName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.instrumentName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun toggleKeyboard(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        val row = rows.getOrNull(position) ?: return
        val ui = uiFor(row.channel)
        ui.keyboardVisible = !ui.keyboardVisible
        notifyItemChanged(position, PAYLOAD_UI)
    }

    private fun toggleMute(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        val row = rows.getOrNull(position) ?: return
        val nowMuted = !MidiEventDispatcher.isChannelMuted(row.channel)
        onMuteChanged?.invoke(row.channel, nowMuted)
        notifyItemChanged(position, PAYLOAD_UI)
    }

    // === Instruments : les Program Change se lisent dans MidiLiveState, une fois par image ===

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        seenProgramsVersion = -1
        MidiFrameClock.register(this)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        MidiFrameClock.unregister(this)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onFrame(frameTimeNanos: Long) {
        val version = MidiLiveState.programsVersion()
        if (version == seenProgramsVersion) return
        seenProgramsVersion = version
        if (rows.isNotEmpty()) notifyItemRangeChanged(0, rows.size, PAYLOAD_PROGRAM)
    }

    private companion object {
        const val PAYLOAD_PROGRAM = "program"
        const val PAYLOAD_UI = "ui"
        val MUTED_TINT = 0xFFFF5252.toInt()
    }
}
