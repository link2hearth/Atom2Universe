package com.Atom2Universe.app.music.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.view.AudioVisualizerView

class VizReorderAdapter(
    val items: MutableList<Pair<AudioVisualizerView.VisualizationMode, String>>
) : RecyclerView.Adapter<VizReorderAdapter.VH>() {

    var touchHelper: ItemTouchHelper? = null

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val position: TextView = view.findViewById(R.id.viz_position)
        val name: TextView = view.findViewById(R.id.viz_name)
        val handle: ImageView = view.findViewById(R.id.drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_viz_reorder, parent, false)
        return VH(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.position.text = (position + 1).toString()
        holder.name.text = items[position].second
        holder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchHelper?.startDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = items.size

    fun onItemMove(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        // Update position numbers for affected rows
        val start = minOf(from, to)
        val end = maxOf(from, to)
        notifyItemRangeChanged(start, end - start + 1)
    }
}
