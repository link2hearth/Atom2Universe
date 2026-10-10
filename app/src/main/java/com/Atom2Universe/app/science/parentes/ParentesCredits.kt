package com.Atom2Universe.app.science.parentes

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The hub's About page owns the module's bibliography and provenance. */
object ParentesCredits {
    fun show(activity: FragmentActivity) = with(activity) {
        val palette = SciencePalette(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun paragraph(value: String, bold: Boolean = false, target: LinearLayout = body) {
            target.addView(TextView(this).apply {
                text = value; textSize = 14f; setTextColor(palette.text)
                setPadding(0, padding / 2, 0, padding / 2)
                setTextIsSelectable(true)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            })
        }
        fun link(title: String, url: String, target: LinearLayout = body) {
            target.addView(androidx.appcompat.widget.AppCompatButton(this).apply {
                text = title; isAllCaps = false
                setTextColor(palette.ink(palette.accent)); background = null
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(activity, R.string.pt_no_browser, Toast.LENGTH_LONG).show() }
                }
            })
        }
        fun readingCredits() {
            paragraph(getString(R.string.pt_read_source_method))
            ParentesReading.sections.forEach { section ->
                paragraph(getString(section.title), true)
                link(getString(R.string.pt_read_source), section.url)
            }
        }
        paragraph(getString(R.string.pt_loading))
        val dialog = com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(R.string.pt_about_title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.pt_close, null).create()
        dialog.show()
        val job = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching {
                val repo = ParentesRepository.load(applicationContext)
                repo to ParentesStories.load(applicationContext, repo.tree)
            } }
            if (!dialog.isShowing) return@launch
            body.removeAllViews()
            result.onSuccess { (repo, stories) ->
                readingCredits()
                paragraph(getString(R.string.pt_version, repo.version, repo.taxonomy, repo.retrieved,
                    repo.tree.species.size, repo.groups.size, repo.tree.nodes.size), true)
                paragraph(getString(R.string.pt_method))
                body.addView(androidx.appcompat.widget.AppCompatButton(activity).apply {
                    setText(R.string.ct_life_dating_credits); isAllCaps = false
                    setOnClickListener { com.Atom2Universe.app.science.timeline.TimelineCredits.show(activity) }
                })
                paragraph(getString(R.string.pt_license_note))
                repo.sources.forEach { source ->
                    link(source.citation, source.url)
                    val license = if (source.id == "opentree") getString(R.string.pt_cc0_scope) else source.license
                    link(getString(R.string.pt_license, license), source.licenseUrl)
                }
                body.addView(androidx.appcompat.widget.AppCompatButton(activity).apply {
                    text=getString(R.string.pt_story_credits); isAllCaps=false
                    setOnClickListener {
                        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(activity).setTitle(R.string.pt_story_credits)
                            .setItems(stories.stories.map { repo.text(activity,it.title) }.toTypedArray()) { _,index ->
                                val story=stories.stories[index]
                                val refs=LinearLayout(activity).apply {
                                    orientation=LinearLayout.VERTICAL; setPadding(padding,padding,padding,padding)
                                }
                                paragraph(getString(R.string.pt_story_art_credit),target=refs)
                                paragraph(getString(R.string.pt_story_limits),target=refs)
                                val sources=(repo.sources+stories.sources).associateBy { it.id }
                                story.chapters.forEach { chapter ->
                                    paragraph(repo.text(activity,chapter.title),true,refs)
                                    chapter.sources.forEach { id ->
                                        val source=sources.getValue(id)
                                        link(source.citation,source.url,refs)
                                    }
                                    link(getString(R.string.pt_node_id,chapter.fork),
                                        "https://tree.opentreeoflife.org/opentree/${repo.version}@${chapter.fork}",refs)
                                }
                                com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(activity).setTitle(repo.text(activity,story.title))
                                    .setView(ScrollView(activity).apply { addView(refs) })
                                    .setPositiveButton(R.string.pt_close,null).show()
                            }.setNegativeButton(R.string.pt_close,null).show()
                    }
                })
                body.addView(androidx.appcompat.widget.AppCompatButton(activity).apply {
                    text=getString(R.string.pt_species_references); isAllCaps=false
                    setOnClickListener {
                        val entries=repo.tree.nodes.values.filter { it.species || repo.tree.isBrowsable(it.id) }
                            .sortedWith(compareBy(java.text.Collator.getInstance(resources.configuration.locales[0])) { repo.name(activity,it.id) })
                        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(activity).setTitle(R.string.pt_species_references)
                            .setItems(entries.map { repo.name(activity,it.id) }.toTypedArray()) { _,index ->
                                val node=entries[index]
                                val refs=LinearLayout(activity).apply {
                                    orientation=LinearLayout.VERTICAL; setPadding(padding,padding,padding,padding)
                                }
                                paragraph(node.scientific,true,refs)
                                link(getString(R.string.pt_node_id,node.id),"https://tree.opentreeoflife.org/opentree/${repo.version}@${node.id}",refs)
                                paragraph(getString(if(node.support.any { '@' in it }) R.string.pt_support_study else R.string.pt_support_taxonomy),target=refs)
                                if(node.conflicts.isNotEmpty()) paragraph(getString(R.string.pt_conflicts),target=refs)
                                paragraph(getString(R.string.pt_taxon_sources,node.taxSources.joinToString()),target=refs)
                                (node.support+node.conflicts).filter { '@' in it }.distinct().forEach { study ->
                                    link(getString(R.string.pt_study_link,study),"https://tree.opentreeoflife.org/curator/study/view/${study.substringBefore('@')}",refs)
                                }
                                repo.labels[node.id]?.let { note ->
                                    link(getString(R.string.pt_wikidata_ref,note.qid,note.revision),
                                        "https://www.wikidata.org/w/index.php?title=${note.qid}&oldid=${note.revision}",refs)
                                }
                                val group=repo.tree.ancestors(node.id).firstOrNull { it in repo.groups }
                                repo.groups[group]?.sources.orEmpty().forEach { sourceId ->
                                    val source=repo.sources.first { it.id==sourceId }
                                    link(source.citation,source.url,refs)
                                }
                                com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(activity).setTitle(repo.name(activity,node.id))
                                    .setView(ScrollView(activity).apply { addView(refs) }).setPositiveButton(R.string.pt_close,null).show()
                            }.setNegativeButton(R.string.pt_close,null).show()
                    }
                })
            }.onFailure { readingCredits(); paragraph(getString(R.string.pt_error)) }
        }
        dialog.setOnDismissListener { job.cancel() }
    }
}
