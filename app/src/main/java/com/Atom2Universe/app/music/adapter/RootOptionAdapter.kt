package com.Atom2Universe.app.music.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.RootDisplayMode

/**
 * Option affichée au niveau racine de la navigation
 */
data class RootOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int
) {
    companion object {
        const val OPTION_ARTISTS = "artists"
        const val OPTION_ALBUM_ARTISTS = "album_artists"
        const val OPTION_ALL_ALBUMS = "all_albums"
        const val OPTION_FOLDERS = "folders"
        const val OPTION_SEARCH = "search"
        const val OPTION_PLAYLISTS = "playlists"
        const val OPTION_FAVORITE_ARTISTS = "favorite_artists"
        const val OPTION_FAVORITE_ALBUMS = "favorite_albums"
        const val OPTION_NAVIDROME = "navidrome"
    }
}

class RootOptionAdapter(
    private val onOptionClick: (RootOption) -> Unit
) : ListAdapter<RootOption, RecyclerView.ViewHolder>(DiffCallback()) {

    // Mode d'affichage actuel
    var displayMode: RootDisplayMode = RootDisplayMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_COMPACT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayMode) {
            RootDisplayMode.LIST -> VIEW_TYPE_LIST
            RootDisplayMode.COMPACT -> VIEW_TYPE_COMPACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMPACT -> {
                val view = inflater.inflate(R.layout.item_root_option_compact, parent, false)
                CompactViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_root_option, parent, false)
                ListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val option = getItem(position)
        when (holder) {
            is ListViewHolder -> holder.bind(option, onOptionClick)
            is CompactViewHolder -> holder.bind(option, onOptionClick)
        }
    }

    // ========== LIST VIEW HOLDER (mode cartes) ==========

    class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.option_icon)
        private val title: TextView = itemView.findViewById(R.id.option_title)
        private val subtitle: TextView = itemView.findViewById(R.id.option_subtitle)

        fun bind(option: RootOption, onClick: (RootOption) -> Unit) {
            icon.setImageResource(option.iconResId)
            title.text = option.title
            subtitle.text = option.subtitle

            itemView.setOnClickListener {
                onClick(option)
            }
        }
    }

    // ========== COMPACT VIEW HOLDER (mode compact) ==========

    class CompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.option_icon)
        private val title: TextView = itemView.findViewById(R.id.option_title)
        private val subtitle: TextView = itemView.findViewById(R.id.option_subtitle)

        fun bind(option: RootOption, onClick: (RootOption) -> Unit) {
            icon.setImageResource(option.iconResId)
            title.text = option.title
            // En mode compact, extraire juste le nombre du subtitle
            val countOnly = option.subtitle.filter { it.isDigit() }
            subtitle.text = countOnly.ifEmpty { option.subtitle }

            itemView.setOnClickListener {
                onClick(option)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RootOption>() {
        override fun areItemsTheSame(oldItem: RootOption, newItem: RootOption): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RootOption, newItem: RootOption): Boolean {
            return oldItem == newItem
        }
    }
}
