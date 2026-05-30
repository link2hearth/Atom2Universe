package com.Atom2Universe.app.games.caves

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class CaveWorldMenuActivity : ThemedActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private val worlds = mutableListOf<CaveWorldSave>()
    private lateinit var adapter: WorldAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_cave_menu)

        recycler  = findViewById(R.id.cave_menu_recycler)
        emptyView = findViewById(R.id.cave_menu_empty)

        adapter = WorldAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.cave_menu_btn_back).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.cave_menu_fab_new).setOnClickListener {
            showCreateDialog()
        }
        findViewById<FloatingActionButton>(R.id.cave_menu_fab_controls).setOnClickListener {
            startActivity(Intent(this, CaveControlsEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        worlds.clear()
        worlds.addAll(CaveWorldSaveManager.listWorlds(this))
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (worlds.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility  = if (worlds.isEmpty()) View.GONE   else View.VISIBLE
    }

    private fun showCreateDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_cave_new_world, null)
        val etName = view.findViewById<TextInputEditText>(R.id.cave_dialog_name)
        val etSeed = view.findViewById<TextInputEditText>(R.id.cave_dialog_seed)

        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_menu_new_world)
            .setView(view)
            .setPositiveButton(R.string.cave_menu_create) { _, _ ->
                val name = etName.text.toString().trim()
                    .ifEmpty { getString(R.string.cave_menu_default_name) }
                val seed = etSeed.text.toString().toLongOrNull() ?: System.currentTimeMillis()
                val save = CaveWorldSaveManager.createWorld(this, name, seed)
                launchWorld(save)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(save: CaveWorldSave) {
        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_menu_delete_confirm_title)
            .setMessage(getString(R.string.cave_menu_delete_confirm_msg, save.name))
            .setPositiveButton(R.string.cave_menu_delete_world) { _, _ ->
                CaveWorldSaveManager.deleteWorld(this, save.id)
                Toast.makeText(this, R.string.cave_menu_deleted, Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchWorld(save: CaveWorldSave) {
        startActivity(Intent(this, CaveActivity::class.java).apply {
            putExtra(CaveActivity.EXTRA_WORLD_ID, save.id)
        })
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private inner class WorldAdapter : RecyclerView.Adapter<WorldAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name:   TextView    = view.findViewById(R.id.cave_world_name)
            val seed:   TextView    = view.findViewById(R.id.cave_world_seed)
            val date:   TextView    = view.findViewById(R.id.cave_world_date)
            val delete: ImageButton = view.findViewById(R.id.cave_world_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_cave_world, parent, false))

        override fun getItemCount() = worlds.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val save = worlds[position]
            holder.name.text = save.name
            holder.seed.text = getString(R.string.cave_menu_seed_label, save.seed)
            holder.date.text = getString(R.string.cave_menu_last_played, save.formattedLastPlayed())
            holder.itemView.setOnClickListener { launchWorld(save) }
            holder.delete.setOnClickListener   { showDeleteDialog(save) }
        }
    }
}
