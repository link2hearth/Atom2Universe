package com.Atom2Universe.app.pixelart.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.EditorSession

/** Réduit des pixels ARGB en une vignette (plus proche voisin : le pixel art reste net). */
object Thumbs {
    fun make(pixels: IntArray, w: Int, h: Int, max: Int): Bitmap {
        val s = maxOf(w, h)
        if (s <= max) return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
        val tw = maxOf(1, w * max / s)
        val th = maxOf(1, h * max / s)
        val out = IntArray(tw * th)
        for (y in 0 until th) {
            val sy = (y.toLong() * h / th).toInt()
            for (x in 0 until tw) out[y * tw + x] = pixels[sy * w + (x.toLong() * w / tw).toInt()]
        }
        return Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, tw, 0, 0, tw, th) }
    }

    fun ofFrame(doc: Document, frameId: Int, max: Int = 96): Bitmap {
        val s = maxOf(doc.width, doc.height)
        val tw = if (s <= max) doc.width else maxOf(1, doc.width * max / s)
        val th = if (s <= max) doc.height else maxOf(1, doc.height * max / s)
        return Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also {
            it.setPixels(Compositor.compositeScaled(doc, frameId, tw, th), 0, tw, 0, 0, tw, th)
        }
    }

    fun ofLayer(doc: Document, layerId: Int, frameId: Int, max: Int = 64): Bitmap {
        val cel = doc.cel(layerId, frameId) ?: IntArray(doc.pixelCount)
        return make(cel, doc.width, doc.height, max)
    }
}

// ---- Palette (barre de couleurs) -------------------------------------------------------------

class PaletteAdapter(
    private val onPick: (Int, Int) -> Unit,
    private val onMenu: (View, Int, Int) -> Unit,
    private val onAdd: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var colors: List<Int> = emptyList()
    /** La case qui porte le liseré (la couleur principale). */
    var ringIndex = -1
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    fun submit(list: List<Int>) {
        if (list == colors) return
        colors = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = colors.size + 1
    override fun getItemViewType(position: Int) = if (position == colors.size) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val c = parent.context
        val size = c.dp(32)
        return if (viewType == 1) {
            val b = ImageButton(c).apply {
                setImageResource(R.drawable.ic_px_add)
                background = androidx.core.content.res.ResourcesCompat.getDrawable(resources, R.drawable.bg_px_chip, c.theme)
                imageTintList = androidx.core.content.ContextCompat.getColorStateList(c, R.color.px_icon_tint)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = c.getString(R.string.px_palette_add_color)
                layoutParams = RecyclerView.LayoutParams(size, size).apply { setMargins(c.dp(3), c.dp(11), c.dp(3), c.dp(11)) }
            }
            object : RecyclerView.ViewHolder(b) {}
        } else {
            val s = SwatchView(c).apply {
                layoutParams = RecyclerView.LayoutParams(size, size).apply { setMargins(c.dp(3), c.dp(11), c.dp(3), c.dp(11)) }
            }
            object : RecyclerView.ViewHolder(s) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == colors.size) {
            holder.itemView.setOnClickListener { onAdd() }
            return
        }
        val color = colors[position]
        val v = holder.itemView as SwatchView
        v.color = color
        v.ring = position == ringIndex
        v.setOnClickListener { holder.bindingAdapterPosition.takeIf { it >= 0 }?.let { onPick(it, colors[it]) } }
        v.setOnLongClickListener { holder.bindingAdapterPosition.takeIf { it >= 0 }?.let { onMenu(v, it, colors[it]) }; true }
    }
}

// ---- Calques -----------------------------------------------------------------------------------

class LayersAdapter(
    private val session: () -> EditorSession,
    private val onSelect: (Int) -> Unit,
    private val onToggleVisible: (Int) -> Unit,
    private val onToggleLock: (Int) -> Unit,
    private val onOpenProperties: (Int) -> Unit,
    private val onReorder: (Int, Int) -> Unit,
) : RecyclerView.Adapter<LayersAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.layer_thumb)
        val name: TextView = v.findViewById(R.id.layer_name)
        val info: TextView = v.findViewById(R.id.layer_info)
        val visible: ImageButton = v.findViewById(R.id.layer_visible)
        val lock: ImageButton = v.findViewById(R.id.layer_lock)
    }

    private val thumbs = HashMap<Int, Bitmap>()

    override fun getItemCount() = session().doc.layers.size

    /** La liste montre le calque du dessus en premier : la position d'écran est l'inverse de l'indice. */
    private fun layerIndex(position: Int) = session().doc.layers.size - 1 - position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_px_layer, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val s = session()
        val idx = layerIndex(position)
        val layer = s.doc.layers[idx]
        val ctx = h.itemView.context
        h.itemView.isSelected = idx == s.layerIndex
        h.name.text = layer.name
        h.info.text = if (layer.opacity < 255) ctx.getString(R.string.px_layer_info_opacity, layer.opacity * 100 / 255) else ""
        h.info.visibility = if (h.info.text.isEmpty()) View.GONE else View.VISIBLE
        h.visible.setImageResource(if (layer.visible) R.drawable.ic_px_eye else R.drawable.ic_px_eye_off)
        h.visible.alpha = if (layer.visible) 1f else 0.5f
        h.lock.setImageResource(if (layer.locked) R.drawable.ic_px_lock else R.drawable.ic_px_unlock)
        h.lock.alpha = if (layer.locked) 1f else 0.4f
        h.thumb.background = ctx.checkerDrawable(ctx.dp(4))
        val bmp = thumbs.getOrPut(layer.id) { Thumbs.ofLayer(s.doc, layer.id, s.activeFrameId) }
        h.thumb.setImageDrawable(ctx.pixelDrawable(bmp))
        // Un appui sur le calque déjà actif ouvre ses réglages ; l'appui long le déplace.
        h.itemView.setOnClickListener { if (idx == s.layerIndex) onOpenProperties(idx) else onSelect(idx) }
        h.visible.setOnClickListener { onToggleVisible(idx) }
        h.lock.setOnClickListener { onToggleLock(idx) }
    }

    fun refreshThumb(layerId: Int) {
        thumbs.remove(layerId)
        val s = session()
        val i = s.doc.layerIndexOf(layerId)
        if (i >= 0) notifyItemChanged(s.doc.layers.size - 1 - i)
    }

    /** Tout est à refaire (changement d'image active, annulation, structure). */
    fun refreshAll() {
        thumbs.clear()
        notifyDataSetChanged()
    }

    /** Glisser-déposer : la poignée est toute la ligne, appui long pour saisir. */
    fun touchHelper(): ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        private var dragFrom = -1
        private var dragTo = -1

        override fun isLongPressDragEnabled() = true

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (dragFrom < 0) dragFrom = layerIndex(vh.bindingAdapterPosition)
            dragTo = layerIndex(target.bindingAdapterPosition)
            notifyItemMoved(vh.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (dragFrom >= 0 && dragFrom != dragTo) onReorder(dragFrom, dragTo)
            dragFrom = -1; dragTo = -1
        }
    })
}

// ---- Images (timeline) -----------------------------------------------------------------------

class FramesAdapter(
    private val session: () -> EditorSession,
    private val onSelect: (Int) -> Unit,
) : RecyclerView.Adapter<FramesAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.frame_thumb)
        val index: TextView = v.findViewById(R.id.frame_index)
    }

    private val thumbs = HashMap<Int, Bitmap>()
    /** Image en cours de lecture (mise en avant pendant l'animation), ou -1. */
    var playing = -1
        set(v) {
            val old = field
            field = v
            if (old >= 0) notifyItemChanged(old)
            if (v >= 0) notifyItemChanged(v)
        }

    override fun getItemCount() = session().doc.frames.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_px_frame, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val s = session()
        val frame = s.doc.frames[position]
        val ctx = h.itemView.context
        h.itemView.isSelected = if (playing >= 0) position == playing else position == s.frameIndex
        h.index.text = (position + 1).toString()
        h.thumb.background = ctx.checkerDrawable(ctx.dp(4))
        val bmp = thumbs.getOrPut(frame.id) { Thumbs.ofFrame(s.doc, frame.id) }
        h.thumb.setImageDrawable(ctx.pixelDrawable(bmp))
        h.itemView.setOnClickListener { onSelect(position) }
    }

    fun refreshThumb(frameId: Int) {
        thumbs.remove(frameId)
        val i = session().doc.frameIndexOf(frameId)
        if (i >= 0) notifyItemChanged(i)
    }

    fun refreshAll() {
        thumbs.clear()
        notifyDataSetChanged()
    }

    fun touchHelper(): ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
        private var from = -1
        private var to = -1
        override fun isLongPressDragEnabled() = true
        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (from < 0) from = vh.bindingAdapterPosition
            to = target.bindingAdapterPosition
            notifyItemMoved(vh.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }
        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (from >= 0 && from != to) onMoved?.invoke(from, to)
            from = -1; to = -1
        }
    })

    var onMoved: ((Int, Int) -> Unit)? = null
}
