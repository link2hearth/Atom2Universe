package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.ColorSettingsManager

/**
 * Adapter pour afficher les pistes MIDI avec clavier individuel par piste
 *
 * Chaque piste a:
 * - Header avec nom instrument/piste, numéro canal, indicateur d'activité
 * - Toggle pour afficher/cacher le clavier
 * - Clavier piano dédié montrant les notes de cette piste
 *
 * Note: Un fichier MIDI peut avoir plus de 16 pistes, même si les canaux
 * sont limités à 16. Plusieurs pistes peuvent utiliser le même canal.
 */
class MidiChannelAdapter(private val context: Context) : ListAdapter<MidiChannelAdapter.TrackState, MidiChannelAdapter.ViewHolder>(TrackDiffCallback()) {

    /**
     * État d'une piste MIDI
     */
    data class TrackState(
        val trackIndex: Int,         // Index unique de la piste dans le fichier
        val channel: Int,            // Canal MIDI utilisé par cette piste
        val program: Int,            // Programme initial
        val currentProgram: Int,     // Programme actuel (peut changer pendant la lecture)
        val trackName: String,       // Nom de la piste (peut être différent de l'instrument)
        val instrumentName: String,  // Nom de l'instrument actuel
        val isDrumTrack: Boolean = false,
        val activeNoteCount: Int = 0,
        val lastVelocity: Int = 0,
        val isKeyboardVisible: Boolean = true,  // Visibilité du clavier
        val isMuted: Boolean = false,           // Piste mutée (pas de son)
        val volume: Float = 1.0f,               // Volume du canal (0.0 à 1.0)
        val noteRangeMin: Int = 48,  // Plage de notes pour cette piste
        val noteRangeMax: Int = 84,
        val programCount: Int = 1,   // Nombre de programmes différents (détecté à l'analyse)
        val allPrograms: List<Int> = listOf()  // Liste de tous les programmes utilisés
    ) {
        val isActive: Boolean get() = activeNoteCount > 0
        val isMultiProgram: Boolean get() = programCount > 1
    }

    // Callback quand l'état mute d'un canal change
    var onMuteChanged: ((channel: Int, isMuted: Boolean) -> Unit)? = null

    // Callback quand le volume d'un canal change
    var onVolumeChanged: ((channel: Int, volume: Float) -> Unit)? = null

    // Callback pour le mode pratique
    var onPracticeClick: ((trackIndex: Int, channel: Int, noteRangeMin: Int, noteRangeMax: Int, instrumentName: String, programNumber: Int) -> Unit)? = null

    // Map pour accéder rapidement aux ViewHolders par index de piste
    private val viewHolderMap = mutableMapOf<Int, ViewHolder>()

    // Map pour trouver les pistes par canal (plusieurs pistes peuvent partager un canal)
    private val channelToTracks = mutableMapOf<Int, MutableList<Int>>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val channelColor: View = itemView.findViewById(R.id.channel_color)
        val channelNumber: TextView = itemView.findViewById(R.id.channel_number)
        val instrumentName: TextView = itemView.findViewById(R.id.instrument_name)
        val multiProgramBadge: TextView = itemView.findViewById(R.id.multi_program_badge)
        val volumeSlider: SeekBar = itemView.findViewById(R.id.volume_slider)
        val btnPractice: ImageButton = itemView.findViewById(R.id.btn_practice)
        val btnMute: ImageButton = itemView.findViewById(R.id.btn_mute)
        val btnToggleKeyboard: ImageButton = itemView.findViewById(R.id.btn_toggle_keyboard)
        val pianoScroll: HorizontalScrollView = itemView.findViewById(R.id.piano_scroll)
        val pianoKeyboard: PianoKeyboardView = itemView.findViewById(R.id.piano_keyboard)
        val activityDots: List<View> = listOf(
            itemView.findViewById(R.id.dot_1),
            itemView.findViewById(R.id.dot_2),
            itemView.findViewById(R.id.dot_3),
            itemView.findViewById(R.id.dot_4),
            itemView.findViewById(R.id.dot_5)
        )
        val header: View = itemView.findViewById(R.id.channel_header)

        var boundTrackIndex: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_midi_channel_with_piano, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val state = getItem(position)
        holder.boundTrackIndex = state.trackIndex

        // Enregistrer le holder pour accès direct par index de piste
        viewHolderMap[state.trackIndex] = holder

        // Numéro du canal (1-based pour l'affichage) + index piste si plusieurs pistes partagent le canal
        val channelDisplay = if (state.isDrumTrack) "Dr" else "${state.channel + 1}"
        holder.channelNumber.text = channelDisplay

        // Nom de l'instrument actuel (mis à jour en temps réel avec Program Change)
        holder.instrumentName.text = state.instrumentName

        // Badge multi-programmes (visible si le canal utilise plusieurs instruments)
        if (state.isMultiProgram) {
            holder.multiProgramBadge.visibility = View.VISIBLE
            holder.multiProgramBadge.text = "x${state.programCount}"
        } else {
            holder.multiProgramBadge.visibility = View.GONE
        }

        // Couleur du canal (utilise ColorSettingsManager pour les couleurs personnalisées)
        val channelColor = ColorSettingsManager.getNoteColor(state.channel, state.currentProgram)
        (holder.channelColor.background as? GradientDrawable)?.setColor(channelColor)

        // Configurer le clavier pour cette piste avec le programme actuel
        holder.pianoKeyboard.setNoteRange(state.noteRangeMin, state.noteRangeMax)
        holder.pianoKeyboard.setCurrentProgram(state.currentProgram)

        // Démarrer le sanity check pour ce clavier
        holder.pianoKeyboard.startSanityCheck()

        // Visibilité du clavier
        updateKeyboardVisibility(holder, state.isKeyboardVisible)

        // Toggle button
        holder.btnToggleKeyboard.setOnClickListener {
            toggleKeyboardVisibility(state.trackIndex)
        }

        // Click sur le header aussi pour toggle
        holder.header.setOnClickListener {
            toggleKeyboardVisibility(state.trackIndex)
        }

        // Bouton Practice Mode
        holder.btnPractice.setOnClickListener {
            onPracticeClick?.invoke(
                state.trackIndex,
                state.channel,
                state.noteRangeMin,
                state.noteRangeMax,
                state.instrumentName,
                state.currentProgram
            )
        }

        // Bouton Mute
        updateMuteButton(holder, state)
        holder.btnMute.setOnClickListener {
            toggleMute(state.trackIndex)
        }

        // Slider de volume
        holder.volumeSlider.progress = (state.volume * 100).toInt()
        holder.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    setVolume(state.trackIndex, volume)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Indicateur d'activité (dots)
        updateActivityDots(holder, state, channelColor)

        // Effet visuel: assombri si muté
        holder.itemView.alpha = when {
            state.isMuted -> 0.4f
            state.isActive || state.isKeyboardVisible -> 1f
            else -> 0.7f
        }

        // Nom barré si muté
        holder.instrumentName.paintFlags = if (state.isMuted) {
            holder.instrumentName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.instrumentName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Arrêter le sanity check et retirer du map quand le ViewHolder est recyclé
        holder.pianoKeyboard.stopSanityCheck()
        if (holder.boundTrackIndex >= 0) {
            viewHolderMap.remove(holder.boundTrackIndex)
            holder.boundTrackIndex = -1
        }
    }

    private fun updateKeyboardVisibility(holder: ViewHolder, isVisible: Boolean) {
        holder.pianoScroll.visibility = if (isVisible) View.VISIBLE else View.GONE
        // Rotation de l'icône (flèche vers bas = ouvert, flèche vers droite = fermé)
        holder.btnToggleKeyboard.rotation = if (isVisible) 0f else -90f
    }

    private fun updateMuteButton(holder: ViewHolder, state: TrackState) {
        val context = holder.itemView.context
        if (state.isMuted) {
            // Muté: icône "silent mode" avec couleur rouge
            holder.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
            holder.btnMute.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFF5252.toInt())
            holder.btnMute.contentDescription = context.getString(R.string.midi_unmute_track)
        } else {
            // Actif: icône "silent mode off" avec couleur normale
            holder.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            holder.btnMute.imageTintList = android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.midi_text_secondary)
            )
            holder.btnMute.contentDescription = context.getString(R.string.midi_mute_track)
        }
    }

    /**
     * Toggle le mute pour une piste
     */
    fun toggleMute(trackIndex: Int) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.trackIndex == trackIndex }

        if (index >= 0) {
            val current = currentList[index]
            val newMuted = !current.isMuted
            currentList[index] = current.copy(isMuted = newMuted)
            submitList(currentList)

            // Notifier le callback pour que le dispatcher filtre les notes
            onMuteChanged?.invoke(current.channel, newMuted)
        }
    }

    /**
     * Définit le volume d'une piste (0.0 à 1.0)
     */
    fun setVolume(trackIndex: Int, volume: Float) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.trackIndex == trackIndex }

        if (index >= 0) {
            val current = currentList[index]
            val clampedVolume = volume.coerceIn(0f, 1f)
            currentList[index] = current.copy(volume = clampedVolume)
            // submitList pour persister l'état (DiffCallback ne compare pas le volume,
            // donc pas de rebind inutile)
            submitList(currentList)

            // Notifier le callback pour appliquer le volume au mixer
            onVolumeChanged?.invoke(current.channel, clampedVolume)
        }
    }

    /**
     * Récupère les canaux actuellement mutés
     */
    fun getMutedChannels(): Set<Int> {
        return currentList.filter { it.isMuted }.map { it.channel }.toSet()
    }

    private fun updateActivityDots(holder: ViewHolder, state: TrackState, channelColor: Int) {
        val activeCount = when {
            state.activeNoteCount == 0 -> 0
            state.activeNoteCount <= 1 -> 1
            state.activeNoteCount <= 3 -> 2
            state.activeNoteCount <= 5 -> 3
            state.activeNoteCount <= 8 -> 4
            else -> 5
        }

        holder.activityDots.forEachIndexed { index, dot ->
            if (index < activeCount) {
                dot.visibility = View.VISIBLE
                (dot.background as? GradientDrawable)?.setColor(channelColor)
            } else {
                dot.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Toggle la visibilité du clavier pour une piste
     */
    fun toggleKeyboardVisibility(trackIndex: Int) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.trackIndex == trackIndex }

        if (index >= 0) {
            val current = currentList[index]
            currentList[index] = current.copy(isKeyboardVisible = !current.isKeyboardVisible)
            submitList(currentList)
        }
    }

    /**
     * Note On sur un canal - dispatche vers toutes les pistes utilisant ce canal
     */
    fun noteOn(channel: Int, note: Int, velocity: Int) {
        // Trouver toutes les pistes qui utilisent ce canal
        val trackIndices = channelToTracks[channel] ?: return

        trackIndices.forEach { trackIndex ->
            // Mettre à jour directement le clavier du ViewHolder si disponible
            viewHolderMap[trackIndex]?.pianoKeyboard?.noteOn(channel, note, velocity)

            // Mettre à jour le compteur d'activité
            updateTrackActivity(trackIndex, 1, velocity, increment = true)
        }
    }

    /**
     * Note Off sur un canal - dispatche vers toutes les pistes utilisant ce canal
     */
    fun noteOff(channel: Int, note: Int) {
        // Trouver toutes les pistes qui utilisent ce canal
        val trackIndices = channelToTracks[channel] ?: return

        trackIndices.forEach { trackIndex ->
            // Mettre à jour directement le clavier du ViewHolder si disponible
            viewHolderMap[trackIndex]?.pianoKeyboard?.noteOff(channel, note)

            // Mettre à jour le compteur d'activité
            updateTrackActivity(trackIndex, -1, 0, increment = true)
        }
    }

    /**
     * Éteindre toutes les notes sur tous les claviers
     * Note: Ne pas appeler submitList() ici car ça peut perturber l'adapter
     * et empêcher les nouveaux événements noteOn d'être affichés
     */
    fun allNotesOff() {
        viewHolderMap.values.forEach { holder ->
            holder.pianoKeyboard.allNotesOff()
        }
        // Les compteurs d'activité seront mis à jour naturellement
        // quand les nouvelles notes arriveront
    }

    /**
     * Force le rafraîchissement de tous les claviers en les enroulant puis déroulant.
     * Cela force DiffUtil à re-binder les ViewHolders, ce qui:
     * - Ré-enregistre les ViewHolders dans viewHolderMap
     * - Réinitialise les PianoKeyboardViews
     * - Corrige les problèmes d'affichage des notes
     *
     * @param onComplete callback appelé quand le rafraîchissement est terminé
     */
    fun collapseExpandAllKeyboards(onComplete: (() -> Unit)? = null) {
        val currentList = currentList.toMutableList()
        if (currentList.isEmpty()) {
            onComplete?.invoke()
            return
        }

        // 1. Enrouler tous les claviers (mettre isKeyboardVisible à false)
        val collapsedList = currentList.map { it.copy(isKeyboardVisible = false) }
        submitList(collapsedList) {
            // 2. Une fois enroulés, les dérouler immédiatement (mettre isKeyboardVisible à true)
            val expandedList = collapsedList.map { it.copy(isKeyboardVisible = true) }
            submitList(expandedList) {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Démarre le sanity check périodique sur tous les claviers
     * (nettoyage des notes fantômes)
     */
    fun startSanityCheck() {
        viewHolderMap.values.forEach { holder ->
            holder.pianoKeyboard.startSanityCheck()
        }
    }

    /**
     * Arrête le sanity check sur tous les claviers
     */
    fun stopSanityCheck() {
        viewHolderMap.values.forEach { holder ->
            holder.pianoKeyboard.stopSanityCheck()
        }
    }

    /**
     * Met à jour l'activité d'une piste
     */
    private fun updateTrackActivity(trackIndex: Int, delta: Int, velocity: Int, increment: Boolean) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.trackIndex == trackIndex }

        if (index >= 0) {
            val current = currentList[index]
            val newCount = if (increment) {
                (current.activeNoteCount + delta).coerceAtLeast(0)
            } else {
                delta
            }
            currentList[index] = current.copy(
                activeNoteCount = newCount,
                lastVelocity = if (velocity > 0) velocity else current.lastVelocity
            )
            submitList(currentList)
        }
    }

    /**
     * Initialise avec les pistes détectées
     * Note: noteRangeMin/Max globaux sont gardés pour compatibilité mais on utilise
     * maintenant les plages par canal pour un affichage optimisé
     */
    fun setTracks(tracks: List<MidiNoteTracker.TrackInfo>, noteRangeMin: Int, noteRangeMax: Int) {
        // D'abord nettoyer toutes les notes des pianos existants
        viewHolderMap.values.forEach { holder ->
            holder.pianoKeyboard.allNotesOff()
        }
        // NE PAS faire viewHolderMap.clear() ici !
        // submitList() est asynchrone (AsyncListDiffer). Si les pistes n'ont pas changé
        // (même trackIndex, même canal, même programme), DiffUtil ne rappelle pas
        // onBindViewHolder(), donc les ViewHolders ne seraient jamais ré-enregistrés
        // dans le map. On nettoie les entrées obsolètes dans le callback de submitList.

        // Construire le map channel -> trackIndices
        channelToTracks.clear()
        tracks.forEach { info ->
            val list = channelToTracks.getOrPut(info.channel) { mutableListOf() }
            list.add(info.trackIndex)
        }

        // Garder les trackIndex valides pour le nettoyage post-diff
        val newTrackIndices = tracks.map { it.trackIndex }.toSet()

        val states = tracks.map { info ->
            // Formater le nom avec le numéro de programme (sauf pour batterie)
            val displayName = if (info.isDrumTrack) {
                info.instrumentName
            } else {
                formatInstrumentName(info.program)
            }
            TrackState(
                trackIndex = info.trackIndex,
                channel = info.channel,
                program = info.program,
                currentProgram = info.program,  // Initialiser au programme de départ
                trackName = info.trackName,
                instrumentName = displayName,
                isDrumTrack = info.isDrumTrack,
                activeNoteCount = 0,
                lastVelocity = 0,
                isKeyboardVisible = true,  // Tous visibles par défaut
                // Utiliser la plage spécifique du canal au lieu de la plage globale
                noteRangeMin = info.channelNoteRangeMin,
                noteRangeMax = info.channelNoteRangeMax,
                programCount = info.programCount,
                allPrograms = info.allPrograms
            )
        }
        // Forcer un remplacement synchrone de la liste pour éviter une race condition.
        // submitList(states) est normalement asynchrone (AsyncListDiffer lance un diff
        // sur un thread background). Pendant ce diff, des événements noteOn peuvent arriver
        // et appeler updateTrackActivity(), qui lit currentList (encore l'ancienne liste)
        // puis appelle submitList(ancienneListe_modifiée), ce qui ANNULE le diff en cours.
        // Résultat: la nouvelle liste de pistes est perdue et l'adapter garde l'ancienne.
        //
        // En appelant submitList(null) d'abord, on emprunte le fast path synchrone
        // d'AsyncListDiffer (mList = null immédiatement). Ensuite submitList(states)
        // emprunte aussi le fast path synchrone (car mList == null → insertion directe).
        // currentList retourne immédiatement la bonne liste, et les noteOn qui suivent
        // travaillent sur les bonnes pistes.
        submitList(null)
        submitList(states) {
            // Après que DiffUtil ait terminé, supprimer les entrées du map
            // dont le trackIndex n'existe plus dans la nouvelle liste
            viewHolderMap.keys.retainAll(newTrackIndices)
        }
    }

    /**
     * Met à jour le programme (instrument) d'un canal en temps réel
     * Appelé lors d'un Program Change pendant la lecture
     * Met à jour TOUTES les pistes utilisant ce canal (pas seulement la première)
     */
    fun onProgramChange(channel: Int, program: Int) {
        val currentList = currentList.toMutableList()
        var hasChanges = false
        val affectedTrackIndices = mutableListOf<Int>()

        // Trouver TOUTES les pistes qui utilisent ce canal
        for (i in currentList.indices) {
            val current = currentList[i]
            if (current.channel == channel && !current.isDrumTrack) {
                val newInstrumentName = formatInstrumentName(program)
                currentList[i] = current.copy(
                    currentProgram = program,
                    instrumentName = newInstrumentName
                )
                hasChanges = true
                affectedTrackIndices.add(current.trackIndex)
            }
        }

        if (hasChanges) {
            submitList(currentList)

            // Mettre à jour directement les ViewHolders pour garantir la réactivité.
            // Le submitList ci-dessus est asynchrone et peut être annulé par un
            // updateTrackActivity (noteOn) qui lit currentList avant la fin du diff,
            // perdant ainsi le changement de programme. La mise à jour directe du
            // ViewHolder garantit que le nom, le clavier et la couleur sont corrects
            // même si le diff est annulé.
            val newInstrumentName = formatInstrumentName(program)
            affectedTrackIndices.forEach { trackIndex ->
                viewHolderMap[trackIndex]?.let { holder ->
                    holder.instrumentName.text = newInstrumentName
                    holder.pianoKeyboard.setCurrentProgram(program)
                    val channelColor = ColorSettingsManager.getNoteColor(channel, program)
                    (holder.channelColor.background as? GradientDrawable)?.setColor(channelColor)
                }
            }
        }
    }

    /**
     * Formate le nom d'instrument avec son numéro de programme MIDI
     * Ex: "0: Piano Acoustique"
     */
    private fun formatInstrumentName(program: Int): String {
        val name = GeneralMidiInstruments.getName(context, program)
        return "$program: $name"
    }

    /**
     * Met à jour la plage de notes pour tous les canaux
     */
    fun updateNoteRange(noteRangeMin: Int, noteRangeMax: Int) {
        val currentList = currentList.toMutableList()
        for (i in currentList.indices) {
            currentList[i] = currentList[i].copy(
                noteRangeMin = noteRangeMin,
                noteRangeMax = noteRangeMax
            )
        }
        submitList(currentList)

        // Mettre à jour aussi les claviers déjà affichés
        viewHolderMap.values.forEach { holder ->
            holder.pianoKeyboard.setNoteRange(noteRangeMin, noteRangeMax)
        }
    }

    private class TrackDiffCallback : DiffUtil.ItemCallback<TrackState>() {
        override fun areItemsTheSame(oldItem: TrackState, newItem: TrackState): Boolean {
            return oldItem.trackIndex == newItem.trackIndex
        }

        override fun areContentsTheSame(oldItem: TrackState, newItem: TrackState): Boolean {
            // Ne pas comparer activeNoteCount ni volume pour éviter les refreshs constants
            return oldItem.trackIndex == newItem.trackIndex &&
                    oldItem.channel == newItem.channel &&
                    oldItem.program == newItem.program &&
                    oldItem.currentProgram == newItem.currentProgram &&
                    oldItem.instrumentName == newItem.instrumentName &&
                    oldItem.trackName == newItem.trackName &&
                    oldItem.isKeyboardVisible == newItem.isKeyboardVisible &&
                    oldItem.isMuted == newItem.isMuted &&
                    oldItem.noteRangeMin == newItem.noteRangeMin &&
                    oldItem.noteRangeMax == newItem.noteRangeMax &&
                    oldItem.programCount == newItem.programCount
        }
    }
}
