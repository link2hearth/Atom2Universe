package com.Atom2Universe.app.sf2creator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class combining project entity with its sample count.
 */
data class ProjectWithCount(
    val project: Sf2ProjectEntity,
    val sampleCount: Int
)

/**
 * Adapter for displaying SF2 projects in a RecyclerView.
 */
class ProjectAdapter(
    private val onProjectClick: (Sf2ProjectEntity) -> Unit,
    private val onDeleteClick: (Sf2ProjectEntity) -> Unit
) : ListAdapter<ProjectWithCount, ProjectAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val projectName: TextView = itemView.findViewById(R.id.project_name)
        private val sampleCount: TextView = itemView.findViewById(R.id.sample_count)
        private val modifiedDate: TextView = itemView.findViewById(R.id.modified_date)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(item: ProjectWithCount) {
            projectName.text = item.project.name
            sampleCount.text = itemView.context.getString(
                R.string.sf2_samples_count,
                item.sampleCount
            )
            modifiedDate.text = dateFormat.format(Date(item.project.modifiedAt))

            itemView.setOnClickListener {
                onProjectClick(item.project)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(item.project)
            }
        }
    }

    private class ProjectDiffCallback : DiffUtil.ItemCallback<ProjectWithCount>() {
        override fun areItemsTheSame(oldItem: ProjectWithCount, newItem: ProjectWithCount): Boolean {
            return oldItem.project.id == newItem.project.id
        }

        override fun areContentsTheSame(oldItem: ProjectWithCount, newItem: ProjectWithCount): Boolean {
            return oldItem == newItem
        }
    }
}
