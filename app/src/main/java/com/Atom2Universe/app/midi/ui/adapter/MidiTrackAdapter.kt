package com.Atom2Universe.app.midi.ui.adapter

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer
import com.Atom2Universe.app.midi.data.MidiTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter pour afficher la liste des tracks MIDI
 * Supporte:
 * - Affichage des favoris avec une étoile cliquable
 * - Ajout à une playlist via le bouton +
 * - Section d'infos extensible au clic sur le bouton info
 */
class MidiTrackAdapter(
    private val onTrackClick: (MidiTrack) -> Unit,
    private val onFavoriteClick: ((MidiTrack, Boolean) -> Unit)? = null,
    private val onAddToPlaylistClick: ((MidiTrack) -> Unit)? = null,
    private val onLongClick: ((MidiTrack, View) -> Boolean)? = null
) : ListAdapter<MidiTrack, MidiTrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    // Set des IDs de tracks favoris
    private var favoriteTrackIds: Set<Long> = emptySet()

    // ID du track actuellement étendu (-1L si aucun)
    private var expandedTrackId: Long = -1L

    // Track actuellement en cours de lecture
    private var nowPlayingTrackId: Long? = null
    private var isPlaybackActive: Boolean = false

    // Cache des infos MIDI chargées (par trackId)
    private val infoCache = mutableMapOf<Long, MidiFileAnalyzer.MidiFileInfo>()

    // Jobs de chargement en cours
    private val loadingJobs = mutableMapOf<Long, Job>()

    /**
     * Met à jour la liste des favoris
     */
    fun setFavorites(favorites: Set<Long>) {
        val oldFavorites = favoriteTrackIds
        favoriteTrackIds = favorites

        // Notifier les changements pour les items affectés
        currentList.forEachIndexed { index, track ->
            val wasInFavorites = oldFavorites.contains(track.id)
            val isInFavorites = favorites.contains(track.id)
            if (wasInFavorites != isInFavorites) {
                notifyItemChanged(index, PAYLOAD_FAVORITE_CHANGED)
            }
        }
    }

    /**
     * Collapse l'item actuellement étendu
     */
    fun collapseAll() {
        if (expandedTrackId != -1L) {
            val oldExpandedId = expandedTrackId
            expandedTrackId = -1L
            val position = currentList.indexOfFirst { it.id == oldExpandedId }
            if (position >= 0) {
                notifyItemChanged(position, PAYLOAD_COLLAPSE)
            }
        }
    }

    /**
     * Met à jour l'indicateur du track en cours de lecture
     */
    fun setNowPlaying(trackId: Long?, isPlaying: Boolean) {
        val previousTrackId = nowPlayingTrackId
        val previousPlaying = isPlaybackActive
        nowPlayingTrackId = trackId
        isPlaybackActive = isPlaying

        if (previousTrackId != trackId) {
            previousTrackId?.let { previous ->
                val oldPosition = currentList.indexOfFirst { it.id == previous }
                if (oldPosition >= 0) {
                    notifyItemChanged(oldPosition, PAYLOAD_PLAYBACK_STATE)
                }
            }
            trackId?.let { current ->
                val newPosition = currentList.indexOfFirst { it.id == current }
                if (newPosition >= 0) {
                    notifyItemChanged(newPosition, PAYLOAD_PLAYBACK_STATE)
                }
            }
        } else if (previousPlaying != isPlaying && trackId != null) {
            val position = currentList.indexOfFirst { it.id == trackId }
            if (position >= 0) {
                notifyItemChanged(position, PAYLOAD_PLAYBACK_STATE)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_midi_track, parent, false)
        return TrackViewHolder(view, parent.context)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = getItem(position)
        val isFavorite = favoriteTrackIds.contains(track.id)
        val isExpanded = track.id == expandedTrackId
        val cachedInfo = infoCache[track.id]
        holder.bind(track, isFavorite, isExpanded, cachedInfo)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val track = getItem(position)

        payloads.forEach { payload ->
            when (payload) {
                PAYLOAD_FAVORITE_CHANGED -> {
                    val isFavorite = favoriteTrackIds.contains(track.id)
                    holder.updateFavoriteIcon(isFavorite)
                }
                PAYLOAD_COLLAPSE -> {
                    holder.collapse()
                }
                PAYLOAD_EXPAND -> {
                    holder.expand()
                }
                PAYLOAD_INFO_LOADED -> {
                    val info = infoCache[track.id]
                    if (info != null) {
                        holder.showInfo(info)
                    }
                }
                PAYLOAD_PLAYBACK_STATE -> {
                    val isNowPlaying = isPlaybackActive && track.id == nowPlayingTrackId
                    holder.updatePlaybackIndicator(isNowPlaying)
                }
            }
        }
    }

    inner class TrackViewHolder(
        itemView: View,
        private val context: Context
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val artistText: TextView = itemView.findViewById(R.id.track_artist)
        private val albumText: TextView = itemView.findViewById(R.id.track_album)
        private val trackIcon: ImageView = itemView.findViewById(R.id.track_icon)
        private val favoriteButton: ImageButton = itemView.findViewById(R.id.btn_favorite)
        private val addToPlaylistButton: ImageButton = itemView.findViewById(R.id.btn_add_to_playlist)
        private val menuButton: ImageButton = itemView.findViewById(R.id.btn_menu)

        // Info section
        private val infoSection: LinearLayout = itemView.findViewById(R.id.info_section)
        private val infoLoading: LinearLayout = itemView.findViewById(R.id.info_loading)
        private val infoError: TextView = itemView.findViewById(R.id.info_error)
        private val infoContent: LinearLayout = itemView.findViewById(R.id.info_content)
        private val infoSummary: TextView = itemView.findViewById(R.id.info_summary)
        private val infoFile: TextView = itemView.findViewById(R.id.info_file)
        private val infoTempo: TextView = itemView.findViewById(R.id.info_tempo)
        private val infoInstruments: TextView = itemView.findViewById(R.id.info_instruments)
        private val infoCopyright: TextView = itemView.findViewById(R.id.info_copyright)

        private var currentTrack: MidiTrack? = null
        private var isFavorite: Boolean = false

        init {
            itemView.setOnClickListener {
                currentTrack?.let { onTrackClick(it) }
            }

            itemView.setOnLongClickListener { view ->
                currentTrack?.let { track ->
                    onLongClick?.invoke(track, view) ?: false
                } ?: false
            }

            favoriteButton.setOnClickListener {
                currentTrack?.let { track ->
                    val newState = !isFavorite
                    onFavoriteClick?.invoke(track, newState)
                }
            }

            addToPlaylistButton.setOnClickListener {
                currentTrack?.let { track ->
                    onAddToPlaylistClick?.invoke(track)
                }
            }

            menuButton.setOnClickListener {
                currentTrack?.let { track ->
                    toggleExpand(track)
                }
            }
        }

        private fun toggleExpand(track: MidiTrack) {
            val wasExpanded = track.id == expandedTrackId

            // Collapse l'ancien item étendu
            if (expandedTrackId != -1L && expandedTrackId != track.id) {
                val oldPosition = currentList.indexOfFirst { it.id == expandedTrackId }
                expandedTrackId = -1L
                if (oldPosition >= 0) {
                    notifyItemChanged(oldPosition, PAYLOAD_COLLAPSE)
                }
            }

            if (wasExpanded) {
                // Collapse cet item
                expandedTrackId = -1L
                collapse()
            } else {
                // Expand cet item
                expandedTrackId = track.id
                expand()
                loadInfoIfNeeded(track)
            }
        }

        fun expand() {
            infoSection.visibility = View.VISIBLE
            menuButton.setImageResource(android.R.drawable.arrow_up_float)
        }

        fun collapse() {
            infoSection.visibility = View.GONE
            menuButton.setImageResource(android.R.drawable.ic_menu_info_details)
        }

        private fun loadInfoIfNeeded(track: MidiTrack) {
            // Si déjà en cache, afficher directement
            val cachedInfo = infoCache[track.id]
            if (cachedInfo != null) {
                showInfo(cachedInfo)
                return
            }

            // Annuler le job précédent si en cours
            loadingJobs[track.id]?.cancel()

            // Afficher le loading
            infoLoading.visibility = View.VISIBLE
            infoError.visibility = View.GONE
            infoContent.visibility = View.GONE

            // Charger en arrière-plan
            loadingJobs[track.id] = CoroutineScope(Dispatchers.Main).launch {
                val info = withContext(Dispatchers.IO) {
                    MidiFileAnalyzer(context).analyze(track.filePath)
                }

                // Mettre en cache
                infoCache[track.id] = info

                // Afficher si toujours étendu
                if (expandedTrackId == track.id) {
                    showInfo(info)
                }
            }
        }

        fun showInfo(info: MidiFileAnalyzer.MidiFileInfo) {
            infoLoading.visibility = View.GONE

            if (info.error != null) {
                infoError.text = info.error
                infoError.visibility = View.VISIBLE
                infoContent.visibility = View.GONE
            } else {
                infoError.visibility = View.GONE
                infoContent.visibility = View.VISIBLE

                // Résumé
                infoSummary.text = info.summary

                // Infos fichier
                val fileInfo = "${info.midiTypeDescription} • ${info.trackCount} pistes • ${info.resolution} PPQ • ${info.formattedFileSize}"
                infoFile.text = fileInfo

                // Tempo
                val tempoInfo = buildString {
                    append("Tempo: ${info.mainBpm.toInt()} BPM")
                    if (info.tempoCount > 1) {
                        append(" (${info.tempoCount} changements)")
                    }
                    if (info.timeSignature.isNotEmpty()) {
                        append(" • Mesure: ${info.timeSignature}")
                    }
                }
                infoTempo.text = tempoInfo

                // Instruments
                infoInstruments.text = info.instrumentsList

                // Copyright
                if (info.copyright != null) {
                    infoCopyright.text = "© ${info.copyright}"
                    infoCopyright.visibility = View.VISIBLE
                } else {
                    infoCopyright.visibility = View.GONE
                }
            }
        }

        fun bind(track: MidiTrack, favorite: Boolean, expanded: Boolean, cachedInfo: MidiFileAnalyzer.MidiFileInfo?) {
            currentTrack = track
            isFavorite = favorite
            titleText.text = track.title
            artistText.text = track.artist
            albumText.text = track.album
            updateFavoriteIcon(favorite)
            updatePlaybackIndicator(isPlaybackActive && track.id == nowPlayingTrackId)

            // Afficher/masquer le bouton add to playlist selon si le callback est défini
            addToPlaylistButton.visibility = if (onAddToPlaylistClick != null) View.VISIBLE else View.GONE

            // Gérer l'état étendu
            if (expanded) {
                expand()
                if (cachedInfo != null) {
                    showInfo(cachedInfo)
                } else {
                    loadInfoIfNeeded(track)
                }
            } else {
                collapse()
            }
        }

        fun updatePlaybackIndicator(isPlaying: Boolean) {
            val color = if (isPlaying) {
                // En lecture : utiliser l'accent du thème
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)
                typedValue.data
            } else {
                // Inactif : gris clair
                ContextCompat.getColor(context, R.color.midi_track_icon_idle)
            }
            trackIcon.setColorFilter(color)
        }

        fun updateFavoriteIcon(favorite: Boolean) {
            isFavorite = favorite
            favoriteButton.setImageResource(
                if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
    }

    private class TrackDiffCallback : DiffUtil.ItemCallback<MidiTrack>() {
        override fun areItemsTheSame(oldItem: MidiTrack, newItem: MidiTrack): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MidiTrack, newItem: MidiTrack): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val PAYLOAD_FAVORITE_CHANGED = "favorite_changed"
        private const val PAYLOAD_COLLAPSE = "collapse"
        private const val PAYLOAD_EXPAND = "expand"
        private const val PAYLOAD_INFO_LOADED = "info_loaded"
        private const val PAYLOAD_PLAYBACK_STATE = "playback_state"
    }
}
