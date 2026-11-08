package com.Atom2Universe.app.studio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.music.Style
import com.Atom2Universe.app.studio.music.Styles
import com.Atom2Universe.app.studio.ui.StudioThumb
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * L'accueil du Studio : la liste des morceaux, chacun une carte (vignette = sa grille de rythme). On y crée un morceau
 * (un nom et un style de départ, ou vide), on en ouvre un, on le renomme, le duplique ou le supprime.
 */
class StudioLibraryActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val library by lazy { StudioLibrary(File(filesDir, "studio/songs")) }
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: SongsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_studio_library)

        list = findViewById(R.id.lib_list)
        empty = findViewById(R.id.lib_empty)
        list.layoutManager = GridLayoutManager(this, spanCount())
        adapter = SongsAdapter(this, ::openSong, ::showMenu)
        list.adapter = adapter

        findViewById<View>(R.id.lib_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.lib_fab_new).setOnClickListener { newSong() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 170f).toInt().coerceAtLeast(2)
    }

    private fun reload() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { library.list() }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openSong(id: String) {
        startActivity(Intent(this, StudioActivity::class.java).putExtra(EXTRA_PROJECT_ID, id))
    }

    /** « Nouveau » : un nom et un style de départ (ou « vide »), puis le morceau s'ouvre. */
    private fun newSong() {
        val default = getString(R.string.studio_default_name, adapter.itemCount + 1)
        // null = un morceau vide
        val styles: List<Style?> = Styles.ALL + listOf<Style?>(null)
        var chosen: Style? = Styles.ALL.first()
        bottomSheet(getString(R.string.studio_new_title)) { root, dlg ->
            val nameField = EditText(this).apply {
                setText(default)
                setSingleLine()
                hint = getString(R.string.studio_name_hint)
                setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
                setSelectAllOnFocus(true)
            }
            root.addView(nameField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            root.addView(label(getString(R.string.studio_start_from), 12f).apply { setPadding(dp(2), dp(16), 0, dp(6)) })
            val chips = ArrayList<Pair<Style?, TextView>>()
            fun select(s: Style?) {
                chosen = s
                chips.forEach { (v, c) -> c.isSelected = v == s }
            }
            val flow = com.Atom2Universe.app.studio.ui.FlowRow(this).apply { hGap = dp(8); vGap = dp(8) }
            for (s in styles) {
                val text = if (s == null) getString(R.string.studio_style_blank) else getString(StudioNames.style(s.id))
                val c = chip(text) { select(s) }
                chips.add(s to c)
                flow.addView(c)
            }
            select(chosen)
            root.addView(flow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            root.addView(primaryButton(getString(R.string.studio_create)) {
                val name = nameField.text.toString().trim().ifEmpty { default }
                val style = chosen
                dlg.dismiss()
                lifecycleScope.launch {
                    val entry = withContext(Dispatchers.IO) {
                        library.create(name, if (style == null) StudioProject.blank() else StudioProject.fromStyle(style))
                    }
                    openSong(entry.id)
                }
            }.also { (it.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = dp(16) })
        }.show()
    }

    private fun showMenu(item: StudioLibrary.Entry) {
        actionSheet(item.name, listOf(
            SheetItem(R.drawable.ic_px_pencil, getString(R.string.studio_open)) { openSong(item.id) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.studio_rename)) {
                promptText(R.string.studio_rename, item.name, android.R.string.ok) { name ->
                    if (name.isNotEmpty()) lifecycleScope.launch {
                        withContext(Dispatchers.IO) { library.rename(item.id, name) }
                        reload()
                    }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.studio_duplicate)) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { library.duplicate(item.id, getString(R.string.studio_copy_of, item.name)) }
                    reload()
                }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.studio_delete), destructive = true) {
                confirm(R.string.studio_delete, getString(R.string.studio_delete_message, item.name), R.string.studio_delete, true) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { library.delete(item.id) }
                        reload()
                    }
                }
            },
        )).show()
    }
}

private class SongsAdapter(
    private val context: Context,
    private val onOpen: (String) -> Unit,
    private val onMenu: (StudioLibrary.Entry) -> Unit,
) : RecyclerView.Adapter<SongsAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.project_thumb)
        val menu: View = v.findViewById(R.id.project_menu)
        val name: TextView = v.findViewById(R.id.project_name)
        val info: TextView = v.findViewById(R.id.project_info)
    }

    private var items: List<StudioLibrary.Entry> = emptyList()
    private val thumbs = object : LruCache<String, Bitmap>(48) {}

    fun submit(list: List<StudioLibrary.Entry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_audio_project, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val e = items[position]
        h.name.text = e.name
        h.info.text = context.getString(
            R.string.studio_card_info,
            e.project.bpm,
            context.getString(StudioNames.style(e.project.styleId)),
            if (System.currentTimeMillis() - e.modified < DateUtils.MINUTE_IN_MILLIS) context.getString(R.string.studio_just_now)
            else DateUtils.getRelativeTimeSpanString(e.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
        val key = e.id + e.modified
        val bmp = thumbs.get(key) ?: StudioThumb.render(e.project, 480, 168).also { thumbs.put(key, it) }
        h.thumb.setImageBitmap(bmp)
        h.itemView.setOnClickListener { onOpen(e.id) }
        h.itemView.setOnLongClickListener { onMenu(e); true }
        h.menu.setOnClickListener { onMenu(e) }
    }
}
