package com.Atom2Universe.app.cloud.projects

import android.content.res.ColorStateList
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.cloud.CloudActivity
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Ce que les galeries de projets (pixel art, canvas…) montrent du cloud : le bouton « Google sync »
 * d'une tuile, la question quand deux versions existent, la liste « Importer depuis le cloud ».
 *
 * Les opérations Drive sont lancées en [NonCancellable] : quitter la galerie en plein envoi ne doit pas
 * les couper entre « Drive a le fichier » et « on l'a noté », sinon le projet se prendrait ensuite en
 * conflit avec lui-même. Seul l'affichage du résultat, lui, suit la vie de l'écran.
 */
object CloudProjectsUi {

    private val warnRed = 0xFFFF6B6B.toInt()

    /** Vrai si l'on est connecté ; sinon ouvre l'écran Cloud, où l'on se connecte, et retourne faux. */
    private fun requireSignedIn(activity: AppCompatActivity): Boolean {
        if (GoogleSignInManager(activity).isSignedIn()) return true
        activity.startActivity(CloudActivity.intent(activity))
        return false
    }

    // ─── Le bouton « Google sync » d'une tuile ────────────────────────────────

    /**
     * @param onChanged appelé quand la galerie doit se relire (un projet est arrivé, ou a changé de pastille)
     */
    fun syncProject(activity: AppCompatActivity, adapter: CloudProjectAdapter, id: String, name: String, onChanged: () -> Unit) {
        if (!requireSignedIn(activity)) return
        toast(activity, R.string.cloud_proj_syncing)
        activity.lifecycleScope.launch {
            val outcome = withContext(NonCancellable) { CloudProjectSync.sync(activity, adapter, id) }
            handle(activity, adapter, id, name, outcome, onChanged)
        }
    }

    private fun handle(
        activity: AppCompatActivity,
        adapter: CloudProjectAdapter,
        id: String,
        name: String,
        outcome: CloudProjectSync.Outcome,
        onChanged: () -> Unit,
    ) {
        when (outcome) {
            CloudProjectSync.Outcome.UpToDate -> toast(activity, R.string.cloud_proj_uptodate)
            CloudProjectSync.Outcome.Uploaded -> { toast(activity, R.string.cloud_proj_uploaded); onChanged() }
            CloudProjectSync.Outcome.Downloaded -> { toast(activity, R.string.cloud_proj_downloaded); onChanged() }
            CloudProjectSync.Outcome.KeptBoth -> { toast(activity, R.string.cloud_proj_kept_both); onChanged() }
            CloudProjectSync.Outcome.NotSignedIn -> activity.startActivity(CloudActivity.intent(activity))
            CloudProjectSync.Outcome.Failed -> toast(activity, R.string.cloud_proj_failed)
            CloudProjectSync.Outcome.Unreadable -> toast(activity, R.string.cloud_proj_unreadable)
            is CloudProjectSync.Outcome.Conflict -> askConflict(activity, adapter, id, name, outcome.remote, onChanged)
        }
    }

    /** Les deux côtés ont bougé : seule la personne sait lequel est le bon, ou si les deux le sont. */
    private fun askConflict(
        activity: AppCompatActivity,
        adapter: CloudProjectAdapter,
        id: String,
        name: String,
        remote: CloudProject,
        onChanged: () -> Unit,
    ) {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        activity.lifecycleScope.launch {
            val localModified = withContext(Dispatchers.IO) { adapter.localModified(id) } ?: return@launch
            val message = activity.getString(
                R.string.cloud_proj_conflict_message,
                name,
                fmt.format(Date(localModified)),
                fmt.format(Date(remote.driveModified)),
                remote.deviceName.ifBlank { "?" },
            )
            fun resolve(choice: CloudProjectSync.Choice) {
                toast(activity, R.string.cloud_proj_syncing)
                activity.lifecycleScope.launch {
                    val outcome = withContext(NonCancellable) {
                        CloudProjectSync.resolve(activity, adapter, id, remote, choice, activity.getString(R.string.cloud_proj_copy_suffix))
                    }
                    handle(activity, adapter, id, name, outcome, onChanged)
                }
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cloud_proj_conflict_title)
                .setMessage(message)
                .setPositiveButton(R.string.cloud_proj_keep_local) { _, _ -> resolve(CloudProjectSync.Choice.KEEP_LOCAL) }
                .setNeutralButton(R.string.cloud_proj_keep_both) { _, _ -> resolve(CloudProjectSync.Choice.KEEP_BOTH) }
                .setNegativeButton(R.string.cloud_proj_keep_cloud) { _, _ -> resolve(CloudProjectSync.Choice.KEEP_CLOUD) }
                .show()
        }
    }

    // ─── Retirer du cloud ─────────────────────────────────────────────────────

    fun removeFromCloud(activity: AppCompatActivity, module: CloudModule, id: String, name: String, onChanged: () -> Unit) {
        if (!requireSignedIn(activity)) return
        activity.confirm(
            R.string.cloud_proj_remove_title,
            activity.getString(R.string.cloud_proj_remove_message, name),
            R.string.cloud_delete,
            destructive = true,
        ) {
            activity.lifecycleScope.launch {
                val ok = withContext(NonCancellable) { CloudProjectSync.removeFromCloud(activity, module, id) }
                toast(activity, if (ok) R.string.cloud_proj_removed else R.string.cloud_proj_failed)
                if (ok) onChanged()
            }
        }
    }

    // ─── « Importer depuis le cloud » ─────────────────────────────────────────

    /**
     * La liste des projets du cloud : un appui apporte le projet (ou le met à jour s'il est déjà là, avec
     * la même question qu'un conflit), la poubelle de chaque ligne supprime sa copie cloud.
     */
    fun showImportSheet(activity: AppCompatActivity, adapter: CloudProjectAdapter, onChanged: () -> Unit) {
        if (!requireSignedIn(activity)) return
        val module = adapter.module
        lateinit var box: LinearLayout
        lateinit var sheet: com.google.android.material.bottomsheet.BottomSheetDialog
        sheet = activity.bottomSheet(activity.getString(R.string.cloud_proj_import_title)) { root, _ ->
            box = root
            root.addView(activity.label(activity.getString(R.string.cloud_proj_loading), 14f))
        }
        sheet.show()

        activity.lifecycleScope.launch {
            val listed = CloudProjectSync.listCloud(activity, module)
            val rows = listed?.let { list ->
                withContext(Dispatchers.IO) { list.map { it to (adapter.localModified(it.id) != null) } }
            }
            // Le titre reste ; le « Chargement… » (dernier ajouté) laisse la place à la liste.
            box.removeViewAt(box.childCount - 1)
            when {
                rows == null -> { sheet.dismiss(); toast(activity, R.string.cloud_proj_failed) }
                rows.isEmpty() -> box.addView(activity.label(activity.getString(R.string.cloud_proj_import_empty), 14f))
                else -> for ((project, here) in rows) box.addView(importRow(activity, adapter, sheet, box, project, here, onChanged))
            }
        }
    }

    private fun importRow(
        activity: AppCompatActivity,
        adapter: CloudProjectAdapter,
        sheet: com.google.android.material.bottomsheet.BottomSheetDialog,
        box: LinearLayout,
        project: CloudProject,
        here: Boolean,
        onChanged: () -> Unit,
    ): LinearLayout {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = activity.dp(56)
            setPadding(activity.dp(4), 0, 0, 0)
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_px_tool, activity.theme)
        }
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(activity.label(project.name, 15f, true))
        texts.addView(
            activity.label(
                activity.getString(
                    R.string.cloud_proj_import_meta,
                    Formatter.formatShortFileSize(activity, project.size),
                    fmt.format(Date(project.driveModified)),
                ),
                12f,
            ),
        )
        row.addView(texts)
        if (here) row.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_px_check)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.audio_text_secondary))
            layoutParams = LinearLayout.LayoutParams(activity.dp(20), activity.dp(20)).apply { marginEnd = activity.dp(4) }
        })
        row.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_px_delete)
            imageTintList = ColorStateList.valueOf(warnRed)
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_px_tool, activity.theme)
            contentDescription = activity.getString(R.string.cloud_proj_remove)
            layoutParams = LinearLayout.LayoutParams(activity.dp(44), activity.dp(44))
            setOnClickListener {
                activity.confirm(
                    R.string.cloud_proj_remove_title,
                    activity.getString(R.string.cloud_proj_remove_message, project.name),
                    R.string.cloud_delete,
                    destructive = true,
                ) {
                    activity.lifecycleScope.launch {
                        val ok = withContext(NonCancellable) { CloudProjectSync.removeFromCloud(activity, project.module, project.id) }
                        if (ok) { box.removeView(row); onChanged() }
                        toast(activity, if (ok) R.string.cloud_proj_removed else R.string.cloud_proj_failed)
                    }
                }
            }
        })
        row.setOnClickListener {
            sheet.dismiss()
            if (here) {
                syncProject(activity, adapter, project.id, project.name, onChanged)
            } else {
                toast(activity, R.string.cloud_proj_syncing)
                activity.lifecycleScope.launch {
                    val outcome = withContext(NonCancellable) { CloudProjectSync.fetch(activity, adapter, project) }
                    handle(activity, adapter, project.id, project.name, outcome, onChanged)
                }
            }
        }
        return row
    }

    private fun toast(activity: AppCompatActivity, res: Int) {
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    }
}
