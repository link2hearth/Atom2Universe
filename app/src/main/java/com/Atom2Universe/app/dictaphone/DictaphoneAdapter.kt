package com.Atom2Universe.app.dictaphone

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DictaphoneAdapter(
    private val onPlayPause: (DictaphoneRecording) -> Unit,
    private val onRename: (DictaphoneRecording) -> Unit,
    private val onDelete: (DictaphoneRecording) -> Unit
) : ListAdapter<DictaphoneRecording, DictaphoneAdapter.ViewHolder>(DIFF) {

    private var playingUri: Uri? = null
    private var isPlaying: Boolean = false

    fun updatePlayback(uri: Uri?, playing: Boolean) {
        val oldUri = playingUri
        playingUri = uri
        isPlaying = playing
        if (oldUri != uri) {
            currentList.indexOfFirst { it.uri == oldUri }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }
        currentList.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dictaphone_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.dictaphone_item_name)
        private val tvMeta: TextView = itemView.findViewById(R.id.dictaphone_item_meta)
        private val btnPlay: ImageButton = itemView.findViewById(R.id.dictaphone_item_play)
        private val btnRename: ImageButton = itemView.findViewById(R.id.dictaphone_item_rename)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.dictaphone_item_delete)

        fun bind(recording: DictaphoneRecording) {
            tvName.text = recording.displayName
            tvMeta.text = buildMeta(recording)

            val isThisPlaying = playingUri == recording.uri && isPlaying
            btnPlay.setImageResource(if (isThisPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            btnPlay.setOnClickListener { onPlayPause(recording) }
            btnRename.setOnClickListener { onRename(recording) }
            btnDelete.setOnClickListener { onDelete(recording) }
        }

        private fun buildMeta(r: DictaphoneRecording): String {
            val dur = formatDuration(r.durationMs)
            val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(r.dateAddedSec * 1000))
            return "$dur · $date"
        }

        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "--:--"
            val min = TimeUnit.MILLISECONDS.toMinutes(ms)
            val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            return "%d:%02d".format(min, sec)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DictaphoneRecording>() {
            override fun areItemsTheSame(a: DictaphoneRecording, b: DictaphoneRecording) = a.uri == b.uri
            override fun areContentsTheSame(a: DictaphoneRecording, b: DictaphoneRecording) = a == b
        }
    }
}
