package com.Atom2Universe.app.music.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.FolderDisplayMode
import com.Atom2Universe.app.music.model.Folder

class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onFolderLongClick: ((Folder, View) -> Boolean)? = null,
    private val onPlayClick: ((Folder) -> Unit)? = null,
    private val onShuffleClick: ((Folder) -> Unit)? = null
) : ListAdapter<Folder, RecyclerView.ViewHolder>(FolderDiffCallback()) {

    // Current display mode
    var displayMode: FolderDisplayMode = FolderDisplayMode.LIST
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
            FolderDisplayMode.LIST -> VIEW_TYPE_LIST
            FolderDisplayMode.COMPACT -> VIEW_TYPE_COMPACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMPACT -> {
                val view = inflater.inflate(R.layout.item_music_folder_compact, parent, false)
                CompactViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_music_folder, parent, false)
                ListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val folder = getItem(position)

        when (holder) {
            is ListViewHolder -> holder.bind(folder)
            is CompactViewHolder -> holder.bind(folder)
        }

        holder.itemView.setOnClickListener { onFolderClick(folder) }
        holder.itemView.setOnLongClickListener { view ->
            onFolderLongClick?.invoke(folder, view) ?: false
        }
    }

    // ========== LIST VIEW HOLDER (mode avec icones) ==========

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon: ImageView = itemView.findViewById(R.id.folder_icon)
        private val folderName: TextView = itemView.findViewById(R.id.folder_name)
        private val folderInfo: TextView = itemView.findViewById(R.id.folder_info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)

        fun bind(folder: Folder) {
            folderName.text = folder.name
            folderInfo.text = buildInfoText(folder)

            // Set folder icon
            folderIcon.setImageResource(R.drawable.ic_folder_open)
            folderIcon.imageTintList = ContextCompat.getColorStateList(
                itemView.context, R.color.music_text_primary
            )

            // Quick action buttons
            btnPlay.setOnClickListener { onPlayClick?.invoke(folder) }
            btnShuffle.setOnClickListener { onShuffleClick?.invoke(folder) }
        }
    }

    // ========== COMPACT VIEW HOLDER (mode sans icones) ==========

    inner class CompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderName: TextView = itemView.findViewById(R.id.folder_name)
        private val folderInfo: TextView = itemView.findViewById(R.id.folder_info)

        fun bind(folder: Folder) {
            folderName.text = folder.name
            // In compact mode, show folder and track info on a single line
            folderInfo.text = buildInfoText(folder)
        }
    }

    /**
     * Builds the info text showing subfolder and track counts.
     */
    private fun buildInfoText(folder: Folder): String {
        val parts = mutableListOf<String>()

        if (folder.subfolderCount > 0) {
            parts.add("${folder.subfolderCount} \uD83D\uDCC1") // folder emoji
        }

        val trackCount = folder.totalTrackCount
        if (trackCount > 0) {
            parts.add("$trackCount \u266B") // music note emoji
        }

        return parts.joinToString(" • ")
    }

    class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem.path == newItem.path &&
                    oldItem.name == newItem.name &&
                    oldItem.trackCount == newItem.trackCount &&
                    oldItem.subfolderCount == newItem.subfolderCount
        }
    }
}
