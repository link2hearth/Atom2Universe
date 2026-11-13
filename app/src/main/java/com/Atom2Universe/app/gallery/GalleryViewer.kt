package com.Atom2Universe.app.gallery

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.comics.ZoomableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun LinearLayout.action(icon: Int, label: Int, click: () -> Unit): ImageButton {
    val button = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(context.galleryColor(R.attr.a2uMidiAccent))
        contentDescription = context.getString(label)
        tooltipText = contentDescription
        background = AppearanceStyle.drawable(context, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
        val padding = context.dp(12)
        setPadding(padding, padding, padding, padding)
        setOnClickListener { click() }
    }
    addView(button, context.dp(48), context.dp(48))
    return button
}

/** [compact] is for a file name beside other buttons; the large size is for screen titles. */
internal fun LinearLayout.heading(compact: Boolean = false): TextView = TextView(context).also {
    it.textSize = if (compact) (if (context.tablet()) 17f else 15f) else if (context.tablet()) 24f else 20f
    it.setTypeface(it.typeface, Typeface.BOLD)
    it.setTextColor(context.galleryColor(R.attr.a2uTextColor))
    it.setSingleLine()
    it.ellipsize = TextUtils.TruncateAt.END
    it.setPadding(context.dp(6), 0, context.dp(6), 0)
    addView(it, LinearLayout.LayoutParams(0, -2, 1f))
}

internal class GalleryViewer(
    context: Context, private val scope: CoroutineScope, items: List<Photo>, initial: Int,
    back: () -> Unit, favorite: (Photo) -> Unit, share: (Photo) -> Unit,
    remove: (Photo) -> Unit, edit: (Photo) -> Unit
) : FrameLayout(context) {
    private val photos = items.toMutableList()
    private val pager = ViewPager2(context)
    private fun bar() = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        background = AppearanceStyle.drawable(context, R.attr.a2uPanelBackground)
    }
    private val top = bar()
    private val name: TextView
    private val heart: ImageButton
    private val counter = TextView(context).apply {
        textSize = if (context.tablet()) 16f else 14f
        setTextColor(context.galleryColor(R.attr.a2uSecondaryTextColor))
        setPadding(0, 0, context.dp(12), 0)
    }
    val current: Photo get() = photos[pager.currentItem]
    var controlsVisible: Boolean
        get() = top.visibility == VISIBLE
        set(value) {
            top.visibility = if (value) VISIBLE else GONE
        }
    init {
        setBackgroundColor(Color.BLACK)
        addView(pager, LayoutParams(-1, -1))
        top.action(R.drawable.ic_arrow_back_24, R.string.back, back)
        name = top.heading(compact = true)
        top.addView(counter)
        top.action(R.drawable.ic_edit_24, R.string.photo_edit) { edit(current) }
        heart = top.action(R.drawable.ic_star_outline_24, R.string.gallery_favorite_add) { favorite(current) }
        top.action(R.drawable.ic_share, R.string.gallery_share) { share(current) }
        top.action(R.drawable.ic_delete, R.string.gallery_delete) { remove(current) }
        addView(top, LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(context.dp(8), context.dp(8), context.dp(8), 0) })
        pager.adapter = Pages()
        pager.setCurrentItem(initial, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateActions(position) }
        })
        updateActions(initial)
    }
    private fun updateActions(index: Int) {
        val photo = photos[index]
        name.text = photo.name
        counter.text = context.getString(R.string.gallery_position, index + 1, photos.size)
        heart.setImageResource(if (photo.favorite) R.drawable.ic_star_filled_24 else R.drawable.ic_star_outline_24)
        heart.contentDescription = context.getString(if (photo.favorite) R.string.gallery_favorite_remove else R.string.gallery_favorite_add)
        heart.tooltipText = heart.contentDescription
    }
    fun release() { pager.adapter = null }
    fun updateFavorite(photo: Photo) {
        val index = photos.indexOfFirst { it.id == photo.id }
        if (index < 0) return
        photos[index] = photo
        if (index == pager.currentItem) updateActions(index)
    }

    private inner class Page(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        val image = ZoomableImageView(context).apply {
            insidePager = true
            onTap = { controlsVisible = !controlsVisible }
        }
        val spinner = ProgressBar(context)
        val error = TextView(context).apply {
            setText(R.string.gallery_unreadable)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        var job: Job? = null
        init {
            frame.layoutParams = RecyclerView.LayoutParams(-1, -1)
            frame.addView(image, LayoutParams(-1, -1))
            frame.addView(spinner, LayoutParams(context.dp(36), context.dp(36), Gravity.CENTER))
            frame.addView(error, LayoutParams(-1, -2, Gravity.CENTER))
        }
    }
    private inner class Pages : RecyclerView.Adapter<Page>() {
        override fun getItemCount() = photos.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Page(FrameLayout(context))
        override fun onBindViewHolder(holder: Page, position: Int) = with(holder) {
            job?.cancel()
            val photo = photos[position]
            image.setBitmap(null)
            image.contentDescription = photo.name
            spinner.visibility = VISIBLE
            error.visibility = GONE
            val display = resources.displayMetrics
            job = scope.launch {
                val bitmap = GalleryImages.screen(context, photo.uri, maxOf(display.widthPixels, display.heightPixels))
                image.setBitmap(bitmap)
                spinner.visibility = GONE
                error.visibility = if (bitmap == null) VISIBLE else GONE
            }
        }
        override fun onViewRecycled(holder: Page) {
            holder.job?.cancel()
            holder.image.setBitmap(null)
        }
    }
}
