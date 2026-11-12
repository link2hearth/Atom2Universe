package com.Atom2Universe.app.gallery

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.util.LruCache
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.MusicPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.reading.CoverThumbs
import kotlin.math.roundToInt

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
internal fun Context.galleryColor(attr: Int) = AppearanceStyle.color(this, attr)
internal fun Context.tablet() = resources.configuration.smallestScreenWidthDp >= 600

internal class GalleryGrid(context: Context, initialColumns: Int, private val save: (Int) -> Unit) : RecyclerView(context) {
    val columns = StaggeredGridLayoutManager(initialColumns, VERTICAL)
    var zoomEnabled = true
    private var pinching = false
    private var needsPacking = false
    private var draggingPhoto = false
    private val pack = Runnable {
        if (needsPacking && !draggingPhoto && !pinching && scrollState == SCROLL_STATE_IDLE) {
            needsPacking = false
            columns.invalidateSpanAssignments()
        }
    }

    fun aspectDiscovered() {
        needsPacking = true
        removeCallbacks(pack)
        postDelayed(pack, 80)
    }

    fun dragging(value: Boolean) {
        draggingPhoto = value
        if (!value && needsPacking) post(pack)
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE && needsPacking) post(pack)
    }
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var factor = 1f
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            factor = 1f
            return zoomEnabled
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            factor *= detector.scaleFactor
            val change = when {
                factor < 0.8f -> 1
                factor > 1.25f -> -1
                else -> return true
            }
            factor = 1f
            val count = (columns.spanCount + change).coerceIn(MusicPreferences.MIN_TILE_COLUMNS, MusicPreferences.MAX_TILE_COLUMNS)
            if (count != columns.spanCount) {
                val anchor = findChildViewUnder(detector.focusX, detector.focusY)
                val position = anchor?.let(::getChildAdapterPosition) ?: NO_POSITION
                columns.spanCount = count
                if (position != NO_POSITION) columns.scrollToPositionWithOffset(position, anchor!!.top - paddingTop)
                adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
                save(count)
            }
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    init {
        layoutManager = columns
        itemAnimator = null
        clipToPadding = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return super.dispatchTouchEvent(event)
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && !pinching) {
            pinching = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (!pinching) return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pinching = false
            if (needsPacking) post(pack)
        }
        return true
    }
}

internal class GalleryTileView(context: Context) : FrameLayout(context) {
    var aspect = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspect).roundToInt().coerceIn(context.dp(40), maxOf(width * 5, context.dp(40)))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}

internal class GalleryTiles(
    private val scope: CoroutineScope, private val click: (GalleryTile) -> Unit,
    private val favorite: (Photo) -> Unit, private val selectPhoto: (Photo) -> Unit,
    private val edit: (Photo) -> Unit, private val manualOrder: (List<GalleryTile>) -> Unit,
    private val holdFolder: (Folder) -> Boolean
) : RecyclerView.Adapter<GalleryTiles.Holder>() {
    var showCovers = false
    var inTrash = false
    var selected: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            val previous = field
            field = value
            if (previous.isEmpty() || value.isEmpty()) notifyItemRangeChanged(0, itemCount, "controls")
            else for (id in (previous - value) + (value - previous)) {
                val index = tiles.indexOfFirst { it is Photo && it.id == id }
                if (index >= 0) notifyItemChanged(index, "controls")
            }
        }
    private val aspects = LruCache<String, Float>(6000)
    private var dragged = false
    private val drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        override fun isLongPressDragEnabled() = false
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            if (tiles.getOrNull(a) !is Photo || tiles.getOrNull(b) !is Photo) return false
            entries.add(b, entries.removeAt(a))
            notifyItemMoved(a, b)
            dragged = true
            return true
        }
        override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(holder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder is Holder) {
                holder.grid.dragging(true)
                holder.itemView.alpha = 0.85f
            }
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && dragged) {
                manualOrder(tiles)
                dragged = false
            }
        }
        override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
            super.clearView(rv, holder)
            holder.itemView.alpha = 1f
            (rv as GalleryGrid).dragging(false)
        }
    })
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) = drag.attachToRecyclerView(recyclerView)
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) = drag.attachToRecyclerView(null)
    private val entries = mutableListOf<GalleryTile>()
    val tiles: List<GalleryTile> get() = entries
    private var byId = mutableMapOf<String, Photo>()
    fun photos(ids: Set<String>): List<Photo> = ids.mapNotNull(byId::get)

    fun display(items: List<GalleryTile>) {
        entries.clear()
        entries.addAll(items)
        byId = items.filterIsInstance<Photo>().associateByTo(mutableMapOf()) { it.id }
        notifyDataSetChanged()
    }

    fun updateFavorite(photo: Photo) {
        val index = tiles.indexOfFirst { it is Photo && it.id == photo.id }
        if (index < 0) return
        entries[index] = photo
        byId[photo.id] = photo
        notifyItemChanged(index, "controls")
    }

    class Holder(val tile: GalleryTileView, val grid: GalleryGrid) : RecyclerView.ViewHolder(tile) {
        val context = tile.context
        val preview = ImageView(context)
        val glyph = ImageView(context).apply {
            imageTintList = ColorStateList.valueOf(context.galleryColor(R.attr.a2uMidiAccent))
        }
        val label = TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            textSize = if (context.tablet()) 18f else 15f
            setTextColor(context.galleryColor(R.attr.a2uTextColor))
            setPadding(context.dp(4), 0, context.dp(4), context.dp(8))
        }
        private fun badge() = ImageView(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xA6000000.toInt()) }
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            val pad = context.dp(5)
            setPadding(pad, pad, pad, pad)
        }
        val star = badge()
        val more = badge().apply { setImageResource(R.drawable.ic_more_vert_24) }
        val check = badge()
        var hold: Runnable? = null
        var suppressClick = false
        fun cancelHold() { hold?.let(tile::removeCallbacks); hold = null }
        var task: Job? = null
        init {
            tile.isFocusable = true
            tile.clipToOutline = true
            tile.foreground = AppearanceStyle.drawable(context, androidx.appcompat.R.attr.selectableItemBackground)
            tile.addView(preview, FrameLayout.LayoutParams(-1, -1))
            tile.addView(glyph, FrameLayout.LayoutParams(context.dp(38), context.dp(38), Gravity.CENTER))
            tile.addView(label, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            for (button in listOf(star, more, check)) tile.addView(button)
        }
    }

    override fun getItemCount() = tiles.size
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(GalleryTileView(parent.context), parent as GalleryGrid)
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        val photo = tiles[position] as? Photo
        if (payloads.isNotEmpty() && photo != null) controls(holder, photo)
        else onBindViewHolder(holder, position)
    }

    private fun controls(holder: Holder, photo: Photo?) = with(holder) {
        val selecting = selected.isNotEmpty()
        star.visibility = if (photo != null && !selecting && !inTrash) View.VISIBLE else View.GONE
        more.visibility = if (photo != null && !selecting) View.VISIBLE else View.GONE
        check.visibility = if (photo != null && selecting) View.VISIBLE else View.GONE
        tile.isSelected = photo != null && photo.id in selected
        if (photo == null) return@with
        val size = (grid.width / grid.columns.spanCount / 2 - context.dp(3)).coerceIn(context.dp(16), context.dp(30))
        fun corner(offset: Int) = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(2); marginEnd = context.dp(2) + offset
        }
        star.layoutParams = corner(size + context.dp(1))
        more.layoutParams = corner(0)
        check.layoutParams = corner(0)
        star.setImageResource(if (photo.favorite) R.drawable.ic_star_filled_24 else R.drawable.ic_star_outline_24)
        star.imageTintList = ColorStateList.valueOf(if (photo.favorite) context.galleryColor(R.attr.a2uMidiAccent) else Color.WHITE)
        star.contentDescription = context.getString(if (photo.favorite) R.string.gallery_favorite_remove else R.string.gallery_favorite_add)
        star.setOnClickListener { favorite(photo) }
        more.contentDescription = context.getString(R.string.gallery_options, photo.name)
        more.setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                if (!inTrash) {
                    setForceShowIcon(true)
                    menu.add(R.string.photo_edit).setIcon(R.drawable.ic_edit_24).setOnMenuItemClickListener { edit(photo); true }
                }
                menu.add(R.string.gallery_select).setOnMenuItemClickListener { selectPhoto(photo); true }
            }.show()
        }
        val checked = photo.id in selected
        check.setImageResource(if (checked) R.drawable.ic_check else 0)
        (check.background as GradientDrawable).apply {
            setColor(if (checked) context.galleryColor(R.attr.a2uMidiAccent) else 0x68000000)
            setStroke(context.dp(1), Color.WHITE)
        }
        check.imageTintList = ColorStateList.valueOf(if (checked) context.galleryColor(R.attr.a2uOnAccentColor) else Color.WHITE)
    }

    private fun watchHold(holder: Holder, photo: Photo?) = with(holder) {
        cancelHold()
        if (photo == null || inTrash) { tile.setOnTouchListener(null); return@with }
        var x = 0f
        var y = 0f
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        tile.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (selected.isEmpty()) {
                    suppressClick = false
                    cancelHold()
                    x = event.x
                    y = event.y
                    hold = Runnable {
                        if (bindingAdapterPosition != RecyclerView.NO_POSITION && selected.isEmpty()) {
                            suppressClick = true
                            tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            drag.startDrag(holder)
                        }
                    }.also { tile.postDelayed(it, 1000) }
                }
                MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - x) > slop || kotlin.math.abs(event.y - y) > slop) cancelHold()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelHold()
            }
            false
        }
    }
    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder) {
        val item = tiles[position]
        val photo = item as? Photo
        val folder = item as? Folder
        val spacing = context.dp(if (photo == null) 5 else 1)
        tile.layoutParams = StaggeredGridLayoutManager.LayoutParams(-1, -2).apply { setMargins(spacing, spacing, spacing, spacing) }
        tile.background = AppearanceStyle.drawable(context, if (photo == null) R.attr.a2uProjectBackground else R.attr.a2uPanelBackground)
        tile.setPadding(0, 0, 0, 0)
        preview.scaleType = if (photo == null) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        val aspectKey = photo?.let { "${it.uri}|${it.modified}" }
        tile.aspect = aspectKey?.let(aspects::get) ?: 1f
        preview.setImageDrawable(null)
        glyph.visibility = View.VISIBLE
        glyph.setImageResource(when (item) {
            is Folder -> R.drawable.ic_folder_open
            is Photo -> R.drawable.ic_px_image
            AddFolder -> R.drawable.ic_add_24
        })
        label.visibility = if (folder == null) View.GONE else View.VISIBLE
        label.text = folder?.name
        label.background = null
        controls(holder, photo)
        watchHold(holder, photo)
        tile.contentDescription = photo?.name ?: folder?.name ?: context.getString(R.string.gallery_add)
        suppressClick = false
        tile.setOnClickListener {
            if (!suppressClick || selected.isNotEmpty()) click(item)
            suppressClick = false
        }
        tile.setOnLongClickListener(if (folder == null) null else View.OnLongClickListener { holdFolder(folder) })
        tile.isLongClickable = folder != null
        task?.cancel()
        if (photo != null || (showCovers && folder != null)) {
            val size = grid.width / grid.columns.spanCount
            task = scope.launch {
                val source = photo ?: withContext(CoverThumbs.dispatcher) {
                    runCatching { GalleryLibrary.get(context).contents(requireNotNull(folder), coverOnly = true).firstOrNull() as? Photo }.getOrNull()
                }
                source?.let { GalleryImages.thumbnail(context, it, size) }?.let {
                    if (aspectKey != null) {
                        val ratio = it.width.toFloat() / it.height
                        if (kotlin.math.abs(tile.aspect - ratio) > 0.01f) grid.aspectDiscovered()
                        tile.aspect = ratio
                        aspects.put(aspectKey, ratio)
                    }
                    preview.setImageBitmap(it)
                    glyph.visibility = View.GONE
                    if (folder != null) label.setBackgroundColor(context.galleryColor(R.attr.a2uSurfaceColor))
                }
            }
        }
    }
    override fun onViewRecycled(holder: Holder) {
        holder.cancelHold()
        holder.task?.cancel()
        holder.preview.setImageDrawable(null)
    }
}
