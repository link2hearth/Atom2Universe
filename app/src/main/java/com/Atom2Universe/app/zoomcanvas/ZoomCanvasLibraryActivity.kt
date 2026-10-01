package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import com.Atom2Universe.app.zoomcanvas.data.ZoomProjectSummary
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La galerie des canvas infinis : chaque projet est une carte. */
class ZoomCanvasLibraryActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val store by lazy { ZoomCanvasStorage.store(this) }
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: ZoomProjectsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_zoom_canvas_library)

        list = findViewById(R.id.zc_lib_list)
        empty = findViewById(R.id.zc_lib_empty)
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        list.layoutManager = GridLayoutManager(this, (widthDp / 170f).toInt().coerceAtLeast(2))
        adapter = ZoomProjectsAdapter(this, ::openProject, ::showProjectMenu)
        list.adapter = adapter

        findViewById<View>(R.id.zc_lib_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.zc_lib_fab_new).setOnClickListener { newProject() }
    }

    /** Une fenêtre (menu, feuille, dialogue) vient de se fermer : on recache les barres système. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBarsVisibility()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            // L'éditeur qu'on vient de quitter écrit peut-être encore : on attend qu'il ait fini.
            ZoomCanvasStorage.writesInFlight.first { it == 0 }
            val items = withContext(Dispatchers.IO) { store.list() }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openProject(id: String) {
        startActivity(ZoomCanvasEditorActivity.intent(this, id))
    }

    /** Un nom suffit : tous les projets ont le même rapport d'échelle ([ZoomScene.DEFAULT_RATIO]). */
    private fun newProject() {
        bottomSheet(getString(R.string.zc_new_title)) { root, dialog ->
            val name = EditText(this).apply {
                setText(getString(R.string.zc_untitled_n, adapter.itemCount + 1))
                setSelectAllOnFocus(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine()
                setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
                hint = getString(R.string.zc_name)
            }
            root.addView(name)
            root.addView(primaryButton(getString(R.string.zc_create)) {
                dialog.dismiss()
                val n = name.text.toString().trim().ifEmpty { getString(R.string.zc_untitled_n, adapter.itemCount + 1) }
                lifecycleScope.launch {
                    val p = withContext(Dispatchers.IO) { store.create(n, ZoomScene.DEFAULT_RATIO) }
                    openProject(p.meta.id)
                }
            })
        }.show()
    }

    private fun showProjectMenu(item: ZoomProjectSummary) {
        actionSheet(item.name, listOf(
            SheetItem(R.drawable.ic_px_pencil, getString(R.string.px_open)) { openProject(item.id) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.px_rename)) {
                promptText(R.string.px_rename, item.name, R.string.px_ok) { name ->
                    if (name.isNotEmpty()) lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.rename(item.id, name) }
                        reload()
                    }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.px_duplicate)) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.duplicate(item.id, getString(R.string.px_copy_of, item.name)) }
                    reload()
                }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.px_delete), destructive = true) {
                confirm(R.string.px_delete_project_title, getString(R.string.px_delete_project_message, item.name), R.string.px_delete, true) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.delete(item.id) }
                        reload()
                    }
                }
            },
        )).show()
    }
}

private class ZoomProjectsAdapter(
    private val context: Context,
    private val onOpen: (String) -> Unit,
    private val onMenu: (ZoomProjectSummary) -> Unit,
) : RecyclerView.Adapter<ZoomProjectsAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.project_thumb)
        val badge: ImageView = v.findViewById(R.id.project_link_badge)
        val menu: View = v.findViewById(R.id.project_menu)
        val name: TextView = v.findViewById(R.id.project_name)
        val info: TextView = v.findViewById(R.id.project_info)
    }

    private var items: List<ZoomProjectSummary> = emptyList()
    private val thumbs = object : LruCache<String, Bitmap>(48) {}
    private val store = ZoomCanvasStorage.store(context)

    fun submit(list: List<ZoomProjectSummary>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_px_project, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = items[position]
        h.name.text = p.name
        h.info.text = context.getString(
            R.string.zc_project_info,
            context.resources.getQuantityString(R.plurals.zc_layers_count, p.layerCount, p.layerCount),
            Formatter.formatShortFileSize(context, p.sizeBytes),
            DateUtils.getRelativeTimeSpanString(p.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
        h.badge.visibility = View.GONE
        h.thumb.setBackgroundColor(0xFFFAF8F3.toInt())
        val key = p.id + p.modified
        val cached = thumbs.get(key)
        if (cached != null) h.thumb.setImageDrawable(BitmapDrawable(context.resources, cached)) else {
            h.thumb.setImageDrawable(null)
            val f = store.thumbFile(p.id)
            (context as? AppCompatActivity)?.lifecycleScope?.launch {
                val bmp = withContext(Dispatchers.IO) { if (f.isFile) BitmapFactory.decodeFile(f.path) else null }
                if (bmp != null) {
                    thumbs.put(key, bmp)
                    if (h.bindingAdapterPosition == position) h.thumb.setImageDrawable(BitmapDrawable(context.resources, bmp))
                }
            }
        }
        h.itemView.setOnClickListener { onOpen(p.id) }
        h.itemView.setOnLongClickListener { onMenu(p); true }
        h.menu.setOnClickListener { onMenu(p) }
    }
}
