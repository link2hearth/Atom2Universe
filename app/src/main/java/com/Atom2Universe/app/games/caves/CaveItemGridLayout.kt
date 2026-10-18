package com.Atom2Universe.app.games.caves

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Calculate spans before laying out children, including the very first layout. */
internal class CaveItemGridLayout(context: Context) : GridLayoutManager(context,1) {
    private val pitch=CaveItemTile.pitch(context)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler,state: RecyclerView.State) {
        val columns=((width-paddingLeft-paddingRight)/pitch).coerceAtLeast(1)
        if(spanCount!=columns) spanCount=columns
        super.onLayoutChildren(recycler,state)
    }
}
