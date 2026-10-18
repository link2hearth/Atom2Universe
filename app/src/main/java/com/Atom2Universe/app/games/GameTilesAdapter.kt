package com.Atom2Universe.app.games

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

class GameTilesAdapter(
    private val games: List<GameTile>,
    private val onGameClick: (GameTile) -> Unit
) : RecyclerView.Adapter<GameTilesAdapter.GameTileViewHolder>() {

    class GameTileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.game_icon)
        val title: TextView = itemView.findViewById(R.id.game_title)
        val description: TextView = itemView.findViewById(R.id.game_description)
        val status: TextView = itemView.findViewById(R.id.game_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameTileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_tile, parent, false)
        return GameTileViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameTileViewHolder, position: Int) {
        val game = games[position]
        val context = holder.itemView.context

        holder.icon.setImageResource(game.iconResId)
        holder.title.text = context.getString(game.titleResId)
        holder.description.text = context.getString(game.descriptionResId)

        // Show status if present
        if (game.statusResId != null) {
            holder.status.text = context.getString(game.statusResId)
            holder.status.visibility = View.VISIBLE
        } else {
            holder.status.visibility = View.GONE
        }

        // Handle click
        holder.itemView.setOnClickListener {
            onGameClick(game)
        }

        // Dim if not available
        holder.itemView.alpha = if (game.activityClass != null) 1.0f else 0.6f
    }

    override fun getItemCount(): Int = games.size
}
