package com.Atom2Universe.app.games.cards

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.SystemBarsManager
import com.Atom2Universe.app.util.followImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Visionnage indépendant de la sélection des collections pour les jeux. */
class CardBackGalleryDialog : DialogFragment() {
    private var pager: ViewPager2? = null
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getInt(POSITION) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        com.Atom2Universe.app.util.ImmersiveDialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setCanceledOnTouchOutside(false)
            followImmersiveMode()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val family = CardBacks.families.first { it.preferenceId == requireArguments().getString(FAMILY) }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val types = WindowInsetsCompat.Type.displayCutout() or
                if (SystemBarsManager.shouldShowSystemBars(context)) WindowInsetsCompat.Type.systemBars() else 0
            val safe = insets.getInsets(types)
            view.setPadding(dp(8) + safe.left, dp(8) + safe.top, dp(8) + safe.right, dp(8) + safe.bottom)
            insets
        }
        fun textView() = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        fun button(icon: Int, description: Int) = ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = getString(description)
            val attr = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, attr, true)
            setBackgroundResource(attr.resourceId)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = textView().apply {
            text = getString(family.labelRes)
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(button(R.drawable.ic_close, R.string.settings_card_gallery_close).apply {
            setOnClickListener { dismiss() }
        })
        root.addView(header)
        val gallery = ViewPager2(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        pager = gallery
        root.addView(gallery)
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val previous = button(R.drawable.ic_chevron_left, R.string.settings_card_gallery_previous)
        val next = button(R.drawable.ic_chevron_right, R.string.settings_card_gallery_next)
        val counter = textView().apply {
            text = getString(R.string.settings_card_gallery_loading)
            textSize = 16f
            ViewCompat.setAccessibilityLiveRegion(this, View.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        previous.isEnabled = false
        next.isEnabled = false
        footer.addView(previous)
        footer.addView(counter, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(next)
        root.addView(footer)
        previous.setOnClickListener { gallery.setCurrentItem(gallery.currentItem - 1, true) }
        next.setOnClickListener { gallery.setCurrentItem(gallery.currentItem + 1, true) }

        // Le cycle de vie de la vue est disponible à partir de onViewCreated.
        root.tag = GalleryViews(family, gallery, counter, previous, next)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = view.tag as GalleryViews
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val paths = withContext(Dispatchers.IO) { views.family.paths(context) }
            if (paths.isEmpty()) {
                views.counter.setText(R.string.settings_card_gallery_unavailable)
                return@launch
            }
            fun update(index: Int) {
                position = index
                views.counter.text = getString(R.string.settings_card_gallery_counter, index + 1, paths.size)
                views.previous.isEnabled = index > 0
                views.next.isEnabled = index < paths.lastIndex
                views.previous.alpha = if (views.previous.isEnabled) 1f else 0.3f
                views.next.alpha = if (views.next.isEnabled) 1f else 0.3f
            }
            val startIndex = position.coerceIn(paths.indices)
            views.pager.adapter = GalleryAdapter(paths)
            views.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(index: Int) = update(index)
            })
            views.pager.setCurrentItem(startIndex, false)
            update(views.pager.currentItem)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        view?.let { ViewCompat.requestApplyInsets(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(POSITION, pager?.takeIf { it.adapter != null }?.currentItem ?: position)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        pager?.adapter = null
        pager = null
        super.onDestroyView()
    }

    private data class GalleryViews(
        val family: CardBacks.Family, val pager: ViewPager2, val counter: TextView,
        val previous: ImageButton, val next: ImageButton
    )

    private class Page(val frame: FrameLayout, val image: ImageView, val loading: ProgressBar,
                       val error: TextView) : RecyclerView.ViewHolder(frame) {
        var job: Job? = null
    }

    private inner class GalleryAdapter(private val paths: List<String>) : RecyclerView.Adapter<Page>() {
        override fun getItemCount() = paths.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page {
            val context = parent.context
            val frame = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val loading = ProgressBar(context)
            val size = (32 * resources.displayMetrics.density).toInt()
            frame.addView(loading, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            val error = TextView(context).apply {
                setText(R.string.settings_card_gallery_unavailable)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
            frame.addView(error, FrameLayout.LayoutParams(-1, -1))
            return Page(frame, image, loading, error)
        }

        override fun onBindViewHolder(holder: Page, index: Int) {
            holder.job?.cancel()
            holder.image.setImageDrawable(null)
            holder.image.contentDescription = getString(R.string.settings_card_gallery_counter, index + 1, paths.size)
            holder.loading.visibility = View.VISIBLE
            holder.error.visibility = View.GONE
            val context = holder.frame.context.applicationContext
            val maxEdge = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels).coerceAtMost(2048)
            holder.job = viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { CardBacks.loadPreview(context, paths[index], maxEdge) }
                holder.image.setImageBitmap(bitmap)
                holder.loading.visibility = View.GONE
                holder.error.visibility = if (bitmap == null) View.VISIBLE else View.GONE
            }
        }

        override fun onViewRecycled(holder: Page) {
            holder.job?.cancel()
            holder.job = null
            holder.image.setImageDrawable(null)
        }
    }

    companion object {
        const val TAG = "card_back_gallery"
        private const val FAMILY = "family"
        private const val POSITION = "position"
        fun forFamily(family: CardBacks.Family) = CardBackGalleryDialog().apply {
            arguments = Bundle().apply { putString(FAMILY, family.preferenceId) }
        }
    }
}
